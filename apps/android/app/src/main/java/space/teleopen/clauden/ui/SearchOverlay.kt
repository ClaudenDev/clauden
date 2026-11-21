package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.Conversation

private val searchTabs = listOf(
    "all" to "Все",
    "artifacts" to "Артефакты",
    "chats" to "Чаты",
    "projects" to "Проекты",
    "sessions" to "Сеансы",
    "tasks" to "Задачи",
    "scheduled" to "Отложенные",
)

@Composable
fun SearchOverlay(state: ChatUiState, vm: ChatViewModel) {
    if (!state.searchOpen) return
    BackHandler { vm.closeSearch() }
    val colors = LocalClaudenColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("all") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }

    val chats = remember(state.conversations, query) {
        if (query.isBlank()) state.conversations.take(6) else state.conversations.filter { chat ->
            chat.title.contains(query, ignoreCase = true) ||
                chat.messages.any { it.content.contains(query, ignoreCase = true) }
        }
    }
    val projects = remember(state.projects, query) {
        state.projects.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    }
    val allArtifacts = remember(state.conversations) {
        collectArtifacts(state, codeOnly = false)
    }
    val artifacts = remember(allArtifacts, query) {
        allArtifacts.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
        }
    }
    val sessions = remember(state.sessions, query) {
        state.sessions.filter {
            query.isBlank() || it.device.contains(query, ignoreCase = true) ||
                it.location.contains(query, ignoreCase = true) ||
                it.platform.contains(query, ignoreCase = true)
        }
    }
    val tasks = remember(state.tasks, query) {
        state.tasks.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    }
    val scheduled = remember(state.scheduled, query) {
        state.scheduled.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.prompt.contains(query, ignoreCase = true)
        }
    }

    fun goChat(id: String) {
        vm.openChat(id)
        vm.closeSearch()
    }

    fun goArtifacts() {
        vm.openArtifacts()
        vm.closeSearch()
    }

    fun goSessions() {
        vm.openSettingsSection(Screen.Sessions)
        vm.closeSearch()
    }

    fun goTasks() {
        vm.openSettingsSection(Screen.Tasks)
        vm.closeSearch()
    }

    fun goScheduled() {
        vm.openSettingsSection(Screen.Scheduled)
        vm.closeSearch()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable { vm.closeSearch() },
    ) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 34.dp)
                .fillMaxHeight(0.9f)
                .blockClicks(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Поиск",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = colors.textTertiary,
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                color = colors.textPrimary,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.clay),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                        )
                    }
                    IconButton(onClick = { vm.closeSearch() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = colors.textSecondary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    searchTabs.forEach { (id, label) ->
                        SearchTab(label, tab == id) { tab = id }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                ) {
                    val show = { id: String -> tab == "all" || tab == id }

                    if (query.isBlank() && tab == "all") {
                        SearchLabel("Недавние")
                        if (chats.isEmpty()) {
                            SearchEmpty("Пока нет чатов")
                        } else {
                            chats.forEach { chat ->
                                ChatResult(chat, onClick = { goChat(chat.id) })
                            }
                        }
                        SearchLabel("Действия")
                        SearchAction("Новый чат", ClaudenIcons.plus) {
                            vm.newChat()
                            vm.closeSearch()
                        }
                        SearchAction("Customize", ClaudenIcons.spark) {
                            vm.openCustomize("skills")
                            vm.closeSearch()
                        }
                        SearchAction("Задачи", ClaudenIcons.doc) { goTasks() }
                        SearchAction("Отложенные", ClaudenIcons.clock) { goScheduled() }
                    } else {
                        var found = false

                        if (show("chats") && chats.isNotEmpty()) {
                            found = true
                            SearchLabel("Чаты")
                            chats.take(8).forEach { chat ->
                                ChatResult(chat, onClick = { goChat(chat.id) })
                            }
                        }
                        if (show("projects") && projects.isNotEmpty()) {
                            found = true
                            SearchLabel("Проекты")
                            projects.take(6).forEach { project ->
                                SearchAction(project.title, ClaudenIcons.projects) {
                                    vm.openProject(project.id)
                                    vm.closeSearch()
                                }
                            }
                        }
                        if (show("artifacts") && artifacts.isNotEmpty()) {
                            found = true
                            SearchLabel("Артефакты")
                            artifacts.take(6).forEach { artifact ->
                                SearchAction(artifact.title, ClaudenIcons.code) { goArtifacts() }
                            }
                        }
                        if (show("sessions") && sessions.isNotEmpty()) {
                            found = true
                            SearchLabel("Сеансы")
                            sessions.take(6).forEach { session ->
                                SearchAction(
                                    "${session.device} · ${session.location.ifBlank { "Android" }}",
                                    ClaudenIcons.android,
                                ) { goSessions() }
                            }
                        }
                        if (show("tasks") && tasks.isNotEmpty()) {
                            found = true
                            SearchLabel("Задачи")
                            tasks.take(8).forEach { task ->
                                SearchAction(
                                    (if (task.done) "[x] " else "") + task.title,
                                    ClaudenIcons.doc,
                                ) { goTasks() }
                            }
                        }
                        if (show("scheduled") && scheduled.isNotEmpty()) {
                            found = true
                            SearchLabel("Отложенные")
                            scheduled.take(8).forEach { task ->
                                SearchAction(task.title, ClaudenIcons.clock) { goScheduled() }
                            }
                        }

                        if (!found) {
                            SearchEmpty("Ничего не найдено")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.control else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .height(30.dp)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (selected) colors.textPrimary else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SearchLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = LocalClaudenColors.current.textTertiary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = LocalClaudenColors.current.textTertiary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
    )
}

@Composable
private fun SearchAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatResult(chat: Conversation, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    val snippet = chat.messages.firstOrNull { it.role == "user" }?.content
        ?.replace('\n', ' ')
        ?.take(80)
        .orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            ClaudenIcons.chatBubble,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
