package space.teleopen.clauden.proxy

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

object RelayResolver {

    @Volatile
    var RESOLVERS: List<String> = listOf(
        "1.1.1.1",
        "8.8.8.8",
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query",
    )

    @Volatile
    var EXIT_COUNTS: Map<String, Int> = emptyMap()

    @Volatile
    var userKey: String = ""

    private fun hashOf(text: String): Int {
        var h = 7
        for (c in text) h = h * 31 + c.code
        return h and Int.MAX_VALUE
    }

    private fun weightedPick(): String? {
        val list = RESOLVERS
        if (list.isEmpty()) return null
        val key = userKey
        if (key.isBlank()) return list[0]
        val weighted = ArrayList<String>(list.size * 4)
        for (spec in list) {
            val count = (EXIT_COUNTS[spec] ?: 1).coerceIn(1, 8)
            repeat(count) { weighted.add(spec) }
        }
        return weighted[hashOf(key) % weighted.size]
    }

    suspend fun fetch(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/') + "/v1/proxy/dns"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
                .newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                val root = org.json.JSONObject(body)
                val array = root.optJSONArray("resolvers") ?: return@withContext emptyList()
                val out = ArrayList<String>(array.length())
                for (i in 0 until array.length()) {
                    val host = array.optString(i, "").trim()
                    if (host.isBlank()) continue

                    val ok = host.matches(Regex("^[a-zA-Z0-9.-]+$")) ||
                        host.matches(Regex("^https://[a-zA-Z0-9.-]+/[a-zA-Z0-9._/-]*$"))
                    if (ok) out.add(host)
                }

                root.optJSONArray("entries")?.let { entries ->
                    val counts = HashMap<String, Int>()
                    for (i in 0 until entries.length()) {
                        val item = entries.optJSONObject(i) ?: continue
                        val spec = item.optString("spec", "").trim()
                        val exits = item.optInt("exits", 1)
                        if (spec.isNotBlank() && exits > 0) counts[spec] = exits
                    }
                    if (counts.isNotEmpty()) EXIT_COUNTS = counts
                }
                out
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private val RELAYED_HOSTS = listOf("claude.ai", "anthropic.com")

    @Volatile
    private var resolvedIps: List<String> = emptyList()

    @Volatile
    private var activeResolver: String = ""

    val relayIps: List<String> get() = resolvedIps

    val resolver: String get() = activeResolver

    private fun isRelayed(host: String): Boolean =
        RELAYED_HOSTS.any { host == it || host.endsWith(".$it") }

    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (!isRelayed(hostname) || resolvedIps.isEmpty()) {
                return Dns.SYSTEM.lookup(hostname)
            }
            val viaRelay = resolvedIps.mapNotNull { ip ->
                try {
                    InetAddress.getByName(ip)
                } catch (_: Throwable) {
                    null
                }
            }
            return viaRelay.ifEmpty { Dns.SYSTEM.lookup(hostname) }
        }
    }

    private fun buildQuery(host: String): ByteArray {
        val id = 0x1234
        val header = byteArrayOf(
            (id shr 8).toByte(), id.toByte(),
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )

        val labels = host.split('.')
        val qname = ByteArray(labels.sumOf { it.length + 1 } + 1)
        var qi = 0
        for (label in labels) {
            qname[qi++] = label.length.toByte()
            for (b in label.toByteArray(Charsets.UTF_8)) qname[qi++] = b
        }
        qname[qi] = 0
        val qtype = byteArrayOf(0x00, 0x01, 0x00, 0x01)
        return header + qname + qtype
    }

    private fun udpQuery(resolverIp: String, host: String): List<String> {
        val query = buildQuery(host)
        val socket = java.net.DatagramSocket()
        socket.soTimeout = 3000
        return try {
            socket.send(
                java.net.DatagramPacket(
                    query, query.size,
                    java.net.InetAddress.getByName(resolverIp), 53,
                )
            )
            val buffer = ByteArray(512)
            val packet = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            parseA(buffer.copyOf(packet.length))
        } catch (_: Throwable) {
            emptyList()
        } finally {
            socket.close()
        }
    }

    private fun isPlainDns(resolver: String): Boolean =
        resolver.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))

    private fun queryResolver(spec: String, host: String): List<String> = try {
        when {
            spec.startsWith("https://") -> dohQuery(spec, host)
            isPlainDns(spec) -> udpQuery(spec, host)
            else -> dohQuery(spec, host)
        }
    } catch (t: Throwable) {
        Log.i("RelayResolver", "резолвер $spec ошибка: ${t.javaClass.simpleName} ${t.message}")
        emptyList()
    }

    private fun dohQuery(resolverHost: String, host: String): List<String> {
        val query = buildQuery(host)

        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
        val url = if (resolverHost.startsWith("http")) {
            resolverHost
        } else {
            "https://$resolverHost/dns-query"
        }
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/dns-message")
            .header("Accept", "application/dns-message")
            .post(query.toRequestBody("application/dns-message".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.bytes() ?: return emptyList()
            if (!response.isSuccessful) {
                Log.i("RelayResolver", "DoH $resolverHost: код ${response.code}")
                return emptyList()
            }
            return parseA(body)
        }
    }

    private fun parseA(msg: ByteArray): List<String> {
        if (msg.size < 12) return emptyList()
        val ancount = ((msg[6].toInt() and 0xFF) shl 8) or (msg[7].toInt() and 0xFF)
        var off = 12

        while (off < msg.size && msg[off].toInt() != 0) {
            off += (msg[off].toInt() and 0xFF) + 1
        }
        off += 5
        val out = ArrayList<String>()
        repeat(ancount) {
            if (off >= msg.size) return out
            if ((msg[off].toInt() and 0xC0) == 0xC0) {
                off += 2
            } else {
                while (off < msg.size && msg[off].toInt() != 0) {
                    off += (msg[off].toInt() and 0xFF) + 1
                }
                off += 1
            }
            if (off + 10 > msg.size) return out
            val type = ((msg[off].toInt() and 0xFF) shl 8) or (msg[off + 1].toInt() and 0xFF)
            val rdlen = ((msg[off + 8].toInt() and 0xFF) shl 8) or (msg[off + 9].toInt() and 0xFF)
            off += 10
            if (off + rdlen > msg.size) return out
            if (type == 1 && rdlen == 4) {
                out.add(
                    "${msg[off].toInt() and 0xFF}.${msg[off + 1].toInt() and 0xFF}." +
                        "${msg[off + 2].toInt() and 0xFF}.${msg[off + 3].toInt() and 0xFF}"
                )
            }
            off += rdlen
        }
        return out
    }

    suspend fun activate(): Boolean = withContext(Dispatchers.IO) {
        val list = RESOLVERS
        if (list.isEmpty()) return@withContext false

        val preferred = weightedPick()
        val start = list.indexOf(preferred).let { if (it < 0) 0 else it }
        for (step in 0 until list.size) {
            val host = list[(start + step).mod(list.size)]
            val ips = queryResolver(host, "claude.ai")
            if (ips.isEmpty()) continue
            if (probeRelay(ips)) {
                resolvedIps = orderByUser(ips)
                activeResolver = host
                return@withContext true
            }
        }
        resolvedIps = emptyList()
        activeResolver = ""
        Log.i("RelayResolver", "релей не отвечает — нужен прокси")
        false
    }

    private fun orderByUser(ips: List<String>): List<String> {
        val key = userKey
        if (key.isBlank() || ips.size <= 1) return ips
        val idx = hashOf(key + "|relay") % ips.size
        return ips.drop(idx) + ips.take(idx)
    }

    private fun probeRelay(ips: List<String>): Boolean {
        val client = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    ips.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
            })
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://claude.ai/cdn-cgi/trace")
            .header("Accept", "text/plain")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                response.isSuccessful && text.contains("h=claude.ai")
            }
        }.getOrDefault(false)
    }

    suspend fun rotate(): Boolean = withContext(Dispatchers.IO) {
        val current = RESOLVERS.indexOf(activeResolver)
        for (step in 1..RESOLVERS.size) {
            val host = RESOLVERS[(current + step).mod(RESOLVERS.size)]
            val ips = queryResolver(host, "claude.ai")
            if (ips.isEmpty()) continue
            if (!probeRelay(ips)) continue
            resolvedIps = orderByUser(ips)
            activeResolver = host
            return@withContext true
        }
        false
    }

    fun activateBlocking(): Boolean {
        val list = RESOLVERS
        if (list.isEmpty()) return false
        val preferred = weightedPick()
        val start = list.indexOf(preferred).let { if (it < 0) 0 else it }
        for (step in 0 until list.size) {
            val host = list[(start + step).mod(list.size)]
            val ips = queryResolver(host, "claude.ai")
            if (ips.isEmpty()) continue
            if (!probeRelay(ips)) continue
            resolvedIps = orderByUser(ips)
            activeResolver = host
            return true
        }
        resolvedIps = emptyList()
        activeResolver = ""
        Log.i("RelayResolver", "релей не отвечает — нужен прокси")
        return false
    }

    fun rotateBlocking(): Boolean {
        val current = RESOLVERS.indexOf(activeResolver)
        for (step in 1..RESOLVERS.size) {
            val host = RESOLVERS[(current + step).mod(RESOLVERS.size)]
            val ips = queryResolver(host, "claude.ai")
            if (ips.isEmpty()) continue
            if (!probeRelay(ips)) continue
            resolvedIps = orderByUser(ips)
            activeResolver = host
            return true
        }
        return false
    }

    val isActive: Boolean get() = resolvedIps.isNotEmpty()

    fun reset() {
        resolvedIps = emptyList()
        activeResolver = ""
    }

    val systemDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname)
    }

    fun selectorFor(hostname: String): java.net.ProxySelector = object : java.net.ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val scheme = uri?.scheme?.lowercase()
            val http = scheme == "http" || scheme == "https" || scheme == "ws" || scheme == "wss"
            val local = uri?.host?.lowercase() in listOf("127.0.0.1", "localhost", "::1")
            if (http && !local && ProxyController.state.value.connected) {
                return listOf(
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", ProxyController.SOCKS_PORT))
                )
            }
            return listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException) {
        }
    }
}
