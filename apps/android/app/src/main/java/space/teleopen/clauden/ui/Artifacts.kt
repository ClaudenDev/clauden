package space.teleopen.clauden.ui

import space.teleopen.clauden.data.ChatMessage

data class ChatArtifact(
    val id: String,
    val title: String,
    val type: String,
    val content: String,
)

private val artifactRegex = Regex(
    pattern = """<(?:ant)?artifact\b([^>]*)>(.*?)</(?:ant)?artifact>""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
)

private val attributeRegex = Regex("""([A-Za-z_][A-Za-z0-9_-]*)\s*=\s*"([^"]*)"""")

internal fun artifactKey(artifact: ChatArtifact): String {
    val title = artifact.title.trim()

    if (title.isNotBlank() && !title.startsWith("Артефакт", ignoreCase = true)) {
        return title.lowercase()
    }
    val id = artifact.id.trim()
    return if (id.isNotBlank()) id.lowercase() else title.lowercase() + "|" + artifact.type.lowercase()
}

fun parseArtifacts(text: String): List<ChatArtifact> {
    val found = LinkedHashMap<String, ChatArtifact>()
    artifactRegex.findAll(text).forEachIndexed { index, match ->
        val attributes = attributeRegex.findAll(match.groupValues[1]).associate {
            it.groupValues[1].lowercase() to it.groupValues[2]
        }
        val content = match.groupValues[2].trim('\n')
        if (content.isNotBlank()) {
            val title = attributes["title"] ?: attributes["identifier"] ?: "Артефакт ${index + 1}"
            val type = attributes["type"] ?: "text/plain"

            if (title != "Файл" || !type.equals("text/plain", ignoreCase = true)) {
                val artifact = ChatArtifact(
                    id = attributes["identifier"] ?: "artifact-$index",
                    title = title,
                    type = type,
                    content = content,
                )

                found[artifactKey(artifact)] = artifact
            }
        }
    }
    return found.values.toList()
}

fun latestArtifactMessageTs(messages: List<ChatMessage>): Map<String, Long> {
    val latest = LinkedHashMap<String, Long>()
    messages.forEach { message ->
        if (message.role != "assistant") return@forEach
        parseArtifacts(message.content).forEach { artifact ->
            latest[artifactKey(artifact)] = message.ts
        }
    }
    return latest
}

fun latestArtifacts(messages: List<ChatMessage>): Map<String, ChatArtifact> {
    val latest = LinkedHashMap<String, ChatArtifact>()
    messages.forEach { message ->
        if (message.role != "assistant") return@forEach
        parseArtifacts(message.content).forEach { artifact ->
            latest[artifactKey(artifact)] = artifact
        }
    }
    return latest
}

fun stripArtifacts(text: String): String {

    if (!text.contains("<artifact", ignoreCase = true)) return text.trim()
    return artifactRegex.replace(text, "").trim()
}

fun ChatArtifact.isHtml(): Boolean =
    type.contains("html", ignoreCase = true) || type.contains("svg", ignoreCase = true)
