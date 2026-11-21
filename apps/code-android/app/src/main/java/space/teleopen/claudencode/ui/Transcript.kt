package space.teleopen.claudencode.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.claudencode.data.CodeForm
import space.teleopen.claudencode.data.CodeMessage
import space.teleopen.claudencode.data.CodeSession
import space.teleopen.claudencode.data.CodeStep

sealed interface Block {
    data class Assistant(
        val messages: List<CodeMessage>,
        val streaming: Boolean,
        val fallbackTs: Long,
    ) : Block
    data class User(val message: CodeMessage) : Block
    data class Options(val form: CodeForm) : Block
    data class Note(val text: String, val tone: NoteTone = NoteTone.Gray) : Block
    data class Todos(val count: Int) : Block
}

enum class NoteTone { Gray, Red }

fun choiceForm(session: CodeSession): CodeForm? = session.forms.firstOrNull { form ->
    form.fields.size == 1 && form.fields.first().options.isNotEmpty() && !form.fields.first().custom
}

fun buildBlocks(session: CodeSession): List<Block> {
    val blocks = mutableListOf<Block>()
    var group: MutableList<CodeMessage>? = null

    fun flush() {
        val items = group ?: return
        group = null
        if (items.isEmpty()) return
        val meaningful = items.any {
            it.steps.isNotEmpty() || cleanAssistantText(it.text).isNotBlank()
        }
        if (!meaningful) return
        blocks += Block.Assistant(
            messages = items.toList(),
            streaming = items.any { it.status != "done" },
            fallbackTs = session.updatedAt,
        )
    }

    session.messages.forEach { message ->
        when {
            message.role == "user" -> {
                flush()
                blocks += Block.User(message)
            }
            message.role == "assistant" || message.role.isBlank() -> {
                if (group == null) group = mutableListOf()
                group?.add(message)
            }
        }
    }
    flush()
    choiceForm(session)?.let { blocks += Block.Options(it) }
    if (session.todos.isNotEmpty()) blocks += Block.Todos(session.todos.size)
    if (session.error.isNotBlank()) {
        blocks += Block.Note(session.error, NoteTone.Red)
    }
    if (session.status == "stopped" || session.status == "interrupted") {
        blocks += Block.Note("Остановлено пользователем")
    }
    return blocks
}

private fun cleanAssistantText(raw: String): String =
    raw.replace(Regex("(?s)<device_shell[^>]*>.*?</device_shell>"), "")
        .trim()

@Composable
fun BlockView(
    block: Block,
    userName: String,
    onTodos: () -> Unit = {},
    onAnswerForm: (CodeForm, Map<String, String>) -> Unit = { _, _ -> },
    onInteract: () -> Unit = {},
) {
    when (block) {
        is Block.Assistant -> AssistantView(block, onInteract)
        is Block.User -> UserView(block.message, userName)
        is Block.Options -> OptionsView(block.form, onAnswerForm)
        is Block.Note -> Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
        ) {
            Text(
                block.text,
                color = if (block.tone == NoteTone.Red) Pal.Red else Pal.Dim,
                style = type(12.5.sp, null, 1.4f),
            )
        }
        is Block.Todos -> Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .termClickable(onTodos),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCheckRing(size = 18.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                "Список задач · ${block.count}",
                color = Pal.PeriSoft,
                style = type(12.5.sp, null, 1.3f),
            )
            Spacer(Modifier.width(6.dp))
            IconChevron(size = 14.dp, tint = Pal.Faint)
        }
    }
}

@Composable
private fun AssistantView(block: Block.Assistant, onInteract: () -> Unit) {
    val ts = block.messages.firstOrNull { it.ts > 0 }?.ts ?: block.fallbackTs
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ClaudeMark(size = 23.dp)
        Spacer(Modifier.width(21.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Claude Code",
                    color = Pal.Accent,
                    style = type(14.sp, FontWeight.Bold, 1.25f),
                )
                val time = timeLabel(ts)
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        time,
                        color = Pal.Dim,
                        style = type(11.5.sp, null, 1.2f),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            block.messages.forEach { message ->
                message.steps.forEach { step ->
                    StepCard(step, onInteract)
                    Spacer(Modifier.height(8.dp))
                }
                val text = cleanAssistantText(message.text)
                if (text.isNotBlank()) {
                    MarkdownText(text)
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (block.streaming) {
                TypingDots()
            }
        }
    }
}

@Composable
private fun UserView(message: CodeMessage, userName: String) {
    val letter = userName.trim().firstOrNull()?.uppercase() ?: "Я"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(Color(0xFF2A2827), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                color = Pal.PeriSoft,
                style = type(13.sp, FontWeight.Medium, 1.1f),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ты", color = Pal.White, style = type(14.sp, FontWeight.Bold, 1.25f))
                val time = timeLabel(message.ts)
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(time, color = Pal.Dim, style = type(11.5.sp, null, 1.2f))
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkdownText(message.text)
        }
    }
}

@Composable
private fun StepCard(step: CodeStep, onInteract: () -> Unit) {
    val view = stepView(step)
    var expanded by remember(step.id, step.status) { mutableStateOf(false) }
    val expandable = view.detail.isNotBlank() || view.subtitle.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pal.CodeBg, RoundedCornerShape(12.dp))
            .border(1.dp, Pal.CodeBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = expandable) {
                onInteract()
                expanded = !expanded
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepState(step.status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    view.title,
                    color = Pal.White,
                    style = type(13.5.sp, FontWeight.Medium, 1.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (view.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        view.subtitle,
                        color = Pal.Gray,
                        style = type(12.sp, null, 1.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expandable) {
                Spacer(Modifier.width(8.dp))
                IconChevron(
                    size = 16.dp,
                    tint = Pal.Dim,
                    up = expanded,
                )
            }
        }
        if (expanded && view.detail.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Pal.BorderSoft),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                view.detail,
                color = Pal.PeriSoft,
                style = type(11.5.sp, null, 1.5f),
                maxLines = 22,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StepState(status: String) {
    when (status) {
        "running", "working" -> StepSpinner()
        "error", "failed" -> Box(
            Modifier
                .size(22.dp)
                .background(Color(0xFF2A1210), CircleShape)
                .border(1.4.dp, Pal.Red, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconClose(size = 12.dp, tint = Pal.Red)
        }
        else -> IconCheckRing(size = 22.dp)
    }
}

@Composable
private fun StepSpinner() {
    val transition = rememberInfiniteTransition("step")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
        ),
        label = "stepAngle",
    )
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(19.dp)
                .rotate(angle),
        ) {
            drawArc(
                color = Pal.Accent,
                startAngle = 20f,
                sweepAngle = 285f,
                useCenter = false,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

private data class StepView(
    val title: String,
    val subtitle: String,
    val detail: String,
)

private fun stepView(step: CodeStep): StepView {
    val name = step.name.lowercase()
    val raw = step.title.trim()
    val clean = raw.removePrefix("$ ").trim()
    val detail = buildString {
        if (raw.isNotBlank()) append(raw)
        if (step.detail.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(step.detail)
        }
    }
    return when {
        name.contains("shell") || name.contains("bash") || name.contains("execute") ->
            StepView("Выполняю команду…", clean.take(96), detail)
        name.contains("read") ->
            StepView("Читаю файл…", fileName(clean), detail)
        name.contains("write") || name.contains("edit") || name.contains("patch") ->
            StepView("Правлю файл…", fileName(clean), detail)
        name.contains("webfetch") ->
            StepView("Открываю страницу…", clean.take(96), detail)
        name.contains("websearch") ->
            StepView("Ищу в интернете…", clean.take(96), detail)
        name.contains("grep") || name.contains("glob") || name.contains("list") ||
            name.contains("search") || name.contains("fs") ->
            StepView("Просматриваю репозиторий…", clean.take(96), detail)
        name.contains("todo") ->
            StepView("Обновляю задачи…", "", detail)
        name == "tool" || raw.isBlank() ->
            StepView("Просматриваю репозиторий…", "", detail)
        else -> StepView("Работаю…", clean.take(96), detail)
    }
}

private fun fileName(raw: String): String {
    var text = raw
    for (prefix in listOf("Чтение ", "Правка ", "Создаю ", "Правлю ", "Читаю ")) {
        if (text.startsWith(prefix)) text = text.removePrefix(prefix)
    }
    return text.trim().trimEnd(',', ':').split('/', '\\').lastOrNull { it.isNotBlank() }
        ?: text.take(80)
}

@Composable
private fun OptionsView(
    form: CodeForm,
    onAnswer: (CodeForm, Map<String, String>) -> Unit,
) {
    val field = form.fields.firstOrNull() ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (form.title.isNotBlank()) {
            Text(
                form.title,
                color = Pal.Gray,
                style = type(11.5.sp, null, 1.3f),
            )
        }
        field.options.forEachIndexed { index, option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.CodeBg, RoundedCornerShape(12.dp))
                    .border(1.dp, Pal.CodeBorder, RoundedCornerShape(12.dp))
                    .termClickable {
                        onAnswer(form, mapOf(field.key to option.value))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    color = Pal.Dim,
                    style = type(12.5.sp, null, 1.2f),
                    modifier = Modifier.width(18.dp),
                )
                IconChevron(size = 15.dp, tint = Pal.Dim)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        option.label.ifBlank { option.value },
                        color = Pal.White,
                        style = type(13.5.sp, FontWeight.Medium, 1.3f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (option.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            option.description,
                            color = Pal.Dim,
                            style = type(11.sp, null, 1.3f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val ordered: Boolean, val number: String = "") : MdBlock
    data class Code(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

private fun parseMarkdown(source: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = source.replace("\r\n", "\n").split('\n')
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        out += MdBlock.Paragraph(paragraph.joinToString("\n").trimEnd())
        paragraph.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                out += MdBlock.Code(code.joinToString("\n"))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code += line
            continue
        }
        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                out += MdBlock.Rule
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                out += MdBlock.Heading(trimmed.trimStart('#').trim())
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                out += MdBlock.Quote(trimmed.removePrefix("> ").trim())
            }
            Regex("^\\s*[-*•]\\s+.*").matches(line) -> {
                flushParagraph()
                out += MdBlock.Bullet(
                    trimmed.replaceFirst(Regex("^[-*•]\\s+"), ""),
                    ordered = false,
                )
            }
            Regex("^\\s*\\d+[.)]\\s+.*").matches(line) -> {
                flushParagraph()
                val number = trimmed.takeWhile { it.isDigit() }
                out += MdBlock.Bullet(
                    trimmed.replaceFirst(Regex("^\\d+[.)]\\s+"), ""),
                    ordered = true,
                    number = number,
                )
            }
            else -> paragraph += line
        }
    }
    if (inCode && code.isNotEmpty()) out += MdBlock.Code(code.joinToString("\n"))
    flushParagraph()
    return out
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        inlineMarkdown(block.text),
                        color = Pal.White,
                        style = type(16.sp, FontWeight.Bold, 1.35f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.Paragraph -> {
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        inlineMarkdown(block.text),
                        color = Pal.White,
                        style = type(14.5.sp, null, 1.55f),
                    )
                }
                is MdBlock.Bullet -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (index > 0) 6.dp else 0.dp),
                    ) {
                        Text(
                            if (block.ordered) "${block.number}." else "•",
                            color = Pal.Accent,
                            style = type(14.sp, FontWeight.Medium, 1.5f),
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            inlineMarkdown(block.text),
                            color = Pal.White,
                            style = type(14.5.sp, null, 1.5f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MdBlock.Code -> {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Pal.CodeBg, RoundedCornerShape(10.dp))
                            .border(1.dp, Pal.CodeBorder, RoundedCornerShape(10.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            block.text,
                            color = Pal.PeriSoft,
                            style = type(12.sp, null, 1.65f),
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                is MdBlock.Quote -> {
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Pal.CodeBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .background(Pal.AccentDim, RoundedCornerShape(1.dp)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            inlineMarkdown(block.text),
                            color = Pal.PeriSoft,
                            style = type(13.5.sp, null, 1.5f),
                        )
                    }
                }
                MdBlock.Rule -> {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Pal.Border),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val bold = text.indexOf("**", index)
        val code = text.indexOf('`', index)
        val link = text.indexOf('[', index)
        val next = listOf(bold, code, link).filter { it >= 0 }.minOrNull() ?: -1
        if (next < 0) {
            append(text.substring(index))
            break
        }
        append(text.substring(index, next))
        when (next) {
            bold -> {
                val end = text.indexOf("**", bold + 2)
                if (end > bold) {
                    pushStyle(SpanStyle(color = Pal.White, fontWeight = FontWeight.Bold))
                    append(text.substring(bold + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append("**")
                    index = bold + 2
                }
            }
            code -> {
                val end = text.indexOf('`', code + 1)
                if (end > code) {
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFFB8B4AF),
                            background = Color(0xFF1B1A19),
                            fontSize = 12.5.sp,
                        ),
                    )
                    append(" ${text.substring(code + 1, end)} ")
                    pop()
                    index = end + 1
                } else {
                    append('`')
                    index = code + 1
                }
            }
            else -> {
                val close = text.indexOf(']', link)
                val paren = if (close > link) text.indexOf('(', close) else -1
                val end = if (paren == close + 1) text.indexOf(')', paren) else -1
                if (close > link && end > paren) {
                    pushStyle(
                        SpanStyle(
                            color = Pal.PeriSoft,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    append(text.substring(link + 1, close))
                    pop()
                    index = end + 1
                } else {
                    append('[')
                    index = link + 1
                }
            }
        }
    }
}
