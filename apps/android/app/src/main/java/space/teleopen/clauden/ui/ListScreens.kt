package space.teleopen.clauden.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.Project
import space.teleopen.clauden.data.UiStyle

@Composable
fun ProjectsScreen(state: ChatUiState, vm: ChatViewModel, onMenu: () -> Unit) {
    val colors = LocalClaudenColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }

    val source = state.projects
        .filter { it.archived == showArchived }
        .sortedWith(compareByDescending<Project> { it.pinned }.thenByDescending { it.updatedAt })
    val filtered = if (query.isBlank()) {
        source
    } else {
        source.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        ScreenHeader(title = "Проекты", onMenu = onMenu) {
            ProjectsFilterMenu(
                showArchived = showArchived,
                onActive = { showArchived = false },
                onArchived = { showArchived = true },
            )
        }

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Поиск проектов",
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    item {
                        if (query.isBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                ClaudenSceneCard(
                                    scene = "scene-open-chat",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(184.dp),
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = if (showArchived) "Архив пуст" else "Пока нет проектов",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                    color = colors.textSecondary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (showArchived) {
                                        "Сюда попадут проекты, которые вы архивировали"
                                    } else {
                                        "Соберите чаты, файлы и инструкции в одну папку"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = colors.textTertiary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                items(items = filtered, key = { it.id }) { project ->
                    Box(modifier = Modifier.animateItem()) {
                        ProjectRow(
                            project = project,
                            chatsCount = state.conversations.count { it.projectId == project.id },
                            archived = showArchived,
                            onOpen = { vm.openProject(project.id) },
                            onRename = { renaming = project },
                            onPin = { vm.toggleProjectPin(project.id) },
                            onArchive = { vm.archiveProject(project.id, !project.archived) },
                            onDelete = { deleting = project },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.size(96.dp)) }
            }

            if (!showArchived) {
                PillButton(
                    text = "Новый проект",
                    withPlus = true,
                    onClick = { creating = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 44.dp),
                )
            }
        }
    }

    if (creating) {
        CreateProjectSheet(
            onDismiss = { creating = false },
            onCreate = { title, description ->
                vm.createProject(title, description)
                creating = false
            },
        )
    }

    renaming?.let { project ->
        RenameProjectDialog(
            title = project.title,
            onDismiss = { renaming = null },
            onSave = { title ->
                vm.renameProject(project.id, title)
                renaming = null
            },
        )
    }

    deleting?.let { project ->
        DeleteProjectDialog(
            project = project,
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteProject(project.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun ProjectsFilterMenu(
    showArchived: Boolean,
    onActive: () -> Unit,
    onArchived: () -> Unit,
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
                text = { Text("Активные") },
                leadingIcon = { Icon(ClaudenIcons.projects, contentDescription = null) },
                trailingIcon = if (!showArchived) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    open = false
                    onActive()
                },
            )
            DropdownMenuItem(
                text = { Text("В архиве") },
                leadingIcon = { Icon(ClaudenIcons.projects, contentDescription = null) },
                trailingIcon = if (showArchived) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    open = false
                    onArchived()
                },
            )
        }
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    chatsCount: Int,
    archived: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ClaudenIcons.projects,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (project.pinned) {
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
                text = "${filesCountLabel(project.files.size)} · " +
                    "${chatsCountLabel(chatsCount)} · ${formatDate(project.updatedAt)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Действия",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = colors.bg300,
                shape = RoundedCornerShape(18.dp),
            ) {
                DropdownMenuItem(
                    text = { Text(if (project.pinned) "Открепить" else "Закрепить") },
                    leadingIcon = { Icon(ClaudenIcons.pin, contentDescription = null) },
                    onClick = {
                        menu = false
                        onPin()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    leadingIcon = { Icon(ClaudenIcons.pencil, contentDescription = null) },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (archived) "Вернуть из архива" else "Архивировать") },
                    leadingIcon = { Icon(ClaudenIcons.projects, contentDescription = null) },
                    onClick = {
                        menu = false
                        onArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Удалить проект", color = colors.danger) },
                    leadingIcon = {
                        Icon(ClaudenIcons.trash, contentDescription = null, tint = colors.danger)
                    },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

internal data class ArtifactItem(
    val id: String,
    val title: String,
    val type: String,
    val content: String,
    val language: String = "",
    val chatId: String = "",
    val timestamp: Long,

    val artifactId: String = "",
)

private val artifactCodeLanguages = setOf("html", "svg", "markdown", "md")

private val codeLanguages = setOf(
    "kotlin", "java", "python", "py", "javascript", "js", "typescript", "ts",
    "go", "golang", "rust", "c", "cpp", "c++", "csharp", "cs", "swift", "php",
    "ruby", "rb", "sql", "bash", "sh", "shell", "json", "yaml", "yml", "xml",
    "dart", "lua", "scala", "perl", "r", "objective-c", "objc",
)

@Composable
fun ArtifactsScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    onMenu: () -> Unit,
    codeOnly: Boolean,
) {
    val colors = LocalClaudenColors.current
    var query by rememberSaveable(codeOnly) { mutableStateOf("") }

    var opened by remember { mutableStateOf<ArtifactItem?>(null) }
    val all = remember(state.conversations, codeOnly) {
        collectArtifacts(state, codeOnly)
    }
    val filtered = if (query.isBlank()) {
        all
    } else {
        all.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        ScreenHeader(title = if (codeOnly) "Code" else "Артефакты", onMenu = onMenu) { FilterButton() }

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = if (codeOnly) "Поиск по коду" else "Поиск артефактов",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    if (query.isBlank()) {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            ClaudenSceneCard(
                                scene = "scene-success",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(176.dp),
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = if (codeOnly) "Пока нет кода" else "Пока нет артефактов",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (codeOnly) {
                                    "Code-сниппеты из ответов появятся здесь"
                                } else {
                                    "Код и документы из ответов будут собираться здесь"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textTertiary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(
                            text = "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                        )
                    }
                }
            }
            items(items = filtered, key = { it.id }) { artifact ->
                Box(modifier = Modifier.animateItem()) {
                    ArtifactCard(artifact = artifact, onClick = { opened = artifact })
                }
            }
        }
    }

    opened?.let { item ->
        ArtifactViewer(
            artifact = ChatArtifact(
                id = item.artifactId.ifBlank { item.id },
                title = item.title,
                type = item.type,
                content = item.content,
            ),
            chatId = item.chatId,
            state = state,
            vm = vm,
            onDismiss = { opened = null },
        )
    }
}

@Composable
private fun ArtifactCard(artifact: ArtifactItem, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    val isWeb = artifact.type.contains("html", ignoreCase = true) ||
        artifact.type.contains("svg", ignoreCase = true)
    val isCode = artifact.language.isNotBlank() ||
        artifact.type.equals("Код", ignoreCase = true) ||
        artifact.type.contains("code", ignoreCase = true)

    Surface(
        color = colors.card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.bg300),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isWeb -> Icon(
                        imageVector = ClaudenIcons.artifacts,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(38.dp),
                    )

                    isCode -> Text(
                        text = artifact.content,
                        style = ClaudenType.mono.copy(fontSize = 8.5.sp, lineHeight = 11.5.sp),
                        color = colors.textTertiary,
                        maxLines = 8,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(8.dp),
                    )

                    else -> Text(
                        text = artifact.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = LocalClaudenFonts.current.serif,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        ),
                        color = colors.textTertiary,
                        maxLines = 7,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ClaudenIcons.lock,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Только вы",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Изменено: ${relativeTime(artifact.timestamp)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun collectArtifacts(state: ChatUiState, codeOnly: Boolean): List<ArtifactItem> {
    val items = mutableListOf<ArtifactItem>()
    state.conversations
        .sortedByDescending { it.updatedAt }
        .forEach { conversation ->
            conversation.messages
                .filter { it.role == "assistant" }
                .forEach { message ->
                    if (codeOnly) {
                        parseMarkdown(message.content)
                            .filterIsInstance<MdBlock.CodeBlock>()
                            .forEachIndexed { index, block ->
                                val lang = block.language.lowercase()
                                if (lang !in codeLanguages) return@forEachIndexed
                                val firstLine = block.code.lineSequence()
                                    .firstOrNull { it.isNotBlank() }
                                    ?.trim()
                                    ?.take(64)
                                    ?: "${lang.ifBlank { "code" }} snippet"
                                items += ArtifactItem(
                                    id = "${conversation.id}-${message.ts}-code-$index",
                                    title = firstLine,
                                    type = "Код",
                                    content = block.code,
                                    language = lang,
                                    chatId = conversation.id,
                                    timestamp = message.ts,
                                )
                            }
                    } else {
                        val parsed = parseArtifacts(message.content)
                        if (parsed.isNotEmpty()) {
                            parsed.forEachIndexed { index, artifact ->
                                items += ArtifactItem(
                                    id = "${conversation.id}-${message.ts}-${artifact.id}-$index",
                                    title = artifact.title,
                                    type = artifact.type,
                                    content = artifact.content,
                                    chatId = conversation.id,
                                    timestamp = message.ts,
                                    artifactId = artifact.id,
                                )
                            }
                        } else {

                            parseMarkdown(message.content)
                                .filterIsInstance<MdBlock.CodeBlock>()
                                .filter { it.language.lowercase() in artifactCodeLanguages }
                                .forEachIndexed { index, block ->
                                    val lang = block.language.lowercase()
                                    val type = when (lang) {
                                        "html" -> "text/html"
                                        "svg" -> "image/svg+xml"
                                        else -> "text/markdown"
                                    }
                                    val firstLine = block.code.lineSequence()
                                        .firstOrNull { it.isNotBlank() }
                                        ?.trim()
                                        ?.removePrefix("#")
                                        ?.trim()
                                        ?.take(72)
                                        .orEmpty()
                                    items += ArtifactItem(
                                        id = "${conversation.id}-${message.ts}-doc-$index",
                                        title = block.name.ifBlank {
                                            firstLine.ifBlank { if (lang == "svg") "SVG" else "Документ" }
                                        },
                                        type = type,
                                        content = block.code,
                                        language = lang,
                                        chatId = conversation.id,
                                        timestamp = message.ts,
                                    )
                                }
                        }
                    }
                }
        }

    return items
        .groupBy { "${it.chatId}|${it.title.trim().lowercase()}" }
        .values
        .mapNotNull { versions -> versions.maxByOrNull { it.timestamp } }
        .sortedByDescending { it.timestamp }
}

@Composable
private fun FilterButton() {
    val colors = LocalClaudenColors.current
    var open by remember { mutableStateOf(false) }
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
                trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                onClick = { open = false },
            )
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Surface(
        color = if (material3) colors.bg300 else Color.Transparent,
        shape = if (material3) RoundedCornerShape(28.dp) else RoundedCornerShape(24.dp),
        border = if (material3) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = colors.textTertiary,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.clay),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
