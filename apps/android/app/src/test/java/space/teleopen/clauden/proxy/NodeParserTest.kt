package space.teleopen.clauden.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeParserTest {

    private val vlessReality =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?security=reality&sni=www.google.com&fp=chrome&pbk=PUBKEY&sid=12ab" +
            "&type=tcp&flow=xtls-rprx-vision#Test%20Server"

    @Test
    fun parsesVlessReality() {
        val node = NodeParser.parse(vlessReality)
        assertNotNull(node)
        assertEquals(VpnProtocol.VLESS, node!!.protocol)
        assertEquals("example.com", node.address)
        assertEquals(443, node.port)
        assertEquals("Test Server", node.name)
        assertEquals("11111111-2222-3333-4444-555555555555", node.params["uuid"])
        assertEquals("reality", node.params["security"])

        val config = NodeParser.buildXrayConfig(node)
        assertTrue(config.contains("\"protocol\":\"vless\""))
        assertTrue(config.contains("\"publicKey\":\"PUBKEY\""))
        assertTrue(config.contains("\"serverName\":\"www.google.com\""))
        assertTrue(config.contains("\"flow\":\"xtls-rprx-vision\""))
        assertTrue(config.contains("\"port\":${ProxyController.SOCKS_PORT}"))
        assertTrue(config.contains("\"port\":${ProxyController.HTTP_PORT}"))
    }

    @Test
    fun parsesVmessJson() {
        val json = """{"v":"2","ps":"VM","add":"vm.example.com","port":"8443",""" +
            """"id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","aid":"0","scy":"auto",""" +
            """"net":"ws","host":"cdn.example.com","path":"/ws","tls":"tls","sni":"cdn.example.com"}"""
        val link = "vmess://" + java.util.Base64.getEncoder().encodeToString(json.toByteArray())
        val node = NodeParser.parse(link)
        assertNotNull(node)
        assertEquals(VpnProtocol.VMESS, node!!.protocol)
        assertEquals(8443, node.port)
        assertEquals("/ws", node.params["path"])

        val config = NodeParser.buildXrayConfig(node)
        assertTrue(config.contains("\"protocol\":\"vmess\""))
        assertTrue(config.contains("\"path\":\"/ws\""))
        assertTrue(config.contains("\"serverName\":\"cdn.example.com\""))
        assertTrue(config.contains("\"Host\":\"cdn.example.com\""))
    }

    @Test
    fun parsesShadowsocksAndTrojan() {
        val ssUser = java.util.Base64.getEncoder()
            .encodeToString("aes-256-gcm:secret".toByteArray())
        val ss = NodeParser.parse("ss://$ssUser@1.2.3.4:8388#SS%20Node")
        assertNotNull(ss)
        assertEquals(VpnProtocol.SHADOWSOCKS, ss!!.protocol)
        assertEquals("aes-256-gcm", ss.params["method"])
        assertEquals("secret", ss.params["password"])
        assertTrue(NodeParser.buildXrayConfig(ss).contains("\"protocol\":\"shadowsocks\""))

        val trojan = NodeParser.parse("trojan://pass@t.example.com:443?sni=t.example.com#T")
        assertNotNull(trojan)
        assertEquals(VpnProtocol.TROJAN, trojan!!.protocol)
        val config = NodeParser.buildXrayConfig(trojan)
        assertTrue(config.contains("\"protocol\":\"trojan\""))
        assertTrue(config.contains("\"password\":\"pass\""))
    }

    @Test
    fun hysteriaIsParsedButNotSupported() {
        val node = NodeParser.parse("hysteria2://pass@h.example.com:443#H")
        assertNotNull(node)
        assertEquals(VpnProtocol.HYSTERIA2, node!!.protocol)
        assertFalse(node.supported)
        var failed = false
        try {
            NodeParser.buildXrayConfig(node)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun parsesWholeBase64Subscription() {
        val body = listOf(
            "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls&sni=a.example.com#A",
            "trojan://pass@b.example.com:443?sni=b.example.com#B",
        ).joinToString("\n")
        val encoded = java.util.Base64.getEncoder().encodeToString(body.toByteArray())
        val result = SubscriptionLoader.load(encoded)
        assertEquals(null, result.error)
        assertEquals(2, result.nodes.size)
        assertEquals(listOf("A", "B"), result.nodes.map { it.name })
    }

    @Test
    fun parsesSocksAndDeduplicates() {
        val link = "socks://user:pass@127.0.0.1:1080#Socks"
        val node = NodeParser.parse(link)
        assertNotNull(node)
        assertEquals(VpnProtocol.SOCKS, node!!.protocol)
        assertEquals("user", node.params["user"])
        assertTrue(NodeParser.buildXrayConfig(node).contains("\"protocol\":\"socks\""))

        val result = SubscriptionLoader.load("$link\n$link")
        assertEquals(1, result.nodes.size)
    }
}
