package space.teleopen.claudencode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import space.teleopen.claudencode.BuildConfig
import space.teleopen.claudencode.data.AuthApi
import space.teleopen.claudencode.data.AuthExpiredException
import space.teleopen.claudencode.data.AuthStore
import space.teleopen.claudencode.data.AuthUser
import space.teleopen.claudencode.data.CodeAgent
import space.teleopen.claudencode.data.CodeApi
import space.teleopen.claudencode.data.CodeAttachment
import space.teleopen.claudencode.data.CodeCommand
import space.teleopen.claudencode.data.CodeDiff
import space.teleopen.claudencode.data.CodeException
import space.teleopen.claudencode.data.CodeFilesListing
import space.teleopen.claudencode.data.CodeModel
import space.teleopen.claudencode.data.CodePermission
import space.teleopen.claudencode.data.CodeRepo
import space.teleopen.claudencode.data.CodeJob
import space.teleopen.claudencode.data.CodeJobResult
import space.teleopen.claudencode.data.CodeSession
import space.teleopen.claudencode.data.SshConnectionHolder
import space.teleopen.claudencode.data.SshHost
import space.teleopen.claudencode.data.SshStore
import space.teleopen.claudencode.data.collectRemoteContext
import space.teleopen.claudencode.service.AgentRunnerService
import space.teleopen.claudencode.data.remoteFsExec
import space.teleopen.claudencode.data.CodeUsage
import space.teleopen.claudencode.data.CodeForm

private const val APP_VERSION = "v2.0.0"

private const val INIT_PROMPT =
    "Analyze this codebase and create or improve AGENTS.md with: project summary," +
        " build and test commands, code style rules, pitfalls. Keep it concise."

private val tuiJson = Json { ignoreUnknownKeys = true }

private enum class Gate { Boot, Login, Ready }

private sealed interface UiOverlay {
    data object None : UiOverlay
    data object Help : UiOverlay
    data object Shortcuts : UiOverlay
    data object Resume : UiOverlay
    data object Repositories : UiOverlay
    data object Models : UiOverlay
    data object Effort : UiOverlay
    data object Agents : UiOverlay
    data object Todos : UiOverlay
    data object Sessions : UiOverlay
    data object GithubToken : UiOverlay
    data object Github : UiOverlay
    data object HomeMenu : UiOverlay
    data object SessionMenu : UiOverlay
    data object ProjectInfo : UiOverlay
    data object SshHosts : UiOverlay
    data class SshEdit(val host: SshHost) : UiOverlay
    data class ShowDiff(val diff: CodeDiff) : UiOverlay
    data class Browse(val path: String) : UiOverlay
    data class ReadFile(val path: String, val content: String) : UiOverlay
}

@Composable
fun IdeApp(auth: AuthStore) {
    val scope = rememberCoroutineScope()
    val base = BuildConfig.CLAUDEN_BASE_URL
    var gate by remember { mutableStateOf(Gate.Boot) }
    var user by remember { mutableStateOf(auth.user()) }

    fun refreshUser() {
        if (!auth.signedIn) {
            user = AuthUser()
            gate = Gate.Login
            return
        }
        scope.launch {
            runCatching { AuthApi(base, auth).me(auth.token) }
                .onSuccess { returned ->
                    user = returned
                    auth.saveUser(returned)
                }
                .onFailure { err ->
                    if (err is AuthExpiredException) {
                        auth.clear()
                        user = AuthUser()
                    }
                }
            gate = if (auth.signedIn) Gate.Ready else Gate.Login
        }
    }

    LaunchedEffect(Unit) { refreshUser() }

    when (gate) {
        Gate.Boot -> BootScreen()
        Gate.Login -> LoginScreen(
            api = AuthApi(base, auth),
            onDone = {
                gate = Gate.Boot
                refreshUser()
            },
        )
        Gate.Ready -> CodeIde(auth = auth, base = base, user = user)
    }
}

@Composable
private fun BootScreen() {
    ScreenBackground {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClaudeMark(size = 92.dp)
            Spacer(Modifier.height(22.dp))
            Text("Claude Code", color = Pal.White, style = type(23.sp, FontWeight.Bold, 1.2f))
            Spacer(Modifier.height(8.dp))
            Text(
                "Подключение к Clauden…",
                color = Pal.Gray,
                style = type(13.sp, null, 1.3f),
            )
            Spacer(Modifier.height(24.dp))
            TypingDots()
        }
    }
}

@Composable
private fun PaywallScreen(user: AuthUser, onLogout: () -> Unit) {
    val context = LocalContext.current
    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            ClaudeMark(size = 78.dp)
            Spacer(Modifier.height(20.dp))
            Text("Claude Code", color = Pal.White, style = type(24.sp, FontWeight.Bold, 1.2f))
            Spacer(Modifier.height(6.dp))
            Text(
                "Агентный режим для Android",
                color = Pal.Gray,
                style = type(13.sp, null, 1.3f),
            )
            Spacer(Modifier.height(30.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClaudeMark(size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Code недоступен",
                        color = Pal.White,
                        style = type(16.sp, FontWeight.Bold, 1.25f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Claude Code работает на платных тарифах Clauden. " +
                        "Сейчас: ${planLabel(user)} · ${user.email}",
                    color = Pal.Gray,
                    style = type(12.5.sp, null, 1.5f),
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Открыть тарифы") {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://teleopen.space/clauden")),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Выйти из аккаунта", onLogout)
            Spacer(Modifier.height(36.dp))
            Text(
                "clauden.best",
                color = Pal.Faint,
                style = type(11.5.sp, null, 1.2f),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CodeIde(auth: AuthStore, base: String, user: AuthUser) {
    if (!user.codeAccess) {
        PaywallScreen(user = user, onLogout = {
            auth.clear()
            android.os.Process.killProcess(android.os.Process.myPid())
        })
        return
    }
    val api = remember { CodeApi(base, auth.token) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val hostContext = LocalContext.current

    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<CodeSession?>(null) }
    var sessions by remember { mutableStateOf<List<CodeSession>>(emptyList()) }
    var repos by remember { mutableStateOf<List<CodeRepo>>(emptyList()) }
    var ghRepos by remember { mutableStateOf<List<CodeRepo>>(emptyList()) }
    var github by remember { mutableStateOf<space.teleopen.claudencode.data.CodeGitHub?>(null) }
    var reposBusy by remember { mutableStateOf(false) }
    var ghTokenBusy by remember { mutableStateOf(false) }
    var ghTokenError by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<CodeModel>>(emptyList()) }
    var agentsList by remember { mutableStateOf<List<CodeAgent>>(emptyList()) }
    var serverCommands by remember { mutableStateOf<List<CodeCommand>>(emptyList()) }
    var usage by remember { mutableStateOf<CodeUsage?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busyLine by remember { mutableStateOf<String?>(null) }
    var queuedList by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingStart by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<UiOverlay>(UiOverlay.None) }
    var filesState by remember { mutableStateOf<Pair<String, CodeFilesListing>?>(null) }
    var draft by remember { mutableStateOf("") }
    var pendingImages by remember { mutableStateOf<List<CodeAttachment>>(emptyList()) }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(3),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> readCodeImage(hostContext, uri) }
                }
                if (loaded.isEmpty()) {
                    note = "Не удалось прочитать картинку"
                } else {
                    pendingImages = (pendingImages + loaded).take(3)
                }
            }
        }
    }
    val sshStore = remember { SshStore(hostContext) }
    var sshHosts by remember { mutableStateOf(sshStore.hosts()) }
    var sshActiveId by rememberSaveable { mutableStateOf<String?>(null) }
    var sshTabs by remember { mutableStateOf<List<String>>(emptyList()) }
    var sshPane by remember { mutableStateOf("term") }

    var sshIntent by remember { mutableStateOf("agent") }

    var sshAgentHostId by remember { mutableStateOf<String?>(null) }
    var shellRepo by remember { mutableStateOf<CodeRepo?>(null) }
    var homeMode by rememberSaveable { mutableStateOf("phone") }
    val uiPrefs = remember {
        hostContext.getSharedPreferences("claudencode_ui", Context.MODE_PRIVATE)
    }
    var favoriteIds by remember {
        mutableStateOf(uiPrefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    val focusRequester = remember { FocusRequester() }

    fun toggleFavorite(id: String) {
        if (id.isBlank()) return
        val next = if (id in favoriteIds) favoriteIds - id else favoriteIds + id
        favoriteIds = next
        uiPrefs.edit().putStringSet("favorites", next).apply()
    }

    LaunchedEffect(note) {
        val shown = note ?: return@LaunchedEffect
        delay(6500)
        if (note == shown) note = null
    }

    fun push(text: String?) { note = text }

    suspend fun fresh(id: String) {
        runCatching { api.session(id) }.onSuccess { loaded ->
            session = loaded.settle()
            if (loaded.source == "ssh") {
                sshHosts.firstOrNull { it.target == loaded.remote?.target }?.let {
                    sshAgentHostId = it.id
                    AgentRunnerService.start(hostContext, it.id, loaded.id)
                }
            }
            if (!loaded.working) {
                usage = runCatching { api.usage(id) }.getOrNull()
            }
        }
    }

    fun reloadSessions() { scope.launch { runCatching { api.sessions() }.onSuccess { sessions = it } } }
    fun reloadRepos() {
        reposBusy = true
        scope.launch {
            runCatching { api.repos() }.onSuccess { repos = it }
            runCatching { api.github() }.onSuccess { info ->
                github = info
                if (info.connected) {
                    runCatching { api.githubRepos() }.onSuccess { ghRepos = it }
                }
            }
            reposBusy = false
        }
    }
    fun reloadModels() { scope.launch { runCatching { api.models() }.onSuccess { models = it } } }

    LaunchedEffect(Unit) {
        reloadSessions()
        reloadRepos()
        reloadModels()
    }

    fun openSshPane(host: SshHost, pane: String) {
        sshTabs = (sshTabs + host.id).distinct()
        sshActiveId = host.id
        sshPane = pane
        overlay = UiOverlay.None
    }

    fun startSshAgent(host: SshHost, prompt: String = "") {
        if (host.host.isBlank() || host.user.isBlank()) {
            overlay = UiOverlay.SshEdit(host)
            push("Fill in host and user first")
            return
        }
        overlay = UiOverlay.None
        sshIntent = "agent"
        sshActiveId = null
        homeMode = "ssh"
        busyLine = "Connecting to ${host.target}…"
        scope.launch {
            try {
                val ssh = withContext(Dispatchers.IO) {
                    val client = SshConnectionHolder.get(host)
                    if (!client.connected) client.connect()
                    client
                }
                if (host.fingerprint.isBlank() && ssh.observedFingerprint.isNotBlank()) {
                    sshStore.updateFingerprint(host.id, ssh.observedFingerprint)
                    sshHosts = sshStore.hosts()
                }
                var existing = host.agentSession
                    .takeIf { it.isNotBlank() }
                    ?.let { saved -> runCatching { api.session(saved) }.getOrNull() }
                    ?.takeIf { it.source == "ssh" && it.remote?.target == host.target }
                val root = existing?.remote?.root?.takeIf { it.isNotBlank() }
                    ?: withContext(Dispatchers.IO) {
                        runCatching { ssh.pwd() }.getOrElse {
                            runCatching { ssh.exec("pwd", "", 10).stdout.trim() }.getOrDefault("/")
                        }
                    }
                val ready = existing ?: run {
                    busyLine = "Reading project context…"
                    val context = runCatching {
                        withContext(Dispatchers.IO) { collectRemoteContext(ssh, root) }
                    }.getOrNull()?.toJson().orEmpty()
                    api.createSsh(host.label, host.target, root, context).also { created ->
                        sshStore.updateAgentSession(host.id, created.id)
                        sshHosts = sshStore.hosts()
                    }
                }
                sshAgentHostId = host.id
                sessionId = ready.id
                session = ready.settle()
                AgentRunnerService.start(hostContext, host.id, ready.id)
                if (prompt.isNotBlank()) {
                    busyLine = "Отправляю задачу…"
                    runCatching { api.send(ready.id, prompt) }
                        .onSuccess { session = it.settleTail() }
                        .onFailure { push(it.message ?: "Не удалось отправить задачу") }
                }
                shellRepo = null
                queuedList = emptyList()
                note = null
                reloadSessions()
            } catch (err: Throwable) {
                push(err.message ?: "SSH connection failed")
            } finally {
                busyLine = null
            }
        }
    }

    fun quickSsh(pane: String) {
        sshIntent = pane
        val only = sshHosts.singleOrNull()
        if (only == null) {
            overlay = UiOverlay.SshHosts
            return
        }
        if (pane == "agent") startSshAgent(only) else openSshPane(only, pane)
    }

    fun agentHost(): SshHost? =
        sshHosts.firstOrNull { it.id == sshAgentHostId }
            ?: sshHosts.firstOrNull { it.target == session?.remote?.target }

    fun closeSshTab(id: String) {
        SshConnectionHolder.close(id)
        clearSshBuffer(id)
        sshTabs = sshTabs.filterNot { it == id }
        if (sshActiveId == id) {
            sshActiveId = sshTabs.lastOrNull()
            sshPane = "term"
        }
    }

    fun openSession(id: String) {
        session = null
        shellRepo = null
        sessionId = id
        overlay = UiOverlay.None
        note = null
        usage = null
        sessions.firstOrNull { it.id == id }?.let { entry ->
            homeMode = if (entry.source == "ssh") "ssh" else "phone"
            if (entry.source == "ssh") {
                sshHosts.firstOrNull { it.target == entry.remote?.target }?.let {
                    sshAgentHostId = it.id
                }
            }
        }
        scope.launch { fresh(id) }
        reloadSessions()
    }

    fun leaveSession(message: String?) {
        sessionId = null
        session = null
        shellRepo = null
        usage = null
        queuedList = emptyList()
        pendingImages = emptyList()
        push(message)
        reloadSessions()
    }

    fun enqueue(text: String) {
        queuedList = queuedList + text
    }

    fun interrupt() {
        val current = session ?: return
        queuedList = emptyList()
        scope.launch { runCatching { api.stop(current.id) }.onFailure { push(it.message) } }
    }

    fun startOn(repo: CodeRepo, prompt: String) {
        scope.launch {
            busyLine = "Setting up ${repo.name}…"
            try {
                val created = api.create(repo.id, prompt)
                session = created
                sessionId = created.id
                homeMode = "phone"
                sshAgentHostId = null
                shellRepo = null
                note = null
                reloadSessions()
            } catch (err: Throwable) {
                push(err.message ?: "Failed to start the session")
            } finally {
                busyLine = null
            }
        }
    }

    fun startConversation(text: String) {
        if (text.isBlank()) return
        pendingStart = text
        reloadRepos()
        overlay = UiOverlay.Repositories
    }

    fun chooseRepo(repo: CodeRepo) {
        val text = pendingStart ?: "Продолжим работу над проектом"
        pendingStart = null
        overlay = UiOverlay.None
        startOn(repo, text)
    }

    fun homeHost(): SshHost? =
        sshHosts.firstOrNull { it.id == sshAgentHostId } ?: sshHosts.firstOrNull()

    fun openProjectPicker() {
        if (homeMode == "ssh") {
            if (sshHosts.isEmpty()) {
                overlay = UiOverlay.SshEdit(SshHost())
            } else {
                sshIntent = "agent"
                overlay = UiOverlay.SshHosts
            }
            return
        }
        pendingStart = draft.ifBlank { "Продолжим работу над проектом" }
        reloadRepos()
        overlay = UiOverlay.Repositories
    }

    fun sendHome(body: String) {
        if (homeMode == "ssh") {
            val host = homeHost()
            if (host == null) {
                pendingStart = body
                if (sshHosts.isEmpty()) {
                    overlay = UiOverlay.SshEdit(SshHost())
                } else {
                    sshIntent = "agent"
                    overlay = UiOverlay.SshHosts
                }
            } else {
                startSshAgent(host, body)
            }
            return
        }
        val shell = shellRepo
        if (shell != null) startOn(shell, body) else startConversation(body)
    }

    fun newFromSession() {
        val current = session
        val seed = draft.ifBlank { "Продолжим работу над проектом" }
        val wasSsh = current?.source == "ssh"
        val host = agentHost()
        val repo = current?.let {
            CodeRepo(
                id = it.repo,
                name = it.repoName.ifBlank { it.repo },
                defaultBranch = it.branch,
            )
        }
        leaveSession(null)
        draft = ""
        if (wasSsh) {
            if (host != null) {
                startSshAgent(host, seed)
            } else {
                pendingStart = seed
                sshIntent = "agent"
                overlay = UiOverlay.SshHosts
            }
        } else if (repo != null && repo.id.isNotBlank() && repo.id != "ssh") {
            startOn(repo, seed)
        } else {
            startConversation(seed)
        }
    }

    fun deliverUser(text: String, images: List<CodeAttachment> = emptyList()) {
        val value = session
        val id = sessionId
        val body = text.trim()
        if (body.isEmpty() && images.isEmpty()) return
        if (value == null || id == null) {
            if (images.isNotEmpty()) {
                push("Сначала откройте сессию, чтобы приложить фото")
                return
            }
            val shell = shellRepo
            if (shell != null) {
                startOn(shell, body)
            } else {
                startConversation(body)
            }
            return
        }
        if (value.working) {
            enqueue(body.ifEmpty { "Посмотри вложение" })
            return
        }
        scope.launch {
            runCatching { api.send(id, body.ifEmpty { "Посмотри вложение и опиши, что на нём." }, images) }
                .onSuccess { session = it.settleTail() }
                .onFailure { push(it.message ?: "Failed to send") }
        }
    }

    fun answerPermission(perm: CodePermission, decision: String) {
        val value = session ?: return
        scope.launch {
            runCatching { api.answerPermission(value.id, perm.id, decision) }
                .onSuccess { session = it }
                .onFailure { push(it.message) }
        }
    }

    fun answerForm(form: CodeForm, answers: Map<String, String>) {
        val value = session ?: return
        scope.launch {
            runCatching { api.answerForm(value.id, form.id, answers) }
                .onSuccess { session = it }
                .onFailure { push(it.message) }
        }
    }

    fun routeCommand(raw: String) {
        draft = ""
        val head = raw.substringBefore(' ').trim().lowercase().removePrefix("/")
        val arg = raw.substringAfter(' ', "").trim()
        fun sureSession(): CodeSession? {
            val value = session
            val id = sessionId
            if (value == null || id == null) {
                push("Сначала откройте сессию — напишите задачу или /resume")
                return null
            }
            return value
        }

        when (head) {
            "" -> {}
            "help" -> overlay = UiOverlay.Help
            "shortcuts" -> overlay = UiOverlay.Shortcuts
            "sessions" -> overlay = UiOverlay.Sessions
            "project" -> {
                if (session == null) {
                    openProjectPicker()
                } else {
                    overlay = UiOverlay.ProjectInfo
                }
            }
            "github" -> {
                if (github?.connected == true) {
                    overlay = UiOverlay.Github
                } else {
                    ghTokenError = null
                    overlay = UiOverlay.GithubToken
                }
            }
            "resume" -> {
                if (sessions.isEmpty()) reloadSessions()
                overlay = UiOverlay.Resume
            }
            "clear" -> {
                val active = session
                if (active == null) {
                    push("Nothing to clear — you are already on a fresh context")
                } else {
                    shellRepo = CodeRepo(
                        id = active.repo,
                        name = active.repoName.ifBlank { active.repo },
                        defaultBranch = active.branch,
                    )
                    sessionId = null
                    session = null
                    usage = null
                    queuedList = emptyList()
                    push("Context cleared. Type a task to continue on the same repo.")
                }
            }
            "compact" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.compact(ctx.id) }
                        .onSuccess { push("✻ Compacting conversation…") }
                }
            }
            "usage" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.usage(ctx.id) }
                        .onSuccess { result ->
                            val tokens = result.tokens
                            push(
                                "Tokens: in ${tokens?.input ?: 0} · out ${tokens?.output ?: 0}" +
                                    " · cost \$${"%.2f".format(result.cost)}" +
                                    " · ${result.model.ifBlank { ctx.model }}",
                            )
                        }
                        .onFailure { push(it.message ?: "Usage unavailable") }
                }
            }
            "status" -> {
                val title = session?.let {
                    val repo = it.repoName.ifBlank { it.repo }
                    val branch = if (it.branch.isNotBlank()) "@${it.branch}" else ""
                    val eff = if (it.variant.isNotBlank()) "/${it.variant}" else ""
                    "$repo$branch · ${it.model}$eff"
                }.orEmpty()
                push("Clauden Code $APP_VERSION${
                    if (title.isNotBlank()) " · $title" else ""
                } · ${planLabel(user)} · ${user.email}")
            }
            "model" -> {
                if (models.isEmpty()) reloadModels()
                overlay = UiOverlay.Models
            }
            "effort" -> {
                if (session == null) {
                    push("Model effort applies inside a session")
                    return
                }
                if (models.isEmpty()) reloadModels()
                overlay = UiOverlay.Effort
            }
            "agents" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.agents(ctx.id) }.onSuccess { agentsList = it }
                    overlay = UiOverlay.Agents
                }
            }
            "mcp" -> push("No MCP servers configured — connect them from Clauden.")
            "rename" -> sureSession()?.let { ctx ->
                if (arg.isBlank()) {
                    push("Usage: /rename <new name>")
                } else {
                    scope.launch {
                        runCatching { api.rename(ctx.id, arg) }
                            .onSuccess {
                                session = it
                                push("Session renamed to: $arg")
                            }
                            .onFailure { push(it.message) }
                    }
                }
            }
            "init" -> {
                val active = session
                if (active != null) deliverUser(INIT_PROMPT) else startConversation(INIT_PROMPT)
            }
            "diff" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.diff(ctx.id) }
                        .onSuccess { overlay = UiOverlay.ShowDiff(it) }
                        .onFailure { push(it.message) }
                }
            }
            "files" -> if (sureSession() != null) {
                filesState = null
                overlay = UiOverlay.Browse("")
            }
            "commit" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.commit(ctx.id, arg) }
                        .onSuccess { hash ->
                            push(
                                if (hash.isBlank()) {
                                    "Nothing to commit"
                                } else {
                                    "Committed ${hash.take(8)} · ${arg.ifBlank { "session work" }}"
                                },
                            )
                            fresh(ctx.id)
                        }
                        .onFailure { push(it.message ?: "Commit failed") }
                }
            }
            "push" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.push(ctx.id) }
                        .onSuccess { branch ->
                            push(
                                if (branch.isBlank()) "Push failed — no token"
                                else "Pushed to $branch",
                            )
                        }
                        .onFailure { push(it.message ?: "Push failed") }
                }
            }
            "undo" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.undo(ctx.id) }
                        .onSuccess {
                            session = it
                            push("Undone the last turn")
                        }
                        .onFailure { push(it.message) }
                }
            }
            "redo" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.redo(ctx.id) }
                        .onSuccess {
                            session = it
                            push("Redo on")
                        }
                        .onFailure { push(it.message) }
                }
            }
            "export" -> sureSession()?.let { ctx ->
                scope.launch {
                    runCatching { api.exportMarkdown(ctx.id) }
                        .onSuccess { text ->
                            clipboard.setText(AnnotatedString(text))
                            push("Transcript copied to clipboard")
                        }
                        .onFailure { push(it.message) }
                }
            }
            "todos", "todo" -> overlay = UiOverlay.Todos
            "logout" -> {
                auth.clear()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            "upgrade" -> runCatching {
                hostContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://teleopen.space/clauden"),
                    ),
                )
            }
            else -> {
                val server = serverCommands.firstOrNull { it.name == "/$head" }
                val value = session
                val id = sessionId
                if (server != null && value != null && id != null) {
                    scope.launch {
                        runCatching { api.runCommand(id, server.name, arg) }
                            .onSuccess { session = it }
                            .onFailure { push(it.message) }
                    }
                    return
                }
                deliverUser(raw)
            }
        }
    }

    fun sendText(text: String, images: List<CodeAttachment> = emptyList()) {
        val body = text.trim()
        if (body.isEmpty() && images.isEmpty()) return
        if (body.startsWith("/")) {
            routeCommand(body)
            return
        }
        val current = session
        if (current == null) sendHome(body) else deliverUser(body, images)
        if (images.isNotEmpty()) pendingImages = emptyList()
        draft = ""
    }

    LaunchedEffect(sessionId) {
        val id = sessionId ?: return@LaunchedEffect
        while (isActive) {
            try {
                api.stream(id, onEvent = { type, data ->
                    session = applyEvent(session, type, data)
                })
                delay(1200)
                fresh(id)
            } catch (_: CancellationException) {
                break
            } catch (err: Throwable) {
                if (err is CodeException && err.status in listOf(401, 403, 404)) {
                    push(err.message)
                    break
                }
                delay(2500)
                fresh(id)
            }
        }
    }

    LaunchedEffect(session?.status) {
        val statusRunning = session?.working == true
        if (!statusRunning && queuedList.isNotEmpty()) {
            val id = sessionId ?: return@LaunchedEffect
            val next = queuedList.first()
            queuedList = queuedList.drop(1)
            scope.launch {
                runCatching { api.send(id, next) }
                    .onSuccess { session = it.settleTail() }
                    .onFailure { push(it.message ?: "Queued message failed") }
            }
        }
    }

    LaunchedEffect(session?.id) {
        val id = session?.id ?: run {
            serverCommands = emptyList()
            return@LaunchedEffect
        }
        runCatching { api.commands(id) }.onSuccess { serverCommands = it }
    }

    val promptCommands = buildPromptCommands(
        working = session?.working == true,
        hasSession = session != null,
        serverCommands = serverCommands,
    ) { command -> routeCommand(command) }

    BackHandler(
        enabled = overlay != UiOverlay.None ||
            sshActiveId != null ||
            session != null ||
            shellRepo != null ||
            homeMode == "ssh",
    ) {
        when {
            overlay is UiOverlay.SshEdit -> overlay = UiOverlay.SshHosts
            overlay != UiOverlay.None -> overlay = UiOverlay.None
            sshActiveId != null -> {
                if (sshPane == "sftp") sshPane = "term" else sshActiveId = null
            }
            session != null -> leaveSession(null)
            homeMode == "ssh" -> homeMode = "phone"
        }
    }

    val current = session
    val phoneSessions = sessions.filter { it.source != "ssh" }
    val sshSessions = sessions.filter { it.source == "ssh" }
    val recentPhone = phoneSessions.maxByOrNull { it.updatedAt }
    val phoneRepo = shellRepo ?: recentPhone?.let { entry ->
        CodeRepo(
            id = entry.repo,
            name = entry.repoName.ifBlank { entry.repo },
            defaultBranch = entry.branch,
        )
    }
    val sshHomeHost = homeHost()
    val recentSsh = sshSessions.maxByOrNull { it.updatedAt }
    val homeProject = when {
        homeMode == "ssh" && sshHomeHost == null && recentSsh == null -> ProjectUi(
            title = "Добавить SSH-сервер",
            label = "Удалённый проект",
            configured = false,
        )
        homeMode == "ssh" -> ProjectUi(
            title = sshHomeHost?.label?.ifBlank { sshHomeHost?.target }
                ?: recentSsh?.remote?.label?.ifBlank { "SSH-сервер" }
                ?: "SSH-сервер",
            label = "SSH-проект",
            branch = "main",
            state = "Готов",
        )
        phoneRepo == null -> ProjectUi(
            title = "Выберите проект",
            label = "Текущий проект",
            configured = false,
        )
        else -> ProjectUi(
            title = phoneRepo.name.ifBlank { phoneRepo.id },
            label = "Текущий проект",
            branch = phoneRepo.defaultBranch.ifBlank { "main" },
            state = "Готов",
        )
    }
    val homeSessions = sessions
        .sortedWith(
            compareByDescending<CodeSession> { it.id in favoriteIds }
                .thenByDescending { it.updatedAt },
        )
    val homeWorking = current?.working == true
    val statusTitle = when {
        homeWorking -> "Работаю…"
        homeProject.configured -> "Готов к работе!"
        else -> "Начнём!"
    }
    val statusSubtitle = when {
        homeWorking -> current?.steps?.lastOrNull { it.status == "running" }?.title
            ?.take(64) ?: "Claude Code выполняет команды…"
        homeProject.configured -> "Claude Code ждёт команды.."
        homeMode == "ssh" -> "Подключите сервер по SSH, чтобы начать."
        else -> "Выберите проект, чтобы начать."
    }
    fun modelLabelFor(entry: CodeSession): String {
        val known = models.firstOrNull {
            it.key == entry.model || entry.model.endsWith("/${it.id}")
        }?.name
        return known ?: shortModelLabel(entry.model)
    }

    fun cardModelLabel(entry: CodeSession): String =
        modelLabelFor(entry).removePrefix("Claude ")

    fun openGithub() {
        if (github?.connected == true) {
            overlay = UiOverlay.Github
        } else {
            ghTokenError = null
            overlay = UiOverlay.GithubToken
        }
    }

    fun onStatusTap() {
        if (homeMode == "ssh") {
            val last = sshSessions.maxByOrNull { it.updatedAt }
            val host = sshHomeHost
            when {
                last != null -> openSession(last.id)
                host != null -> startSshAgent(host)
                else -> overlay = UiOverlay.SshEdit(SshHost())
            }
        } else {
            val last = phoneSessions.maxByOrNull { it.updatedAt }
            when {
                last != null -> openSession(last.id)
                phoneRepo != null -> startOn(
                    phoneRepo,
                    draft.ifBlank { "Продолжим работу над проектом" },
                )
                else -> openProjectPicker()
            }
        }
    }

    val sshHostActive = sshHosts.firstOrNull { it.id == sshActiveId }
    if (sshHostActive != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Pal.Bg)
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            SshWorkspace(
                hosts = sshHosts.filter { it.id in sshTabs },
                host = sshHostActive,
                pane = sshPane,
                store = sshStore,
                onPane = { sshPane = it },
                onSelect = { sshActiveId = it },
                onCloseTab = ::closeSshTab,
                onNewHost = { overlay = UiOverlay.SshEdit(SshHost()) },
                onHosts = { overlay = UiOverlay.SshHosts },
                onLeave = { sshActiveId = null },
                onSetRoot = { path ->
                    val id = sessionId
                    val host = agentHost()
                    if (id != null && host != null) {
                        scope.launch {
                            busyLine = "Updating project root…"
                            try {
                                val context = withContext(Dispatchers.IO) {
                                    val client = SshConnectionHolder.get(host)
                                    if (!client.connected) client.connect()
                                    collectRemoteContext(client, path).toJson()
                                }
                                api.updateRemote(id, path, context)
                                session = session?.copy(
                                    remote = session?.remote?.copy(root = path),
                                )
                                push("Project root: $path")
                            } catch (err: Throwable) {
                                push(err.message ?: "Cannot set project root")
                            } finally {
                                busyLine = null
                            }
                        }
                    }
                },
            )
        }
    } else if (current == null) {
        HomeScreen(
            mode = homeMode,
            onMode = { homeMode = it },
            project = homeProject,
            statusTitle = statusTitle,
            statusSubtitle = statusSubtitle,
            working = homeWorking,
            sessions = homeSessions,
            usage = usage,
            modelLabel = ::cardModelLabel,
            draft = draft,
            onDraft = { draft = it },
            onSend = { sendText(draft) },
            commands = promptCommands,
            note = note,
            onNoteClose = { note = null },
            onNew = ::openProjectPicker,
            onMenu = { overlay = UiOverlay.HomeMenu },
            onProject = ::openProjectPicker,
            onStatus = ::onStatusTap,
            onAllSessions = { overlay = UiOverlay.Sessions },
            onOpenSession = ::openSession,
            onHelp = { overlay = UiOverlay.Help },
            onGithub = ::openGithub,
            onSessions = { overlay = UiOverlay.Sessions },
        )
    } else {
        SessionScreen(
            session = current,
            usage = usage,
            note = note,
            queued = queuedList,
            draft = draft,
            commands = promptCommands,
            userName = displayName(user),
            favorite = current.id in favoriteIds,
            onDraft = { draft = it },
            onSend = { sendText(draft, pendingImages) },
            onBack = { leaveSession(null) },
            onTodos = { overlay = UiOverlay.Todos },
            onProject = { overlay = UiOverlay.ProjectInfo },
            onModel = { routeCommand("/model") },
            onSessions = { overlay = UiOverlay.Sessions },
            onNew = ::newFromSession,
            onFavorite = { toggleFavorite(current.id) },
            onMenu = { overlay = UiOverlay.SessionMenu },
            onHelp = { overlay = UiOverlay.Help },
            onGithub = ::openGithub,
            onNoteClose = { note = null },
            onAnswerPermission = ::answerPermission,
            onAnswerForm = ::answerForm,
            attachments = pendingImages,
            onAttach = {
                pickImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemoveAttachment = { image -> pendingImages = pendingImages - image },
        )
    }

    if (busyLine != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC050505)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 26.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClaudeMark(size = 54.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    busyLine.orEmpty(),
                    color = Pal.White,
                    style = type(13.sp, null, 1.4f),
                )
                Spacer(Modifier.height(14.dp))
                TypingDots()
            }
        }
    }

    when (val state = overlay) {
        UiOverlay.None -> {}
        is UiOverlay.Help -> HelpOverlay(
            commands = baseHelpList() + serverCommands.map { it.name to it.description },
            onClose = { overlay = UiOverlay.None },
        )
        UiOverlay.Shortcuts -> ShortcutsOverlay(
            onClose = { overlay = UiOverlay.None },
        )
        UiOverlay.Resume -> SelectOverlay(
            header = "Продолжить сессию",
            options = sessions.take(9).map { entry ->
                SelectOption(
                    title = entry.title.ifBlank { "Новая сессия" },
                    subtitle = "${entry.repoName.ifBlank { entry.repo }} · ${agoRu(entry.updatedAt)}",
                    onClick = { openSession(entry.id) },
                )
            },
            onCancel = { overlay = UiOverlay.None },
        )
        UiOverlay.Repositories -> SelectOverlay(
            header = when {
                reposBusy -> "Загружаем репозитории…"
                repos.isEmpty() && ghRepos.isEmpty() -> "Пока нет репозиториев"
                else -> "Выберите репозиторий"
            },
            options = buildList {
                repos.take(9).forEach { repo ->
                    add(
                        SelectOption(
                            title = repo.name,
                            subtitle = repo.defaultBranch.ifBlank { "server" },
                            onClick = { chooseRepo(repo) },
                        ),
                    )
                }
                ghRepos.take(9).forEach { repo ->
                    add(
                        SelectOption(
                            title = repo.name,
                            subtitle = "github · ${repo.defaultBranch.ifBlank { "main" }}",
                            onClick = { chooseRepo(repo) },
                        ),
                    )
                }
                if (github?.connected != true) {
                    add(
                        SelectOption(
                            "Подключить GitHub…",
                            subtitle = "личный токен",
                            onClick = {
                                ghTokenError = null
                                overlay = UiOverlay.GithubToken
                            },
                        ),
                    )
                } else {
                    add(
                        SelectOption(
                            "Обновить репозитории",
                            subtitle = github?.login.orEmpty(),
                            onClick = { reloadRepos() },
                        ),
                    )
                }
                if (repos.isEmpty() && ghRepos.isEmpty() && github?.connected == true) {
                    add(
                        SelectOption(
                            "В этом аккаунте GitHub нет репозиториев",
                            subtitle = "создайте на github.com",
                            onClick = { reloadRepos() },
                        ),
                    )
                }
            },
            onCancel = {
                pendingStart = null
                overlay = UiOverlay.None
            },
        )
        UiOverlay.SshHosts -> SshHostsSheet(
            hosts = sshHosts,
            mode = sshIntent,
            onAdd = {
                overlay = UiOverlay.SshEdit(SshHost())
            },
            onEdit = { host ->
                overlay = UiOverlay.SshEdit(host)
            },
            onOpen = { host ->
                if (sshIntent == "agent") {
                    val prompt = pendingStart.orEmpty()
                    pendingStart = null
                    startSshAgent(host, prompt)
                } else {
                    openSshPane(host, sshIntent)
                }
            },
            onSftp = { host -> openSshPane(host, "sftp") },
            onClose = { overlay = UiOverlay.None },
            onLeave = {
                overlay = UiOverlay.None
                sshActiveId = null
            },
        )
        is UiOverlay.SshEdit -> SshHostEditor(
            host = state.host,
            onSave = { host ->
                sshStore.save(host)
                sshHosts = sshStore.hosts()
                if (host.id.isNotBlank()) {

                    SshConnectionHolder.close(host.id)
                    clearSshBuffer(host.id)
                    sshTabs = sshTabs.filterNot { it == host.id }
                    if (sshActiveId == host.id) sshActiveId = sshTabs.lastOrNull()
                }
                overlay = UiOverlay.SshHosts
            },
            onDelete = { host ->
                sshStore.remove(host.id)
                sshHosts = sshStore.hosts()
                sshTabs = sshTabs.filterNot { it == host.id }
                SshConnectionHolder.close(host.id)
                clearSshBuffer(host.id)
                if (sshActiveId == host.id) sshActiveId = sshTabs.lastOrNull()
                overlay = UiOverlay.SshHosts
            },
            onClose = { overlay = UiOverlay.None },
        )
        UiOverlay.GithubToken -> GithubTokenSheet(
            busy = ghTokenBusy,
            error = ghTokenError,
            onConnect = { token ->
                if (!ghTokenBusy) {
                    ghTokenBusy = true
                    ghTokenError = null
                    scope.launch {
                        runCatching { api.githubConnect(token) }
                            .onSuccess { info ->
                                github = info
                                runCatching { api.githubRepos() }
                                    .onSuccess { ghRepos = it }
                                overlay = UiOverlay.Repositories
                            }
                            .onFailure { ghTokenError = it.message ?: "GitHub rejected the token" }
                        ghTokenBusy = false
                    }
                }
            },
            onClose = { overlay = UiOverlay.Repositories },
        )
        UiOverlay.HomeMenu -> ActionSheet(
            title = "Claude Code",
            subtitle = "",
            onClose = { overlay = UiOverlay.None },
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClaudeMark(size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.email.ifBlank { "Аккаунт Clauden" },
                            color = Pal.PeriSoft,
                            style = type(12.5.sp, null, 1.3f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            planLabel(user),
                            color = Pal.Dim,
                            style = type(11.sp, null, 1.3f),
                        )
                    }
                }
            },
            actions = listOf(
                SheetAction(
                    "Новая сессия",
                    if (homeMode == "ssh") "На SSH-сервере" else "В новом проекте",
                    SheetIcon.New,
                ) {
                    overlay = UiOverlay.None
                    openProjectPicker()
                },
                SheetAction(
                    "Все сессии",
                    "${sessions.size} всего",
                    SheetIcon.Sessions,
                ) {
                    overlay = UiOverlay.Sessions
                },
                SheetAction(
                    "GitHub",
                    if (github?.connected == true) github?.login.orEmpty() else "Не подключён",
                    SheetIcon.Github,
                ) {
                    overlay = UiOverlay.None
                    openGithub()
                },
                SheetAction("Справка", "Команды и горячие клавиши", SheetIcon.Help) {
                    overlay = UiOverlay.Help
                },
                SheetAction("Выйти", "Завершить сеанс", SheetIcon.Logout, danger = true) {
                    auth.clear()
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
            ),
        )
        UiOverlay.SessionMenu -> {
            val active = session
            if (active == null) {
                overlay = UiOverlay.None
            } else {
                val host = agentHost()
                ActionSheet(
                    title = "Сессия",
                    subtitle = active.title.ifBlank {
                        active.repoName.ifBlank { active.remote?.label ?: "Claude Code" }
                    },
                    onClose = { overlay = UiOverlay.None },
                    actions = buildList {
                        add(
                            SheetAction(
                                "Модель",
                                modelLabelFor(active),
                                SheetIcon.Model,
                            ) {
                                overlay = UiOverlay.None
                                routeCommand("/model")
                            },
                        )
                        add(
                            SheetAction("Уровень усилий", active.variant.ifBlank { "по умолчанию" }, SheetIcon.Effort) {
                                overlay = UiOverlay.None
                                routeCommand("/effort")
                            },
                        )
                        add(
                            SheetAction("Изменения", "Git diff", SheetIcon.Diff) {
                                overlay = UiOverlay.None
                                routeCommand("/diff")
                            },
                        )
                        add(
                            SheetAction("Файлы", "Рабочая папка", SheetIcon.Files) {
                                overlay = UiOverlay.None
                                routeCommand("/files")
                            },
                        )
                        add(
                            SheetAction("Задачи", "${active.todos.size}", SheetIcon.Tasks) {
                                overlay = UiOverlay.None
                                routeCommand("/todos")
                            },
                        )
                        add(
                            SheetAction("Использование", "Токены и стоимость", SheetIcon.Usage) {
                                overlay = UiOverlay.None
                                routeCommand("/usage")
                            },
                        )
                        if (active.source == "ssh" && host != null) {
                            add(
                                SheetAction("Терминал", host.target, SheetIcon.Terminal) {
                                    overlay = UiOverlay.None
                                    openSshPane(host, "term")
                                },
                            )
                            add(
                                SheetAction("SFTP", "Файлы на сервере", SheetIcon.Files) {
                                    overlay = UiOverlay.None
                                    openSshPane(host, "sftp")
                                },
                            )
                        }
                        if (active.working) {
                            add(
                                SheetAction("Остановить", "Прервать текущий ход", SheetIcon.Stop, danger = true) {
                                    overlay = UiOverlay.None
                                    interrupt()
                                },
                            )
                        }
                        add(
                            SheetAction("Новая сессия", "На этом проекте", SheetIcon.New) {
                                overlay = UiOverlay.None
                                newFromSession()
                            },
                        )
                        add(
                            SheetAction("Выйти из сессии", "Вернуться на главную", SheetIcon.Leave) {
                                overlay = UiOverlay.None
                                leaveSession(null)
                            },
                        )
                    },
                )
            }
        }
        UiOverlay.ProjectInfo -> {
            val active = session
            if (active == null) {
                overlay = UiOverlay.None
            } else {
                val host = agentHost()
                ProjectInfoSheet(
                    session = active,
                    modelName = modelLabelFor(active),
                    onFiles = {
                        overlay = UiOverlay.None
                        routeCommand("/files")
                    },
                    onDiff = {
                        overlay = UiOverlay.None
                        routeCommand("/diff")
                    },
                    onTodos = {
                        overlay = UiOverlay.None
                        routeCommand("/todos")
                    },
                    onModel = {
                        overlay = UiOverlay.None
                        routeCommand("/model")
                    },
                    onTerminal = if (active.source == "ssh" && host != null) {
                        {
                            overlay = UiOverlay.None
                            openSshPane(host, "term")
                        }
                    } else {
                        null
                    },
                    onNew = {
                        overlay = UiOverlay.None
                        newFromSession()
                    },
                    onClose = { overlay = UiOverlay.None },
                )
            }
        }
        UiOverlay.Github -> ActionSheet(
            title = "GitHub",
            subtitle = if (github?.connected == true) {
                "Подключён как ${github?.login.orEmpty()}"
            } else {
                "Аккаунт не подключён"
            },
            onClose = { overlay = UiOverlay.None },
            actions = if (github?.connected == true) {
                listOf(
                    SheetAction("Обновить репозитории", "Загрузить список с GitHub", SheetIcon.Github) {
                        reloadRepos()
                        overlay = UiOverlay.Repositories
                    },
                    SheetAction("Отключить GitHub", "Удалить сохранённый токен", SheetIcon.Logout, danger = true) {
                        scope.launch {
                            runCatching { api.githubDisconnect() }
                                .onSuccess {
                                    github = null
                                    ghRepos = emptyList()
                                    push("GitHub отключён")
                                }
                                .onFailure { push(it.message ?: "Не удалось отключить GitHub") }
                        }
                        overlay = UiOverlay.None
                    },
                )
            } else {
                listOf(
                    SheetAction("Подключить по токену", "Personal access token", SheetIcon.Github) {
                        ghTokenError = null
                        overlay = UiOverlay.GithubToken
                    },
                )
            },
        )
        UiOverlay.Models -> SelectOverlay(
            header = "Выберите модель",
            options = models.take(9).ifEmpty {
                listOf(
                    CodeModel(
                        id = "claude-sonnet-5-5",
                        providerID = "clauden",
                        name = "Claude Sonnet 5.5",
                    ),
                )
            }.map { model ->
                SelectOption(
                    title = model.name,
                    subtitle = model.variants.joinToString("/"),
                    onClick = {
                        val id = session?.id
                        if (id == null) {
                            push("Model applies when the session starts: ${model.name}")
                        } else {
                            scope.launch {
                                runCatching { api.setModel(id, model.key, "") }
                                    .onSuccess { change ->
                                        push("Model: ${model.name}${if (change.variant.isNotBlank()) " · ${change.variant}" else ""}")
                                        fresh(id)
                                    }
                                    .onFailure { push(it.message) }
                            }
                        }
                        overlay = UiOverlay.None
                    },
                )
            },
            onCancel = { overlay = UiOverlay.None },
        )
        UiOverlay.Effort -> {
            val activeModel = models.firstOrNull {
                it.key == session?.model || session?.model?.endsWith("/${it.id}") == true
            }
            val variants = activeModel?.variants ?: listOf("low", "medium", "high")
            SelectOverlay(
                header = "Уровень усилий",
                options = variants.map { variant ->
                    SelectOption(title = variant, onClick = {
                        val id = session?.id
                        val key = activeModel?.key ?: session?.model
                        if (id != null && key != null) {
                            scope.launch {
                                runCatching { api.setModel(id, key, variant) }
                                    .onSuccess { fresh(id) }
                                    .onFailure { push(it.message) }
                            }
                        }
                        overlay = UiOverlay.None
                    })
                },
                onCancel = { overlay = UiOverlay.None },
            )
        }
        UiOverlay.Agents -> SelectOverlay(
            header = "Выберите агента",
            options = agentsList.take(9).map { agent ->
                SelectOption(
                    title = agent.name,
                    subtitle = agent.description,
                    onClick = {
                        val id = session?.id
                        if (id != null) {
                            scope.launch {
                                runCatching { api.setAgent(id, agent.id) }
                                    .onSuccess { push("Agent: ${agent.name}") }
                                    .onFailure { push(it.message) }
                            }
                        }
                        overlay = UiOverlay.None
                    },
                )
            },
            onCancel = { overlay = UiOverlay.None },
        )
        UiOverlay.Todos -> TodosPanel(
            todos = session?.todos.orEmpty(),
            onClose = { overlay = UiOverlay.None },
        )
        UiOverlay.Sessions -> SessionsSheet(
            activeId = sessionId,
            sessions = sessions.sortedWith(
                compareByDescending<CodeSession> { it.id in favoriteIds }
                    .thenByDescending { it.updatedAt },
            ),
            modelLabel = ::modelLabelFor,
            onOpen = {
                overlay = UiOverlay.None
                openSession(it)
            },
            onNew = {
                overlay = UiOverlay.None
                if (session != null) {
                    newFromSession()
                } else {
                    pendingStart = draft.ifBlank { "Продолжим работу над проектом" }
                    draft = ""
                    openProjectPicker()
                }
            },
            onClose = { overlay = UiOverlay.None },
        )
        is UiOverlay.ShowDiff ->
            DiffOverlay(state.diff, onClose = { overlay = UiOverlay.None })
        is UiOverlay.Browse -> {
            val listing = filesState
            FilesOverlay(
                listing = listing?.second ?: CodeFilesListing(path = state.path),
                onTapFile = { file ->
                    val id = session?.id
                    if (id != null) {
                        scope.launch {
                            runCatching { api.fileRead(id, file) }
                                .onSuccess { loaded ->
                                    overlay = UiOverlay.ReadFile(loaded.path, loaded.content)
                                }
                        }
                    }
                },
                onTapDir = { dir -> overlay = UiOverlay.Browse(dir) },
                onParent = {
                    val parent = listing?.second?.parent
                    overlay = UiOverlay.Browse(parent ?: "")
                },
                onClose = { overlay = UiOverlay.None },
            )
        }
        is UiOverlay.ReadFile ->
            FileOverlay(state.path, state.content, onClose = { overlay = UiOverlay.None })
    }

    LaunchedEffect(overlay) {
        val browse = overlay as? UiOverlay.Browse ?: return@LaunchedEffect
        val id = session?.id ?: return@LaunchedEffect
        if (filesState?.first != browse.path) {
            runCatching { api.files(id, browse.path) }
                .onSuccess { listing -> filesState = browse.path to listing }
                .onFailure { push(it.message) }
        }
    }
}

private fun applyEvent(
    session: CodeSession?,
    type: String,
    data: JsonElement,
): CodeSession? = when (type) {
    "snapshot" -> runCatching {
        val loaded = tuiJson.decodeFromJsonElement<CodeSession>(data)
        if (!loaded.working) loaded.settle() else loaded
    }.getOrNull() ?: session
    "delta" -> {
        val piece = runCatching {
            data.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (piece != null) session?.appendDelta(piece) else session
    }
    "step" -> runCatching {
        val step = tuiJson.decodeFromJsonElement<space.teleopen.claudencode.data.CodeStep>(data)
        session?.upsertStep(step)
    }.getOrNull() ?: session
    "todos" -> runCatching {
        val list = tuiJson.decodeFromJsonElement<List<space.teleopen.claudencode.data.CodeTodo>>(data)
        session?.copy(todos = list)
    }.getOrNull() ?: session
    "forms" -> runCatching {
        val list = tuiJson.decodeFromJsonElement<List<CodeForm>>(data)
        session?.copy(forms = list)
    }.getOrNull() ?: session
    "permissions" -> runCatching {
        val list = tuiJson.decodeFromJsonElement<List<CodePermission>>(data)
        session?.copy(permissions = list)
    }.getOrNull() ?: session
    "job" -> runCatching {
        tuiJson.decodeFromJsonElement<CodeJob>(data)
    }.getOrNull()?.let { job ->
        session?.copy(
            jobs = session.jobs.filterNot { it.id == job.id } + job,
        )
    } ?: session
    "status" -> {
        val status = runCatching {
            data.jsonObject["status"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val error = runCatching {
            data.jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (status != null) session?.copy(status = status, error = error.orEmpty()) else session
    }
    else -> session
}

private fun CodeSession.settleTail(): CodeSession = settle()

private fun buildPromptCommands(
    working: Boolean,
    hasSession: Boolean,
    serverCommands: List<CodeCommand>,
    run: (String) -> Unit,
): List<PromptCommand> {
    val out = mutableListOf<PromptCommand>()
    fun add(name: String, description: String) = out.add(PromptCommand(name, description) { run(name) })
    add("/help", "Show available commands")
    add("/resume", "Pick a past session")
    if (hasSession) {
        add("/clear", "Leave this session, start fresh")
        add("/model", "Switch model")
        add("/effort", "Set effort level (low…max)")
        add("/diff", "Show current changes")
        add("/files", "Browse workspace files")
        add("/commit <msg>", "Commit the branch")
        add("/push", "Push the branch")
        add("/compact", "Collapse the context")
        add("/usage", "Tokens and cost")
        add("/rename <name>", "Rename the session")
        add("/undo", "Take back the last turn")
        add("/export", "Copy transcript as markdown")
        add("/todos", "Show the todolist")
        serverCommands.forEach { add(it.name, it.description) }
    } else {
        add("/init", "Analyze a repo and write AGENTS.md")
    }
    add("/status", "Plan, model, account")
    add("/mcp", "MCP servers")
    add("/logout", "Sign out of Clauden")
    return out
}

private fun baseHelpList(): List<Pair<String, String>> = listOf(
    "/help" to "Show available commands",
    "/resume" to "Resume a past session",
    "/init" to "Analyze the project, create AGENTS.md",
    "/model" to "Switch model",
    "/effort" to "Effort: low / medium / high",
    "/clear" to "Fresh session on this tab",
    "/compact" to "Collapse the context",
    "/usage" to "Tokens and cost of this session",
    "/status" to "Plan, model, account",
    "/rename <name>" to "Rename the session",
    "/diff" to "Show current changes",
    "/files" to "Browse workspace files",
    "/commit <msg>" to "Commit changes on the branch",
    "/push" to "Push the branch",
    "/undo" to "Take back the last turn",
    "/export" to "Copy the transcript",
    "/todos" to "Todolist panel",
    "/mcp" to "MCP servers",
    "/logout" to "Sign out",
)

private fun defaultModelLabel(models: List<CodeModel>, current: CodeSession?): String {
    val key = current?.model
    val active = models.firstOrNull { key == null || it.key == key || key.endsWith("/${it.id}") }
    val name = (active?.name ?: key?.substringAfter('/') ?: "Claude Sonnet 5.5")
    return name
}

private const val MAX_CODE_IMAGE_BYTES = 5 * 1024 * 1024

private fun readCodeImage(context: Context, uri: Uri): CodeAttachment? {
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
        if (bytes.isEmpty() || bytes.size > MAX_CODE_IMAGE_BYTES) return null
        CodeAttachment(name = name, mime = mime, base64 = Base64.encodeToString(bytes, Base64.NO_WRAP))
    } catch (_: Exception) {
        null
    }
}
