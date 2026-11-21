package space.teleopen.clauden.proxy

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SubscriptionResult(
    val nodes: List<VpnNode>,
    val title: String = "",
    val error: String? = null,
)

object SubscriptionLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private val happHeaders = linkedMapOf(
        "User-Agent" to "Happ/3.20.4/Android/17782185961531805698",
        "X-Device-Locale" to "ru",
        "X-HWID" to "116383e366cf0719",
        "X-Device-Os" to "Android",
        "X-Ver-Os" to "16",
        "X-Device-Model" to "SM-A366E",
    )

    private val fallbackAgents = listOf(
        "V2RayTun/1.0",
        "hiddify/2.0.5",
        "v2rayNG/1.8.36",
        "sing-box/1.9.0",
        "clash-meta/1.18.0",
    )

    fun load(input: String): SubscriptionResult {
        val value = input.trim()
        if (value.isEmpty()) {
            return SubscriptionResult(emptyList(), error = "Вставьте ссылку на подписку или список серверов")
        }

        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            val nodes = parseText(value)
            return if (nodes.isEmpty()) {
                SubscriptionResult(emptyList(), error = "Не удалось разобрать серверы из вставленного текста")
            } else {
                SubscriptionResult(nodes)
            }
        }

        var lastError = ""
        for ((index, userAgent) in (listOf<String?>(null) + fallbackAgents).withIndex()) {
            val request = Request.Builder()
                .url(value)
                .apply {
                    if (index == 0) {
                        happHeaders.forEach { (name, headerValue) -> header(name, headerValue) }
                    } else {
                        header("User-Agent", userAgent ?: "")
                    }
                }
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        return@use
                    }
                    val body = response.body ?: return@use
                    if (body.contentLength() > MAX_BYTES) {
                        lastError = "Подписка слишком большая"
                        return@use
                    }
                    val bytes = body.bytes()
                    if (bytes.size > MAX_BYTES) {
                        lastError = "Подписка слишком большая"
                        return@use
                    }
                    val text = String(bytes, Charsets.UTF_8)
                    val nodes = parseText(text)
                    if (nodes.isEmpty()) {
                        lastError = "В ответе нет серверов"
                        return@use
                    }
                    val title = decodeTitle(response.header("profile-title"))
                        .ifBlank { bodyMetaTitle(text).ifBlank { bodyMetaTitle(decodeBody(text)) } }
                    return SubscriptionResult(nodes = nodes, title = title)
                }
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            }
        }

        val friendly = when {
            lastError.contains("404") -> "Подписка не найдена (404) — ссылка устарела"
            lastError.contains("403") || lastError.contains("401") ->
                "Доступ к подписке закрыт ($lastError) — обновите ссылку"
            lastError.isBlank() -> "Сервер подписки не ответил"
            else -> "Не удалось загрузить подписку: $lastError"
        }
        return SubscriptionResult(emptyList(), error = friendly)
    }

    private const val MAX_BYTES = 4L * 1024 * 1024

    private fun parseText(raw: String): List<VpnNode> {
        val text = decodeBody(raw)
        val nodes = LinkedHashMap<String, VpnNode>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim().trim('\uFEFF')
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            if (!trimmed.contains("://")) continue

            val tokens = trimmed.split(Regex("\\s+")).filter { it.contains("://") }
            if (tokens.size > 1) {
                for (token in tokens) {
                    NodeParser.parse(token)?.let { nodes[it.id] = it }
                }
            } else {
                NodeParser.parse(trimmed)?.let { nodes[it.id] = it }
            }
        }
        return nodes.values.toList()
    }

    private fun decodeBody(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.contains("://")) return trimmed
        val decoded = NodeParser.tryB64Decode(trimmed)
        return if (decoded != null && decoded.contains("://")) decoded else trimmed
    }

    private fun decodeTitle(value: String?): String {
        if (value.isNullOrBlank()) return ""
        if (value.startsWith("base64:", true)) {
            return NodeParser.tryB64Decode(value.substring(7))?.trim().orEmpty()
        }
        return value.trim()
    }

    private fun bodyMetaTitle(raw: String): String {
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#")) continue
            val idx = trimmed.indexOf(':')
            if (idx < 2) continue
            val key = trimmed.substring(1, idx).trim().lowercase()
            if (key == "profile-title") return trimmed.substring(idx + 1).trim()
        }
        return ""
    }
}
