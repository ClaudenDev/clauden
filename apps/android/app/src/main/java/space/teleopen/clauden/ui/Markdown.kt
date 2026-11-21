package space.teleopen.clauden.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MdBlock {
    data class TextBlock(val text: String) : MdBlock
    data class CodeBlock(
        val language: String,
        val code: String,
        val name: String = "",
    ) : MdBlock
}

fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val textBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var language = ""
    var codeName = ""
    var inCode = false

    fun flushText() {
        val chunk = textBuffer.toString().trimEnd()
        if (chunk.isNotBlank()) blocks += MdBlock.TextBlock(chunk)
        textBuffer.clear()
    }

    source.lines().forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.CodeBlock(
                    language = language,
                    code = codeBuffer.toString().trimEnd('\n'),
                    name = codeName,
                )
                codeBuffer.clear()
                language = ""
                codeName = ""
                inCode = false
            } else {
                flushText()
                inCode = true
                val info = trimmed.removePrefix("```").trim()
                val parts = info.split(Regex("\\s+"), limit = 2)
                language = parts.firstOrNull().orEmpty()

                val maybeName = parts.getOrNull(1)?.trim().orEmpty()
                codeName = if (maybeName.contains('.')) maybeName.substringAfterLast('/') else ""
            }
        } else if (inCode) {
            codeBuffer.appendLine(line)
        } else {
            textBuffer.appendLine(line)
        }
    }
    if (inCode) {
        blocks += MdBlock.CodeBlock(language, codeBuffer.toString().trimEnd('\n'), codeName)
    }
    flushText()
    return blocks
}

private val mathGreek = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
    "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
    "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π",
    "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ",
    "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
    "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
    "Psi" to "Ψ", "Omega" to "Ω",
)

private val mathSymbols = mapOf(
    "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓",
    "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
    "approx" to "≈", "equiv" to "≡", "sim" to "∼", "propto" to "∝",
    "infty" to "∞", "partial" to "∂", "nabla" to "∇", "sum" to "∑", "prod" to "∏",
    "int" to "∫", "iint" to "∬", "oint" to "∮", "sqrt" to "√",
    "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
    "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩",
    "forall" to "∀", "exists" to "∃", "neg" to "¬", "land" to "∧", "lor" to "∨",
    "rightarrow" to "→", "to" to "→", "leftarrow" to "←", "gets" to "←",
    "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
    "Leftrightarrow" to "⇔", "mapsto" to "↦", "uparrow" to "↑", "downarrow" to "↓",
    "degree" to "°", "circ" to "∘", "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
    "ldots" to "…", "dots" to "…", "cdots" to "⋯", "prime" to "′",
    "quad" to " ", "qquad" to "  ", " " to " ",
)

private val superscripts = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ', 'i' to 'ⁱ',
)
private val subscripts = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', 'a' to 'ₐ', 'e' to 'ₑ',
    'i' to 'ᵢ', 'j' to 'ⱼ', 'n' to 'ₙ', 'x' to 'ₓ',
)

private fun unlatex(value: String): String {
    var text = value
    text = text.replace(Regex("""\\(?:left|right|displaystyle|textstyle|limits)\b"""), "")
    text = text.replace(Regex("""\\(?:,|;|!|:| )"""), " ")
    text = text.replace(Regex("""\\(?:quad|qquad)\b"""), "  ")
    text = text.replace(Regex("""\\(?:mathrm|mathbf|mathit|mathsf|mathtt)\s*\{([^{}]*)\}""")) {
        it.groupValues[1]
    }
    text = text.replace(Regex("""\\(?:text|textrm|textit)\s*\{([^{}]*)\}""")) {
        it.groupValues[1]
    }
    repeat(3) {
        text = text.replace(
            Regex("""\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}"""),
        ) { match ->
            val top = match.groupValues[1].trim()
            val bottom = match.groupValues[2].trim()
            if (top.length <= 3 && bottom.length <= 3) "($top)/($bottom)" else "($top) / ($bottom)"
        }
    }
    text = text.replace(Regex("""\\sqrt\s*\{([^{}]*)\}""")) { "√(${it.groupValues[1]})" }
    text = text.replace(Regex("""\\(?:overline|bar)\s*\{([^{}]*)\}""")) { "${it.groupValues[1]}̄" }
    text = text.replace(Regex("""\\vec\s*\{([^{}]*)\}""")) { "${it.groupValues[1]}⃗" }
    text = text.replace(Regex("""\\hat\s*\{([^{}]*)\}""")) { "${it.groupValues[1]}̂" }
    text = text.replace(Regex("""\\(?:begin|end)\s*\{[^{}]*\}"""), "")
    text = text.replace(Regex("""\\[a-zA-Z]+""")) { match ->
        val name = match.value.removePrefix("\\")
        mathGreek[name] ?: mathSymbols[name] ?: match.value
    }
    text = text.replace(Regex("""\^\{?([0-9+\-ni])\}?""")) { match ->
        superscripts[match.groupValues[1].firstOrNull()]?.toString() ?: match.value
    }
    text = text.replace(Regex("""_\{?([0-9+\-=aeijnx])\}?""")) { match ->
        subscripts[match.groupValues[1].firstOrNull()]?.toString() ?: match.value
    }
    return text
        .replace("&", " ")
        .replace("\\\\", "\n")
        .replace("{}", "")
}

private fun normalizeLatex(source: String): String {
    if (!source.contains('$') && !source.contains("\\(") && !source.contains("\\[")) {
        return source
    }
    val mathSpan = Regex(
        "\\$\\$(.+?)\\$\\$|\\\\\\[(.+?)\\\\\\]|\\\\\\((.+?)\\\\\\)|\\$(.+?)\\$",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    return mathSpan.replace(source) { match ->
        val body = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
        unlatex(body).trim()
    }
}

private fun AnnotatedString.Builder.appendInline(
    source: String,
    colors: ClaudenColors? = null,
) {
    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            c == '`' -> {
                val end = source.indexOf('`', i + 1)
                if (end > i) {
                    val code = source.substring(i + 1, end)
                    val inline = if (colors != null) {
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = colors.blueText,

                            background = colors.control,
                        )
                    } else {
                        SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    }

                    withStyle(inline) {
                        append('\u2009')
                        append(code)
                        append('\u2009')
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            c == '*' && i + 1 < source.length && source[i + 1] == '*' -> {
                val end = source.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(source.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(c)
                    i++
                }
            }

            c == '*' || (c == '_' && i + 1 < source.length) -> {
                val end = source.indexOf(c, i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(source.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            c == '[' -> {
                val close = source.indexOf(']', i + 1)
                val openParen = if (close > 0) source.indexOf('(', close + 1) else -1
                val closeParen = if (openParen > 0) source.indexOf(')', openParen + 1) else -1
                if (close > 0 && openParen == close + 1 && closeParen > openParen) {
                    withStyle(
                        SpanStyle(
                            color = colors?.blueLink
                                ?: androidx.compose.ui.graphics.Color(0xFFD97757),
                            textDecoration = TextDecoration.Underline,
                        )
                    ) { append(source.substring(i + 1, close)) }
                    i = closeParen + 1
                } else {
                    append(c)
                    i++
                }
            }

            else -> {
                append(c)
                i++
            }
        }
    }
}

fun buildInlineMarkdown(
    source: String,
    colors: ClaudenColors? = null,
): AnnotatedString = buildAnnotatedString {
    val normalized = normalizeLatex(source)
    val trimmed = normalized.trimStart()
    when {
        trimmed.startsWith("### ") -> withStyle(
            SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        ) { appendInline(trimmed.removePrefix("### "), colors) }

        trimmed.startsWith("## ") -> withStyle(
            SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        ) { appendInline(trimmed.removePrefix("## "), colors) }

        trimmed.startsWith("# ") -> withStyle(
            SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        ) { appendInline(trimmed.removePrefix("# "), colors) }

        else -> appendInline(normalized, colors)
    }
}

@Composable
fun MarkdownText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    onOpenCode: (ChatArtifact) -> Unit = {},
) {
    val colors = LocalClaudenColors.current
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.TextBlock -> TextBlockView(block.text, style, textColor)
                is MdBlock.CodeBlock -> {
                    val isSnippet = block.code.lines().size == 1 && block.code.length <= 80
                    if (isSnippet) {
                        SingleLineCode(block.code)
                    } else {
                        val fileName = remember(block, blocks, index) {
                            codeFileName(block, blocks, index)
                        }
                        CodeFileCard(
                            title = fileName,
                            language = block.language,
                            block = block,
                            onOpen = {
                                onOpenCode(
                                    ChatArtifact(
                                        id = "code-${fileName}-$index",
                                        title = fileName,
                                        type = "Код",
                                        content = block.code,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun codeFileName(
    block: MdBlock.CodeBlock,
    blocks: List<MdBlock>,
    index: Int,
): String {
    if (block.name.isNotBlank()) return block.name
    val previous = (blocks.getOrNull(index - 1) as? MdBlock.TextBlock)?.text.orEmpty()
    val found = Regex(
        """([\w.-]*\.(?:py|kt|java|js|ts|tsx|jsx|sh|bash|env|json|ya?ml|sql|html|css|txt|toml|ini|cfg|conf|xml|go|rs|c|cc|cpp|h|md))\b""",
        RegexOption.IGNORE_CASE,
    ).find(previous)?.groupValues?.get(1)
    if (!found.isNullOrBlank()) return found

    val hint = previous.lowercase()
    if (hint.contains("зависимост") || hint.contains("requirements")) return "requirements.txt"
    if (hint.contains("dockerfile") || hint.contains("докерфайл")) return "Dockerfile"
    if (hint.contains("compose")) return "docker-compose.yml"

    return when (block.language.lowercase()) {
        "python", "py" -> "main.py"
        "kotlin", "kt" -> "Main.kt"
        "java" -> "Main.java"
        "javascript", "js" -> "main.js"
        "typescript", "ts" -> "main.ts"
        "bash", "sh", "shell", "zsh" -> "script.sh"
        "env", "dotenv" -> ".env"
        "json" -> "data.json"
        "yaml", "yml" -> "config.yml"
        "sql" -> "query.sql"
        "html" -> "index.html"
        "css" -> "styles.css"
        "go" -> "main.go"
        "rust", "rs" -> "main.rs"
        "dockerfile" -> "Dockerfile"
        "toml" -> "config.toml"
        else -> "snippet.txt"
    }
}

@Composable
private fun SingleLineCode(code: String) {
    val colors = LocalClaudenColors.current
    Surface(color = colors.codeBg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = code,
            style = ClaudenType.mono.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.blueText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun CodeFileCard(
    title: String,
    language: String,
    block: MdBlock.CodeBlock,
    onOpen: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val clipboard = LocalClipboardManager.current
    Surface(
        color = colors.field,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.clay.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ClaudenIcons.code,
                    contentDescription = null,
                    tint = colors.clay,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Код · " + language.uppercase().ifBlank { "TXT" },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
            }
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(block.code)) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = ClaudenIcons.share,
                    contentDescription = "Скопировать код",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun TextBlockView(text: String, style: TextStyle, textColor: Color? = null) {
    val colors = LocalClaudenColors.current
    val primary = textColor ?: colors.textPrimary
    val inlineColors = if (textColor == null) colors else colors.copy(textPrimary = textColor)
    val lines = remember(text) { parseTextBlocks(text) }
    Column {
        lines.forEachIndexed { index, line ->
            val first = index == 0
            when (line) {
                is MdLine.Heading -> Text(
                    text = buildInlineMarkdown(line.text, inlineColors),
                    style = style.copy(
                        fontSize = when (line.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            3 -> 17.sp
                            else -> 16.sp
                        },
                        lineHeight = when (line.level) {
                            1 -> 30.sp
                            2 -> 27.sp
                            else -> 25.sp
                        },
                        fontWeight = if (line.level <= 3) FontWeight.Bold else FontWeight.SemiBold,
                        color = primary,
                    ),
                    modifier = Modifier.padding(top = if (first) 0.dp else 14.dp, bottom = 2.dp),
                )

                is MdLine.Paragraph -> Text(
                    text = buildInlineMarkdown(line.text, inlineColors),
                    style = style.copy(color = primary),
                    modifier = Modifier.padding(top = if (first) 0.dp else 10.dp),
                )

                is MdLine.Bullet -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = if (first) 2.dp else 7.dp),
                ) {
                    Text(
                        text = "•",
                        style = style.copy(color = primary),
                        modifier = Modifier.width(12.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildInlineMarkdown(line.text, inlineColors),
                        style = style.copy(color = primary),
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdLine.Ordered -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = if (first) 2.dp else 7.dp),
                ) {
                    Text(
                        text = line.number + ".",
                        style = style.copy(color = primary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.width(26.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildInlineMarkdown(line.text, inlineColors),
                        style = style.copy(color = primary),
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdLine.Table -> Box(modifier = Modifier.padding(top = if (first) 2.dp else 10.dp)) {
                    MarkdownTable(rows = line.rows, style = style)
                }
            }
        }
    }
}

internal sealed interface MdLine {
    data class Heading(val level: Int, val text: String) : MdLine
    data class Paragraph(val text: String) : MdLine
    data class Bullet(val text: String) : MdLine
    data class Ordered(val number: String, val text: String) : MdLine
    data class Table(val rows: List<List<String>>) : MdLine
}

private val headingLine = Regex("^(#{1,6})\\s+(.*)$")
private val bulletLine = Regex("^[-*•]\\s+(.*)$")
private val orderedLine = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
private val boldHeadingLine = Regex("^\\*\\*([^*]{1,80})\\*\\*$|^__([^_]{1,80})__$")

internal fun parseTextBlocks(text: String): List<MdLine> {
    val lines = text.lines()
    val blocks = mutableListOf<MdLine>()
    var index = 0

    fun isBlockStart(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.isBlank() ||
            headingLine.matches(trimmed) ||
            boldHeadingLine.matches(trimmed) ||
            bulletLine.matches(trimmed) ||
            orderedLine.matches(trimmed) ||
            trimmed.startsWith("|") ||
            trimmed.startsWith(">")
    }

    while (index < lines.size) {
        val raw = lines[index]
        val trimmed = raw.trimStart()
        when {
            trimmed.isBlank() -> index++

            headingLine.matches(trimmed) -> {
                val match = headingLine.find(trimmed)!!
                blocks += MdLine.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim(),
                )
                index++
            }

            boldHeadingLine.matches(trimmed) -> {
                val match = boldHeadingLine.find(trimmed)!!
                val title = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()

                if (title.isNotEmpty()) {
                    blocks += MdLine.Heading(level = 3, text = title)
                }
                index++
            }

            looksLikeTable(lines, index) -> {
                val rows = mutableListOf<List<String>>()
                rows += parseTableRow(lines[index])
                index += 2
                while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                    rows += parseTableRow(lines[index])
                    index++
                }
                blocks += MdLine.Table(rows)
            }

            bulletLine.matches(trimmed) -> {
                val match = bulletLine.find(trimmed)!!
                val body = StringBuilder(match.groupValues[1].trim())
                index++
                while (index < lines.size && lines[index].isNotBlank() &&
                    !isBlockStart(lines[index])
                ) {
                    body.append(' ').append(lines[index].trim())
                    index++
                }
                blocks += MdLine.Bullet(body.toString())
            }

            orderedLine.matches(trimmed) -> {
                val match = orderedLine.find(trimmed)!!
                val body = StringBuilder(match.groupValues[2].trim())
                index++
                while (index < lines.size && lines[index].isNotBlank() &&
                    !isBlockStart(lines[index])
                ) {
                    body.append(' ').append(lines[index].trim())
                    index++
                }
                blocks += MdLine.Ordered(match.groupValues[1], body.toString())
            }

            trimmed.startsWith(">") -> {
                val body = StringBuilder(trimmed.removePrefix(">").trimStart())
                index++
                while (index < lines.size && lines[index].isNotBlank() &&
                    lines[index].trimStart().startsWith(">")
                ) {
                    body.append('\n').append(lines[index].trimStart().removePrefix(">").trimStart())
                    index++
                }
                blocks += MdLine.Paragraph(body.toString())
            }

            else -> {
                val paragraph = StringBuilder()
                while (index < lines.size && lines[index].isNotBlank() &&
                    !isBlockStart(lines[index])
                ) {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(lines[index].trim())
                    index++
                }
                blocks += MdLine.Paragraph(paragraph.toString())
            }
        }
    }
    return blocks
}

private fun looksLikeTable(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val head = lines[index].trim()
    if (!head.contains('|')) return false
    val separator = lines[index + 1].trim()
    if (!separator.contains('-') || !separator.contains('|')) return false
    val cells = separator.removePrefix("|").removeSuffix("|").split('|')
    return cells.isNotEmpty() && cells.all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && c.all { it == '-' || it == ':' }
    }
}

private fun parseTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|")
        .split('|')
        .map { it.trim() }

@Composable
private fun MarkdownTable(rows: List<List<String>>, style: TextStyle) {
    val colors = LocalClaudenColors.current
    if (rows.isEmpty()) return
    val columns = rows.maxOf { it.size }
    val shape = RoundedCornerShape(8.dp)

    val widths = (0 until columns).map { column ->
        val longest = rows.take(24).maxOfOrNull { row ->
            row.getOrElse(column) { "" }.length
        } ?: 0
        (longest.coerceIn(7, 32) * 7 + 22).dp
    }
    val tableWidth = widths.fold(0.dp) { total, width -> total + width }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(tableWidth)
                .clip(shape)
                .border(0.5.dp, colors.border, shape),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.width(tableWidth)) {
                    for (column in 0 until columns) {
                        Box(
                            modifier = Modifier
                                .width(widths[column])
                                .background(if (rowIndex == 0) colors.bg300 else Color.Transparent)
                                .border(0.25.dp, colors.border)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = buildInlineMarkdown(row.getOrElse(column) { "" }, colors),
                                style = style.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val codeKeywords = mapOf(
    "kotlin" to setOf("fun", "val", "var", "class", "object", "interface", "data", "sealed", "if", "else", "when", "for", "while", "return", "import", "package", "in", "is", "as", "null", "true", "false", "try", "catch", "finally", "throw", "override", "private", "public", "internal", "suspend", "by", "init", "companion", "this", "super", "typealias"),
    "java" to setOf("public", "private", "protected", "class", "interface", "extends", "implements", "static", "final", "void", "int", "long", "double", "float", "boolean", "char", "if", "else", "for", "while", "return", "new", "try", "catch", "finally", "throw", "import", "package", "null", "true", "false", "this", "super"),
    "python" to setOf("def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "in", "is", "not", "and", "or", "None", "True", "False", "try", "except", "finally", "with", "lambda", "yield", "async", "await", "pass", "raise", "global", "nonlocal", "self"),
    "javascript" to setOf("const", "let", "var", "function", "class", "extends", "if", "else", "for", "while", "return", "import", "export", "from", "new", "try", "catch", "finally", "throw", "async", "await", "null", "undefined", "true", "false", "this", "typeof", "instanceof", "switch", "case", "break", "continue"),
    "typescript" to setOf("const", "let", "var", "function", "class", "interface", "type", "enum", "extends", "implements", "if", "else", "for", "while", "return", "import", "export", "from", "new", "try", "catch", "finally", "throw", "async", "await", "null", "undefined", "true", "false", "this", "typeof", "instanceof", "switch", "case", "break", "continue", "public", "private", "readonly"),
    "rust" to setOf("fn", "let", "mut", "struct", "enum", "impl", "trait", "pub", "use", "mod", "if", "else", "match", "for", "while", "loop", "return", "self", "Self", "true", "false", "Some", "None", "Ok", "Err", "async", "await", "move", "where", "crate", "super"),
    "go" to setOf("func", "package", "import", "type", "struct", "interface", "var", "const", "if", "else", "for", "range", "return", "go", "defer", "select", "case", "switch", "break", "continue", "map", "chan", "nil", "true", "false"),
    "sql" to setOf("select", "from", "where", "join", "left", "right", "inner", "outer", "on", "group", "by", "order", "having", "insert", "into", "values", "update", "set", "delete", "create", "table", "index", "and", "or", "not", "null", "as", "limit", "offset", "distinct", "count", "sum", "avg"),
    "bash" to setOf("if", "then", "else", "elif", "fi", "for", "do", "done", "while", "case", "esac", "function", "return", "export", "local", "echo", "cd", "sudo", "rm", "cp", "mv", "grep", "sed", "awk", "cat"),
    "shell" to setOf("if", "then", "else", "elif", "fi", "for", "do", "done", "while", "case", "esac", "function", "return", "export", "local", "echo", "cd", "sudo", "rm", "cp", "mv", "grep", "sed", "awk", "cat"),
)

private fun highlightCode(code: String, language: String, colors: ClaudenColors): AnnotatedString {
    val lang = language.lowercase().removePrefix("language-")
    val keywords = codeKeywords[lang] ?: codeKeywords[lang.substringBefore(' ')] ?: emptySet()
    val hashComments = lang in setOf("python", "bash", "shell", "ruby", "yaml", "yml", "toml")
    val builder = AnnotatedString.Builder()
    var i = 0
    while (i < code.length) {
        val c = code[i]
        when {
            c == '/' && i + 1 < code.length && code[i + 1] == '/' -> {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                builder.withStyle(SpanStyle(color = colors.textTertiary)) {
                    append(code.substring(i, end))
                }
                i = end
            }

            c == '#' && hashComments -> {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                builder.withStyle(SpanStyle(color = colors.textTertiary)) {
                    append(code.substring(i, end))
                }
                i = end
            }

            c == '"' || c == '\'' || c == '`' -> {
                var j = i + 1
                while (j < code.length) {
                    if (code[j] == '\\') {
                        j += 2
                        continue
                    }
                    if (code[j] == c) {
                        j++
                        break
                    }
                    j++
                }
                builder.withStyle(SpanStyle(color = colors.clay)) {
                    append(code.substring(i, j.coerceAtMost(code.length)))
                }
                i = j
            }

            c.isDigit() -> {
                var j = i + 1
                while (j < code.length && (code[j].isDigit() || code[j] == '.' || code[j] == '_')) j++
                builder.withStyle(SpanStyle(color = colors.textSecondary)) {
                    append(code.substring(i, j))
                }
                i = j
            }

            c.isLetter() || c == '_' -> {
                var j = i + 1
                while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                if (word in keywords || word.lowercase() in keywords) {
                    builder.withStyle(SpanStyle(color = colors.blueLink)) { append(word) }
                } else {
                    builder.append(word)
                }
                i = j
            }

            else -> {
                builder.append(c)
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun CodeBlockView(block: MdBlock.CodeBlock) {
    val colors = LocalClaudenColors.current
    val clipboard = LocalClipboardManager.current
    Surface(
        color = colors.codeBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(block.code)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = ClaudenIcons.copy,
                        contentDescription = "Копировать код",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = remember(block.code, block.language, colors) {
                        highlightCode(block.code, block.language, colors)
                    },
                    style = ClaudenType.mono.copy(fontSize = 13.sp, lineHeight = 20.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(end = 8.dp, bottom = 2.dp),
                )
            }
        }
    }
}
