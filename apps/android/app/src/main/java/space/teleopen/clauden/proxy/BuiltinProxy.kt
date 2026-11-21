package space.teleopen.clauden.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BuiltinProxy {

    const val PATH = "/v1/proxy/nodes"

    val FALLBACK_NODES: List<String> = listOf(
        "vless://00000000-0000-4000-8000-000000000000@203.0.113.10:8413" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.example.com" +
            "&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "&sid=0000000000000000&type=tcp#Clauden%20DE",
        "vless://00000000-0000-4000-8000-000000000000@203.0.113.10:8414" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.example.com" +
            "&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "&sid=0000000000000000&type=tcp#Clauden%20NL",
        "vless://00000000-0000-4000-8000-000000000000@203.0.113.10:8416" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.example.com" +
            "&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "&sid=0000000000000000&type=tcp#Clauden%20EE",
        "vless://00000000-0000-4000-8000-000000000000@203.0.113.10:8417" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.example.com" +
            "&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "&sid=0000000000000000&type=tcp#Clauden%20KZ",
        "vless://00000000-0000-4000-8000-000000000000@203.0.113.10:8415" +
            "?encryption=none&security=tls&sni=example.com&fp=chrome&type=xhttp" +
            "&path=%2Fclauden&host=example.com#Clauden%20TLS",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .proxySelector(ProxyController.selector)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/') + PATH
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                val array = JSONObject(body).optJSONArray("nodes") ?: return@withContext emptyList()
                val out = ArrayList<String>(array.length())
                for (i in 0 until array.length()) {
                    val raw = array.optString(i, "").trim()
                    if (raw.startsWith("vless://") || raw.startsWith("vmess://") ||
                        raw.startsWith("trojan://") || raw.startsWith("ss://")
                    ) {
                        out.add(raw)
                    }
                }
                out
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun toNodes(raws: List<String>): List<VpnNode> = raws.mapNotNull { NodeParser.parse(it) }
}
