package space.teleopen.claudencode.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

fun inline(text: String, base: androidx.compose.ui.text.TextStyle? = null): AnnotatedString {
    return formatInline(text, Pal.White, italicBody = false)
}

private data class Token(val range: IntRange, val style: SpanStyle)

private fun formatInline(text: String, baseColor: Color, italicBody: Boolean):
    AnnotatedString = buildAnnotatedString {
    var plain = ""
    var index = 0
    val styles = mutableListOf<Pair<IntRange, SpanStyle>>()

    while (index < text.length) {
        val bold = text.indexOf("**", index)
        val code = text.indexOf('`', index)
        val link = text.indexOf('[', index)
        when {
            bold >= 0 && (code < 0 || bold < code) && (link < 0 || bold < link) ->
                run {
                    val end = text.indexOf("**", bold + 2)
                    if (end > bold) {
                        plain += text.substring(index, bold)
                        val start = plain.length
                        plain += text.substring(bold + 2, end)
                        styles += IntRange(start, plain.length) to SpanStyle(
                            color = ColorTone.Bright,
                            fontWeight = FontWeight.Bold,
                        )
                        index = end + 2
                        return@run
                    }
                    plain += text[index]
                    index++
                }
            code >= 0 && (link < 0 || code < link) -> run {
                val end = text.indexOf('`', code + 1)
                if (end > code) {
                    plain += text.substring(index, code)
                    val start = plain.length
                    plain += text.substring(code + 1, end)
                    styles += IntRange(start, plain.length) to SpanStyle(color = Pal.Peri)
                    index = end + 1
                    return@run
                }
                plain += text[index]
                index++
            }
            link >= 0 -> run {
                val close = text.indexOf(']', link)
                val paren = if (close > link) text.indexOf('(', close) else -1
                val end = if (paren > close) text.indexOf(')', paren) else -1
                if (close > link && paren == close + 1 && end > paren) {
                    plain += text.substring(index, link)
                    val start = plain.length
                    plain += text.substring(link + 1, close)
                    styles += IntRange(start, plain.length) to SpanStyle(
                        color = Pal.PeriSoft,
                        textDecoration = null,
                    )
                    index = end + 1
                    return@run
                }
                plain += text[index]
                index++
            }
            else -> {
                plain += text.substring(index)
                index = text.length
            }
        }
    }

    append(plain)
    styles.forEach { (range, style) ->
        runCatching { addStyle(style, range.first, range.last + 1) }
    }
}

private object ColorTone {
    val Bright = Pal.White
}
