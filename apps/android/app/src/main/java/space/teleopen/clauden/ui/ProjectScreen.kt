package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.Conversation

@Composable
fun ProjectScreen(state: ChatUiState, vm: ChatViewModel) {
    val project = state.activeProject
    if (project == null) {
        LaunchedEffect(Unit) { vm.closeProject() }
        return
    }

    val colors = LocalClaudenColors.current
    val chats = remember(state.conversations, project.id) {
        state.conversations.filter { it.projectId == project.id }
            .sortedByDescending { it.updatedAt }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var instructionsOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    BackHandler { vm.closeProject() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.closeProject() }, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = ClaudenIcons.arrowBack,
                        contentDescription = "Назад",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Действия с проектом",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = colors.bg300,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (project.pinned) "Открепить" else "Закрепить") },
                            leadingIcon = { Icon(ClaudenIcons.pin, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                vm.toggleProjectPin(project.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Изменить сведения") },
                            leadingIcon = { Icon(ClaudenIcons.pencil, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                detailsOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (project.archived) "Вернуть из архива" else "Архивировать")
                            },
                            leadingIcon = { Icon(ClaudenIcons.projects, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                vm.archiveProject(project.id, !project.archived)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить", color = colors.danger) },
                            leadingIcon = {
                                Icon(ClaudenIcons.trash, contentDescription = null, tint = colors.danger)
                            },
                            onClick = {
                                menuOpen = false
                                deleteOpen = true
                            },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 2.dp,
                    bottom = 120.dp,
                ),
            ) {
                item {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = LocalClaudenFonts.current.serif,
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrivatePill()
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProjectActionCard(
                            title = "Контекст",
                            action = if (project.files.isEmpty()) "Добавить файлы"
                            else filesCountLabel(project.files.size),
                            onClick = { vm.openProjectContext() },
                            modifier = Modifier.weight(1f),
                        )
                        ProjectActionCard(
                            title = "Пользовательские инструкции",
                            action = if (project.instructions.isBlank()) "Добавить инструкции"
                            else "Изменить инструкции",
                            onClick = { instructionsOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (chats.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.56f)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = ClaudenIcons.projectEmpty,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(96.dp),
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            Text(
                                text = "Спросите Clauden о чем угодно. Чаты этого проекта " +
                                    "будут отображаться здесь.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    lineHeight = 24.sp,
                                ),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Чаты",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                            ),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(items = chats, key = { it.id }) { chat ->
                        ProjectChatRow(chat = chat, onClick = { vm.openChat(chat.id) })
                    }
                }
            }
        }

        Surface(
            color = colors.textPrimary,
            contentColor = colors.bg,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp)
                .height(54.dp)
                .clickable { vm.newChat(project.id) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 22.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colors.bg,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Новый чат",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }

    if (instructionsOpen) {
        ProjectInstructionsDialog(
            project = project,
            onDismiss = { instructionsOpen = false },
            onSave = { text ->
                vm.updateProjectInstructions(project.id, text)
                instructionsOpen = false
            },
        )
    }

    if (detailsOpen) {
        EditProjectDialog(
            project = project,
            onDismiss = { detailsOpen = false },
            onSave = { title, description ->
                vm.updateProjectDetails(project.id, title, description)
                detailsOpen = false
            },
        )
    }

    if (deleteOpen) {
        DeleteProjectDialog(
            project = project,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                vm.deleteProject(project.id)
                deleteOpen = false
            },
        )
    }
}

@Composable
private fun PrivatePill() {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.control.copy(alpha = 0.55f),
        contentColor = colors.textSecondary,
        shape = CircleShape,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = ClaudenIcons.lock,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Приватный",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ProjectActionCard(
    title: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = modifier
            .height(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 15.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = action,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = colors.clay,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProjectChatRow(chat: Conversation, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ClaudenIcons.chatBubble,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relativeTime(chat.updatedAt),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.textTertiary,
            )
        }
    }
}
