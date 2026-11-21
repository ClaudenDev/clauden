package space.teleopen.clauden.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.ChatAction
import space.teleopen.clauden.data.DeviceActions
import space.teleopen.clauden.data.WidgetData
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ActionCard(action: ChatAction) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var localStatus by remember(action.id, action.status) { mutableStateOf(action.status) }
    var localDetail by remember(action.id, action.detail) { mutableStateOf(action.detail) }
    val canOpen = (
        action.type in setOf(
            "alarm",
            "timer",
            "calendar",
            "calendar_update",
            "calendar_delete",
            "map",
        ) || (action.type == "location" && localStatus == "failed")
        ) && (localStatus == "ready" || localStatus == "failed")
    val widget = action.widget
    val htmlWidget = action.type == "widget" &&
        widget?.kind.equals("html", ignoreCase = true) &&
        !widget?.html.isNullOrBlank()
    var expanded by remember(action.id) { mutableStateOf(false) }
    val failed = localStatus == "failed"
    val dangerAction = action.type == "calendar_delete"
    val accent = if (failed || dangerAction) colors.danger else colors.clay
    val completed = localStatus == "done" ||
        localStatus == "sent" ||
        localStatus == "draft"

    val title = actionTitle(action)
    val detail = localDetail.ifBlank { actionDefaultDetail(action.copy(status = localStatus)) }
    val open = {
        if (canOpen) {
            val result = DeviceActions.open(context, action)
            localStatus = result.status
            localDetail = result.detail
        }
    }

    Surface(
        color = colors.field,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (failed) colors.danger.copy(alpha = 0.45f) else colors.border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp, top = 4.dp, bottom = 2.dp)
            .clickable(enabled = canOpen, onClick = open),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (failed || dangerAction) {
                                colors.danger.copy(alpha = 0.14f)
                            } else {
                                colors.clay.copy(alpha = 0.16f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = actionIcon(action),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = if (failed) colors.danger else colors.textTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (htmlWidget) {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = ClaudenIcons.external,
                            contentDescription = "Открыть на весь экран",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                when {
                    canOpen -> Text(
                        text = when {
                            action.type == "calendar" -> "Создать"
                            action.type == "calendar_update" -> "Обновить"
                            action.type == "calendar_delete" -> "Удалить"
                            action.type == "location" -> "Включить"
                            localStatus == "failed" -> "Повторить"
                            else -> "Открыть"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.clay,
                    )
                    completed -> Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.clay.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Готово",
                            tint = colors.clay,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }

            if (action.type == "widget" && widget != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (htmlWidget) {
                    HtmlWidget(
                        html = widget.html,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(368.dp),
                    )
                } else {
                    WidgetChart(widget)
                }
            }

            if (action.type == "message" && action.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = colors.codeBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = action.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    ActionTextButton("Копировать") {
                        clipboard.setText(AnnotatedString(action.text))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    ActionTextButton("Поделиться") {
                        shareText(context, action.text)
                    }
                }
            }
        }
    }

    if (expanded && htmlWidget && widget != null) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                color = colors.bg,
                contentColor = colors.textPrimary,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = widget.title.ifBlank { "Виджет" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { expanded = false },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HtmlWidget(
                        html = widget.html,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTextButton(text: String, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = colors.clay,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

private fun actionTitle(action: ChatAction): String = when (action.type) {
    "alarm" -> {
        val hour = action.hour ?: 0
        val minute = action.minute ?: 0
        val days = action.days
            .mapNotNull { day -> dayName(day) }
            .joinToString(", ")
        val suffix = if (days.isBlank()) "" else " · $days"
        "Будильник на " + String.format("%02d:%02d", hour, minute) + suffix
    }
    "timer" -> "Таймер на " + durationText(action.seconds ?: 0)
    "calendar" -> action.title.ifBlank { "Событие" }
    "calendar_update" -> "Изменить: " + action.title.ifBlank { "событие" }
    "calendar_delete" -> "Удалить: " + action.title.ifBlank { "событие" }
    "widget" -> action.widget?.title?.ifBlank { "График" } ?: "График"
    "message" -> if (action.recipient.isBlank()) {
        "Черновик сообщения"
    } else {
        "Сообщение для ${action.recipient}"
    }
    "location" -> if (action.accuracy == "precise") {
        "Точная геопозиция"
    } else {
        "Приблизительная геопозиция"
    }
    "map" -> "Карта: " + action.query.ifBlank { action.title.ifBlank { "место" } }
    else -> "Действие"
}

private fun actionDefaultDetail(action: ChatAction): String = when (action.status) {
    "waiting" -> "Нужно разрешение на геопозицию"
    "done" -> "Готово"
    "sent" -> "Действие отправлено на телефон"
    "draft" -> "Черновик готов"
    "ready" -> "Нажмите, чтобы открыть"
    "failed" -> "Не удалось выполнить"
    else -> "Выполняется…"
}

private fun actionIcon(action: ChatAction) = when (action.type) {
    "alarm", "timer" -> ClaudenIcons.clock
    "calendar", "calendar_update", "calendar_delete" -> ClaudenIcons.calendar
    "location" -> ClaudenIcons.location
    "map" -> ClaudenIcons.location
    "widget" -> ClaudenIcons.chart
    "message" -> ClaudenIcons.doc
    else -> ClaudenIcons.spark
}

private fun dayName(day: Int): String? = when (day) {
    1 -> "Пн"
    2 -> "Вт"
    3 -> "Ср"
    4 -> "Чт"
    5 -> "Пт"
    6 -> "Сб"
    7 -> "Вс"
    else -> null
}

private fun durationText(seconds: Long): String = when {
    seconds >= 3600 && seconds % 3600 == 0L -> "${seconds / 3600} ч"
    seconds >= 60 && seconds % 60 == 0L -> "${seconds / 60} мин"
    seconds >= 60 -> "${seconds / 60} мин ${seconds % 60} с"
    else -> "$seconds с"
}

@Composable
fun WidgetChart(data: WidgetData) {
    val colors = LocalClaudenColors.current
    val values = data.values
    if (values.isEmpty()) return
    val labels = values.indices.map { data.labels.getOrElse(it) { "" } }
    val palette = listOf(
        colors.clay,
        colors.blue,
        colors.clay.copy(alpha = 0.55f),
        colors.textTertiary,
        colors.clay.copy(alpha = 0.3f),
    )

    var shown by remember(data) { mutableStateOf(false) }
    LaunchedEffect(data) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(650),
        label = "widget",
    )
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    val current = selected?.takeIf { it in values.indices }

    if (current != null) {
        Text(
            text = labels.getOrElse(current) { "" } + " · " +
                valueWithUnit(values[current], data.unit),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.clay,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    when (data.kind.lowercase()) {
        "line" -> LineChart(values, labels, data.unit, progress, current) { selected = it }
        "pie" -> PieChart(values, labels, palette, data.unit, progress, current) { selected = it }
        else -> BarChart(values, labels, data.unit, progress, current) { selected = it }
    }
    Text(
        text = "Нажмите на элемент, чтобы увидеть значение",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = colors.textTertiary.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun BarChart(
    values: List<Float>,
    labels: List<String>,
    unit: String,
    progress: Float,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val maximum = values.maxOrNull()?.coerceAtLeast(0.0001f) ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = valueWithUnit(value, unit),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (selected == index) colors.clay else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((value / maximum).coerceIn(0.02f, 1f) * progress)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (selected == index ||
                                    (selected == null && index == values.indices.maxByOrNull { values[it] })
                                ) {
                                    colors.clay
                                } else {
                                    colors.clay.copy(alpha = 0.42f)
                                }
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LineChart(
    values: List<Float>,
    labels: List<String>,
    unit: String,
    progress: Float,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val maximum = values.maxOrNull()?.coerceAtLeast(0.0001f) ?: 1f
    val minimum = values.minOrNull()?.coerceAtMost(0f) ?: 0f
    val range = max(0.0001f, maximum - minimum)
    Column {
        Text(
            text = valueWithUnit(maximum, unit),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.textTertiary,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .padding(top = 6.dp)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        if (values.isEmpty()) return@detectTapGestures
                        val step = if (values.size > 1) size.width / (values.size - 1f) else 1f
                        val index = if (values.size > 1) {
                            (offset.x / step).roundToInt().coerceIn(0, values.lastIndex)
                        } else {
                            0
                        }
                        onSelect(index)
                    }
                },
        ) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = if (values.size > 1) index * stepX else size.width / 2f,
                    y = size.height - ((value - minimum) / range) * size.height,
                )
            }
            for (line in 0..3) {
                val y = size.height * line / 3f
                drawLine(
                    color = colors.border,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            if (points.isNotEmpty()) {
                val visible = (points.size * progress).toInt().coerceAtLeast(1)
                val path = Path()
                points.take(visible).forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                if (visible > 1) {
                    drawPath(
                        path = path,
                        color = colors.clay,
                        style = Stroke(width = 3f, cap = StrokeCap.Round),
                    )
                }
                points.take(visible).forEachIndexed { index, point ->
                    val isSelected = selected == index
                    drawCircle(
                        color = if (isSelected) colors.clay else colors.clay.copy(alpha = 0.65f),
                        radius = if (isSelected) 6.5f else 4.5f,
                        center = point,
                    )
                    drawCircle(color = colors.field, radius = if (isSelected) 3f else 2f, center = point)
                    if (isSelected) {
                        drawLine(
                            color = colors.clay.copy(alpha = 0.35f),
                            start = Offset(point.x, 0f),
                            end = Offset(point.x, size.height),
                            strokeWidth = 1.5f,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (selected == index) colors.clay else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PieChart(
    values: List<Float>,
    labels: List<String>,
    palette: List<Color>,
    unit: String,
    progress: Float,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val total = values.sum().coerceAtLeast(0.0001f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .padding(6.dp),
        ) {
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = (value / total) * 360f * progress
                drawArc(
                    color = palette[index % palette.size].copy(
                        alpha = if (selected == null || selected == index) 1f else 0.3f
                    ),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                start += sweep
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            labels.forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                palette[index % palette.size].copy(
                                    alpha = if (selected == null || selected == index) 1f else 0.35f
                                )
                            ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label + " · " + valueWithUnit(values.getOrElse(index) { 0f }, unit),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (selected == index) colors.clay else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun valueWithUnit(value: Float, unit: String): String =
    formatValue(value) + if (unit.isBlank()) "" else " $unit"

private fun formatValue(value: Float): String {
    val rounded = if (kotlin.math.abs(value - value.toInt()) < 0.01f) {
        value.toInt().toString()
    } else {
        DecimalFormat("0.#").format(value)
    }
    return rounded.replace('.', ',')
}
