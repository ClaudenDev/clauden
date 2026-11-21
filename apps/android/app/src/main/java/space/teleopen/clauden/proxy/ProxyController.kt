package space.teleopen.clauden.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController as WebViewProxyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

object ProxyController {

    const val SOCKS_PORT = 20818
    const val HTTP_PORT = 20819

    private const val ACTION_STATE = "space.teleopen.clauden.proxy.STATE"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(selector)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val _state = MutableStateFlow(ProxyState())
    val state: StateFlow<ProxyState> = _state.asStateFlow()

    @Volatile
    private var attached = false

    @Volatile
    private var autoConnectTried = false

    private lateinit var appContext: Context
    private lateinit var store: ProxyStore

    private fun isOwnBackendHost(host: String): Boolean {
        if (
            host == "clauden.best" || host.endsWith(".clauden.best") ||
            host == "teleopen.space" || host.endsWith(".teleopen.space")
        ) {
            return true
        }
        if (!attached) return false
        return runCatching {
            val base = space.teleopen.clauden.data.Prefs(appContext).claudenBaseUrl
                .ifBlank { space.teleopen.clauden.data.ClaudenCatalog.baseUrl }
            val baseHost = URI(base).host?.lowercase().orEmpty()
            baseHost.isNotBlank() && (host == baseHost || host.endsWith(".$baseHost"))
        }.getOrDefault(false)
    }

    val selector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val scheme = uri?.scheme?.lowercase()
            val host = uri?.host?.lowercase()
            val http = scheme == "http" || scheme == "https" ||
                scheme == "ws" || scheme == "wss"
            val local = host == "127.0.0.1" || host == "localhost" || host == "::1"
            val ownBackend = host != null && isOwnBackendHost(host)
            if (http && !local && !ownBackend && _state.value.status == VpnStatus.ON) {
                return listOf(
                    Proxy(
                        Proxy.Type.SOCKS,
                        InetSocketAddress("127.0.0.1", SOCKS_PORT),
                    ),
                )
            }
            return listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {

        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_STATE) return
            val status = intent.getStringExtra(ClaudenVpnService.EXTRA_STATUS).orEmpty()
            val name = intent.getStringExtra(ClaudenVpnService.EXTRA_NAME).orEmpty()
            val message = intent.getStringExtra(ClaudenVpnService.EXTRA_ERROR)
            when (status) {
                "CONNECTING" -> _state.value = _state.value.copy(
                    status = VpnStatus.CONNECTING,
                    activeName = name,
                    error = null,
                    needsPermission = false,
                )
                "ON" -> {
                    _state.value = _state.value.copy(
                        status = VpnStatus.ON,
                        activeName = name,
                        error = null,
                        needsPermission = false,
                    )
                    applyWebViewProxy()

                    RelayResolver.reset()
                    _state.value = _state.value.copy(relayActive = false, relayResolver = "")
                    probeClaude()
                }
                "OFF" -> {
                    _state.value = _state.value.copy(
                        status = VpnStatus.OFF,
                        activeName = "",
                    )
                    clearWebViewProxy()
                }
                "ERROR" -> {
                    _state.value = _state.value.copy(
                        status = VpnStatus.ERROR,
                        activeName = "",
                        error = message ?: "Не удалось подключиться",
                    )
                    clearWebViewProxy()
                }
            }
        }
    }

    @Synchronized
    fun attach(context: Context) {
        if (attached) return
        appContext = context.applicationContext
        store = ProxyStore(appContext)
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        attached = true
        userKey = runCatching {
            val prefs = space.teleopen.clauden.data.Prefs(appContext)
            prefs.authUserId.ifBlank { prefs.installId }
        }.getOrElse { "" }
        RelayResolver.userKey = userKey
        reload()
        loadBuiltin(context)
        loadResolvers(context)
        probeClaude()
    }

    fun loadResolvers(context: Context) {
        if (!attached) return
        scope.launch {
            val base = runCatching {
                space.teleopen.clauden.data.Prefs(appContext).claudenBaseUrl
                    .ifBlank { space.teleopen.clauden.data.ClaudenCatalog.baseUrl }
            }.getOrElse { space.teleopen.clauden.data.ClaudenCatalog.baseUrl }
            val list = RelayResolver.fetch(base)
            if (list.isNotEmpty()) RelayResolver.RESOLVERS = list
        }
    }

    private fun reload() {
        val nodes = store.nodes()
        val builtin = store.builtinNodes()
        val selectedRaw = store.selectedRaw
        val selected = nodes.firstOrNull { it.raw == selectedRaw }
            ?: builtin.firstOrNull { it.raw == selectedRaw }
            ?: nodes.firstOrNull()
            ?: builtin.firstOrNull()
        val running = ClaudenVpnService.running
        _state.value = _state.value.copy(
            nodes = nodes,
            builtinNodes = builtin,
            builtinSelected = builtin.firstOrNull { it.raw == selectedRaw } ?: builtin.firstOrNull(),
            selected = selected,
            subscription = store.subscription,
            title = store.title,
            autoStart = store.autoStart,
            status = if (running) VpnStatus.ON else VpnStatus.OFF,
            activeName = if (running) ClaudenVpnService.activeName.orEmpty() else "",
        )
    }

    fun loadBuiltin(context: Context) {
        if (!attached) return
        scope.launch {
            val base = runCatching {
                space.teleopen.clauden.data.Prefs(appContext).claudenBaseUrl
                    .ifBlank { space.teleopen.clauden.data.ClaudenCatalog.baseUrl }
            }.getOrElse { space.teleopen.clauden.data.ClaudenCatalog.baseUrl }
            val fetched = BuiltinProxy.fetch(base)
            val nodes = BuiltinProxy.toNodes(
                if (fetched.isEmpty()) BuiltinProxy.FALLBACK_NODES else fetched
            )
            if (nodes.isEmpty()) return@launch
            store.saveBuiltinNodes(nodes)

            if (store.autoNode) {
                autoNode(nodes)?.let { store.selectedRaw = it.raw }
            }
            withContext(Dispatchers.Main) { reload() }
        }
    }

    private var userKey: String = ""

    private fun hashOf(text: String): Int {
        var h = 7
        for (c in text) h = h * 31 + c.code
        return h and Int.MAX_VALUE
    }

    private fun selectedIsBuiltin(state: ProxyState = _state.value): Boolean {
        val raw = state.selected?.raw ?: return false
        return state.builtinNodes.any { it.raw == raw }
    }

    private fun spreadNodes(list: List<VpnNode>): List<VpnNode> =
        list.filter { !it.name.contains("(резерв)", ignoreCase = true) }
            .ifEmpty { list }

    fun autoNode(list: List<VpnNode>): VpnNode? {
        if (list.isEmpty()) return null
        if (!store.autoNode) return _state.value.builtinSelected ?: list.first()
        val pool = spreadNodes(list)
        if (userKey.isBlank()) return pool.first()
        return pool[hashOf(userKey) % pool.size]
    }

    fun selectBuiltin(context: Context, node: VpnNode) {
        attach(context)

        store.autoNode = false
        store.selectedRaw = node.raw
        _state.value = _state.value.copy(
            selected = node,
            builtinSelected = node,
            error = null,
            notice = null,
        )
    }

    fun connectBuiltin(context: Context): Boolean {
        attach(context)
        val builtin = store.builtinNodes().ifEmpty {
            BuiltinProxy.toNodes(BuiltinProxy.FALLBACK_NODES).also { store.saveBuiltinNodes(it) }
        }
        if (builtin.isEmpty()) {
            _state.value = _state.value.copy(error = "Нет доступных серверов прокси")
            return false
        }
        val node = autoNode(builtin) ?: builtin.first()
        store.selectedRaw = node.raw
        _state.value = _state.value.copy(
            selected = node,
            builtinSelected = node,
            error = null,
            notice = null,
        )
        return connect(context)
    }

    fun rotateBuiltinBlocking(): Boolean {
        val builtin = store.builtinNodes().ifEmpty {
            BuiltinProxy.toNodes(BuiltinProxy.FALLBACK_NODES).also { store.saveBuiltinNodes(it) }
        }
        if (builtin.size < 2) return false
        val pool = spreadNodes(builtin)
        val idx = pool.indexOfFirst { it.raw == store.selectedRaw }
        val next = pool[((if (idx >= 0) idx else 0) + 1) % pool.size]
        val config = runCatching { NodeParser.buildXrayConfig(next) }.getOrNull() ?: return false
        runCatching { ClaudenVpnService.stop(appContext) }
        Thread.sleep(500)
        val started = runCatching {
            ClaudenVpnService.start(appContext, config, next.name)
            true
        }.getOrDefault(false)
        if (!started) return false
        store.selectedRaw = next.raw
        _state.value = _state.value.copy(
            selected = next,
            builtinSelected = next,
            activeName = next.name,
        )

        repeat(40) {
            if (socksReady()) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun socksReady(): Boolean = runCatching {
        java.net.Socket("127.0.0.1", SOCKS_PORT).use { it.isConnected }
    }.getOrDefault(false)

    fun pathLabel(): String = when {
        _state.value.connected ->
            "туннель:" + _state.value.activeName.ifBlank { "подключён" }
        RelayResolver.isActive -> "быстрый канал:" + RelayResolver.resolver
        else -> "напрямую"
    }

    private data class ExitTrace(val ip: String, val country: String)

    private val directClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)

            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun parseTrace(text: String): ExitTrace? {
        if (!text.contains("h=claude.ai")) return null
        val lines = text.lineSequence()
        val ip = lines.firstOrNull { it.startsWith("ip=") }
            ?.substringAfter("=")?.trim().orEmpty()
        val loc = text.lineSequence().firstOrNull { it.startsWith("loc=") }
            ?.substringAfter("=")?.trim()?.uppercase().orEmpty()
        if (ip.isBlank()) return null
        return ExitTrace(ip, loc)
    }

    private fun probeOwnExit(): ExitTrace? = runCatching {
        val request = Request.Builder()
            .url("https://claude.ai/cdn-cgi/trace")
            .header("Accept", "text/plain")
            .get()
            .build()
        directClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            parseTrace(response.body?.string().orEmpty())
        }
    }.getOrNull()

    private fun isOutsideRussia(exit: ExitTrace?): Boolean =
        exit != null && exit.country.isNotBlank() && !exit.country.equals("RU", true)

    private fun stopAutoTunnelForOwnExit() {
        store.autoStarted = false
        runCatching { ClaudenVpnService.stop(appContext) }
        clearWebViewProxy()
        _state.value = _state.value.copy(
            status = VpnStatus.OFF,
            activeName = "",
            relayActive = false,
            relayResolver = "",
        )
    }

    fun probeClaude() {
        scope.launch {
            val trace = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url("https://claude.ai/cdn-cgi/trace")
                        .header("Accept", "text/plain")
                        .get()
                        .build()
                    probeClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) null
                        else parseTrace(response.body?.string().orEmpty())
                    }
                }.getOrNull()
            }
            if (trace != null) {

                val ownExit = withContext(Dispatchers.IO) { probeOwnExit() }
                val relayUsed = RelayResolver.isActive
                val tunnelUsed = ClaudenVpnService.running

                val outsideRussia = isOutsideRussia(ownExit) ||
                    (ownExit == null && !tunnelUsed && !relayUsed && isOutsideRussia(trace))
                if (outsideRussia && relayUsed) RelayResolver.reset()

                val restrictedExit = !outsideRussia && !tunnelUsed && !relayUsed &&
                    (ownExit?.country ?: "").equals("RU", ignoreCase = true)
                if (restrictedExit) {
                    val relay = RelayResolver.activate()
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            claudeReachable = true,
                            exitIp = ownExit?.ip ?: trace.ip,
                            exitCountry = ownExit?.country ?: trace.country,
                            relayActive = relay,
                            relayResolver = if (relay) RelayResolver.resolver else "",
                            notice = if (relay) {
                                "Быстрый канал включён автоматически: ${RelayResolver.resolver}"
                            } else {
                                _state.value.notice
                            },
                        )
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        claudeReachable = true,
                        exitIp = ownExit?.ip
                            ?: if (!tunnelUsed && !relayUsed) trace.ip else "",
                        exitCountry = ownExit?.country
                            ?: if (!tunnelUsed && !relayUsed) trace.country else "",
                        relayActive = if (outsideRussia) false else _state.value.relayActive,
                        relayResolver = if (outsideRussia) "" else _state.value.relayResolver,
                    )
                }
                return@launch
            }

            if (ClaudenVpnService.running) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        claudeReachable = false,
                        exitIp = "",
                        exitCountry = "",
                        relayActive = false,
                        relayResolver = "",
                    )
                }
                return@launch
            }

            val relay = RelayResolver.activate()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    claudeReachable = relay,
                    exitIp = "",
                    exitCountry = "",
                    relayActive = relay,
                    relayResolver = if (relay) RelayResolver.resolver else "",
                )
            }
        }
    }

    fun enableRelay() {

        if (ClaudenVpnService.running) {
            runCatching { ClaudenVpnService.stop(appContext) }
            clearWebViewProxy()
        }
        scope.launch {
            val ok = RelayResolver.activate()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    relayActive = ok,
                    relayResolver = if (ok) RelayResolver.resolver else "",
                    claudeReachable = if (ok) true else _state.value.claudeReachable,
                    notice = if (ok) {
                        "Быстрый режим включён: ${RelayResolver.resolver}"
                    } else {
                        "Релей не отвечает — нужен прокси"
                    },
                )
            }
        }
    }

    fun disableRelay() {
        RelayResolver.reset()
        _state.value = _state.value.copy(relayActive = false, relayResolver = "")
        probeClaude()
    }

    fun refresh() {
        if (!attached) return
        if (ClaudenVpnService.running && _state.value.status != VpnStatus.ON) {
            _state.value = _state.value.copy(
                status = VpnStatus.ON,
                activeName = ClaudenVpnService.activeName.orEmpty(),
            )
        } else if (!ClaudenVpnService.running && _state.value.status == VpnStatus.ON) {
            _state.value = _state.value.copy(status = VpnStatus.OFF, activeName = "")
        }

        scope.launch {
            val exit = withContext(Dispatchers.IO) { probeOwnExit() } ?: return@launch
            withContext(Dispatchers.Main) {
                val outside = isOutsideRussia(exit)
                if (outside && store.autoStarted && ClaudenVpnService.running &&
                    selectedIsBuiltin()
                ) {
                    stopAutoTunnelForOwnExit()
                    _state.value = _state.value.copy(
                        claudeReachable = true,
                        exitIp = exit.ip,
                        exitCountry = exit.country,
                        notice = "Ваш выход: ${exitCountryLabel(exit.country)} (${exit.ip}). " +
                            "Прокси Clauden отключён — claude.ai пойдёт напрямую.",
                    )
                } else {
                    _state.value = _state.value.copy(
                        exitIp = exit.ip,
                        exitCountry = exit.country,
                        relayActive = if (outside) false else _state.value.relayActive,
                        relayResolver = if (outside) "" else _state.value.relayResolver,
                    )
                    if (outside && RelayResolver.isActive) RelayResolver.reset()
                }
            }
        }
    }

    fun refreshSubscription(context: Context, input: String) {
        attach(context)
        _state.value = _state.value.copy(loading = true, error = null, notice = null)
        scope.launch {
            val result = SubscriptionLoader.load(input)
            withContext(Dispatchers.Main) {
                if (result.error != null) {
                    _state.value = _state.value.copy(loading = false, error = result.error)
                    return@withContext
                }

                val previous = _state.value.selected
                val nodes = result.nodes
                val selected = nodes.firstOrNull { it.raw == previous?.raw } ?: nodes.firstOrNull()

                store.subscription = input.trim()
                store.saveNodes(nodes)
                store.title = result.title
                store.selectedRaw = selected?.raw.orEmpty()

                val wasConnected = ClaudenVpnService.running
                if (wasConnected) {
                    ClaudenVpnService.stop(appContext)
                    clearWebViewProxy()
                }

                _state.value = _state.value.copy(
                    nodes = nodes,
                    selected = selected,
                    subscription = input.trim(),
                    title = result.title,
                    loading = false,
                    status = VpnStatus.OFF,
                    activeName = "",
                    error = null,
                    notice = "Загружено серверов: ${nodes.size}",
                )
            }
        }
    }

    fun select(context: Context, node: VpnNode) {
        attach(context)
        store.selectedRaw = node.raw
        _state.value = _state.value.copy(selected = node, error = null, notice = null)

    }

    fun connect(context: Context, automatic: Boolean = false): Boolean {
        attach(context)

        RelayResolver.reset()
        _state.value = _state.value.copy(relayActive = false, relayResolver = "")
        val current = _state.value
        val node = current.selected
        if (node == null) {
            _state.value = current.copy(error = "Сначала добавьте подписку и выберите сервер")
            return false
        }
        if (!node.supported) {
            _state.value = current.copy(
                error = if (node.protocol == VpnProtocol.HYSTERIA2) {
                    "Hysteria2 пока не поддерживается. Выберите другой сервер."
                } else {
                    "Этот протокол не поддерживается"
                },
            )
            return false
        }
        if (VpnService.prepare(appContext) != null) {
            _state.value = current.copy(
                needsPermission = true,
                error = "Разрешите подключение VPN в системном окне",
            )
            return false
        }

        val config = try {
            NodeParser.buildXrayConfig(node)
        } catch (t: Throwable) {
            _state.value = current.copy(error = t.message ?: "Не удалось собрать конфиг")
            return false
        }

        _state.value = current.copy(
            status = VpnStatus.CONNECTING,
            activeName = node.name,
            error = null,
            notice = null,
            needsPermission = false,
        )
        return try {
            ClaudenVpnService.start(appContext, config, node.name)
            store.autoStarted = automatic

            store.desiredOn = true
            true
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                status = VpnStatus.ERROR,
                error = t.message ?: "Не удалось запустить VPN",
            )
            false
        }
    }

    fun disconnect(context: Context) {
        attach(context)

        store.desiredOn = false
        store.autoStarted = false
        try {
            ClaudenVpnService.stop(appContext)
        } catch (_: Throwable) {
        }
        clearWebViewProxy()
        _state.value = _state.value.copy(
            status = VpnStatus.OFF,
            activeName = "",
            error = null,
            notice = null,
        )
    }

    fun autoConnect(context: Context) {
        attach(context)
        if (autoConnectTried) return
        autoConnectTried = true
        val current = _state.value
        if (!current.autoStart || !store.desiredOn || current.selected == null) return
        scope.launch {

            val exit = withContext(Dispatchers.IO) { probeOwnExit() }
            if (exit != null && isOutsideRussia(exit)) {
                val builtin = withContext(Dispatchers.Main) { selectedIsBuiltin() }
                if (builtin) {
                    withContext(Dispatchers.Main) {
                        if (store.autoStarted && ClaudenVpnService.running) {
                            stopAutoTunnelForOwnExit()
                        } else {
                            RelayResolver.reset()
                        }
                        _state.value = _state.value.copy(
                            claudeReachable = true,
                            exitIp = exit.ip,
                            exitCountry = exit.country,
                            relayActive = false,
                            relayResolver = "",
                            notice = "Ваш выход: ${exitCountryLabel(exit.country)} (${exit.ip}). " +
                                "Прокси Clauden не нужен.",
                        )
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        exitIp = exit.ip,
                        exitCountry = exit.country,
                    )
                }
            }
            if (current.status == VpnStatus.ON || current.status == VpnStatus.CONNECTING) {
                return@launch
            }
            val askPermission = withContext(Dispatchers.IO) {
                VpnService.prepare(appContext) != null
            }
            withContext(Dispatchers.Main) {
                if (askPermission) {
                    _state.value = _state.value.copy(needsPermission = true)
                } else {
                    connect(context, automatic = true)
                }
            }
        }
    }

    fun bootConnect(context: Context) {
        attach(context)
        val current = _state.value
        if (!current.autoStart || !store.desiredOn || current.selected == null) return
        if (VpnService.prepare(appContext) != null) return
        scope.launch {

            val exit = withContext(Dispatchers.IO) { probeOwnExit() }
            if (exit != null && isOutsideRussia(exit) && selectedIsBuiltin()) return@launch
            withContext(Dispatchers.Main) { connect(context, automatic = true) }
        }
    }

    fun permissionDenied(context: Context) {
        attach(context)
        _state.value = _state.value.copy(
            status = VpnStatus.OFF,
            needsPermission = true,
            error = "Без разрешения VPN подключение невозможно",
        )
    }

    fun setAutoStart(context: Context, enabled: Boolean) {
        attach(context)
        store.autoStart = enabled
        _state.value = _state.value.copy(autoStart = enabled)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    private fun applyWebViewProxy() {
        if (!attached) return
        try {
            val config = ProxyConfig.Builder()
                .addProxyRule("http://127.0.0.1:$HTTP_PORT")
                .build()
            WebViewProxyController.getInstance().setProxyOverride(
                config,
                ContextCompat.getMainExecutor(appContext),
                Runnable { },
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                notice = "WebView не принял прокси: ${t.message ?: "ошибка"}. " +
                    "Вход в claude.ai может требовать VPN в системе.",
            )
        }
    }

    private fun clearWebViewProxy() {
        if (!attached) return
        try {
            WebViewProxyController.getInstance().clearProxyOverride(
                ContextCompat.getMainExecutor(appContext),
                Runnable { },
            )
        } catch (_: Throwable) {
        }
    }
}
