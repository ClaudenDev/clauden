package space.teleopen.clauden.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.Conversation
import space.teleopen.clauden.data.Project

@Composable
fun ChatContextMenu(
    chat: Conversation,
    projects: List<Project>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    vm: ChatViewModel,
) {
    val colors = LocalClaudenColors.current
    var renameOpen by remember(chat.id) { mutableStateOf(false) }
    var deleteOpen by remember(chat.id) { mutableStateOf(false) }
    var projectOpen by remember(chat.id) { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.bg300,
        shape = RoundedCornerShape(20.dp),
    ) {
        ChatMenuItem(
            label = if (chat.pinned) "Открепить" else "Закрепить",
            icon = ClaudenIcons.pin,
        ) {
            onDismiss()
            vm.togglePin(chat.id)
        }
        ChatMenuItem(
            label = "Переименовать",
            icon = ClaudenIcons.pencil,
        ) {
            onDismiss()
            renameOpen = true
        }
        ChatMenuItem(
            label = "Добавить в проект",
            icon = ClaudenIcons.projects,
        ) {
            onDismiss()
            projectOpen = true
        }
        ChatMenuItem(
            label = "Удалить",
            icon = ClaudenIcons.trash,
            danger = true,
        ) {
            onDismiss()
            deleteOpen = true
        }
    }

    if (renameOpen) {
        RenameDialog(
            chat = chat,
            onDismiss = { renameOpen = false },
            onSave = { title ->
                vm.renameChat(chat.id, title)
                renameOpen = false
            },
        )
    }

    if (deleteOpen) {
        DeleteChatDialog(
            chat = chat,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                vm.deleteChat(chat.id)
                deleteOpen = false
            },
        )
    }

    if (projectOpen) {
        ProjectPickerDialog(
            projects = projects,
            selectedId = chat.projectId,
            onDismiss = { projectOpen = false },
            onPick = { projectId ->
                vm.setChatProject(chat.id, projectId)
                projectOpen = false
            },
        )
    }
}

@Composable
fun ChatMenuItem(
    label: String,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = if (danger) colors.danger else colors.textPrimary,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (danger) colors.danger else colors.textSecondary,
                modifier = Modifier.size(21.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
fun ProjectPickerDialog(
    projects: List<Project>,
    selectedId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Добавить в проект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PickerRow(
                    title = "Без проекта",
                    selected = selectedId.isBlank(),
                    onClick = { onPick("") },
                )
                projects.forEach { project ->
                    PickerRow(
                        title = project.title,
                        subtitle = filesCountLabel(project.files.size),
                        selected = project.id == selectedId,
                        onClick = { onPick(project.id) },
                    )
                }
                if (projects.isEmpty()) {
                    Text(
                        text = "Проектов пока нет. Создайте его в разделе «Проекты».",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
    )
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String = "",
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Выбрано",
                tint = colors.blueLink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
