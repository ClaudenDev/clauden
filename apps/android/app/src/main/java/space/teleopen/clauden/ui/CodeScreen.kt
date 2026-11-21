package space.teleopen.clauden.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.alarm.ClaudenReplyNotifications
import space.teleopen.clauden.data.DeviceFolder
import java.io.File
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.CodeAction
import space.teleopen.clauden.data.CodeApi
import space.teleopen.clauden.data.CodeAttachment
import space.teleopen.clauden.data.CodeDevice
import space.teleopen.clauden.data.CodeFs
import space.teleopen.clauden.data.CodeJob
import space.teleopen.clauden.data.CodeJobResult
import space.teleopen.clauden.data.TermuxBridge
import space.teleopen.clauden.data.CodeDiff
import space.teleopen.clauden.data.CodeException
import space.teleopen.clauden.data.CodeGithub
import space.teleopen.clauden.data.CodeMessage
import space.teleopen.clauden.data.CodeRepo
import space.teleopen.clauden.data.CodePull
import space.teleopen.clauden.data.CodeSession
import space.teleopen.clauden.data.CodeStep

private object CodeBootGate {
    @Volatile
    var done = false
}

private enum class CodeStage { Boot, Terminal, Projects }

@Composable
fun CodeScreen(state: ChatUiState, vm: ChatViewModel, onMenu: () -> Unit) {

    val api = remember(state.claudenBaseUrl, state.auth.token) {
        CodeApi(state.claudenBaseUrl, state.auth.token)
    }

    var booted by remember { mutableStateOf(CodeBootGate.done) }

    var tab by rememberSaveable { mutableStateOf("remote") }

    var remoteToken by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (booted) return@LaunchedEffect
        delay(3000)
        CodeBootGate.done = true
        booted = true
    }

    LaunchedEffect(state.remoteToken) {
        if (state.remoteToken.isNotBlank()) tab = "remote"
    }

    Crossfade(
        targetState = booted,
        animationSpec = tween(280),
        label = "code-stage",
    ) { ready ->
        if (!ready) {
            CodeBootScreen()
        } else {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(title = "Code", onMenu = onMenu)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CodeTabPill("Remote", tab == "remote") { tab = "remote" }
                    CodeTabPill("Проекты", tab == "projects") { tab = "projects" }
                }
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(WindowInsets.statusBars),
                ) {
                    if (tab == "remote") {
                        RemoteControlScreen(
                            baseUrl = state.claudenBaseUrl,
                            authToken = state.auth.token,
                            signedIn = state.auth.token.isNotBlank(),
                            claudeMode = state.authMode == AuthMode.Claude,
                            codeAccess = state.auth.codeAccess,
                            token = remoteToken,
                            onTokenChange = { remoteToken = it },
                            pendingToken = state.remoteToken,
                            onConsumePending = vm::consumeRemoteToken,
                            onMenu = onMenu,
                        )
                    } else {
                        CodeTerminalApp(
                            api = api,
                            signedIn = state.auth.token.isNotBlank(),
                            claudeMode = state.authMode == AuthMode.Claude,
                            codeAccess = state.auth.codeAccess,
                            onMenu = onMenu,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.control else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.height(34.dp).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = if (selected) colors.textPrimary else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun CodeHome(
    api: CodeApi,
    signedIn: Boolean,
    claudeMode: Boolean,
    codeAccess: Boolean,
    refreshKey: Int,
    onMenu: () -> Unit,
    onOpen: (String) -> Unit,
    onTerminal: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val folder = remember { DeviceFolder(context) }
    var folderName by remember { mutableStateOf(folder.name()) }
    var mode by rememberSaveable { mutableStateOf("phone") }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { folder.setTree(uri) }
            folderName = folder.name()
            mode = "phone"
        }
    }
    var repos by remember { mutableStateOf<List<CodeRepo>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<CodeSession>>(emptyList()) }
    var repoId by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var github by remember { mutableStateOf<CodeGithub?>(null) }
    var ghRepos by remember { mutableStateOf<List<CodeRepo>>(emptyList()) }
    var showGithub by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val allRepos = repos + ghRepos

    fun load() {
        if (!signedIn) return
        scope.launch {
            loading = true
            runCatching { api.repos() }
                .onSuccess { list ->
                    repos = list
                    if (repoId.isBlank()) repoId = list.firstOrNull()?.id ?: ""
                }
                .onFailure { error = it.codeMessage }
            runCatching { api.github() }
                .onSuccess { info ->
                    github = info
                    if (info.connected) {
                        runCatching { api.githubRepos() }
                            .onSuccess { list ->
                                ghRepos = list
                                if (repoId.isBlank()) repoId = list.firstOrNull()?.id ?: ""
                            }
                    }
                }
            runCatching { api.sessions() }
                .onSuccess { sessions = it }
                .onFailure { err ->
                    if ((err as? CodeException)?.status == 403) locked = true
                }
            loading = false
        }
    }

    LaunchedEffect(refreshKey) { load() }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "Code",
            onMenu = onMenu,
            trailing = {
                IconButton(onClick = onTerminal, modifier = Modifier.size(44.dp)) {
                    Icon(
                        ClaudenIcons.code,
                        contentDescription = "Терминал",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        if (!signedIn) {
            HintCard("Войдите в аккаунт Clauden — Code работает через наш сервер.")
            return@Column
        }
        if (claudeMode) {
            HintCard(
                "Сейчас включён режим Claude. Code работает через сервер Clauden — " +
                    "переключите движок в настройках или оформите подписку.",
            )
            return@Column
        }
        if (!codeAccess || locked) {
            HintCard(
                "Code доступен только с подпиской, оформленной в Clauden. " +
                    "Подписка, выданная вручную или купленная в Claude, Code не открывает.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Новая задача",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (mode == "phone") {
                                "Возьмите папку с телефона: агент поработает с копией на сервере, " +
                                    "изменения вернём обратно на устройство."
                            } else {
                                "Агент склонирует репозиторий, поработает в отдельной копии и покажет дифф."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RepoChip("Телефон", mode == "phone") { mode = "phone" }
                            RepoChip("Репозиторий", mode == "repo") { mode = "repo" }
                        }
                        Spacer(Modifier.height(10.dp))
                        val selected = allRepos.firstOrNull { it.id == repoId }
                        if (mode == "phone") {
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { pickFolder.launch(null) },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        folderName.ifBlank { "Выбрать папку на телефоне" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = if (folderName.isBlank()) colors.textTertiary else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("▾", color = colors.textTertiary)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Доступ выдаёте вы системным диалогом. Файлы до 20 МБ, до 2000 штук.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = colors.textTertiary,
                            )
                        } else {
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .clickable { showPicker = true },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        ClaudenIcons.code,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        selected?.name ?: if (loading) "Загружаю…" else "Выберите репозиторий",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = if (selected == null) colors.textTertiary else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "▾",
                                        color = colors.textTertiary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            GitHubRow(
                                github = github,
                                onConnect = { showGithub = true },
                                onDisconnect = {
                                    scope.launch {
                                        runCatching { api.githubDisconnect() }
                                        github = CodeGithub(connected = false)
                                        ghRepos = emptyList()
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = {
                                Text(
                                    "Что сделать в проекте?",
                                    color = colors.textTertiary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        val hasFolder = folderName.isNotBlank()
                        val enabled = prompt.isNotBlank() && !creating &&
                            if (mode == "phone") hasFolder else repoId.isNotBlank()
                        Button(
                            onClick = {
                                if (mode == "phone") {
                                    scope.launch {
                                        creating = true
                                        error = null
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                val session = api.createDevice(
                                                    prompt.trim(),
                                                    folder.name(),
                                                )
                                                val temp = File(context.cacheDir, "clauden-source.zip")
                                                try {
                                                    val count = folder.zipTo(temp)
                                                    if (count == 0) error("В папке нет файлов")
                                                    api.uploadSource(session.id, temp)
                                                } finally {
                                                    temp.delete()
                                                }
                                                session.id
                                            }
                                        }.onSuccess { id ->
                                            prompt = ""
                                            onOpen(id)
                                        }.onFailure { err ->
                                            error = err.codeMessage
                                            if ((err as? CodeException)?.status == 403) locked = true
                                        }
                                        creating = false
                                    }
                                } else {
                                    scope.launch {
                                        creating = true
                                        error = null
                                        runCatching { api.create(repoId, prompt.trim()) }
                                            .onSuccess { session ->
                                                prompt = ""
                                                sessions = listOf(session) + sessions
                                                onOpen(session.id)
                                            }
                                            .onFailure { err ->
                                                error = err.codeMessage
                                                if ((err as? CodeException)?.status == 403) locked = true
                                            }
                                        creating = false
                                    }
                                }
                            },
                            enabled = enabled,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.clay,
                                contentColor = Color.White,
                                disabledContainerColor = colors.buttonDisabled,
                                disabledContentColor = colors.buttonDisabledText,
                            ),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                        ) {
                            Text(
                                if (creating) "Готовлю сессию…"
                                else if (mode == "phone") "Запустить из папки" else "Запустить",
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }

            error?.let { text ->
                item {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.danger,
                    )
                }
            }

            item {
                Text(
                    "Сессии",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (sessions.isEmpty() && !loading) {
                item {
                    Text(
                        "Пока нет сессий. Опишите задачу выше — это займёт полминуты.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.textTertiary,
                    )
                }
            }

            items(sessions, key = { it.id }) { session ->
                SessionRow(session) { onOpen(session.id) }
            }
        }
    }

    if (showPicker) {
        RepoPickerDialog(
            repos = allRepos,
            selected = repoId,
            onPick = { repoId = it; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    if (showGithub) {
        GithubDialog(
            onDismiss = { showGithub = false },
            onConnect = { token, done ->
                scope.launch {
                    runCatching { api.githubConnect(token) }
                        .onSuccess { info ->
                            github = info
                            runCatching { api.githubRepos() }
                                .onSuccess { list ->
                                    ghRepos = list
                                    if (repoId.isBlank()) repoId = list.firstOrNull()?.id ?: ""
                                }
                            done(null)
                            showGithub = false
                        }
                        .onFailure { done(it.codeMessage) }
                }
            },
        )
    }
}

@Composable
private fun GitHubRow(
    github: CodeGithub?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (github?.connected == true) "GitHub: @${github.login}" else "GitHub не подключён",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = if (github?.connected == true) colors.textSecondary else colors.textTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = if (github?.connected == true) onDisconnect else onConnect) {
            Text(
                if (github?.connected == true) "Отключить" else "Подключить",
                fontSize = 12.sp,
                color = colors.clay,
            )
        }
    }
}

@Composable
private fun RepoPickerDialog(
    repos: List<CodeRepo>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Репозиторий") },
        text = {
            if (repos.isEmpty()) {
                Text("Пока нечего выбрать: добавьте репозиторий на сервере или подключите GitHub.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(repos, key = { it.id }) { repo ->
                        val active = repo.id == selected
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(repo.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                repo.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = if (active) colors.clay else colors.textPrimary,
                            )
                            Text(
                                buildString {
                                    if (repo.id.startsWith("gh:")) append("GitHub · ")
                                    append(repo.defaultBranch)
                                    if (repo.isPrivate) append(" · приватный")
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = colors.textTertiary,
                            )
                        }
                        HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun GithubDialog(
    onDismiss: () -> Unit,
    onConnect: (String, (String?) -> Unit) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var token by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключить GitHub") },
        text = {
            Column {
                Text(
                    "Токен хранится только на сервере Clauden и в приложение не возвращается. " +
                        "Создайте fine-grained token: Contents — Read and write, Pull requests — Read and write.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = { Text("github_pat_…", color = colors.textTertiary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = colors.danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = token.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    error = null
                    onConnect(token.trim()) { message ->
                        busy = false
                        error = message
                    }
                },
            ) { Text(if (busy) "Проверяю…" else "Подключить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun RepoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.drawerPill else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .border(
                1.dp,
                if (selected) colors.clay else colors.border,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = if (selected) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SessionRow(session: CodeSession, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title.ifBlank { session.repoName },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(session.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.repoName} · ${session.branch}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
            val tail = session.lastText.ifBlank { session.error }
            if (tail.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val colors = LocalClaudenColors.current
    val (label, color) = when (status) {
        "working" -> "Работает" to colors.clay
        "waiting_device" -> "Ждёт телефон" to colors.clay
        "failed" -> "Ошибка" to colors.danger
        "stopped" -> "Остановлено" to colors.textTertiary
        "interrupted" -> "Прервано" to colors.textTertiary
        else -> "Готово" to colors.blue
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CodeSessionScreen(api: CodeApi, sessionId: String, onBack: () -> Unit) {
    val colors = LocalClaudenColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val json = remember { Json { ignoreUnknownKeys = true } }
    var session by remember { mutableStateOf<CodeSession?>(null) }
    var showPr by remember { mutableStateOf(false) }
    var showDevice by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<CodeJob?>(null) }
    var jobBusy by remember { mutableStateOf(false) }
    var jobNote by remember { mutableStateOf<String?>(null) }
    var actionNote by remember { mutableStateOf<String?>(null) }
    var writeJob by remember { mutableStateOf<CodeJob?>(null) }
    val handledJobs = remember { mutableSetOf<String>() }
    var termuxReady by remember { mutableStateOf(TermuxBridge.installed(context)) }
    val termuxPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        termuxReady = TermuxBridge.installed(context)
        if (!granted) jobNote = "Без разрешения Termux команда не выполнится"
    }
    var device by remember { mutableStateOf<CodeDevice?>(null) }
    var deviceBusy by remember { mutableStateOf(false) }
    var deviceNote by remember { mutableStateOf<String?>(null) }
    val folder = remember { DeviceFolder(context) }
    var folderName by remember { mutableStateOf(folder.name()) }
    var browseName by remember { mutableStateOf(folder.browseName()) }
    val pickBrowse = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { folder.setBrowse(uri) }
            browseName = folder.browseName()
            deviceNote = "Агент видит: $browseName"
        }
    }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { folder.setTree(uri) }
            folderName = folder.name()
            deviceNote = null
        }
    }
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingImages by remember { mutableStateOf<List<CodeAttachment>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var diff by remember { mutableStateOf<CodeDiff?>(null) }
    var showDiff by remember { mutableStateOf(false) }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(3),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> readImageAttachment(context, uri) }
                }
                if (loaded.isEmpty()) {
                    error = "Не удалось прочитать картинку"
                } else {
                    pendingImages = (pendingImages + loaded).take(3)
                }
            }
        }
    }

    BackHandler { onBack() }

    var reconnecting by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        runCatching { api.session(sessionId) }
            .onSuccess { session = it }
            .onFailure { error = it.codeMessage }
        runCatching { api.device(sessionId) }.onSuccess { device = it }

        while (true) {
            try {
                api.stream(sessionId, onOpen = { reconnecting = false }) { name, data ->
                    val current = session ?: return@stream
                    when (name) {
                        "snapshot" -> runCatching {
                            json.decodeFromJsonElement<CodeSession>(data)
                        }.onSuccess { session = it }
                        "delta" -> {
                            val piece = runCatching {
                                data.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                            }.getOrNull() ?: return@stream
                            session = current.appendDelta(piece)
                        }
                        "step" -> {
                            val step = runCatching {
                                json.decodeFromJsonElement<CodeStep>(data)
                            }.getOrNull() ?: return@stream
                            session = current.upsertStep(step)
                        }
                        "job" -> scope.launch {
                            runCatching { api.session(sessionId) }.onSuccess { session = it }
                        }
                        "actions" -> runCatching {
                            json.decodeFromJsonElement<List<CodeAction>>(data)
                        }.onSuccess { list -> session = current.copy(actions = list) }
                        "status" -> {
                            val status = runCatching {
                                data.jsonObject["status"]?.jsonPrimitive?.contentOrNull
                            }.getOrNull() ?: return@stream
                            val msg = runCatching {
                                data.jsonObject["error"]?.jsonPrimitive?.contentOrNull
                            }.getOrNull().orEmpty()
                            session = current.copy(status = status, error = msg)
                            if (status != "working") {
                                scope.launch {
                                    runCatching { api.session(sessionId) }
                                        .onSuccess { session = it }
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (err: Throwable) {
                if (!isActive) break
                if (err is CodeException && err.status in listOf(401, 403, 404)) {
                    error = err.codeMessage
                    break
                }
                reconnecting = true
                runCatching { api.session(sessionId) }.onSuccess { session = it }
                delay(2500)
                continue
            }
            if (!isActive) break
            delay(1000)
        }
    }

    LaunchedEffect(session?.jobs) {
        val active = job
        if (active != null) {
            val updated = session?.jobs?.firstOrNull { it.id == active.id }
            if (updated == null || updated.status != "queued") {
                job = null
                jobBusy = false
                jobNote = null
            }
        }
        val queued = session?.jobs.orEmpty().filter { it.status == "queued" }
        for (pending in queued) {
            if (!handledJobs.add(pending.id)) continue
            val fs = pending.fs
            if (fs != null) {
                if (fs.op == "write") {
                    writeJob = pending
                    continue
                }
                scope.launch {
                    val json = runCatching {
                        withContext(Dispatchers.IO) { folder.exec(fs.op, fs.path, fs.content) }
                    }.getOrElse {
                        org.json.JSONObject().put("error", it.codeMessage).toString()
                    }
                    runCatching { api.jobResult(sessionId, pending.id, CodeJobResult(stdout = json, code = 0)) }
                    runCatching { api.session(sessionId) }.onSuccess { session = it }
                }
            } else {
                scope.launch {
                    runCatching {
                        api.jobResult(
                            sessionId,
                            pending.id,
                            CodeJobResult(
                                rejected = true,
                                error = "Termux-команды отключены. Работаем только с файлами.",
                            ),
                        )
                    }
                    runCatching { api.session(sessionId) }.onSuccess { session = it }
                }
            }
        }
    }

    LaunchedEffect(session?.actions) {
        val pending = session?.actions.orEmpty().filter { it.status == "pending" }
        for (action in pending) {
            val ok = runCatching { runDeviceAction(context, action) }.isSuccess
            runCatching { api.markAction(sessionId, action.id, if (ok) "done" else "failed") }
            session = session?.let { current ->
                current.copy(
                    actions = current.actions.map {
                        if (it.id == action.id) it.copy(status = if (ok) "done" else "failed") else it
                    },
                )
            }
        }
    }

    fun send() {
        val text = draft.trim()
        val images = pendingImages
        if ((text.isBlank() && images.isEmpty()) || sending) return
        scope.launch {
            sending = true
            error = null
            runCatching { api.send(sessionId, text, images) }
                .onSuccess { draft = ""; pendingImages = emptyList(); session = it }
                .onFailure { error = it.codeMessage }
            sending = false
        }
    }

    fun reload() {
        scope.launch {
            runCatching { api.session(sessionId) }.onSuccess { session = it }
        }
    }

    fun pullChanges(onDone: (String) -> Unit) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val changes = api.deviceChanges(sessionId)
                    if (changes.zip.isEmpty()) {
                        "Изменений нет"
                    } else {
                        val temp = File(context.cacheDir, "clauden-device-changes.zip")
                        temp.writeBytes(changes.zip)
                        try {
                            "Записано файлов: ${folder.applyZip(temp, changes.deleted)}"
                        } finally {
                            temp.delete()
                        }
                    }
                }
            }.onSuccess(onDone).onFailure { onDone(it.codeMessage) }
        }
    }

    var lastAutoPull by remember { mutableStateOf(0L) }
    LaunchedEffect(session?.updatedAt, session?.status, session?.source) {
        val current = session ?: return@LaunchedEffect
        if (current.source != "device" || current.working) return@LaunchedEffect
        if (!current.sourceReady) return@LaunchedEffect
        if (current.updatedAt <= lastAutoPull) return@LaunchedEffect
        if (folder.treeUri() == null) return@LaunchedEffect
        lastAutoPull = current.updatedAt
        pullChanges { note ->
            actionNote = if (note == "Изменений нет") null else note
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(ClaudenIcons.arrowBack, contentDescription = "Назад", tint = colors.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session?.repoName ?: "Code",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (session?.source == "device") "папка с телефона" else session?.branch.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            session?.let { StatusPill(it.status) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(ClaudenIcons.sliders, contentDescription = "Действия", tint = colors.textSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (session?.source == "device") {
                        DropdownMenuItem(
                            text = { Text("Забрать файлы на телефон") },
                            onClick = {
                                menu = false
                                actionNote = null
                                pullChanges { actionNote = it }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (session?.source == "device") "Папки телефона" else "Папка телефона") },
                        onClick = { menu = false; showDevice = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Показать дифф") },
                        onClick = {
                            menu = false
                            scope.launch {
                                runCatching { api.diff(sessionId) }
                                    .onSuccess { diff = it; showDiff = true }
                                    .onFailure { error = it.codeMessage }
                            }
                        },
                    )
                    if (session?.repo?.startsWith("gh:") == true && session?.working != true) {
                        DropdownMenuItem(
                            text = { Text("Отправить и создать PR") },
                            onClick = { menu = false; showPr = true },
                        )
                    }
                    if (session?.working == true) {
                        DropdownMenuItem(
                            text = { Text("Остановить") },
                            onClick = {
                                menu = false
                                scope.launch {
                                    runCatching { api.stop(sessionId) }
                                        .onFailure { error = it.codeMessage }
                                    reload()
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Удалить сессию", color = colors.danger) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }

        if (reconnecting) {
            Text(
                "Переподключаюсь к серверу…",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        if (session?.working == true) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.clay,
                trackColor = colors.card,
            )
        }

        val items = session?.messages.orEmpty()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { message ->
                if (message.role == "user") UserBubble(message.text) else AgentBlock(message)
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "Готовлю сессию…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        actionNote?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.danger,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(color = colors.border)
        if (pendingImages.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(pendingImages) { image ->
                    Row(
                        Modifier
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            ClaudenIcons.image,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            image.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                        IconButton(
                            onClick = { pendingImages = pendingImages - image },
                            modifier = Modifier.size(26.dp),
                        ) {
                            Text(
                                "×",
                                color = colors.textTertiary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val canSend = (draft.isNotBlank() || pendingImages.isNotEmpty()) &&
                !sending && session?.working != true
            IconButton(
                onClick = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !sending && session?.working != true,
            ) {
                Icon(
                    ClaudenIcons.image,
                    contentDescription = "Прикрепить фото",
                    tint = colors.textSecondary,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        "Что дальше?",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { send() },
                enabled = canSend,
            ) {
                Icon(
                    ClaudenIcons.arrowUp,
                    contentDescription = "Отправить",
                    tint = if (canSend) colors.clay else colors.textTertiary,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить сессию?") },
            text = { Text("Копия репозитория и история сессии удалятся с сервера.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.remove(sessionId) }
                            .onSuccess { onBack() }
                            .onFailure { error = it.codeMessage }
                    }
                }) { Text("Удалить", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }

    if (showDevice) {
        DeviceDialog(
            folderName = folderName,
            browseName = browseName,
            info = device,
            busy = deviceBusy,
            note = deviceNote,
            onPick = { pickFolder.launch(null) },
            onPickBrowse = { pickBrowse.launch(null) },
            onUpload = {
                scope.launch {
                    deviceBusy = true
                    deviceNote = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val temp = File(context.cacheDir, "clauden-device-upload.zip")
                            val count = folder.zipTo(temp)
                            try {
                                if (count == 0) error("В папке нет файлов")
                                api.uploadDevice(sessionId, temp)
                            } finally {
                                temp.delete()
                            }
                        }
                    }.onSuccess {
                        device = it
                        deviceNote = "Отправлено файлов: ${it.files}"
                    }.onFailure { deviceNote = it.codeMessage }
                    deviceBusy = false
                }
            },
            onPull = {
                deviceBusy = true
                deviceNote = null
                pullChanges {
                    deviceNote = it
                    deviceBusy = false
                }
            },
            onDetach = {
                scope.launch {
                    deviceBusy = true
                    runCatching { api.detachDevice(sessionId) }
                        .onSuccess {
                            device = CodeDevice(attached = false)
                            deviceNote = "Папка отключена"
                        }
                        .onFailure { deviceNote = it.codeMessage }
                    deviceBusy = false
                }
            },
            onForget = {
                folder.clear()
                folderName = ""
                deviceNote = "Доступ к папке забыт"
            },
            onDismiss = { showDevice = false },
        )
    }

    writeJob?.let { pending ->
        val fs = pending.fs
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Записать файл на телефон?") },
            text = {
                Column {
                    Text(
                        fs?.path.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (fs?.content.orEmpty().take(300)) + if ((fs?.content?.length ?: 0) > 300) "…" else "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = colors.textSecondary,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val jobNow = pending
                    writeJob = null
                    scope.launch {
                        val json = runCatching {
                            withContext(Dispatchers.IO) {
                                folder.exec("write", fs?.path.orEmpty(), fs?.content.orEmpty())
                            }
                        }.getOrElse {
                            org.json.JSONObject().put("error", it.codeMessage).toString()
                        }
                        runCatching {
                            api.jobResult(sessionId, jobNow.id, CodeJobResult(stdout = json, code = 0))
                        }
                        runCatching { api.session(sessionId) }.onSuccess { session = it }
                    }
                }) { Text("Записать") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val jobNow = pending
                    writeJob = null
                    scope.launch {
                        runCatching {
                            api.jobResult(
                                sessionId,
                                jobNow.id,
                                CodeJobResult(rejected = true, error = "rejected"),
                            )
                        }
                        runCatching { api.session(sessionId) }.onSuccess { session = it }
                    }
                }) { Text("Отклонить", color = colors.danger) }
            },
        )
    }

    job?.let { pending ->
        TermuxJobDialog(
            job = pending,
            installed = termuxReady,
            busy = jobBusy,
            note = jobNote,
            playStore = TermuxBridge.fromPlayStore(context),
            onRun = {
                val permission = TermuxBridge.PERMISSION
                if (ContextCompat.checkSelfPermission(context, permission) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    termuxPermission.launch(permission)
                    jobNote = "Разрешите Clauden запускать команды в Termux и нажмите ещё раз"
                    return@TermuxJobDialog
                }
                val launch = TermuxBridge.run(
                    context,
                    pending.command,
                    (pending.timeout + 30) * 1000L,
                ) { result ->
                    scope.launch {
                        runCatching {
                            api.jobResult(
                                sessionId,
                                pending.id,
                                CodeJobResult(
                                    stdout = result.stdout,
                                    stderr = result.stderr,
                                    code = result.code,
                                    error = result.error,
                                ),
                            )
                        }
                        jobBusy = false
                        job = null
                        jobNote = null
                        reload()
                    }
                }
                if (launch.started) {
                    jobBusy = true
                    jobNote = "Команда ушла в Termux…"
                } else {
                    jobNote = launch.error.ifBlank {
                        "Termux не принял запрос. Проверьте allow-external-apps."
                    }
                }
            },
            onInstall = {
                runCatching { context.startActivity(TermuxBridge.installIntent()) }
                jobNote = "После установки вернитесь и нажмите «Проверить»"
            },
            onOpenTermux = { TermuxBridge.openTermux(context) },
            onCheck = {
                termuxReady = TermuxBridge.installed(context)
                jobNote = if (termuxReady) "Termux найден" else "Termux пока не установлен"
            },
            onReject = {
                scope.launch {
                    runCatching {
                        api.jobResult(
                            sessionId,
                            pending.id,
                            CodeJobResult(rejected = true, error = "rejected"),
                        )
                    }
                    job = null
                    jobBusy = false
                    reload()
                }
            },
        )
    }

    if (showPr) {
        PullDialog(
            defaultTitle = session?.title.orEmpty(),
            onDismiss = { showPr = false },
            onCreate = { title, body, done ->
                scope.launch {
                    runCatching { api.pullRequest(sessionId, title, body) }
                        .onSuccess { done(it, null) }
                        .onFailure { done(null, it.codeMessage) }
                }
            },
        )
    }

    if (showDiff && diff != null) {
        DiffDialog(
            diff = diff!!,
            onDismiss = { showDiff = false },
            onCommit = { message, done ->
                scope.launch {
                    runCatching { api.commit(sessionId, message) }
                        .onSuccess { done("Закоммичено: $it", false) }
                        .onFailure { done(it.codeMessage, true) }
                }
            },
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = LocalClaudenColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = colors.field, shape = RoundedCornerShape(14.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AgentBlock(message: CodeMessage) {
    val colors = LocalClaudenColors.current
    Column(Modifier.fillMaxWidth()) {
        message.steps.forEach { step -> StepRow(step) }
        if (message.steps.isNotEmpty()) Spacer(Modifier.height(6.dp))
        if (message.text.isNotBlank()) {
            MarkdownText(
                message.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )
        } else if (message.status == "working") {
            Text(
                "Думаю…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StepRow(step: CodeStep) {
    val colors = LocalClaudenColors.current
    val tint = when (step.status) {
        "failed" -> colors.danger
        "done" -> colors.textTertiary
        else -> colors.clay
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.status == "running") {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = colors.clay,
            )
        } else {
            Icon(
                if (step.status == "failed") ClaudenIcons.warning else ClaudenIcons.check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            step.title.ifBlank { step.name },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontFamily = if (step.name == "shell") FontFamily.Monospace else null,
            ),
            color = if (step.status == "failed") colors.danger else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DiffDialog(
    diff: CodeDiff,
    onDismiss: () -> Unit,
    onCommit: (String, (String, Boolean) -> Unit) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var message by rememberSaveable { mutableStateOf("Clauden: изменения") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.card,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Изменения",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Text(
                            if (diff.dirty) "${diff.files.size} файл(ов)" else "Изменений нет",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textTertiary,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                diff.files.take(6).forEach { file ->
                    Text(
                        "${file.status}  ${file.path}  +${file.additions} −${file.deletions}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                    )
                }
                HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 8.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        diff.patch.take(60_000).ifBlank { "(пусто)" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = colors.textPrimary,
                    )
                }
                HorizontalDivider(color = colors.border)
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = if (failed) colors.danger else colors.textSecondary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            busy = true
                            onCommit(message) { text, isError ->
                                busy = false
                                failed = isError
                                note = text
                            }
                        },
                        enabled = diff.dirty && !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.clay,
                            contentColor = Color.White,
                            disabledContainerColor = colors.buttonDisabled,
                            disabledContentColor = colors.buttonDisabledText,
                        ),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(if (busy) "Коммичу…" else "Закоммитить", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun CodeSession.appendDelta(piece: String): CodeSession {
    val list = messages.toMutableList()
    val index = list.indexOfLast { it.role == "assistant" }
    if (index >= 0) list[index] = list[index].copy(text = list[index].text + piece)
    return copy(messages = list)
}

private fun CodeSession.upsertStep(step: CodeStep): CodeSession {
    val list = messages.toMutableList()
    val index = list.indexOfLast { it.role == "assistant" }
    if (index >= 0) {
        val steps = list[index].steps.toMutableList()
        val at = steps.indexOfFirst { it.id == step.id }
        if (at >= 0) steps[at] = step else steps.add(step)
        list[index] = list[index].copy(steps = steps)
    }
    return copy(messages = list)
}

@Composable
private fun HintCard(text: String) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalClaudenColors.current.borderStrong,
    unfocusedBorderColor = LocalClaudenColors.current.border,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = LocalClaudenColors.current.clay,
    focusedTextColor = LocalClaudenColors.current.textPrimary,
    unfocusedTextColor = LocalClaudenColors.current.textPrimary,
)

internal fun runDeviceAction(context: Context, action: CodeAction) {
    when (action.type) {
        "notify" -> ClaudenReplyNotifications.device(
            context,
            action.title.ifBlank { "Clauden Code" },
            action.text,
        )
        "clipboard" -> {
            val manager = context.getSystemService(ClipboardManager::class.java)
            manager?.setPrimaryClip(ClipData.newPlainText("Clauden", action.text))
        }
        "open_url" -> {
            val uri = Uri.parse(action.url)
            require(uri.scheme == "http" || uri.scheme == "https") { "bad url" }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        "share" -> {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, action.text)
            }
            context.startActivity(
                Intent.createChooser(send, "Поделиться")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        else -> error("Неизвестное действие: ${action.type}")
    }
}

@Composable
private fun PullDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, (CodePull?, String?) -> Unit) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var title by rememberSaveable { mutableStateOf(defaultTitle.take(80)) }
    var body by rememberSaveable { mutableStateOf("Изменения из Clauden Code.") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pull by remember { mutableStateOf<CodePull?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pull == null) "Отправить и создать PR" else "PR создан") },
        text = {
            val created = pull
            Column {
                if (created == null) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        label = { Text("Заголовок") },
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        minLines = 2,
                        label = { Text("Описание") },
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = colors.danger, fontSize = 12.sp)
                    }
                } else {
                    Text(
                        "Ветка ${created.branch} → ${created.base}, PR #${created.number}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(created.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text(created.url, fontSize = 12.sp, color = colors.clay) }
                }
            }
        },
        confirmButton = {
            if (pull == null) {
                TextButton(
                    enabled = !busy && title.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        onCreate(title, body) { result, message ->
                            busy = false
                            error = message
                            pull = result
                        }
                    },
                ) { Text(if (busy) "Отправляю…" else "Создать PR") }
            } else {
                TextButton(onClick = onDismiss) { Text("Готово") }
            }
        },
        dismissButton = {
            if (pull == null) TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun DeviceDialog(
    folderName: String,
    browseName: String,
    info: CodeDevice?,
    busy: Boolean,
    note: String?,
    onPick: () -> Unit,
    onPickBrowse: () -> Unit,
    onUpload: () -> Unit,
    onPull: () -> Unit,
    onDetach: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Папка телефона") },
        text = {
            Column {
                Text(
                    "Выберите папку — приложение попросит доступ и скопирует её на сервер " +
                        "в _device. Агент сможет читать и править эти файлы, а кнопка " +
                        "«Забрать изменения» вернёт их обратно.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (folderName.isBlank()) "Папка не выбрана" else "Папка: $folderName",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textPrimary,
                )
                if (info?.attached == true) {
                    Text(
                        "На сервере: ${info.files} файл(ов), ${info.bytes / 1024} КБ",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                    )
                }
                note?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = colors.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (browseName.isBlank()) "Просмотр: папка не выбрана" else "Агент видит: $browseName",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
                TextButton(onClick = onPickBrowse, enabled = !busy) {
                    Text("Папка для просмотра (можно корень)", fontSize = 13.sp)
                }
                Text(
                    "Чтобы агент видел все папки, выберите «Внутренняя память». " +
                        "Android/data и obb всё равно закрыты системой.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onPick, enabled = !busy) { Text("Папка проекта") }
                if (folderName.isNotBlank()) {
                    TextButton(onClick = onUpload, enabled = !busy) {
                        Text(if (busy) "Работаю…" else "Отправить на сервер")
                    }
                    TextButton(onClick = onPull, enabled = !busy) { Text("Забрать изменения") }
                }
                if (info?.attached == true) {
                    TextButton(onClick = onDetach, enabled = !busy) {
                        Text("Отключить на сервере", color = colors.danger, fontSize = 12.sp)
                    }
                }
                if (folderName.isNotBlank()) {
                    TextButton(onClick = onForget, enabled = !busy) {
                        Text("Забыть папку", color = colors.textTertiary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
internal fun TermuxJobDialog(
    job: CodeJob,
    installed: Boolean,
    busy: Boolean,
    note: String?,
    playStore: Boolean = false,
    onRun: () -> Unit,
    onInstall: () -> Unit,
    onOpenTermux: () -> Unit,
    onCheck: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    var setup by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Команда на телефоне") },
        text = {
            Column {
                Text(
                    "Агент просит выполнить команду в Termux. Она пойдёт от вашего имени — " +
                        "проверьте, что понимаете её.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(color = colors.codeBg, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        job.command,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !installed -> "Termux не установлен"
                        playStore -> "Termux установлен из Play Store — RUN_COMMAND не работает"
                        else -> "Termux установлен"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (!installed || playStore) colors.danger else colors.textSecondary,
                )
                if (!installed || playStore || note != null) {
                    Text(
                        "Нужен Termux с F-Droid и allow-external-apps=true. " +
                            "Кнопка «Настройка» покажет точные шаги.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                    )
                }
                note?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = colors.textSecondary)
                }
            }
        },
        confirmButton = {
            if (installed) {
                TextButton(onClick = onRun, enabled = !busy) {
                    Text(if (busy) "Выполняю…" else "Выполнить")
                }
            } else {
                TextButton(onClick = onInstall) { Text("Скачать Termux") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { setup = true }) {
                    Text("Настройка", fontSize = 12.sp, color = colors.textTertiary)
                }
                TextButton(onClick = onCheck) {
                    Text("Проверить", fontSize = 12.sp, color = colors.textTertiary)
                }
                if (installed) {
                    TextButton(onClick = onOpenTermux) {
                        Text("Termux", fontSize = 12.sp, color = colors.textTertiary)
                    }
                }
                TextButton(onClick = onReject, enabled = !busy) {
                    Text("Отклонить", color = colors.danger, fontSize = 13.sp)
                }
            }
        },
    )

    if (setup) {
        TermuxSetupDialog(
            playStore = playStore,
            onInstall = onInstall,
            onOpenTermux = onOpenTermux,
            onClose = { setup = false },
        )
    }
}

@Composable
internal fun TermuxSetupDialog(
    playStore: Boolean,
    onInstall: () -> Unit,
    onOpenTermux: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Настройка Termux") },
        text = {
            Column {
                if (playStore) {
                    Text(
                        "Сейчас Termux установлен из Play Store. Эта версия не поддерживает " +
                            "RUN_COMMAND — удалите её и поставьте Termux с F-Droid.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.danger,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onInstall) { Text("Открыть F-Droid") }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "1. Откройте Termux (кнопка ниже). Termux:API для RUN_COMMAND не нужен.\n" +
                        "2. Выполните команду (она создаёт файл и сразу выставляет права):",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Surface(color = colors.codeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        TermuxBridge.SETUP_COMMAND,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(TermuxBridge.SETUP_COMMAND))
                }) { Text("Скопировать команду", fontSize = 12.sp) }
                Text(
                    "3. В выводе cat должна быть строка allow-external-apps=true и не должно быть " +
                        "Permission denied. Если файл не читается — эта же команда чинит права.\n" +
                        "4. Остановите Termux полностью: Настройки Android → Приложения → Termux → " +
                        "Остановить. Смахивания из недавних недостаточно — процесс остаётся жив.\n" +
                        "5. Откройте Termux снова, вернитесь в Clauden и нажмите «Выполнить».",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenTermux) { Text("Открыть Termux") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        },
    )
}

internal val Throwable.codeMessage: String
    get() {
        val raw = message.orEmpty()
        return when {
            this is java.net.SocketException || this is java.net.UnknownHostException ->
                "Связь с сервером прервалась. Проверьте интернет и повторите."
            raw.contains("abort", true) || raw.contains("reset", true) ||
                raw.contains("closed", true) || raw.contains("broken pipe", true) ||
                raw.contains("unexpected end", true) ->
                "Связь с сервером прервалась. Проверьте интернет и повторите."
            raw.contains("timeout", true) || raw.contains("timed out", true) ->
                "Сервер не ответил вовремя. Попробуйте ещё раз."
            raw.isBlank() -> "Что-то пошло не так. Попробуйте ещё раз."
            else -> raw
        }
    }

private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

private fun readImageAttachment(context: Context, uri: Uri): CodeAttachment? {
    return try {
        val resolver = context.contentResolver
        var name = "image"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri) ?: when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> ""
        }
        if (!mime.startsWith("image/")) return null
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_ATTACHMENT_BYTES) return null
        CodeAttachment(name = name, mime = mime, base64 = Base64.encodeToString(bytes, Base64.NO_WRAP))
    } catch (_: Exception) {
        null
    }
}
