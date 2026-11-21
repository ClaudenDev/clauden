package space.teleopen.claudencode.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import space.teleopen.claudencode.BuildConfig
import space.teleopen.claudencode.MainActivity
import space.teleopen.claudencode.data.AuthStore
import space.teleopen.claudencode.data.CodeApi
import space.teleopen.claudencode.data.CodeJobResult
import space.teleopen.claudencode.data.SshConnectionHolder
import space.teleopen.claudencode.data.SshStore
import space.teleopen.claudencode.data.remoteFsExec

class AgentRunnerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runner: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var runningHost = ""
    private var runningSession = ""

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "SSH agent",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps the code agent connected to your server"
                    setShowBadge(false)
                },
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val hostId = intent?.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_HOST, "").orEmpty()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_SESSION, "").orEmpty()
        if (hostId.isBlank() || sessionId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        prefs.edit()
            .putString(KEY_HOST, hostId)
            .putString(KEY_SESSION, sessionId)
            .apply()

        val host = SshStore(this).hosts().firstOrNull { it.id == hostId }
        startForeground(NOTIF_ID, notification(host?.label ?: "SSH server"))

        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClaudenCode:agent")
                .apply { runCatching { acquire() } }
        }
        if (runningHost != hostId || runningSession != sessionId) {
            runner?.cancel()
            runningHost = hostId
            runningSession = sessionId
            runner = scope.launch { loop(hostId, sessionId) }
        } else if (runner?.isActive != true) {
            runner = scope.launch { loop(hostId, sessionId) }
        }
        return START_STICKY
    }

    private suspend fun loop(hostId: String, sessionId: String) {
        val api = CodeApi(BuildConfig.CLAUDEN_BASE_URL, AuthStore(this).token)
        val handled = mutableSetOf<String>()
        while (currentCoroutineContext().isActive) {
            val host = SshStore(this).hosts().firstOrNull { it.id == hostId }
            if (host == null) {
                delay(5_000)
                continue
            }
            try {
                val ssh = withContext(Dispatchers.IO) {
                    val client = SshConnectionHolder.get(host)
                    if (!client.connected) client.connect()
                    client
                }
                val session = api.session(sessionId)
                val root = session.remote?.root.orEmpty()
                val queued = session.jobs.filter { it.status == "queued" && it.id !in handled }
                for (job in queued) {
                    handled.add(job.id)
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val fs = job.fs
                            if (fs != null) {
                                val payload = remoteFsExec(ssh, root, fs.op, fs.path, fs.content)
                                val fsError = runCatching {
                                    JSONObject(payload).optString("error")
                                }.getOrDefault("")
                                CodeJobResult(
                                    stdout = payload,
                                    code = if (fsError.isBlank()) 0 else 1,
                                    error = fsError,
                                )
                            } else {
                                val exec = ssh.exec(job.command, root, job.timeout)
                                CodeJobResult(
                                    stdout = exec.stdout,
                                    stderr = exec.stderr,
                                    code = exec.code,
                                    error = if (exec.timedOut) "Command timed out" else "",
                                )
                            }
                        }
                    }.getOrElse { err ->
                        CodeJobResult(code = -1, error = err.message ?: "SSH error")
                    }
                    runCatching { api.jobResult(sessionId, job.id, result) }
                }
                val busy = session.status == "waiting_device" || session.working
                delay(if (busy || queued.isNotEmpty()) 600 else 2_500)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Throwable) {
                delay(3_000)
            }
        }
    }

    private fun notification(hostLabel: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, AgentRunnerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Clauden Code · $hostLabel")
            .setContentText("Agent is working on your server")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stop,
                ).build(),
            )
            .build()
    }

    private fun shutdown() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
        runner?.cancel()
        runner = null
        runCatching { wake?.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runner?.cancel()
        runner = null
        runCatching { wake?.release() }
        wake = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ssh_agent"
        private const val NOTIF_ID = 41
        private const val PREFS = "clauden_agent_runner"
        private const val KEY_HOST = "host"
        private const val KEY_SESSION = "session"
        private const val EXTRA_HOST = "host_id"
        private const val EXTRA_SESSION = "session_id"
        private const val ACTION_STOP = "space.teleopen.claudencode.STOP_AGENT"

        fun start(context: Context, hostId: String, sessionId: String) {
            if (hostId.isBlank() || sessionId.isBlank()) return
            val intent = Intent(context, AgentRunnerService::class.java)
                .putExtra(EXTRA_HOST, hostId)
                .putExtra(EXTRA_SESSION, sessionId)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentRunnerService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
