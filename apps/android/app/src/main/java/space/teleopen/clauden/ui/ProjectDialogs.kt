package space.teleopen.clauden.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import space.teleopen.clauden.data.Project
import kotlinx.coroutines.delay

@Composable
fun ProjectInstructionsDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var value by rememberSaveable(project.id) { mutableStateOf(project.instructions) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = colors.bg300,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Задать пользовательские инструкции",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 23.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Укажите Clauden, как вести себя и отвечать в проекте " +
                            "«${project.title}»",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        ),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ProjectTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = "Дайте Clauden указания с помощью промптов, например: " +
                            "используй профессиональный тон, пиши кратко и просто, " +
                            "ты — эксперт в астрофизике и т. д.",
                        minLines = 5,
                        maxLines = 10,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogPillButton(
                            text = "Отмена",
                            primary = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        DialogPillButton(
                            text = "Сохранить",
                            primary = true,
                            onClick = { onSave(value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            ) {
            Surface(
                color = colors.bg300,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = "Создание проекта",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.width(40.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "Над чем вы работаете?",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 8.dp),
                        )
                        ProjectTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = "Введите название проекта",
                            singleLine = true,
                            modifier = Modifier.focusRequester(focus),
                        )

                        Text(
                            text = "Чего вы хотите достичь?",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp),
                        )
                        ProjectTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = "Опишите проект, цели, тему и т. д…",
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.height(168.dp),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    DialogPillButton(
                        text = "Создать проект",
                        primary = true,
                        enabled = title.isNotBlank(),
                        onClick = { onCreate(title, description) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            }
        }
    }
}

@Composable
fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var title by rememberSaveable(project.id) { mutableStateOf(project.title) }
    var description by rememberSaveable(project.id) { mutableStateOf(project.description) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = colors.bg300,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Изменить сведения",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ProjectTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Название проекта",
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ProjectTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = "Опишите проект, цели, тему и т. д…",
                        minLines = 3,
                        maxLines = 7,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogPillButton(
                            text = "Отмена",
                            primary = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        DialogPillButton(
                            text = "Сохранить",
                            primary = true,
                            enabled = title.isNotBlank(),
                            onClick = { onSave(title, description) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameProjectDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = LocalClaudenColors.current
    var value by rememberSaveable { mutableStateOf(title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Переименовать проект") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
fun DeleteProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Удалить проект?") },
        text = {
            Text(
                "«${project.title}» и его файлы удалятся. Чаты останутся и выйдут из проекта.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ProjectTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 8,
) {
    val colors = LocalClaudenColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                color = colors.textTertiary,
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = colors.textPrimary,
        ),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.clay,
            unfocusedBorderColor = colors.borderStrong.copy(alpha = 0.6f),
            focusedContainerColor = colors.bg,
            unfocusedContainerColor = colors.bg,
            cursorColor = colors.clay,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
    )
}

@Composable
private fun DialogPillButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalClaudenColors.current
    val background = when {
        !enabled -> colors.buttonDisabled
        primary -> colors.textPrimary
        else -> colors.bg300
    }
    val content = when {
        !enabled -> colors.buttonDisabledText
        primary -> colors.bg
        else -> colors.textPrimary
    }
    Surface(
        color = background,
        contentColor = content,
        shape = CircleShape,
        border = if (!primary && enabled) BorderStroke(1.dp, colors.borderStrong) else null,
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
