package space.teleopen.clauden.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch
import space.teleopen.clauden.data.CodeApi
import space.teleopen.clauden.data.CodeFileEntry
import space.teleopen.clauden.data.CodeFilesListing
import space.teleopen.clauden.data.DeviceFolder
import space.teleopen.clauden.data.DeviceFolderRef
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CodeFilesScreen(
    api: CodeApi,
    sessionId: String,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onNote: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val folder = remember { DeviceFolder(context) }

    var path by rememberSaveable { mutableStateOf("") }
    var listing by remember { mutableStateOf<CodeFilesListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var folders by remember { mutableStateOf(folder.folders()) }
    var autoWrite by remember { mutableStateOf(folder.autoWrite()) }

    var actionTarget by remember { mutableStateOf<CodeFileEntry?>(null) }
    var createKind by remember { mutableStateOf("") }
    var createName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<CodeFileEntry?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CodeFileEntry?>(null) }
    var editorTarget by remember { mutableStateOf<CodeFileEntry?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var editorSaving by remember { mutableStateOf(false) }

    fun report(text: String) {
        status = text
        onNote(text)
    }

    fun load() {
        scope.launch {
            loading = true
            runCatching { api.files(sessionId, path) }
                .onSuccess {
                    listing = it
                }
                .onFailure { status = it.codeMessage }
            loading = false
        }
    }

    LaunchedEffect(path) { load() }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            runCatching { folder.setTree(uri) }
                .onSuccess {
                    folders = folder.folders()
                    report("Папка добавлена. Нажми /upload в сессии, чтобы обновить проект.")
                }
                .onFailure { report(it.codeMessage) }
        }
    }

    fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            folder.treeUri()?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        runCatching { pickFolder.launch(intent) }
            .onFailure { report("Не удалось открыть выбор папки: ${it.message}") }
    }

    fun goUp() {
        if (path.isNotEmpty()) path = listing?.parent.orEmpty()
    }

    fun openEntry(entry: CodeFileEntry) {
        when (entry.type) {
            "dir" -> path = entry.path
            "link" -> report("Симлинк: ${entry.path}")
            else -> scope.launch {
                runCatching { api.fileRead(sessionId, entry.path) }
                    .onSuccess { content ->
                        if (content.binary) {
                            report("Двоичный файл: ${entry.path}")
                        } else {
                            editorTarget = entry
                            editorContent = content.content
                        }
                    }
                    .onFailure { report(it.codeMessage) }
            }
        }
    }

    fun copyPath(entry: CodeFileEntry? = null) {
        val value = if (entry == null) {
            if (path.isEmpty()) "~/" else "~/$path"
        } else {
            "~/${entry.path}"
        }
        clipboard.setText(AnnotatedString(value))
        report("Путь скопирован: $value")
    }

    BackHandler {
        if (actionTarget != null) actionTarget = null
        else if (path.isNotEmpty()) goUp()
        else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(TermBg),
    ) {
        TerminalTopBar(
            title = "SFTP",
            subtitle = "~/${path}",
            onBack = onBack,
            onMenu = onMenu,
            trailing = {
                Text(
                    text = "обновить",
                    style = TermTiny,
                    color = TermFaint,
                    modifier = Modifier
                        .clickable { load() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            },
        )

        FolderAccessCard(
            folders = folders,
            autoWrite = autoWrite,
            onChoose = { chooseFolder() },
            onActivate = { ref ->
                folder.setActiveTree(ref.uri)
                folders = folder.folders()
                report("Активная папка: ${ref.name}. Для новой сессии — /upload.")
            },
            onRemove = { ref ->
                folder.removeTree(ref.uri)
                folders = folder.folders()
                report("Папка отключена: ${ref.name}. Обнови проект через /upload.")
            },
            onAutoWrite = {
                folder.setAutoWrite(it)
                autoWrite = it
                report(
                    if (it) "Файлы будут сохраняться без подтверждения"
                    else "Перед записью файлов снова спрошу",
                )
            },
        )

        Breadcrumb(
            path = path,
            onRoot = { path = "" },
            onSegment = { path = it },
            onCopy = { copyPath() },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SftpAction(
                icon = ClaudenIcons.folder,
                label = "Папка",
                onClick = {
                    createKind = "dir"
                    createName = ""
                },
            )
            SftpAction(
                icon = ClaudenIcons.doc,
                label = "Файл",
                onClick = {
                    createKind = "file"
                    createName = ""
                },
            )
            if (loading) {
                Spacer(Modifier.weight(1f))
                TermSpinner(TermFaint)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (path.isNotEmpty()) {
                item(key = "__up") {
                    UpRow(onClick = { goUp() })
                }
            }
            items(listing?.entries.orEmpty(), key = { it.path }) { entry ->
                FileRow(
                    entry = entry,
                    onClick = { openEntry(entry) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionTarget = entry
                    },
                )
            }
            if (!loading && listing?.entries.isNullOrEmpty()) {
                item(key = "__empty") {
                    EmptyFolder()
                }
            }
        }

        status?.let {
            Text(
                text = it,
                style = TermTiny,
                color = TermDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Text(
            text = "Нажмите папку, чтобы войти. Долгое нажатие — действия. SFTP работает с папкой агента.",
            style = TermTiny,
            color = TermFaint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    createKind.takeIf { it.isNotEmpty() }?.let { kind ->
        SftpNameDialog(
            title = if (kind == "dir") "Новая папка" else "Новый файл",
            hint = "в ~/${path}",
            value = createName,
            confirm = "Создать",
            onChange = { createName = it },
            onDismiss = { createKind = "" },
            onConfirm = {
                val name = createName.trim()
                if (name.isBlank()) {
                    report("Имя не может быть пустым")
                    return@SftpNameDialog
                }
                createKind = ""
                scope.launch {
                    runCatching { api.fileCreate(sessionId, path, name, kind) }
                        .onSuccess {
                            report("Создано: $name")
                            load()
                        }
                        .onFailure { report(it.codeMessage) }
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
                    Text("~/${entry.path}", style = TermTiny, color = TermFaint)
                    Spacer(Modifier.height(8.dp))
                    FileAction(fileIcon(entry), "Открыть", iconTint(entry)) {
                        actionTarget = null
                        openEntry(entry)
                    }
                    FileAction(ClaudenIcons.pencil, "Переименовать", TermAccent) {
                        actionTarget = null
                        renameTarget = entry
                        renameName = entry.name
                    }
                    FileAction(ClaudenIcons.copy, "Копировать путь", TermAccent) {
                        actionTarget = null
                        copyPath(entry)
                    }
                    FileAction(ClaudenIcons.trash, "Удалить", TermRed, danger = true) {
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
        SftpNameDialog(
            title = "Переименовать",
            hint = entry.path,
            value = renameName,
            confirm = "Сохранить",
            onChange = { renameName = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                val name = renameName.trim()
                if (name.isBlank()) {
                    report("Имя не может быть пустым")
                    return@SftpNameDialog
                }
                renameTarget = null
                scope.launch {
                    runCatching { api.fileRename(sessionId, entry.path, name) }
                        .onSuccess {
                            report("Переименовано: $name")
                            load()
                        }
                        .onFailure { report(it.codeMessage) }
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
                    "${entry.path}\n\nУдаление нельзя отменить.",
                    style = TermSmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        runCatching { api.fileDelete(sessionId, entry.path) }
                            .onSuccess {
                                report("Удалено: ${entry.name}")
                                load()
                            }
                            .onFailure { report(it.codeMessage) }
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
                    Text("~/${entry.path}", style = TermTiny, color = TermFaint)
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
                            textStyle = TermSmall.copy(
                                color = TermText,
                                lineHeight = 18.sp,
                            ),
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
                        editorSaving = true
                        scope.launch {
                            runCatching {
                                api.fileWrite(sessionId, entry.path, editorContent)
                            }
                                .onSuccess {
                                    report("Сохранено: ${entry.name}")
                                    editorTarget = null
                                }
                                .onFailure { report(it.codeMessage) }
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
private fun FolderAccessCard(
    folders: List<DeviceFolderRef>,
    autoWrite: Boolean,
    onChoose: () -> Unit,
    onActivate: (DeviceFolderRef) -> Unit,
    onRemove: (DeviceFolderRef) -> Unit,
    onAutoWrite: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(TermPanel, RoundedCornerShape(10.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(TermAccent.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ClaudenIcons.folder,
                    contentDescription = null,
                    tint = TermAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Папки телефона",
                    style = TermSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TermText,
                )
                Text(
                    text = if (folders.isEmpty()) {
                        "Доступ пока не выдан"
                    } else {
                        "В проект попадут все папки, активная — для быстрых операций"
                    },
                    style = TermTiny,
                    color = TermFaint,
                    maxLines = 2,
                )
            }
            Text(
                text = if (folders.isEmpty()) "Выбрать" else "Добавить",
                style = TermTiny,
                color = TermAccent,
                modifier = Modifier
                    .background(TermAccent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .clickable { onChoose() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (folders.isEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                "Android спросит доступ один раз, Clauden его запомнит. " +
                    "Можно добавить несколько папок; всю память Android 11+ обычно выбрать не даёт.",
                style = TermTiny,
                color = TermFaint,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            folders.forEach { ref ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActivate(ref) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        ClaudenIcons.folder,
                        contentDescription = null,
                        tint = if (ref.active) TermAccent else TermDim,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = ref.name,
                            style = TermSmall,
                            color = if (ref.active) TermText else TermDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (ref.active) "активна · синхронизируется" else "нажмите, чтобы выбрать",
                            style = TermTiny,
                            color = if (ref.active) TermAccent else TermFaint,
                        )
                    }
                    Icon(
                        ClaudenIcons.trash,
                        contentDescription = "Отключить папку",
                        tint = TermFaint,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { onRemove(ref) },
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Писать без подтверждений", style = TermTiny, color = TermText)
                    Text(
                        "Агент сохранит файлы сразу, без диалога",
                        style = TermTiny,
                        color = TermFaint,
                    )
                }
                Switch(
                    checked = autoWrite,
                    onCheckedChange = onAutoWrite,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TermBg,
                        checkedTrackColor = TermAccent,
                        uncheckedThumbColor = TermDim,
                        uncheckedTrackColor = TermBorder,
                        uncheckedBorderColor = TermBorder,
                    ),
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

@Composable
private fun Breadcrumb(
    path: String,
    onRoot: () -> Unit,
    onSegment: (String) -> Unit,
    onCopy: () -> Unit,
) {
    val parts = remember(path) { if (path.isBlank()) emptyList() else path.split('/') }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
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
                    .clickable { onRoot() },
            )
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
                        onSegment(parts.take(index + 1).joinToString("/"))
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
                .clickable { onCopy() },
        )
    }
}

@Composable
private fun SftpAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(TermPanel, RoundedCornerShape(7.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TermAccent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TermTiny, color = TermText)
    }
}

@Composable
private fun UpRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermPanel, RoundedCornerShape(8.dp))
            .clickable { onClick() }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: CodeFileEntry,
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
                .background(iconBackground(entry), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                fileIcon(entry),
                contentDescription = null,
                tint = iconTint(entry),
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
                text = when (entry.type) {
                    "dir" -> "папка"
                    "link" -> "симлинк"
                    else -> formatBytes(entry.size)
                },
                style = TermTiny,
                color = TermFaint,
            )
        }
        if (entry.type == "dir") {
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
private fun EmptyFolder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            ClaudenIcons.folder,
            contentDescription = null,
            tint = TermFaint,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("Папка пустая", style = TermSmall, color = TermDim)
        Text(
            "Создайте папку или файл кнопками выше",
            style = TermTiny,
            color = TermFaint,
        )
    }
}

@Composable
private fun SftpNameDialog(
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

@Composable
private fun FileAction(
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
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = TermBody.copy(fontWeight = FontWeight.Normal),
            color = if (danger) TermRed else TermText,
        )
    }
}

private fun fileIcon(entry: CodeFileEntry): ImageVector = when {
    entry.type == "dir" -> ClaudenIcons.folder
    entry.type == "link" -> ClaudenIcons.link
    isImage(entry.name) -> ClaudenIcons.image
    isCode(entry.name) -> ClaudenIcons.code
    else -> ClaudenIcons.doc
}

private fun iconTint(entry: CodeFileEntry): Color = when {
    entry.type == "dir" -> TermAccent
    entry.type == "link" -> TermFaint
    isImage(entry.name) -> TermGreen
    isCode(entry.name) -> Color(0xFF9AB6D6)
    else -> TermDim
}

private fun iconBackground(entry: CodeFileEntry): Color = when {
    entry.type == "dir" -> TermAccent.copy(alpha = 0.13f)
    isImage(entry.name) -> TermGreen.copy(alpha = 0.12f)
    isCode(entry.name) -> Color(0xFF9AB6D6).copy(alpha = 0.12f)
    else -> Color(0xFF8C8983).copy(alpha = 0.10f)
}

private fun isImage(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic")
}

private fun isCode(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf(
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "json", "xml", "html",
        "css", "sh", "bash", "yml", "yaml", "toml", "gradle", "c", "h", "cpp", "go",
        "rs", "rb", "php", "sql", "md",
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f КБ", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
}
