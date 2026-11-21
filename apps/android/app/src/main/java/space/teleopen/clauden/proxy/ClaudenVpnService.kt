package space.teleopen.clauden.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import space.teleopen.clauden.MainActivity

class ClaudenVpnService : VpnService() {

    companion object {
        private const val TAG = "ClaudenVpn"

        const val ACTION_START = "space.teleopen.clauden.proxy.START"
        const val ACTION_STOP = "space.teleopen.clauden.proxy.STOP"
        const val ACTION_STATE = "space.teleopen.clauden.proxy.STATE"

        const val EXTRA_CONFIG = "config"
        const val EXTRA_NAME = "name"
        const val EXTRA_STATUS = "status"
        const val EXTRA_ERROR = "error"

        private const val CHANNEL = "clauden_vpn"
        private const val NOTIF_ID = 7311

        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        var activeName: String? = null
            private set

        fun start(context: Context, config: String, name: String) {
            val intent = Intent(context, ClaudenVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, config)
                putExtra(EXTRA_NAME, name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClaudenVpnService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Throwable) {
                context.stopService(Intent(context, ClaudenVpnService::class.java))
            }
        }
    }

    private val lifecycleLock = Any()
    private var core: CoreController? = null
    private var tunFd: Int = -1
    private var pendingTun: ParcelFileDescriptor? = null
    private var notificationName: String = "VPN"

    @Volatile
    private var starting = false

    @Volatile
    private var intentionalStop = false

    @Volatile
    private var lastStartAt: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            intentionalStop = true

            ProxyStore(this).desiredOn = false
            Thread({
                synchronized(lifecycleLock) { stopVpn() }
            }, "clauden-vpn-stop").apply { isDaemon = true }.start()
            return START_NOT_STICKY
        }

        var config = intent?.getStringExtra(EXTRA_CONFIG).orEmpty()
        var name = intent?.getStringExtra(EXTRA_NAME).orEmpty()

        if (config.isBlank()) {
            val store = ProxyStore(this)
            val node = store.selectedNode()
            if (node == null || !node.supported || !store.autoStart || !store.desiredOn) {
                stopSelf()
                return START_NOT_STICKY
            }
            config = runCatching { NodeParser.buildXrayConfig(node) }.getOrNull().orEmpty()
            name = node.name
            if (config.isBlank()) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (starting || now - lastStartAt < 1200L) {
            Log.i(TAG, "start ignored (already starting or debounced)")
            return START_STICKY
        }
        starting = true
        lastStartAt = now
        intentionalStop = false
        notificationName = name.ifBlank { "VPN" }

        try {
            startForegroundNotice(notificationName)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            starting = false
            sendState("ERROR", "", "Не удалось запустить VPN-сервис")
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) { }
            stopSelf()
            return START_NOT_STICKY
        }

        sendState("CONNECTING", notificationName, null)

        val finalConfig = config
        val finalName = notificationName
        Thread({
            try {
                synchronized(lifecycleLock) {
                    startCore(finalConfig, finalName)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "start thread failed", t)
                synchronized(lifecycleLock) {
                    fail(t.message ?: "Не удалось запустить VPN")
                }
            } finally {
                starting = false
            }
        }, "clauden-vpn-start").apply { isDaemon = true }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        synchronized(lifecycleLock) {
            stopInternals()
        }
        if (!intentionalStop) {
            sendState("OFF", "", null)
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        intentionalStop = true
        synchronized(lifecycleLock) { stopVpn() }
        super.onRevoke()
    }

    private fun startCore(config: String, name: String) {
        stopInternals()

        val tun = buildTun(name) ?: run {
            fail("Не удалось создать VPN-интерфейс. Разрешите VPN заново.")
            return
        }

        var fd = -1
        try {
            pendingTun = tun
            fd = tun.detachFd()
            tunFd = fd
            pendingTun = null

            Libv2ray.initCoreEnv(filesDir.absolutePath, "")

            val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0L

                override fun shutdown(): Long {
                    if (!intentionalStop && running) {
                        Thread({
                            synchronized(lifecycleLock) {
                                if (!intentionalStop && running) {
                                    fail("Соединение с сервером прервано")
                                }
                            }
                        }, "clauden-vpn-death").apply { isDaemon = true }.start()
                    }
                    return 0L
                }

                override fun onEmitStatus(code: Long, message: String?): Long = 0L
            })

            core = controller
            controller.startLoop(ensureTunInbound(config), fd)

            if (!controller.isRunning) {
                fail("Сервер не принял подключение")
                return
            }

            running = true
            activeName = name
            updateNotification(name)
            sendState("ON", name, null)
            Log.i(TAG, "VPN connected: $name")
        } catch (t: Throwable) {
            Log.e(TAG, "startCore failed", t)
            if (fd >= 0) {
                try { ParcelFileDescriptor.adoptFd(fd).close() } catch (_: Throwable) { }
                tunFd = -1
            }
            fail(t.message ?: "Ошибка запуска VPN")
        }
    }

    private fun fail(message: String) {
        stopInternals()
        sendState("ERROR", "", message)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) { }
        stopSelf()
    }

    private fun stopVpn() {
        stopInternals()
        sendState("OFF", "", null)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) { }
        stopSelf()
    }

    private fun stopInternals() {
        running = false
        activeName = null

        try { core?.stopLoop() } catch (_: Throwable) { }
        core = null

        if (tunFd >= 0) {
            try {
                ParcelFileDescriptor.adoptFd(tunFd).close()
            } catch (_: Throwable) { }
            tunFd = -1
        }
        try { pendingTun?.close() } catch (_: Throwable) { }
        pendingTun = null
    }

    private fun buildTun(name: String): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setMtu(1500)
                .addAddress("10.8.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .setSession(name)
                .setBlocking(true)

            if (!PrivateDns.enabled(this)) {
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
            }

            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Throwable) { }

            try {
                builder.addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 64)
                builder.addRoute("::", 0)
            } catch (_: Throwable) { }

            try {
                val cm = getSystemService(ConnectivityManager::class.java)
                val active = cm?.activeNetwork
                if (active != null) builder.setUnderlyingNetworks(arrayOf(active))
            } catch (_: Throwable) { }

            builder.establish()
        } catch (t: Throwable) {
            Log.e(TAG, "buildTun failed", t)
            null
        }
    }

    private fun ensureTunInbound(raw: String): String {
        return try {
            val root = JSONObject(raw)
            val inbounds = root.optJSONArray("inbounds") ?: JSONArray().also {
                root.put("inbounds", it)
            }
            var hasTun = false
            for (i in 0 until inbounds.length()) {
                val item = inbounds.optJSONObject(i) ?: continue
                if (item.optString("protocol").equals("tun", ignoreCase = true)) {
                    hasTun = true
                    break
                }
            }
            if (!hasTun) {
                inbounds.put(
                    JSONObject()
                        .put("port", 0)
                        .put("protocol", "tun")
                        .put("tag", "tun-in")
                        .put(
                            "settings",
                            JSONObject()
                                .put("name", "tun0")
                                .put("MTU", 1500),
                        )
                        .put(
                            "sniffing",
                            JSONObject()
                                .put("enabled", true)
                                .put("destOverride", JSONArray().put("http").put("tls")),
                        ),
                )
            }
            root.put("inbounds", inbounds)
            root.toString()
        } catch (t: Throwable) {
            Log.e(TAG, "ensureTunInbound failed", t)
            raw
        }
    }

    private fun startForegroundNotice(name: String) {
        ensureChannel()
        val notification = buildNotification(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(name: String) {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID, buildNotification(name))
        } catch (_: Throwable) { }
    }

    private fun buildNotification(name: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ClaudenVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(this, CHANNEL)
            .setContentTitle("Clauden VPN")
            .setContentText(name)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отключить",
                stopIntent,
            ).build(),
        )
        return builder.build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Clauden VPN",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
    }

    private fun sendState(status: String, name: String, error: String?) {
        val intent = Intent(ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_NAME, name)
            .putExtra(EXTRA_ERROR, error)
        sendBroadcast(intent)
    }
}
