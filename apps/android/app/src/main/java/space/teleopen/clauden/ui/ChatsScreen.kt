package space.teleopen.clauden.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.Conversation
import space.teleopen.clauden.data.Project
import space.teleopen.clauden.data.UiStyle

@Composable
fun ChatsScreen(state: ChatUiState, vm: ChatViewModel, onMenu: () -> Unit) {
    val colors = LocalClaudenColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }

    val source = if (pinnedOnly) state.pinned else state.conversations
    val filtered = if (query.isBlank()) {
        source
    } else {
        source.filter { conversation ->
            conversation.title.contains(query, ignoreCase = true) ||
                conversation.messages.any { it.content.contains(query, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        ScreenHeader(title = "Чаты", onMenu = onMenu) {
            FilterMenu(
                pinnedOnly = pinnedOnly,
                onAll = { pinnedOnly = false },
                onPinned = { pinnedOnly = true },
            )
        }

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Поиск",
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        if (query.isBlank() && !pinnedOnly) {
                            Column(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                ClaudenSceneCard(
                                    scene = "scene-typing",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(188.dp),
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "Пока нет чатов",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                    color = colors.textSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Напишите первое сообщение — и диалог появится здесь",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = colors.textTertiary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Text(
                                text = if (pinnedOnly) "Нет закреплённых чатов" else "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                items(items = filtered, key = { it.id }) { chat ->
                    Box(modifier = Modifier.animateItem()) {
                        ChatListRow(
                            chat = chat,
                            projects = state.projects,
                            onClick = { vm.openChat(chat.id) },
                            vm = vm,
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(110.dp)) }
            }

            PillButton(
                text = "Новый чат",
                withPlus = true,
                onClick = { vm.newChat() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 44.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    chat: Conversation,
    projects: List<Project>,
    onClick: () -> Unit,
    vm: ChatViewModel,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    var menu by remember(chat.id) { mutableStateOf(false) }

    Box(
        modifier = if (material3) {
            Modifier
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bg300)
        } else {
            Modifier
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menu = true },
                )
                .padding(
                    horizontal = if (material3) 14.dp else 24.dp,
                    vertical = if (material3) 10.dp else 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (material3) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.control),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ClaudenIcons.chatBubble,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            } else {
                Icon(
                    imageVector = ClaudenIcons.chatBubble,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        style = if (material3) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
                        },
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.pinned) {
                        Icon(
                            imageVector = ClaudenIcons.pin,
                            contentDescription = "Закреплён",
                            tint = colors.textTertiary,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(13.dp),
                        )
                    }
                }
                Text(
                    text = relativeTime(chat.updatedAt),
                    style = if (material3) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                    },
                    color = colors.textTertiary,
                )
            }

            IconButton(
                onClick = { menu = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Действия с чатом",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        ChatContextMenu(
            chat = chat,
            projects = projects,
            expanded = menu,
            onDismiss = { menu = false },
            vm = vm,
        )
    }
}

@Composable
private fun FilterMenu(
    pinnedOnly: Boolean,
    onAll: () -> Unit,
    onPinned: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    var open by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = ClaudenIcons.sliders,
                contentDescription = "Фильтр",
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.bg300,
            shape = RoundedCornerShape(16.dp),
        ) {
            DropdownMenuItem(
                text = { Text("Все") },
                leadingIcon = { Icon(ClaudenIcons.chats, contentDescription = null) },
                trailingIcon = if (!pinnedOnly) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    open = false
                    onAll()
                },
            )
            DropdownMenuItem(
                text = { Text("Закрепленные") },
                leadingIcon = { Icon(ClaudenIcons.pin, contentDescription = null) },
                trailingIcon = if (pinnedOnly) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    open = false
                    onPinned()
                },
            )
        }
    }
}
