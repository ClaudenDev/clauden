package space.teleopen.clauden.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.teleopen.clauden.data.RemoteEntry
import space.teleopen.clauden.data.SshHost
import space.teleopen.clauden.data.SshSession
import java.util.Locale

@Composable
internal fun SshFilesPane(
    client: SshSession?,
    host: SshHost,
    onNote: (String) -> Unit,
    onUseAsRoot: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var actionTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var createKind by remember { mutableStateOf("") }
    var createName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var editorTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var editorSaving by remember { mutableStateOf(false) }
    var downloadTarget by remember { mutableStateOf<RemoteEntry?>(null) }

    fun report(text: String) {
        status = text
        onNote(text)
    }

    fun load() {
        val ssh = client ?: return
        scope.launch {
            loading = true
            runCatching { ssh.list(path) }
                .onSuccess { entries = it }
                .onFailure { report(it.message ?: "Не удалось прочитать папку") }
            loading = false
        }
    }

    LaunchedEffect(client) {
        val ssh = client ?: return@LaunchedEffect
        if (path.isBlank()) {
            path = runCatching { ssh.pwd() }.getOrDefault("/")
        }
    }
    LaunchedEffect(client, path) {
        if (client != null && path.isNotBlank()) load()
    }

    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val ssh = client
        if (uri != null && ssh != null) {
            scope.launch {
                val name = (displayName(context, uri) ?: "upload.bin")
                    .substringAfterLast('/')
                    .replace("\\", "-")
                    .ifBlank { "upload.bin" }
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Не удалось открыть файл")
                        input.use { ssh.upload(ssh.joinRemote(path, name), it) }
                    }
                }
                result.onSuccess { report("Загружено: $name"); load() }
                    .onFailure { report(it.message ?: "Ошибка загрузки") }
            }
        }
    }
    val downloadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val ssh = client
        val entry = downloadTarget
        downloadTarget = null
        if (uri != null && ssh != null && entry != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Не удалось открыть файл")
                        output.use { ssh.download(entry.path, it) }
                    }
                }
                result.onSuccess { report("Скачано: ${entry.name}") }
                    .onFailure { report(it.message ?: "Ошибка скачивания") }
            }
        }
    }

    fun goUp() {
        if (path == "/" || path.isBlank()) return
        path = path.substringBeforeLast('/').ifBlank { "/" }
    }

    fun open(entry: RemoteEntry) {
        val ssh = client ?: return
        if (entry.isDir) {
            path = entry.path
            return
        }
        scope.launch {
            runCatching { ssh.readText(entry.path) }
                .onSuccess {
                    editorTarget = entry
                    editorContent = it
                }
                .onFailure { report(it.message ?: "Не удалось открыть файл") }
        }
    }

    fun copyPath(entry: RemoteEntry? = null) {
        val value = entry?.path ?: path
        clipboard.setText(AnnotatedString(value))
        report("Путь скопирован: $value")
    }

    if (client == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Сначала подключись к серверу", style = TermSmall, color = TermDim)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ClaudenIcons.home,
                    contentDescription = "Корень",
                    tint = TermDim,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { path = "/" },
                )
                val parts = path.trim('/').split('/').filter { it.isNotBlank() }
                parts.forEachIndexed { index, part ->
                    Icon(
                        ClaudenIcons.chevronRight,
                        contentDescription = null,
                        tint = TermFaint,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(11.dp),
                    )
                    Text(
                        text = part,
                        style = TermTiny,
                        color = if (index == parts.lastIndex) TermText else TermDim,
                        maxLines = 1,
                        modifier = Modifier.clickable {
                            path = "/" + parts.take(index + 1).joinToString("/")
                        },
                    )
                }
            }
            Icon(
                ClaudenIcons.copy,
                contentDescription = "Копировать путь",
                tint = TermFaint,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(14.dp)
                    .clickable { copyPath() },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SshFileAction(ClaudenIcons.folder, "Папка") {
                createKind = "dir"
                createName = ""
            }
            SshFileAction(ClaudenIcons.doc, "Файл") {
                createKind = "file"
                createName = ""
            }
            SshFileAction(ClaudenIcons.download, "Закачать") {
                uploadPicker.launch(arrayOf("*/*"))
            }
            if (onUseAsRoot != null) {
                SshFileAction(ClaudenIcons.home, "Корень") {
                    onUseAsRoot(path)
                    report("Корень агента: $path")
                }
            }
            if (loading) {
                Spacer(Modifier.width(10.dp))
                TermSpinner(TermFaint)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (path != "/" && path.isNotBlank()) {
                item(key = "__up") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TermPanel, RoundedCornerShape(8.dp))
                            .clickable { goUp() }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            ClaudenIcons.arrowUp,
                            contentDescription = null,
                            tint = TermDim,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("На уровень выше", style = TermSmall, color = TermDim)
                    }
                }
            }
            items(entries, key = { it.path }) { entry ->
                RemoteRow(
                    entry = entry,
                    onClick = { open(entry) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionTarget = entry
                    },
                )
            }
            if (!loading && entries.isEmpty()) {
                item(key = "__empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            ClaudenIcons.folder,
                            contentDescription = null,
                            tint = TermFaint,
                            modifier = Modifier.size(38.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Папка пустая", style = TermSmall, color = TermDim)
                    }
                }
            }
        }

        status?.let {
            Text(
                text = it,
                style = TermTiny,
                color = TermDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
        Text(
            text = if (onUseAsRoot != null) {
                "${host.label} · «Корень» — агент работает в этой папке; долгое нажатие — действия"
            } else {
                "${host.label} · нажмите папку, чтобы войти; долгое нажатие — действия"
            },
            style = TermTiny,
            color = TermFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }

    createKind.takeIf { it.isNotEmpty() }?.let { kind ->
        SshNameDialog(
            title = if (kind == "dir") "Новая папка" else "Новый файл",
            hint = path,
            value = createName,
            confirm = "Создать",
            onChange = { createName = it },
            onDismiss = { createKind = "" },
            onConfirm = {
                val name = createName.trim()
                val ssh = client
                if (ssh == null) {
                    report("Нет SSH-подключения")
                    return@SshNameDialog
                }
                if (name.isBlank() || name.contains('/') || name == "." || name == "..") {
                    report("Недопустимое имя")
                    return@SshNameDialog
                }
                createKind = ""
                scope.launch {
                    runCatching {
                        val target = ssh.joinRemote(path, name)
                        if (kind == "dir") ssh.mkdir(target) else ssh.writeText(target, "")
                    }.onSuccess {
                        report("Создано: $name")
                        load()
                    }.onFailure { report(it.message ?: "Ошибка") }
                }
            },
        )
    }

    actionTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text(entry.path, style = TermTiny, color = TermFaint)
                    Spacer(Modifier.height(8.dp))
                    SshMenuAction(remoteIcon(entry), "Открыть", remoteTint(entry)) {
                        actionTarget = null
                        open(entry)
                    }
                    if (!entry.isDir) {
                        SshMenuAction(ClaudenIcons.download, "Скачать", TermAccent) {
                            downloadTarget = entry
                            actionTarget = null
                            downloadPicker.launch(entry.name)
                        }
                    }
                    SshMenuAction(ClaudenIcons.pencil, "Переименовать", TermAccent) {
                        actionTarget = null
                        renameTarget = entry
                        renameName = entry.name
                    }
                    SshMenuAction(ClaudenIcons.copy, "Копировать путь", TermAccent) {
                        actionTarget = null
                        copyPath(entry)
                    }
                    SshMenuAction(ClaudenIcons.trash, "Удалить", TermRed, danger = true) {
                        actionTarget = null
                        deleteTarget = entry
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) { Text("Закрыть") }
            },
        )
    }

    renameTarget?.let { entry ->
        SshNameDialog(
            title = "Переименовать",
            hint = entry.path,
            value = renameName,
            confirm = "Сохранить",
            onChange = { renameName = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                val name = renameName.trim()
                val ssh = client
                if (ssh == null) {
                    report("Нет SSH-подключения")
                    return@SshNameDialog
                }
                if (name.isBlank() || name.contains('/') || name == "." || name == "..") {
                    report("Недопустимое имя")
                    return@SshNameDialog
                }
                renameTarget = null
                scope.launch {
                    runCatching {
                        ssh.rename(entry.path, ssh.joinRemote(path, name))
                    }.onSuccess {
                        report("Переименовано: $name")
                        load()
                    }.onFailure { report(it.message ?: "Ошибка") }
                }
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить?") },
            text = {
                Text(
                    "${entry.path}\n\n${if (entry.isDir) "Папка будет удалена со всем содержимым. " else ""}" +
                        "Действие нельзя отменить.",
                    style = TermSmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    val ssh = client ?: return@TextButton
                    scope.launch {
                        runCatching { ssh.delete(entry.path) }
                            .onSuccess {
                                report("Удалено: ${entry.name}")
                                load()
                            }
                            .onFailure { report(it.message ?: "Ошибка удаления") }
                    }
                }) { Text("Удалить", color = TermRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
        )
    }

    editorTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!editorSaving) editorTarget = null },
            title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text(entry.path, style = TermTiny, color = TermFaint)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .background(TermBg, RoundedCornerShape(7.dp))
                            .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        BasicTextField(
                            value = editorContent,
                            onValueChange = { editorContent = it },
                            textStyle = TermSmall.copy(color = TermText, lineHeight = 18.sp),
                            cursorBrush = SolidColor(TermAccent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !editorSaving,
                    onClick = {
                        val ssh = client ?: return@TextButton
                        editorSaving = true
                        scope.launch {
                            runCatching { ssh.writeText(entry.path, editorContent) }
                                .onSuccess {
                                    report("Сохранено: ${entry.name}")
                                    editorTarget = null
                                }
                                .onFailure { report(it.message ?: "Ошибка сохранения") }
                            editorSaving = false
                        }
                    },
                ) { Text(if (editorSaving) "Сохраняю…" else "Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editorTarget = null }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun SshFileAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(TermPanel, RoundedCornerShape(7.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TermAccent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = TermTiny, color = TermText)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteRow(
    entry: RemoteEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermPanel, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(remoteTint(entry).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                remoteIcon(entry),
                contentDescription = null,
                tint = remoteTint(entry),
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = TermSmall,
                color = TermText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    entry.isDir -> "папка"
                    entry.isLink -> "симлинк"
                    else -> formatRemoteSize(entry.size)
                },
                style = TermTiny,
                color = TermFaint,
            )
        }
        if (entry.isDir) {
            Icon(
                ClaudenIcons.chevronRight,
                contentDescription = null,
                tint = TermFaint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SshMenuAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = TermBody.copy(fontWeight = FontWeight.Normal),
            color = if (danger) TermRed else TermText,
        )
    }
}

@Composable
private fun SshNameDialog(
    title: String,
    hint: String,
    value: String,
    confirm: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = TermTiny, color = TermFaint)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TermBody.copy(color = TermText),
                    cursorBrush = SolidColor(TermAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermBg, RoundedCornerShape(6.dp))
                        .border(1.dp, TermBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun remoteIcon(entry: RemoteEntry): ImageVector = when {
    entry.isDir -> ClaudenIcons.folder
    entry.isLink -> ClaudenIcons.link
    entry.name.substringAfterLast('.', "").lowercase() in
        setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic") -> ClaudenIcons.image
    entry.name.substringAfterLast('.', "").lowercase() in
        setOf(
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "json", "xml", "html",
            "css", "sh", "bash", "yml", "yaml", "toml", "gradle", "c", "h", "cpp", "go",
            "rs", "rb", "php", "sql", "md",
        ) -> ClaudenIcons.code
    else -> ClaudenIcons.doc
}

private fun remoteTint(entry: RemoteEntry): Color = when {
    entry.isDir -> TermAccent
    entry.isLink -> TermFaint
    entry.name.substringAfterLast('.', "").lowercase() in
        setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic") -> TermGreen
    entry.name.substringAfterLast('.', "").lowercase() in
        setOf(
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "json", "xml", "html",
            "css", "sh", "bash", "yml", "yaml", "toml", "gradle", "c", "h", "cpp", "go",
            "rs", "rb", "php", "sql", "md",
        ) -> Color(0xFF9AB6D6)
    else -> TermDim
}

private fun formatRemoteSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f КБ", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
}

private fun displayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
