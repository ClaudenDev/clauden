package space.teleopen.clauden.proxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.util.Base64

object NodeParser {

    private val ws = Regex("\\s+")

    fun parse(input: String): VpnNode? {
        val cleaned = unescapeHtml(input.trim())
        if (cleaned.isEmpty() || !cleaned.contains("://")) return null
        return try {
            when (cleaned.substringBefore("://").lowercase()) {
                "vless" -> parseVless(cleaned)
                "vmess" -> parseVmess(cleaned)
                "trojan" -> parseTrojan(cleaned)
                "ss" -> parseShadowsocks(cleaned)
                "socks", "socks5" -> parseSocks(cleaned)
                "hysteria2", "hy2" -> parseHysteria2(cleaned)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private data class Parts(
        val scheme: String,
        val body: String,
        val query: Map<String, String>,
        val fragment: String,
    )

    private fun split(uri: String): Parts? {
        val scheme = uri.substringBefore("://", "").lowercase()
        if (scheme.isEmpty()) return null
        val rest = uri.substringAfter("://")

        val hash = rest.indexOf('#')
        val fragment = if (hash >= 0) pctDecode(rest.substring(hash + 1)) else ""
        val noFragment = if (hash >= 0) rest.substring(0, hash) else rest

        val q = noFragment.indexOf('?')
        val queryRaw = if (q >= 0) noFragment.substring(q + 1) else ""
        val body = if (q >= 0) noFragment.substring(0, q) else noFragment

        return Parts(scheme, body, parseQuery(queryRaw), fragment)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split('&')) {
            if (part.isBlank()) continue
            val idx = part.indexOf('=')
            if (idx < 0) {
                out[pctDecode(part).lowercase()] = ""
            } else {
                out[pctDecode(part.substring(0, idx)).lowercase()] =
                    pctDecode(part.substring(idx + 1))
            }
        }
        return out
    }

    private fun parseVless(uri: String): VpnNode? {
        val p = split(uri) ?: return null
        val at = p.body.lastIndexOf('@')
        if (at <= 0) return null
        val uuid = pctDecode(p.body.substring(0, at))
        val hp = hostPort(p.body.substring(at + 1), 443) ?: return null
        if (uuid.isBlank() || hp.first.isBlank()) return null

        val q = p.query
        val name = p.fragment.ifBlank { hp.first }
        val params = mapOf(
            "uuid" to uuid,
            "security" to (q["security"] ?: "none"),
            "sni" to (q["sni"] ?: q["peer"] ?: ""),
            "fp" to (q["fp"] ?: "chrome"),
            "pbk" to (q["pbk"] ?: ""),
            "sid" to (q["sid"] ?: ""),
            "spx" to (q["spx"] ?: ""),
            "flow" to (q["flow"] ?: ""),
            "type" to (q["type"] ?: "tcp"),
            "host" to (q["host"] ?: ""),
            "path" to (q["path"] ?: "/"),
            "serviceName" to (q["servicename"] ?: q["serviceName"] ?: ""),
            "headerType" to (q["headertype"] ?: q["headerType"] ?: "none"),
            "alpn" to (q["alpn"] ?: ""),
            "encryption" to (q["encryption"] ?: "none"),
        )
        return node(uri, name, hp.first, hp.second, VpnProtocol.VLESS, params)
    }

    private fun parseVmess(uri: String): VpnNode? {
        val p = split(uri) ?: return null

        if (p.body.contains('@')) {
            val at = p.body.lastIndexOf('@')
            val uuid = pctDecode(p.body.substring(0, at))
            val hp = hostPort(p.body.substring(at + 1), 443) ?: return null
            if (uuid.isBlank()) return null
            val q = p.query
            val params = mapOf(
                "uuid" to uuid,
                "aid" to (q["aid"] ?: "0"),
                "security" to (q["security"] ?: "auto"),
                "type" to (q["type"] ?: "tcp"),
                "host" to (q["host"] ?: ""),
                "path" to (q["path"] ?: "/"),
                "tls" to (q["tls"] ?: if (q["security"] == "tls") "tls" else ""),
                "sni" to (q["sni"] ?: ""),
                "fp" to (q["fp"] ?: ""),
                "alpn" to (q["alpn"] ?: ""),
                "headerType" to (q["headertype"] ?: "none"),
            )
            return node(uri, p.fragment.ifBlank { hp.first }, hp.first, hp.second, VpnProtocol.VMESS, params)
        }

        val decoded = tryB64(p.body)?.trim() ?: return null
        if (!decoded.startsWith("{")) {

            if (decoded.contains('@')) return parse("vless://$decoded")
            return null
        }
        val j = runCatching { Json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val host = str(j, "add")
        val port = int(j, "port")
        val uuid = str(j, "id")
        if (host.isBlank() || port <= 0 || uuid.isBlank()) return null
        val name = str(j, "ps").ifBlank { host }
        val params = mapOf(
            "uuid" to uuid,
            "aid" to int(j, "aid").toString(),
            "security" to str(j, "scy").ifBlank { "auto" },
            "type" to str(j, "net").ifBlank { "tcp" },
            "host" to str(j, "host"),
            "path" to str(j, "path").ifBlank { "/" },
            "tls" to str(j, "tls"),
            "sni" to str(j, "sni"),
            "fp" to str(j, "fp"),
            "alpn" to str(j, "alpn"),
            "headerType" to str(j, "type").ifBlank { "none" },
        )
        return node(uri, name, host, port, VpnProtocol.VMESS, params)
    }

    private fun parseTrojan(uri: String): VpnNode? {
        val p = split(uri) ?: return null
        val at = p.body.lastIndexOf('@')
        if (at <= 0) return null
        val password = pctDecode(p.body.substring(0, at))
        val hp = hostPort(p.body.substring(at + 1), 443) ?: return null
        if (password.isBlank()) return null
        val q = p.query
        val params = mapOf(
            "password" to password,
            "sni" to (q["sni"] ?: q["peer"] ?: ""),
            "fp" to (q["fp"] ?: "chrome"),
            "type" to (q["type"] ?: "tcp"),
            "host" to (q["host"] ?: ""),
            "path" to (q["path"] ?: "/"),
            "serviceName" to (q["servicename"] ?: ""),
            "alpn" to (q["alpn"] ?: ""),
            "allowInsecure" to if (q["allowinsecure"] == "1" || q["allowinsecure"] == "true") "1" else "0",
        )
        return node(uri, p.fragment.ifBlank { hp.first }, hp.first, hp.second, VpnProtocol.TROJAN, params)
    }

    private fun parseShadowsocks(uri: String): VpnNode? {
        val p = split(uri) ?: return null
        var body = p.body

        var method: String
        var password: String
        var host: String
        var port: Int

        if (body.contains('@')) {
            val at = body.lastIndexOf('@')
            val userInfo = tryB64(body.substring(0, at)) ?: pctDecode(body.substring(0, at))
            val colon = userInfo.indexOf(':')
            if (colon < 0) return null
            method = userInfo.substring(0, colon)
            password = userInfo.substring(colon + 1)
            val hp = hostPort(body.substring(at + 1), 8388) ?: return null
            host = hp.first
            port = hp.second
        } else {
            val decoded = tryB64(body) ?: return null
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            val mp = decoded.substring(0, at)
            val colon = mp.indexOf(':')
            if (colon < 0) return null
            method = mp.substring(0, colon)
            password = mp.substring(colon + 1)
            val hp = hostPort(decoded.substring(at + 1), 8388) ?: return null
            host = hp.first
            port = hp.second
        }

        method = method.trim()
        if (method.isBlank() || host.isBlank() || port <= 0) return null
        val params = mapOf("method" to method, "password" to password)
        return node(uri, p.fragment.ifBlank { host }, host, port, VpnProtocol.SHADOWSOCKS, params)
    }

    private fun parseSocks(uri: String): VpnNode? {
        val p = split(uri) ?: return null
        val at = p.body.lastIndexOf('@')
        val hostPart = if (at >= 0) p.body.substring(at + 1) else p.body
        val hp = hostPort(hostPart, 1080) ?: return null
        var user = ""
        var pass = ""
        if (at > 0) {
            val ui = pctDecode(p.body.substring(0, at))
            val colon = ui.indexOf(':')
            if (colon >= 0) {
                user = ui.substring(0, colon)
                pass = ui.substring(colon + 1)
            } else {
                user = ui
            }
        }
        val params = mapOf("user" to user, "pass" to pass)
        return node(uri, p.fragment.ifBlank { hp.first }, hp.first, hp.second, VpnProtocol.SOCKS, params)
    }

    private fun parseHysteria2(uri: String): VpnNode? {
        val p = split(uri) ?: return null
        val at = p.body.lastIndexOf('@')
        if (at <= 0) return null
        val password = pctDecode(p.body.substring(0, at))
        val hp = hostPort(p.body.substring(at + 1), 443) ?: return null
        val q = p.query
        val params = mapOf(
            "password" to password,
            "sni" to (q["sni"] ?: ""),
            "insecure" to if (q["insecure"] == "1" || q["insecure"] == "true") "1" else "0",
            "obfs" to (q["obfs"] ?: ""),
            "obfsPassword" to (q["obfs-password"] ?: ""),
        )
        return node(uri, p.fragment.ifBlank { hp.first }, hp.first, hp.second, VpnProtocol.HYSTERIA2, params)
    }

    private fun node(
        raw: String,
        name: String,
        host: String,
        port: Int,
        protocol: VpnProtocol,
        params: Map<String, String>,
    ): VpnNode = VpnNode(
        id = "${protocol.name.lowercase()}_${Integer.toHexString(raw.hashCode())}",
        name = name.ifBlank { host },
        address = host,
        port = port,
        protocol = protocol,
        raw = raw,
        params = params,
    )

    fun buildXrayConfig(node: VpnNode): String {
        require(node.supported) {
            if (node.protocol == VpnProtocol.HYSTERIA2) {
                "Hysteria2 пока не поддерживается. Выберите VLESS, VMess, Trojan или Shadowsocks."
            } else {
                "Этот протокол не поддерживается"
            }
        }

        val outbound = when (node.protocol) {
            VpnProtocol.VLESS -> vlessOutbound(node)
            VpnProtocol.VMESS -> vmessOutbound(node)
            VpnProtocol.TROJAN -> trojanOutbound(node)
            VpnProtocol.SHADOWSOCKS -> shadowsocksOutbound(node)
            VpnProtocol.SOCKS -> socksOutbound(node)
            else -> error("Протокол ${node.protocolLabel} не поддерживается")
        }

        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", "clauden-socks")
                    put("listen", "127.0.0.1")
                    put("port", ProxyController.SOCKS_PORT)
                    put("protocol", "socks")
                    put("settings", buildJsonObject {
                        put("auth", "noauth")
                        put("udp", true)
                    })
                })
                add(buildJsonObject {
                    put("tag", "clauden-http")
                    put("listen", "127.0.0.1")
                    put("port", ProxyController.HTTP_PORT)
                    put("protocol", "http")
                    put("settings", buildJsonObject { })
                })
            })
            put("outbounds", buildJsonArray {
                add(outbound)
                add(buildJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                })
                add(buildJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                })
            })
            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    add("1.1.1.1")
                    add("8.8.8.8")
                })
                put("queryStrategy", "UseIP")
            })
            put("routing", buildJsonObject {
                put("domainStrategy", "IPIfNonMatch")
                put("rules", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "field")
                        put("ip", buildJsonArray {
                            add("10.0.0.0/8")
                            add("172.16.0.0/12")
                            add("192.168.0.0/16")
                        })
                        put("outboundTag", "direct")
                    })
                })
            })
        }
        return config.toString()
    }

    private fun vlessOutbound(n: VpnNode): JsonObject {
        val p = n.params
        return buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", buildJsonObject {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("id", p["uuid"].orEmpty())
                                put("encryption", p["encryption"].orEmpty().ifBlank { "none" })
                                put("flow", p["flow"].orEmpty())
                                put("level", 8)
                                put("security", "auto")
                            })
                        })
                    })
                })
            })
            put("streamSettings", streamSettings(
                network = p["type"].orEmpty().ifBlank { "tcp" },
                security = p["security"].orEmpty().ifBlank { "none" },
                sni = p["sni"].orEmpty(),
                fp = p["fp"].orEmpty().ifBlank { "chrome" },
                alpn = p["alpn"].orEmpty(),
                pbk = p["pbk"].orEmpty(),
                sid = p["sid"].orEmpty(),
                spx = p["spx"].orEmpty(),
                host = p["host"].orEmpty(),
                path = p["path"].orEmpty().ifBlank { "/" },
                serviceName = p["serviceName"].orEmpty(),
                headerType = p["headerType"].orEmpty().ifBlank { "none" },
            ))
            put("mux", mux())
        }
    }

    private fun vmessOutbound(n: VpnNode): JsonObject {
        val p = n.params
        val security = if (p["tls"] == "tls") "tls" else "none"
        return buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vmess")
            put("settings", buildJsonObject {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("id", p["uuid"].orEmpty())
                                put("alterId", p["aid"].orEmpty().toIntOrNull() ?: 0)
                                put("security", p["security"].orEmpty().ifBlank { "auto" })
                                put("level", 8)
                            })
                        })
                    })
                })
            })
            put("streamSettings", streamSettings(
                network = p["type"].orEmpty().ifBlank { "tcp" },
                security = security,
                sni = p["sni"].orEmpty(),
                fp = p["fp"].orEmpty(),
                alpn = p["alpn"].orEmpty(),
                host = p["host"].orEmpty(),
                path = p["path"].orEmpty().ifBlank { "/" },
                headerType = p["headerType"].orEmpty().ifBlank { "none" },
            ))
            put("mux", mux())
        }
    }

    private fun trojanOutbound(n: VpnNode): JsonObject {
        val p = n.params
        return buildJsonObject {
            put("tag", "proxy")
            put("protocol", "trojan")
            put("settings", buildJsonObject {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("address", n.address)
                        put("port", n.port)
                        put("password", p["password"].orEmpty())
                        put("level", 8)
                    })
                })
            })
            put("streamSettings", streamSettings(
                network = p["type"].orEmpty().ifBlank { "tcp" },
                security = "tls",
                sni = p["sni"].orEmpty(),
                fp = p["fp"].orEmpty().ifBlank { "chrome" },
                alpn = p["alpn"].orEmpty(),
                host = p["host"].orEmpty(),
                path = p["path"].orEmpty().ifBlank { "/" },
                serviceName = p["serviceName"].orEmpty(),
                allowInsecure = p["allowInsecure"] == "1",
            ))
            put("mux", mux())
        }
    }

    private fun shadowsocksOutbound(n: VpnNode): JsonObject = buildJsonObject {
        put("tag", "proxy")
        put("protocol", "shadowsocks")
        put("settings", buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", n.address)
                    put("port", n.port)
                    put("method", n.params["method"].orEmpty())
                    put("password", n.params["password"].orEmpty())
                    put("level", 8)
                })
            })
        })
    }

    private fun socksOutbound(n: VpnNode): JsonObject = buildJsonObject {
        put("tag", "proxy")
        put("protocol", "socks")
        put("settings", buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", n.address)
                    put("port", n.port)
                    val user = n.params["user"].orEmpty()
                    if (user.isNotBlank()) {
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("user", user)
                                put("pass", n.params["pass"].orEmpty())
                                put("level", 8)
                            })
                        })
                    }
                })
            })
        })
    }

    private fun mux() = buildJsonObject {
        put("enabled", false)
        put("concurrency", 8)
    }

    private fun streamSettings(
        network: String,
        security: String,
        sni: String = "",
        fp: String = "",
        alpn: String = "",
        pbk: String = "",
        sid: String = "",
        spx: String = "",
        host: String = "",
        path: String = "/",
        serviceName: String = "",
        headerType: String = "none",
        allowInsecure: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("network", network)
        put("security", security)

        if (security == "reality") {
            put("realitySettings", buildJsonObject {
                put("serverName", sni)
                put("fingerprint", fp.ifBlank { "chrome" })
                put("publicKey", pbk)
                put("shortId", sid)
                put("spiderX", spx)
                put("show", false)
            })
        } else if (security == "tls") {
            put("tlsSettings", buildJsonObject {
                put("serverName", sni)
                put("fingerprint", fp.ifBlank { "chrome" })
                if (alpn.isNotBlank()) {
                    put("alpn", buildJsonArray {
                        alpn.split(',').forEach { value -> if (value.isNotBlank()) add(value.trim()) }
                    })
                }
                put("allowInsecure", allowInsecure)
            })
        }

        when (network) {
            "ws" -> put("wsSettings", buildJsonObject {
                put("path", path.ifBlank { "/" })
                if (host.isNotBlank()) {
                    put("headers", buildJsonObject { put("Host", host) })
                }
            })
            "grpc" -> put("grpcSettings", buildJsonObject {
                put("serviceName", serviceName)
            })
            "h2" -> put("httpSettings", buildJsonObject {
                put("host", buildJsonArray { if (host.isNotBlank()) add(host) })
                put("path", path.ifBlank { "/" })
            })
            "tcp" -> put("tcpSettings", buildJsonObject {
                put("header", buildJsonObject { put("type", headerType.ifBlank { "none" }) })
            })
            "httpupgrade" -> put("httpupgradeSettings", buildJsonObject {
                put("path", path.ifBlank { "/" })
                if (host.isNotBlank()) put("host", host)
            })
            "xhttp" -> put("xhttpSettings", buildJsonObject {
                put("path", path.ifBlank { "/" })
                if (host.isNotBlank()) put("host", host)
            })
        }
    }

    fun tryB64Decode(value: String): String? = tryB64(value)

    private fun tryB64(value: String): String? {
        val s = value.trim().replace(ws, "")
        if (s.isEmpty()) return null
        val normalized = s.replace('-', '+').replace('_', '/')
        val pad = (4 - normalized.length % 4) % 4
        return try {
            String(Base64.getDecoder().decode(normalized + "=".repeat(pad)), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun pctDecode(value: String): String {
        if (!value.contains('%') && !value.contains('+')) return value
        return try {

            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (_: Throwable) {
            value
        }
    }

    private fun unescapeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&nbsp;", " ")

    private fun hostPort(value: String, defaultPort: Int): Pair<String, Int>? {
        val clean = value.trim().substringBefore('/')
        if (clean.isEmpty()) return null
        if (clean.startsWith("[")) {
            val end = clean.indexOf(']')
            if (end < 0) return null
            val host = clean.substring(1, end)
            val rest = clean.substring(end + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: defaultPort else defaultPort
            return if (host.isBlank()) null else host to port
        }
        val colon = clean.lastIndexOf(':')
        if (colon < 0) return clean to defaultPort
        val host = clean.substring(0, colon)
        val port = clean.substring(colon + 1).toIntOrNull() ?: defaultPort
        return if (host.isBlank()) null else host to port
    }

    private fun str(j: JsonObject, key: String): String = try {
        j[key]?.jsonPrimitive?.content.orEmpty()
    } catch (_: Throwable) {
        ""
    }

    private fun int(j: JsonObject, key: String): Int = try {
        j[key]?.jsonPrimitive?.intOrNull ?: 0
    } catch (_: Throwable) {
        0
    }
}
