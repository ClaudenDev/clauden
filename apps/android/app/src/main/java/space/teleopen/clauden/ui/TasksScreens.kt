package space.teleopen.clauden.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.ScheduledTask
import space.teleopen.clauden.data.TaskItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TasksScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    var adding by remember { mutableStateOf(false) }
    val open = state.tasks.filterNot { it.done }
    val done = state.tasks.filter { it.done }

    NewSettingsScaffold(title = "Задачи", onBack = vm::backToSettings) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (open.isEmpty()) "Все задачи выполнены" else "Открытых: ${open.size}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = colors.textPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .height(32.dp)
                    .clickable { adding = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.bg, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Задача",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = colors.bg,
                    )
                }
            }
        }

        if (state.tasks.isEmpty()) {
            Text(
                text = "Задачи может создавать Clauden из чата — например, «добавь задачу проверить договор». Они появятся здесь.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        } else {
            SettingsCard {
                state.tasks.forEachIndexed { index, task ->
                    if (index > 0) SettingsDivider()
                    TaskRow(task, onToggle = { vm.toggleTask(task.id) }, onDelete = { vm.deleteTask(task.id) })
                }
            }
        }

        if (done.isNotEmpty()) {
            Text(
                text = "Выполнено: ${done.size}. Задачи хранятся в аккаунте и не синхронизируются с другими устройствами.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    if (adding) {
        TaskDialog(
            onDismiss = { adding = false },
            onSave = { title ->
                vm.addTask(title)
                adding = false
            },
        )
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (task.done) colors.blue else Color.Transparent,
                    shape = CircleShape,
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) {
                Icon(Icons.Default.Check, contentDescription = "Вернуть", tint = Color.White, modifier = Modifier.size(15.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .background(Color.Transparent, CircleShape)
                        .then(
                            Modifier.border(1.5.dp, colors.borderStrong, CircleShape),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = if (task.done) colors.textTertiary else colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.dueAt > 0) {
                Text(
                    text = "Срок: ${formatTaskDate(task.dueAt)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Удалить", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatTaskDate(value: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(value))

@Composable
private fun TaskDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    val colors = LocalClaudenColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Новая задача",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = colors.textTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Что нужно сделать",
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsActionButton(
                    text = "Добавить",
                    enabled = title.isNotBlank(),
                    onClick = { onSave(title.trim()) },
                )
            }
        }
    }
}

@Composable
fun ScheduledScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    var adding by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshScheduled() }
    val items = state.scheduled.sortedBy { if (it.runAt <= 0) Long.MAX_VALUE else it.runAt }

    NewSettingsScaffold(title = "Отложенные", onBack = vm::backToSettings) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Clauden выполнит запрос и пришлёт ответ в новый чат.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = colors.textPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .height(32.dp)
                    .clickable { adding = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.bg, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Запрос",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = colors.bg,
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "Пока ничего не запланировано. Например: «завтра в 9 утра собери сводку по проекту».",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        } else {
            items.forEach { task ->
                ScheduledCard(
                    task = task,
                    onOpenChat = { vm.openTaskChat(task.lastChatId) },
                    onDelete = { vm.deleteScheduled(task.id) },
                )
            }
        }
    }

    if (adding) {
        ScheduledDialog(
            onDismiss = { adding = false },
            onSave = { title, prompt, runAt, repeat ->
                vm.addScheduled(title, prompt, runAt, repeat)
                adding = false
            },
        )
    }
}

@Composable
private fun ScheduledCard(task: ScheduledTask, onOpenChat: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(color = colors.bg300, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Удалить", tint = colors.textTertiary, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                text = if (task.runAt > 0) {
                    "Следующий запуск: ${formatScheduled(task.runAt)} · ${task.repeatLabel}"
                } else {
                    "Выполнено: ${formatScheduled(task.lastRunAt)}"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.lastStatus.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (task.lastStatus) {
                            "ok" -> "Ответ готов"
                            "error" -> "Ошибка выполнения"
                            else -> "Запускался"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (task.lastStatus == "error") colors.dangerSoft else colors.blueLink,
                    )
                    if (task.lastStatus == "ok" && task.lastChatId.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Открыть чат",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.blueLink,
                            modifier = Modifier.clickable(onClick = onOpenChat),
                        )
                    }
                }
            }
        }
    }
}

private fun formatScheduled(value: Long): String {
    if (value <= 0) return "—"
    return SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(value))
}

@Composable
private fun ScheduledDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("once") }
    val calendar = remember {
        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    }
    var dateLabel by remember { mutableStateOf(formatScheduled(calendar.timeInMillis)) }

    fun openDate() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                dateLabel = formatScheduled(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun openTime() {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                dateLabel = formatScheduled(calendar.timeInMillis)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true,
        ).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Отложенный запрос",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = colors.textTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                SettingsField(value = title, onValueChange = { title = it }, placeholder = "Название, например «Сводка утром»")
                Spacer(modifier = Modifier.height(10.dp))
                SettingsField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = "Что Clauden должен сделать",
                    height = 120.dp,
                    singleLine = false,
                    textSize = 15,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = colors.control,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { openDate() },
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textPrimary,
                            )
                        }
                    }
                    Surface(
                        color = colors.control,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { openTime() },
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "%02d:%02d".format(
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("once" to "Один раз", "daily" to "Каждый день", "weekly" to "Каждую неделю")
                        .forEach { (id, label) ->
                            Surface(
                                color = if (repeat == id) colors.textPrimary else colors.control,
                                shape = CircleShape,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable { repeat = id },
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 11.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                        color = if (repeat == id) colors.bg else colors.textSecondary,
                                    )
                                }
                            }
                        }
                }
                Spacer(modifier = Modifier.height(16.dp))
                SettingsActionButton(
                    text = "Запланировать",
                    enabled = prompt.isNotBlank() && calendar.timeInMillis > System.currentTimeMillis(),
                    onClick = {
                        onSave(title.trim(), prompt.trim(), calendar.timeInMillis, repeat)
                    },
                )
            }
        }
    }
}
