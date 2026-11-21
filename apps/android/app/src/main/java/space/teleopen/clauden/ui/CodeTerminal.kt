package space.teleopen.clauden.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import space.teleopen.clauden.alarm.ClaudenReplyNotifications
import space.teleopen.clauden.data.CodeAction
import space.teleopen.clauden.data.CodeApi
import space.teleopen.clauden.data.CodeDevice
import space.teleopen.clauden.data.CodeAgent
import space.teleopen.clauden.data.CodeCommand
import space.teleopen.clauden.data.CodeDiff
import space.teleopen.clauden.data.CodeException
import space.teleopen.clauden.data.CodeExport
import space.teleopen.clauden.data.CodeFileContent
import space.teleopen.clauden.data.CodeFilesListing
import space.teleopen.clauden.data.CodeModel
import space.teleopen.clauden.data.CodeForm
import space.teleopen.clauden.data.CodeFormField
import space.teleopen.clauden.data.CodeFs
import space.teleopen.clauden.data.CodeGithub
import space.teleopen.clauden.data.CodeJob
import space.teleopen.clauden.data.CodeJobResult
import space.teleopen.clauden.data.CodeMessage
import space.teleopen.clauden.data.CodeRepo
import space.teleopen.clauden.data.CodePermission
import space.teleopen.clauden.data.CodeSession
import space.teleopen.clauden.data.CodeShellEntry
import space.teleopen.clauden.data.CodeStep
import space.teleopen.clauden.data.CodeTodo
import space.teleopen.clauden.data.CodeUsage
import space.teleopen.clauden.data.SshHost
import space.teleopen.clauden.data.SshSession
import space.teleopen.clauden.data.SshStore
import space.teleopen.clauden.data.collectRemoteContext
import space.teleopen.clauden.data.remoteFsExec
import space.teleopen.clauden.data.DeviceFolder
import space.teleopen.clauden.data.TermuxBridge
import java.io.File

internal val TermBg = Color(0xFF0A0A09)
internal val TermBgTop = Color(0xFF0D0C0B)
internal val TermPanel = Color(0xFF111110)
internal val TermBorder = Color(0xFF2B2A27)
internal val TermText = Color(0xFFE9E6E0)
internal val TermDim = Color(0xFF8C8983)
internal val TermFaint = Color(0xFF5E5B56)
internal val TermAccent = Color(0xFFD97757)
internal val TermGreen = Color(0xFF7FA96B)
internal val TermRed = Color(0xFFD47A6A)

internal val TermBody = ClaudenType.mono.copy(fontSize = 13.sp, lineHeight = 19.sp)
internal val TermSmall = ClaudenType.mono.copy(fontSize = 11.5.sp, lineHeight = 17.sp)
internal val TermTiny = ClaudenType.mono.copy(fontSize = 10.sp, lineHeight = 15.sp)

@Composable
fun CodeBootScreen(modifier: Modifier = Modifier) {
    val view = LocalView.current
    var shown by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "boot-appear",
    )

    LaunchedEffect(Unit) {
        delay(140)
        shown = true
    }

    LightSystemBars()

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .graphicsLayer {
                    alpha = appear
                    val scale = 0.9f + 0.1f * appear
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x2ED97757),
                                    Color(0x12D97757),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = size.minDimension / 2f,
                            ),
                            radius = size.minDimension / 2f,
                        )
                    },
            )
            ClaudenLogo(
                modifier = Modifier.fillMaxSize().padding(30.dp),
                tint = Color(0xFFF7F0E9),
                variant = ClaudenLogoVariant.Idle,
            )
        }
        PixelLoadingText(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 86.dp),
        )
    }
}

@Composable
private fun PixelLoadingText(modifier: Modifier = Modifier) {
    var revealed by remember { mutableIntStateOf(0) }
    var dots by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(420)
        for (index in 1..7) {
            revealed = index
            delay(92)
        }
        while (true) {
            delay(330)
            dots = (dots + 1) % 4
        }
    }

    Canvas(
        modifier = modifier
            .width(232.dp)
            .height(32.dp),
    ) {
        val pixel = 4.dp.toPx()
        val glyphW = 5 * pixel
        val glyphH = 7 * pixel
        val gap = 2 * pixel
        val dotW = 2 * pixel
        val dotGap = 1.4f * pixel
        val letters = "LOADING"
        val lettersW = letters.length * glyphW + (letters.length - 1) * gap
        val dotsW = 3 * dotW + 2 * dotGap
        val totalW = lettersW + gap + dotsW
        val startX = (size.width - totalW) / 2f
        val top = (size.height - glyphH) / 2f

        var x = startX
        letters.forEachIndexed { index, letter ->
            val glyph = PIXEL_GLYPHS[letter] ?: return@forEachIndexed
            val color = if (index < revealed) TermText else Color(0xFF1D1C1A)
            for (row in 0 until 7) {
                val line = glyph[row]
                for (column in 0 until 5) {
                    if (line[column] == '1') {
                        drawRect(
                            color = color,
                            topLeft = Offset(x + column * pixel, top + row * pixel),
                            size = Size(pixel, pixel),
                        )
                    }
                }
            }
            x += glyphW + gap
        }

        x = startX + lettersW + gap
        val dotTop = top + 5 * pixel
        for (slot in 0 until 3) {
            if (slot < dots) {
                drawRect(
                    color = TermAccent,
                    topLeft = Offset(x + slot * (dotW + dotGap), dotTop),
                    size = Size(dotW, 2 * pixel),
                )
            }
        }
    }
}

private val PIXEL_GLYPHS: Map<Char, Array<String>> = mapOf(
    'L' to arrayOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'O' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'A' to arrayOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'D' to arrayOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'I' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    'N' to arrayOf("10001", "11001", "11001", "10101", "10011", "10011", "10001"),
    'G' to arrayOf("01110", "10001", "10000", "10111", "10001", "10001", "01110"),
)

@Composable
private fun LightSystemBars(status: Boolean = true) {
    val view = LocalView.current

    val lightIcons = LocalClaudenColors.current.bg.luminance() > 0.5f
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
            ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)

        if (status) controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            if (status) controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }
}

@Composable
fun CodeTerminalApp(
    api: CodeApi,
    signedIn: Boolean,
    claudeMode: Boolean,
    codeAccess: Boolean,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sshStore = remember { SshStore(context) }
    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var project by remember { mutableStateOf<TermProject?>(null) }
    var mode by rememberSaveable { mutableStateOf("phone") }
    var sshHost by remember { mutableStateOf<SshHost?>(null) }
    var sshHostId by rememberSaveable { mutableStateOf("") }
    var sshMode by rememberSaveable { mutableStateOf("agent") }
    LaunchedEffect(codeAccess) {
        if (!codeAccess) {
            mode = "phone"
            sessionId = null
            sshHost = null
            sshHostId = ""
        }
    }
    LaunchedEffect(sshHostId) {
        if (sshHostId.isBlank()) {
            sshHost = null
            return@LaunchedEffect
        }
        if (sshHost?.id == sshHostId) return@LaunchedEffect
        sshHost = sshStore.hosts().firstOrNull { it.id == sshHostId }
        if (sshHost == null) sshHostId = ""
    }
    val current = sessionId
    val connected = sshHost
    if (connected != null && codeAccess) {
        key(connected.id) {
            if (sshMode == "terminal") {
                SshTerminalScreen(
                    host = connected,
                    onBack = {
                        sshHost = null
                        sshHostId = ""
                    },
                    onAgent = { sshMode = "agent" },
                    onMenu = onMenu,
                )
            } else {
                SshAgentScreen(
                    api = api,
                    signedIn = signedIn,
                    claudeMode = claudeMode,
                    host = connected,
                    onBack = {
                        sshHost = null
                        sshHostId = ""
                    },
                    onTerminal = { sshMode = "terminal" },
                    onMenu = onMenu,
                )
            }
        }
    } else if (current == null || !codeAccess) {
        if (mode == "ssh" && codeAccess) {
            SshHostsScreen(
                api = api,
                onBack = { mode = "phone" },
                onConnect = {
                    sshHost = it
                    sshHostId = it.id
                    sshMode = "agent"
                },
                onTerminal = {
                    sshHost = it
                    sshHostId = it.id
                    sshMode = "terminal"
                },
                onMenu = onMenu,
            )
        } else {
            TerminalShell(
                api = api,
                signedIn = signedIn,
                claudeMode = claudeMode,
                codeAccess = codeAccess,
                onMenu = onMenu,
                onSsh = { mode = "ssh" },
                project = project,
                onProject = { project = it },
                onOpen = { sessionId = it },
                modifier = modifier,
            )
        }
    } else {
        TerminalSession(
            api = api,
            sessionId = current,
            onBack = { sessionId = null },
            onMenu = onMenu,
            modifier = modifier,
        )
    }
}

private data class TermProject(
    val repo: String,
    val label: String,
    val cwd: String,
    val phone: Boolean,
)

private enum class ShellPane { Home, Projects }

private enum class LogKind { Info, Error, Success, Muted }

private data class ShellLog(val text: String, val kind: LogKind)

private data class ProjectChoice(
    val label: String,
    val hint: String,
    val current: Boolean = false,
    val action: () -> Unit,
)

@Composable
private fun TerminalShell(
    api: CodeApi,
    signedIn: Boolean,
    claudeMode: Boolean,
    codeAccess: Boolean,
    onMenu: () -> Unit,
    onSsh: () -> Unit,
    project: TermProject?,
    onProject: (TermProject?) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folder = remember { DeviceFolder(context) }
    val scroll = rememberScrollState()

    var folderName by remember { mutableStateOf(folder.name()) }
    var browseName by remember { mutableStateOf(folder.browseName()) }
    var repos by remember { mutableStateOf<List<CodeRepo>>(emptyList()) }
    var ghRepos by remember { mutableStateOf<List<CodeRepo>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<CodeSession>>(emptyList()) }
    var github by remember { mutableStateOf<CodeGithub?>(null) }
    var pane by remember { mutableStateOf(ShellPane.Home) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var creatingText by remember { mutableStateOf("") }
    var locked by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<List<ShellLog>>(emptyList()) }
    var helpOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CodeSession?>(null) }

    fun push(text: String, kind: LogKind = LogKind.Info) {
        log = (log + ShellLog(text, kind)).takeLast(40)
    }

    fun select(next: TermProject) {
        onProject(next)
        pane = ShellPane.Home
        push("Проект: ${next.label}", LogKind.Success)
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { folder.setTree(uri) }
            folderName = folder.name()
            select(
                TermProject(
                    repo = "device",
                    label = "Телефон · $folderName",
                    cwd = "~/phone/$folderName",
                    phone = true,
                ),
            )
        }
    }
    val pickBrowse = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { folder.setBrowse(uri) }
            browseName = folder.browseName()
            push("Агент видит: $browseName", LogKind.Success)
        }
    }

    fun ensureDefaultProject() {
        if (project != null) return
        if (folder.name().isNotBlank()) {
            onProject(
                TermProject(
                    repo = "device",
                    label = "Телефон · ${folder.name()}",
                    cwd = "~/phone/${folder.name()}",
                    phone = true,
                ),
            )
        } else {
            val repo = repos.firstOrNull() ?: ghRepos.firstOrNull()
            if (repo != null) {
                onProject(
                    TermProject(
                        repo = repo.id,
                        label = "Репозиторий · ${repo.name}",
                        cwd = "~/repos/${repo.name}",
                        phone = false,
                    ),
                )
            }
        }
    }

    fun load() {
        if (!signedIn || !codeAccess) return
        scope.launch {
            loading = true
            runCatching { api.repos() }
                .onSuccess { repos = it }
                .onFailure {
                    if ((it as? CodeException)?.status == 403) locked = true
                    else push(it.codeMessage, LogKind.Error)
                }
            runCatching { api.github() }.onSuccess { info ->
                github = info
                if (info.connected) {
                    runCatching { api.githubRepos() }
                        .onSuccess { ghRepos = it }
                        .onFailure { push(it.codeMessage, LogKind.Error) }
                }
            }
            runCatching { api.sessions() }
                .onSuccess { list -> sessions = list.filterNot { it.source == "ssh" } }
                .onFailure {
                    if ((it as? CodeException)?.status == 403) locked = true
                    else push(it.codeMessage, LogKind.Error)
                }
            ensureDefaultProject()
            loading = false
        }
    }

    LaunchedEffect(signedIn, codeAccess, claudeMode) { load() }
    LaunchedEffect(log.size, pane) {
        delay(40)
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun startTask(text: String) {
        val current = project
        if (current == null) {
            pane = ShellPane.Projects
            push("Сначала выберите проект: папку телефона или репозиторий.", LogKind.Error)
            return
        }
        if (current.phone && folder.treeUri() == null) {
            push("Папка телефона не выбрана.", LogKind.Error)
            pickFolder.launch(null)
            return
        }
        creating = true
        creatingText = if (current.phone) "Готовлю папку и отправляю на сервер…" else "Клонирую репозиторий…"
        push("> $text", LogKind.Info)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (current.phone) {
                        val session = api.createDevice(text, folder.name())
                        val temp = File(context.cacheDir, "clauden-source.zip")
                        try {
                            val count = folder.zipTo(temp)
                            if (count == 0) error("В папке нет файлов")
                            api.uploadSource(session.id, temp)
                        } finally {
                            temp.delete()
                        }
                        session.id
                    } else {
                        api.create(current.repo, text).id
                    }
                }
            }
            result.onSuccess { id ->
                creating = false
                draft = ""
                runCatching { api.sessions() }
                    .onSuccess { list -> sessions = list.filterNot { it.source == "ssh" } }
                onOpen(id)
            }.onFailure { err ->
                creating = false
                if ((err as? CodeException)?.status == 403) locked = true
                push(err.codeMessage, LogKind.Error)
            }
        }
    }

    val choices: List<ProjectChoice> = buildList {
        if (folderName.isBlank()) {
            add(
                ProjectChoice("Выбрать папку на телефоне", "системный выбор папки") {
                    pickFolder.launch(null)
                },
            )
        } else {
            add(
                ProjectChoice(
                    label = "Телефон · $folderName",
                    hint = if (project?.phone == true) "текущий" else "папка с телефона",
                    current = project?.phone == true,
                ) {
                    select(
                        TermProject(
                            repo = "device",
                            label = "Телефон · $folderName",
                            cwd = "~/phone/$folderName",
                            phone = true,
                        ),
                    )
                },
            )
        }
        repos.forEach { repo ->
            add(
                ProjectChoice(
                    label = "Репозиторий · ${repo.name}",
                    hint = repo.description.take(40).ifBlank { repo.defaultBranch },
                    current = project?.repo == repo.id,
                ) {
                    select(
                        TermProject(
                            repo = repo.id,
                            label = "Репозиторий · ${repo.name}",
                            cwd = "~/repos/${repo.name}",
                            phone = false,
                        ),
                    )
                },
            )
        }
        ghRepos.forEach { repo ->
            add(
                ProjectChoice(
                    label = "GitHub · ${repo.name}",
                    hint = if (repo.isPrivate) "private" else "public",
                    current = project?.repo == repo.id,
                ) {
                    select(
                        TermProject(
                            repo = repo.id,
                            label = "GitHub · ${repo.name}",
                            cwd = "~/github/${repo.name}",
                            phone = false,
                        ),
                    )
                },
            )
        }
        add(
            ProjectChoice("Другая папка на телефоне", "открыть системный выбор") {
                pickFolder.launch(null)
            },
        )
        if (github?.connected == true) {
            add(
                ProjectChoice(
                    "GitHub подключён · ${github?.login.orEmpty()}",
                    "токен хранится на сервере",
                ) {
                    push("GitHub: ${github?.name.orEmpty()} (@${github?.login.orEmpty()})", LogKind.Info)
                },
            )
            add(
                ProjectChoice("Отключить GitHub", "удалить токен с сервера") {
                    scope.launch {
                        runCatching { api.githubDisconnect() }
                        github = CodeGithub(connected = false)
                        ghRepos = emptyList()
                        push("GitHub отключён", LogKind.Muted)
                    }
                },
            )
        } else {
            add(
                ProjectChoice("Подключить GitHub", "вставьте токен: /github <token>") {
                    push("Вставьте токен: /github ghp_...", LogKind.Muted)
                },
            )
        }
        add(ProjectChoice("Папка для просмотра", browseName.ifBlank { "не выбрана" }) {
            pickBrowse.launch(null)
        })
        if (folderName.isNotBlank()) {
            add(ProjectChoice("Забыть доступ к папке", "освободить разрешение Android") {
                folder.clear()
                folderName = ""
                onProject(null)
                pane = ShellPane.Home
                push("Доступ к папке забыт", LogKind.Muted)
            })
        }
        add(ProjectChoice("Назад", "к задачам") { pane = ShellPane.Home })
    }

    fun chooseNumber(value: String): Boolean {
        val number = value.toIntOrNull() ?: return false
        val choice = choices.getOrNull(number - 1) ?: return false
        choice.action()
        return true
    }

    fun handleHomeCommand(text: String) {
        val head = text.substringBefore(' ').lowercase()
        val arg = text.substringAfter(' ', "").trim()
        when (head) {
            "/help" -> helpOpen = true
            "/project" -> {
                pane = ShellPane.Projects
                push("Выберите проект номером или нажмите на строку.", LogKind.Muted)
            }
            "/sessions", "/refresh" -> load()
            "/open" -> {
                val index = arg.toIntOrNull()
                val target = index?.let { sessions.getOrNull(it - 1) }
                if (target == null) push("Нет сессии с номером $arg", LogKind.Error)
                else onOpen(target.id)
            }
            "/delete" -> {
                val parts = arg.split(Regex("\\s+"), limit = 2)
                val index = parts.firstOrNull()?.toIntOrNull()
                val target = index?.let { sessions.getOrNull(it - 1) }
                if (target == null) {
                    push("Нет сессии с номером ${parts.firstOrNull().orEmpty()}", LogKind.Error)
                } else if (parts.getOrNull(1) == "yes") {
                    scope.launch {
                        runCatching { api.remove(target.id) }
                            .onSuccess {
                                sessions = sessions.filterNot { it.id == target.id }
                                push("Сессия удалена", LogKind.Success)
                            }
                            .onFailure { push(it.codeMessage, LogKind.Error) }
                    }
                } else {
                    deleteTarget = target
                }
            }
            "/github" -> {
                if (arg.isBlank()) {
                    push("Вставьте токен: /github ghp_...", LogKind.Muted)
                } else {
                    scope.launch {
                        runCatching { api.githubConnect(arg) }
                            .onSuccess { info ->
                                github = info
                                runCatching { api.githubRepos() }.onSuccess { ghRepos = it }
                                push("GitHub: @${info.login}", LogKind.Success)
                            }
                            .onFailure { push(it.codeMessage, LogKind.Error) }
                    }
                }
            }
            "/folder" -> pickFolder.launch(null)
            "/browse" -> pickBrowse.launch(null)
            "/forget" -> {
                folder.clear()
                folderName = ""
                onProject(null)
                push("Доступ к папке забыт", LogKind.Muted)
            }
            "/clear" -> log = emptyList()
            "/files" -> push("Откройте сессию и напишите /files — файлы живут внутри сессии.", LogKind.Muted)
            "/models", "/effort" -> push(
                "Модель и уровень усилий выбираются внутри сессии: откройте " +
                    "задачу или создайте новую, затем /models или /effort.",
                LogKind.Muted,
            )
            "/menu", "/quit" -> onMenu()
            "/ssh" -> if (codeAccess) {
                onSsh()
            } else {
                push("Code доступен только с подпиской, оформленной в Clauden.", LogKind.Error)
            }
            else -> push("Неизвестная команда $head. /help", LogKind.Error)
        }
    }

    fun submit() {
        val text = draft.trim()
        if (text.isBlank() || creating) return
        helpOpen = false
        if (pane == ShellPane.Projects) {
            when {
                text == "b" || text == "/back" -> pane = ShellPane.Home
                chooseNumber(text) -> Unit
                text.startsWith("/") -> handleHomeCommand(text)
                else -> push("Выберите номер проекта или /back", LogKind.Error)
            }
            draft = ""
            return
        }
        draft = ""
        if (text.startsWith("/")) handleHomeCommand(text) else startTask(text)
    }

    val cwd = project?.cwd ?: "~"
    val placeholder = when {
        !signedIn -> "Войдите в аккаунт Clauden"
        !codeAccess || locked -> "Нужна подписка, оформленная в Clauden"
        claudeMode -> "Code работает в режиме Clauden"
        pane == ShellPane.Projects -> "Номер проекта или /back"
        creating -> creatingText
        else -> "Try \"добавь тесты для calc.py\""
    }

    val slash = slashMatches(draft, SHELL_COMMANDS)

    fun pickSlash(command: SlashCommand) {
        if (command.args.isBlank()) {
            draft = ""
            handleHomeCommand("/${command.name}")
        } else {
            draft = "/${command.name} "
        }
    }

    LightSystemBars(status = false)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(Brush.verticalGradient(listOf(TermBgTop, TermBg))),
    ) {
        TerminalTopBar(
            title = "claude code",
            subtitle = project?.label.orEmpty(),
            onMenu = onMenu,
            onProjects = { pane = if (pane == ShellPane.Home) ShellPane.Projects else ShellPane.Home },
        )
        CodeModeTabs(
            active = "phone",
            onPhone = {},
            onSsh = { if (codeAccess) onSsh() },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClaudeWelcomeBox(
                cwd = cwd,
                subtitle = project?.label ?: "проект не выбран — /project",
                tips = true,
                onHelp = { helpOpen = true },
            )

            if (!signedIn) {
                NoteBlock("Войдите в аккаунт Clauden — Code работает через наш сервер.", TermRed)
            } else if (!codeAccess || locked) {
                NoteBlock(
                    "Code доступен только с подпиской, оформленной в Clauden. " +
                        "Подписка, выданная вручную или купленная в Claude, Code не " +
                        "открывает. Оформить: меню → Настройки → Оплата.",
                    TermAccent,
                )
            } else if (claudeMode) {
                NoteBlock(
                    "Сейчас включён режим Claude. Code исполняется на сервере Clauden — " +
                        "переключите режим в настройках.",
                    TermAccent,
                )
            }

            if (pane == ShellPane.Projects) {
                Text("Проекты", style = TermSmall, color = TermDim)
                choices.forEachIndexed { index, choice ->
                    ProjectRow(index + 1, choice)
                }
                Text(
                    "Нажмите на строку или введите номер в поле ниже.",
                    style = TermTiny,
                    color = TermFaint,
                )
            } else {
                SessionsBlock(
                    sessions = sessions,
                    loading = loading,
                    onOpen = onOpen,
                    onDelete = { deleteTarget = it },
                )
                if (creating) {
                    WorkingLine(creatingText)
                }
                log.forEach { entry -> LogLine(entry) }
            }
        }

        if (slash.isNotEmpty()) {
            SlashMenu(commands = slash, onPick = { pickSlash(it) })
        }
        if (helpOpen) {
            HelpPanel(text = SHELL_HELP, onClose = { helpOpen = false })
        }
        TerminalPromptBox(
            value = draft,
            onValueChange = { draft = it },
            onSubmit = { submit() },
            placeholder = placeholder,
            enabled = signedIn && !claudeMode && codeAccess && !locked,
            busy = creating,
            hint = when {
                pane == ShellPane.Projects -> "номер проекта · /back"
                else -> "/help · /project · /sessions · /github"
            },
            hintRight = cwd,
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить сессию?") },
            text = {
                Text(
                    "«${target.title.ifBlank { target.repoName }}» и её папка на сервере будут удалены.",
                    style = TermSmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    deleteTarget = null
                    scope.launch {
                        runCatching { api.remove(id) }
                            .onSuccess {
                                sessions = sessions.filterNot { it.id == id }
                                push("Сессия удалена", LogKind.Success)
                            }
                            .onFailure { push(it.codeMessage, LogKind.Error) }
                    }
                }) { Text("Удалить", color = TermRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionsBlock(
    sessions: List<CodeSession>,
    loading: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (CodeSession) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var revealed by remember { mutableStateOf<String?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Недавние сессии", style = TermSmall, color = TermDim)
        Spacer(Modifier.width(8.dp))
        if (loading) TermSpinner(TermFaint)
    }
    if (sessions.isEmpty()) {
        Text(
            if (loading) "Загружаю…" else "Сессий пока нет — напишите задачу ниже.",
            style = TermSmall,
            color = TermFaint,
        )
        return
    }
    sessions.take(8).forEachIndexed { index, session ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (revealed == session.id) revealed = null else onOpen(session.id)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        revealed = session.id
                    },
                )
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor(session.status), CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Text("${index + 1}.", style = TermSmall, color = TermFaint)
            Spacer(Modifier.width(7.dp))
            Text(
                text = session.title.ifBlank { session.repoName },
                style = TermBody,
                color = TermText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (revealed == session.id) {
                Text(
                    text = "удалить",
                    style = TermTiny,
                    color = TermRed,
                    modifier = Modifier
                        .background(TermRed.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                        .clickable {
                            revealed = null
                            onDelete(session)
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else {
                Text(
                    text = ago(session.updatedAt),
                    style = TermTiny,
                    color = TermFaint,
                )
            }
        }
        Text(
            text = "   ${session.repoName} · ${statusLabel(session.status)}" +
                (session.lastText.takeIf { it.isNotBlank() }?.let { " · ${it.take(48)}" } ?: ""),
            style = TermTiny,
            color = TermFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
    if (sessions.size > 8) {
        Text(
            text = "… ещё ${sessions.size - 8} — /open N или /sessions",
            style = TermTiny,
            color = TermFaint,
        )
    }
    Text(
        text = "Зажмите сессию, чтобы удалить.",
        style = TermTiny,
        color = TermFaint,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ProjectRow(number: Int, choice: ProjectChoice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { choice.action() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$number",
            style = TermBody,
            color = if (choice.current) TermAccent else TermFaint,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = choice.label,
                style = TermBody,
                color = if (choice.current) TermAccent else TermText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (choice.hint.isNotBlank()) {
                Text(
                    text = choice.hint,
                    style = TermTiny,
                    color = TermFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogLine(entry: ShellLog) {
    val color = when (entry.kind) {
        LogKind.Info -> TermText
        LogKind.Error -> TermRed
        LogKind.Success -> TermGreen
        LogKind.Muted -> TermDim
    }
    Text(entry.text, style = TermBody, color = color)
}

@Composable
private fun WorkingLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TermSpinner()
        Spacer(Modifier.width(9.dp))
        Text(text, style = TermBody, color = TermDim)
    }
}

@Composable
internal fun TerminalSession(
    api: CodeApi,
    sessionId: String,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    ssh: SshSession? = null,
    sshHost: SshHost? = null,
    sshRoot: String = "",
    onTerminal: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val diffScroll = rememberScrollState()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val clipboard = LocalClipboardManager.current

    var session by remember { mutableStateOf<CodeSession?>(null) }
    var remoteRoot by remember(sshRoot) { mutableStateOf(sshRoot) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }

    var job by remember { mutableStateOf<CodeJob?>(null) }
    var jobBusy by remember { mutableStateOf(false) }
    var jobNote by remember { mutableStateOf<String?>(null) }
    var writeJob by remember { mutableStateOf<CodeJob?>(null) }
    val handledJobs = remember { mutableSetOf<String>() }

    var device by remember { mutableStateOf<CodeDevice?>(null) }
    var deviceNote by remember { mutableStateOf<String?>(null) }
    var deviceBusy by remember { mutableStateOf(false) }

    var diff by remember { mutableStateOf<CodeDiff?>(null) }
    var lastAutoPull by remember { mutableStateOf(0L) }
    var formField by remember { mutableIntStateOf(0) }
    var formAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var helpOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var hideBefore by remember { mutableIntStateOf(0) }
    var hiddenShellIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filesOpen by remember { mutableStateOf(false) }
    var serverCommands by remember { mutableStateOf<List<CodeCommand>>(emptyList()) }
    var picker by remember { mutableStateOf("") }
    var pickerQuery by remember { mutableStateOf("") }
    var pickerLoading by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<CodeModel>>(emptyList()) }
    var agents by remember { mutableStateOf<List<CodeAgent>>(emptyList()) }
    var variantFor by remember { mutableStateOf<CodeModel?>(null) }

    val pendingForm = session?.forms?.firstOrNull()
    val pendingPermission = session?.permissions?.firstOrNull()

    LaunchedEffect(pendingForm?.id) {
        formField = 0
        formAnswers = emptyMap()
    }

    BackHandler { if (diff != null) diff = null else onBack() }

    val folder = remember { DeviceFolder(context) }
    var folderName by remember { mutableStateOf(folder.name()) }
    var browseName by remember { mutableStateOf(folder.browseName()) }

    var termuxReady by remember { mutableStateOf(TermuxBridge.installed(context)) }
    var termuxSetup by remember { mutableStateOf(false) }
    val termuxPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        termuxReady = TermuxBridge.installed(context)
        if (!granted) jobNote = "Без разрешения Termux команда не выполнится"
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
            note = "Папка телефона: $folderName"
        }
    }
    val pickBrowse = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { folder.setBrowse(uri) }
            browseName = folder.browseName()
            note = "Агент видит: $browseName"
        }
    }

    fun reload() {
        scope.launch {
            runCatching { api.session(sessionId) }.onSuccess { session = it }
        }
    }

    fun refreshRemoteContext(showNote: Boolean = true) {
        val client = ssh ?: return
        val targetRoot = remoteRoot
        scope.launch {
            note = "Читаю AGENTS.md и скиллы на сервере…"
            val ctx = runCatching { collectRemoteContext(client, targetRoot) }.getOrNull()
            if (ctx == null) {
                if (showNote) note = "Не удалось прочитать контекст сервера"
                return@launch
            }
            runCatching {
                api.updateRemote(sessionId, targetRoot, ctx.toJson())
            }.onSuccess {
                session = session?.let { current ->
                    current.copy(remote = current.remote?.copy(root = targetRoot))
                }
            }
            if (showNote) {
                note = buildString {
                    append("Корень: ").append(targetRoot).append('\n')
                    append(ctx.summary())
                    if (ctx.skills.isNotEmpty()) {
                        append("\nСкиллы: ")
                        append(ctx.skills.take(10).joinToString { it.name })
                    }
                }
            }
        }
    }

    fun useAsRoot(path: String) {
        val next = path.trim().ifBlank { "/" }
        remoteRoot = next
        note = "Корень агента: $next"
        refreshRemoteContext(true)
    }

    fun applyWriteJob(pending: CodeJob, always: Boolean = false) {
        if (always) folder.setAutoWrite(true)
        val fs = pending.fs
        writeJob = null
        scope.launch {
            val payload = runCatching {
                withContext(Dispatchers.IO) {
                    folder.exec("write", fs?.path.orEmpty(), fs?.content.orEmpty())
                }
            }.getOrElse {
                org.json.JSONObject().put("error", it.codeMessage).toString()
            }
            runCatching {
                api.jobResult(sessionId, pending.id, CodeJobResult(stdout = payload, code = 0))
            }
            reload()
        }
    }

    LaunchedEffect(sessionId) {
        runCatching { api.session(sessionId) }
            .onSuccess { session = it }
            .onFailure { error = it.codeMessage }
        runCatching { api.device(sessionId) }.onSuccess { device = it }
        runCatching { api.commands(sessionId) }.onSuccess { serverCommands = it }

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
                        "todos" -> runCatching {
                            json.decodeFromJsonElement<List<CodeTodo>>(data)
                        }.onSuccess { list -> session = current.copy(todos = list) }
                        "forms" -> runCatching {
                            json.decodeFromJsonElement<List<CodeForm>>(data)
                        }.onSuccess { list -> session = current.copy(forms = list) }
                        "permissions" -> runCatching {
                            json.decodeFromJsonElement<List<CodePermission>>(data)
                        }.onSuccess { list -> session = current.copy(permissions = list) }
                        "files" -> scope.launch {
                            runCatching { api.session(sessionId) }.onSuccess { session = it }
                        }
                        "shell" -> runCatching {
                            json.decodeFromJsonElement<CodeShellEntry>(data)
                        }.onSuccess { entry ->
                            session = current.copy(
                                shell = (current.shell.filterNot { it.id == entry.id } + entry)
                                    .takeLast(50),
                            )
                            if (note?.startsWith("Выполняю:") == true) note = null
                        }
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
            val upload = pending.upload
            val download = pending.download
            if (ssh != null) {

                scope.launch {
                    if (download != null) {

                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val base = if (remoteRoot.startsWith("~")) {
                                    ssh.pwd()
                                } else {
                                    remoteRoot
                                }
                                val remote = if (download.path.startsWith("/")) {
                                    download.path
                                } else {
                                    ssh.joinRemote(base, download.path)
                                }
                                val safeName = download.name
                                    .substringAfterLast('/')
                                    .replace("\\", "-")
                                    .ifBlank { "download.bin" }
                                val temp = File(
                                    context.cacheDir,
                                    "clauden-download-${pending.id}-${safeName.take(80)}",
                                )
                                temp.outputStream().use { ssh.download(remote, it) }
                                val bytes = temp.readBytes()
                                temp.delete()
                                api.uploadDownload(sessionId, pending.id, bytes)
                                remote
                            }
                        }
                        runCatching {
                            api.jobResult(
                                sessionId,
                                pending.id,
                                if (result.isSuccess) {
                                    CodeJobResult(
                                        stdout = "Скачано: ${result.getOrThrow()}",
                                        code = 0,
                                    )
                                } else {
                                    CodeJobResult(
                                        code = -1,
                                        error = result.exceptionOrNull()?.message
                                            ?: "Не удалось скачать файл",
                                    )
                                },
                            )
                        }
                    } else if (upload != null) {

                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val bytes = api.downloadUpload(sessionId, upload.id)
                                val safeName = upload.name
                                    .substringAfterLast('/')
                                    .replace("\\", "-")
                                    .ifBlank { "upload.bin" }
                                val temp = File(
                                    context.cacheDir,
                                    "clauden-upload-${pending.id}-${safeName.take(80)}",
                                )
                                temp.writeBytes(bytes)
                                val base = if (remoteRoot.startsWith("~")) {
                                    ssh.pwd()
                                } else {
                                    remoteRoot
                                }
                                val target = ssh.joinRemote(base, upload.path)
                                temp.inputStream().use { ssh.upload(target, it) }
                                temp.delete()
                                target
                            }
                        }
                        runCatching {
                            api.jobResult(
                                sessionId,
                                pending.id,
                                if (result.isSuccess) {
                                    CodeJobResult(
                                        stdout = "Загружено: ${result.getOrThrow()}",
                                        code = 0,
                                    )
                                } else {
                                    CodeJobResult(
                                        code = -1,
                                        error = result.exceptionOrNull()?.message
                                            ?: "Не удалось загрузить файл",
                                    )
                                },
                            )
                        }
                    } else if (fs != null) {
                        val payload = withContext(Dispatchers.IO) {
                            remoteFsExec(ssh, remoteRoot, fs.op, fs.path, fs.content)
                        }
                        val fsError = runCatching {
                            org.json.JSONObject(payload).optString("error")
                        }.getOrDefault("")
                        runCatching {
                            api.jobResult(
                                sessionId,
                                pending.id,
                                CodeJobResult(
                                    stdout = payload,
                                    code = if (fsError.isBlank()) 0 else 1,
                                    error = fsError,
                                ),
                            )
                        }
                    } else {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                ssh.exec(pending.command, remoteRoot, pending.timeout)
                            }
                        }
                        val exec = result.getOrNull()
                        runCatching {
                            api.jobResult(
                                sessionId,
                                pending.id,
                                if (exec == null) {
                                    CodeJobResult(
                                        code = -1,
                                        error = result.exceptionOrNull()?.message
                                            ?: "Нет SSH-подключения",
                                    )
                                } else {
                                    CodeJobResult(
                                        stdout = exec.stdout,
                                        stderr = exec.stderr,
                                        code = exec.code,
                                        error = if (exec.timedOut) {
                                            "Команда остановлена по таймауту"
                                        } else {
                                            ""
                                        },
                                    )
                                },
                            )
                        }
                    }
                    reload()
                }
                continue
            }
            if (fs != null) {
                if (fs.op == "write") {
                    if (folder.autoWrite()) applyWriteJob(pending) else writeJob = pending
                    continue
                }
                scope.launch {
                    val payload = runCatching {
                        withContext(Dispatchers.IO) { folder.exec(fs.op, fs.path, fs.content) }
                    }.getOrElse {
                        org.json.JSONObject().put("error", it.codeMessage).toString()
                    }
                    runCatching {
                        api.jobResult(sessionId, pending.id, CodeJobResult(stdout = payload, code = 0))
                    }
                    reload()
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
                    reload()
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

    LaunchedEffect(session?.updatedAt, session?.status, session?.source) {
        val current = session ?: return@LaunchedEffect
        if (current.source != "device" || current.working) return@LaunchedEffect
        if (!current.sourceReady) return@LaunchedEffect
        if (current.updatedAt <= lastAutoPull) return@LaunchedEffect
        if (folder.treeUri() == null) return@LaunchedEffect
        lastAutoPull = current.updatedAt
        pullChanges { result ->
            deviceNote = if (result == "Изменений нет") null else result
        }
    }

    LaunchedEffect(
        session?.messages?.size,
        session?.messages?.lastOrNull()?.text,
        session?.status,
        diff,
        note,
        error,
    ) {
        delay(50)
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun sendText(text: String) {
        if (text.isBlank() || sending) return
        sending = true
        scope.launch {
            runCatching { api.send(sessionId, text) }
                .onSuccess { session = it; note = null }
                .onFailure { note = it.codeMessage }
            sending = false
        }
    }

    fun uploadFolder() {
        if (folder.treeUri() == null) {
            note = "Сначала выберите папку телефона: /folder"
            return
        }
        scope.launch {
            deviceBusy = true
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
                note = "Отправлено файлов: ${it.files}"
            }.onFailure { note = it.codeMessage }
            deviceBusy = false
        }
    }

    fun copyToClipboard(value: String, label: String) {
        clipboard.setText(AnnotatedString(value))
        note = "$label скопирован"
    }

    fun runServerCommand(name: String, text: String) {
        if (session?.working == true) {
            note = "Агент ещё работает. /stop — прервать."
            return
        }
        note = "Команда /$name…"
        scope.launch {
            runCatching { api.runCommand(sessionId, name, text) }
                .onSuccess { session = it; note = null }
                .onFailure { note = it.codeMessage }
        }
    }

    fun openModels() {
        picker = "models"
        pickerQuery = ""
        pickerLoading = true
        if (models.isEmpty()) {
            scope.launch {
                runCatching { api.models() }
                    .onSuccess { models = it }
                    .onFailure { note = it.codeMessage; picker = "" }
                pickerLoading = false
            }
        } else {
            pickerLoading = false
        }
    }

    fun openAgents() {
        picker = "agents"
        pickerQuery = ""
        pickerLoading = true
        scope.launch {
            runCatching { api.agents(sessionId) }
                .onSuccess { list -> agents = list.filterNot { it.id == "ssh" } }
                .onFailure { note = it.codeMessage; picker = "" }
            pickerLoading = false
        }
    }

    fun applyModel(model: CodeModel, variant: String) {
        picker = ""
        variantFor = null
        scope.launch {
            runCatching { api.setModel(sessionId, model.providerID, model.id, variant) }
                .onSuccess {
                    session = session?.copy(model = model.key, variant = variant)
                    note = if (variant.isBlank()) {
                        "Модель: ${model.name}"
                    } else {
                        "Модель: ${model.name} · усилия: ${variantLabel(variant)}"
                    }
                }
                .onFailure { note = it.codeMessage }
        }
    }

    fun chooseModel(model: CodeModel) {
        if (model.variants.isNotEmpty()) {
            picker = ""
            variantFor = model
            return
        }
        applyModel(model, "")
    }

    fun openEffort() {
        val key = session?.model.orEmpty()
        val known = models.firstOrNull { it.key == key }
        if (known != null) {
            if (known.variants.isEmpty()) {
                note = "У модели ${known.name} нет уровней усилий."
            } else {
                variantFor = known
            }
            return
        }
        scope.launch {
            note = "Загружаю модели…"
            runCatching { api.models() }
                .onSuccess { list ->
                    models = list
                    val current = list.firstOrNull { it.key == key }
                    when {
                        current == null -> note = "Сначала выберите модель: /models"
                        current.variants.isEmpty() ->
                            note = "У модели ${current.name} нет уровней усилий."
                        else -> {
                            note = null
                            variantFor = current
                        }
                    }
                }
                .onFailure { note = it.codeMessage }
        }
    }

    fun chooseAgent(agent: CodeAgent) {
        picker = ""
        scope.launch {
            runCatching { api.setAgent(sessionId, agent.id) }
                .onSuccess {
                    session = session?.copy(agent = agent.id)
                    note = "Агент: ${agent.name}"
                }
                .onFailure { note = it.codeMessage }
        }
    }

    fun showStatus() {
        val current = session
        note = buildString {
            append("проект: ").append(current?.repoName.orEmpty().ifBlank { "—" }).append('\n')
            append("папка: ").append(sessionCwd(current)).append('\n')
            append("статус: ").append(statusLabel(current?.status ?: "idle")).append('\n')
            append("модель: ").append(current?.model.orEmpty().ifBlank { "по тарифу" })
            if (!current?.variant.isNullOrBlank()) {
                append(" · усилия: ").append(variantLabel(current.variant))
            }
            append('\n')
            append("агент: ").append(current?.agent.orEmpty().ifBlank { "code" }).append('\n')
            append("сообщений: ").append(current?.messages?.size ?: 0)
            if (current?.todos?.isNotEmpty() == true) {
                append('\n').append("план: ").append(current.todos.count { it.status == "completed" })
                    .append('/').append(current.todos.size)
            }
            if (current?.shell?.isNotEmpty() == true) {
                append('\n').append("shell-команд: ").append(current.shell.size)
            }
        }
    }

    fun command(text: String) {
        val raw = text.removePrefix("/")
        val head = "/" + raw.substringBefore(' ').lowercase()
        val arg = raw.substringAfter(' ', "").trim()
        if (ssh != null && head in SSH_BLOCKED_COMMANDS) {
            note = "В SSH-сессии этот пункт работает с локальной копией, а не с сервером. " +
                "Используйте ! или попросите агента."
            return
        }
        when (head) {
            "/help" -> helpOpen = true
            "/goal" -> {
                if (arg.isBlank()) {
                    note = "Напишите /goal <цель> или /goal pause|resume|clear."
                } else {
                    runServerCommand("goal", arg)
                }
            }
            "/review" -> runServerCommand("review", arg)
            "/init" -> runServerCommand("init", arg)
            "/compact" -> scope.launch {
                note = "Сжимаю контекст…"
                runCatching { api.compact(sessionId) }
                    .onSuccess { note = "Контекст сжат" }
                    .onFailure { note = it.codeMessage }
            }
            "/models" -> openModels()
            "/effort" -> openEffort()
            "/agents" -> {
                if (ssh != null) {
                    note = "В SSH-сессии используется агент ssh: он выполняет команды " +
                        "на сервере через телефон."
                } else {
                    openAgents()
                }
            }
            "/undo" -> scope.launch {
                note = "Откатываю последний ход…"
                runCatching { api.undo(sessionId) }
                    .onSuccess { session = it; note = "Откат выполнен" }
                    .onFailure { note = it.codeMessage }
            }
            "/redo" -> scope.launch {
                note = "Возвращаю откат…"
                runCatching { api.redo(sessionId) }
                    .onSuccess { session = it; note = "Откат отменён" }
                    .onFailure { note = it.codeMessage }
            }
            "/rename" -> {
                if (arg.isBlank()) {
                    note = "Напишите /rename <название>."
                } else {
                    scope.launch {
                        runCatching { api.rename(sessionId, arg) }
                            .onSuccess { session = it; note = "Сессия переименована" }
                            .onFailure { note = it.codeMessage }
                    }
                }
            }
            "/copy" -> {
                val last = session?.messages?.lastOrNull { it.role != "user" && it.text.isNotBlank() }
                if (last == null) note = "В сессии ещё нет ответов."
                else copyToClipboard(last.text, "Ответ")
            }
            "/export" -> scope.launch {
                runCatching { api.exportMarkdown(sessionId) }
                    .onSuccess { copyToClipboard(it.text, "Транскрипт ${it.path}") }
                    .onFailure { note = it.codeMessage }
            }
            "/usage" -> scope.launch {
                runCatching { api.usage(sessionId) }
                    .onSuccess { usage ->
                        val tokens = usage.tokens
                        note = buildString {
                            append("модель: ").append(usage.model.ifBlank { "по тарифу" }).append('\n')
                            append("агент: ").append(usage.agent.ifBlank { "code" }).append('\n')
                            if (tokens != null) {
                                append("токены: вход ").append(tokens.input)
                                    .append(" · выход ").append(tokens.output)
                                if (tokens.reasoning > 0) {
                                    append(" · reasoning ").append(tokens.reasoning)
                                }
                                append('\n')
                            }
                            append("стоимость: $").append(String.format("%.4f", usage.cost))
                        }
                    }
                    .onFailure { note = it.codeMessage }
            }
            "/reload" -> {
                if (ssh != null) {
                    refreshRemoteContext(true)
                } else {
                    note = "Контекст сервера доступен только в SSH-сессии."
                }
            }
            "/skills" -> {
                val client = ssh
                if (client == null) {
                    note = "Скиллы сервера доступны только в SSH-сессии."
                } else {
                    scope.launch {
                        note = "Ищу скиллы на сервере…"
                        val ctx = runCatching {
                            collectRemoteContext(client, remoteRoot)
                        }.getOrNull()
                        if (ctx == null) {
                            note = "Не удалось прочитать скиллы на сервере"
                        } else {
                            runCatching {
                                api.updateRemote(sessionId, remoteRoot, ctx.toJson())
                            }
                            note = if (ctx.skills.isEmpty()) {
                                "Скиллы не найдены. Проверены .agents/skills, " +
                                    ".claude/skills, .clauden/skills."
                            } else {
                                "Скиллы (${ctx.skills.size}):\n" +
                                    ctx.skills.joinToString("\n") {
                                        "· ${it.name} — ${it.path}"
                                    } +
                                    "\n\nУстановить: положите папку со SKILL.md в " +
                                    ".agents/skills/ или попросите агента установить."
                            }
                        }
                    }
                }
            }
            "/status" -> showStatus()
            "/files" -> filesOpen = true
            "/clear" -> {
                hideBefore = session?.messages?.size ?: hideBefore
                hiddenShellIds = session?.shell?.map { it.id }?.toSet() ?: emptySet()
                note = null
                helpOpen = false
            }
            "/diff" -> scope.launch {
                runCatching { api.diff(sessionId) }
                    .onSuccess {
                        diff = it
                        note = null
                        diffScroll.scrollTo(0)
                    }
                    .onFailure { note = it.codeMessage }
            }
            "/commit" -> scope.launch {
                val message = arg.ifBlank { "Clauden: изменения" }
                runCatching { api.commit(sessionId, message) }
                    .onSuccess { note = "Закоммичено: $it"; diff = null }
                    .onFailure { note = it.codeMessage }
            }
            "/push" -> scope.launch {
                runCatching { api.push(sessionId) }
                    .onSuccess { note = it.ifBlank { "Отправлено" } }
                    .onFailure { note = it.codeMessage }
            }
            "/pr" -> scope.launch {
                val title = arg.substringBefore('|').trim()
                    .ifBlank { session?.title.orEmpty() }
                    .ifBlank { "Clauden: изменения" }
                val body = arg.substringAfter('|', "").trim()
                runCatching { api.pullRequest(sessionId, title, body) }
                    .onSuccess { note = "PR #${it.number}: ${it.url}" }
                    .onFailure { note = it.codeMessage }
            }
            "/stop" -> scope.launch {
                runCatching { api.stop(sessionId) }
                    .onFailure { note = it.codeMessage }
                reload()
            }
            "/sync" -> pullChanges { note = it }
            "/upload" -> uploadFolder()
            "/detach" -> scope.launch {
                runCatching { api.detachDevice(sessionId) }
                    .onSuccess { device = CodeDevice(attached = false); note = "Папка отключена" }
                    .onFailure { note = it.codeMessage }
            }
            "/folder" -> pickFolder.launch(null)
            "/browse" -> pickBrowse.launch(null)
            "/forget" -> {
                folder.clear()
                folderName = ""
                browseName = ""
                note = "Доступ к папке забыт"
            }
            "/delete" -> confirmDelete = true
            "/new" -> onBack()
            "/back" -> if (diff != null) diff = null else onBack()
            "/menu" -> onMenu()
            else -> {
                val extra = serverCommands.firstOrNull {
                    it.name.equals(head.removePrefix("/"), ignoreCase = true)
                }
                if (extra != null) {
                    runServerCommand(extra.name, arg)
                } else {
                    note = "Неизвестная команда $head. /help"
                }
            }
        }
    }

    fun runShellCommand(raw: String) {
        val commandText = raw.removePrefix("!").trim()
        if (commandText.isBlank()) {
            note = "Напишите команду после !  Например: !ls -la"
            return
        }
        val client = ssh
        if (client == null) {
            note = "Ручной терминал на сервере Clauden отключён. Команды доступны " +
                "только в SSH-сессии — на вашем сервере."
            return
        }
        scope.launch {
            note = "Выполняю на сервере: $commandText"
            val started = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) { client.exec(commandText, remoteRoot, 120) }
            }.onSuccess { result ->
                val entry = CodeShellEntry(
                    id = "local_${java.util.UUID.randomUUID().toString().take(8)}",
                    command = commandText,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    code = result.code,
                    ts = started,
                    durationMs = System.currentTimeMillis() - started,
                )
                session = session?.let { current ->
                    current.copy(
                        shell = (current.shell.filterNot { it.id == entry.id } + entry)
                            .takeLast(50),
                    )
                }
                note = when {
                    result.code == 0 -> null
                    result.timedOut -> "Команда остановлена по таймауту"
                    else -> "Код выхода: ${result.code}"
                }
            }.onFailure {
                note = it.message ?: "Не удалось выполнить команду на сервере"
            }
        }
    }

    fun answerFormValue(value: String) {
        val form = session?.forms?.firstOrNull() ?: return
        val field = form.fields.getOrNull(formField) ?: return
        val next = formAnswers + (field.key to value)
        if (formField + 1 < form.fields.size) {
            formAnswers = next
            formField += 1
            note = "Ответ принят. Следующий вопрос."
            return
        }
        scope.launch {
            runCatching { api.answerForm(sessionId, form.id, next) }
                .onSuccess {
                    session = it
                    formField = 0
                    formAnswers = emptyMap()
                    note = "Ответ отправлен"
                }
                .onFailure { note = it.codeMessage }
        }
    }

    fun submitForm(text: String) {
        val form = session?.forms?.firstOrNull() ?: return
        val field = form.fields.getOrNull(formField) ?: return
        val options = field.options
        val value = when {
            options.isNotEmpty() -> {
                val index = text.toIntOrNull()
                when {
                    index != null && index in 1..options.size -> options[index - 1].value
                    field.custom && text.isNotBlank() -> text
                    else -> null
                }
            }
            field.type == "boolean" -> when (text.lowercase()) {
                "1", "да", "yes", "true" -> "true"
                "0", "нет", "no", "false" -> "false"
                else -> null
            }
            else -> text.ifBlank { null }
        }
        if (value == null) {
            note = if (options.isNotEmpty()) "Выберите номер варианта" else "Введите ответ"
            return
        }
        answerFormValue(value)
    }

    fun submitPermission(text: String) {
        val permission = session?.permissions?.firstOrNull() ?: return
        val decision = when (text.trim().lowercase()) {
            "1", "once", "раз", "разрешить" -> "once"
            "2", "always", "всегда" -> "always"
            "3", "reject", "нет", "отклонить" -> "reject"
            else -> null
        }
        if (decision == null) {
            note = "1 — разрешить, 2 — всегда, 3 — отклонить"
            return
        }
        scope.launch {
            runCatching { api.answerPermission(sessionId, permission.id, decision) }
                .onSuccess {
                    session = it
                    note = "Ответ отправлен"
                }
                .onFailure { note = it.codeMessage }
        }
    }

    fun submit() {
        val text = draft.trim()
        if (text.isBlank()) return
        helpOpen = false
        if (text.startsWith("/")) {
            draft = ""
            command(text)
            return
        }
        if (text.startsWith("!")) {
            draft = ""
            helpOpen = false
            runShellCommand(text)
            return
        }
        if (session?.forms?.isNotEmpty() == true) {
            draft = ""
            submitForm(text)
            return
        }
        if (session?.permissions?.isNotEmpty() == true) {
            draft = ""
            submitPermission(text)
            return
        }
        if (!text.startsWith("/") && session?.working == true) {
            note = "Агент ещё работает. /stop — прервать, /diff — посмотреть изменения."
            return
        }
        draft = ""
        sendText(text)
    }

    val allCommands = remember(serverCommands, ssh) {
        val known = SESSION_COMMANDS.map { it.name }.toSet()
        val base = if (ssh != null) {
            SESSION_COMMANDS.filterNot { it.name in SSH_HIDDEN_COMMANDS } + listOf(
                SlashCommand("reload", "", "обновить AGENTS.md и скиллы"),
                SlashCommand("skills", "", "список скиллов на сервере"),
            )
        } else {
            SESSION_COMMANDS
        }
        base + if (ssh != null) {
            emptyList()
        } else {
            serverCommands
                .filter { it.name !in known }
                .map { SlashCommand(it.name, "", it.description) }
        }
    }
    val slash = slashMatches(draft, allCommands)

    fun pickSlash(command: SlashCommand) {
        if (command.args.isBlank()) {
            draft = ""
            command("/${command.name}")
        } else {
            draft = "/${command.name} "
        }
    }

    if (filesOpen) {
        if (ssh != null && sshHost != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TermBg),
            ) {
                TerminalTopBar(
                    title = "SFTP",
                    subtitle = sshHost.target,
                    onBack = { filesOpen = false },
                    onMenu = onMenu,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    SshFilesPane(
                        client = ssh,
                        host = sshHost,
                        onNote = { note = it },
                        onUseAsRoot = { useAsRoot(it) },
                    )
                }
            }
        } else {
            CodeFilesScreen(
                api = api,
                sessionId = sessionId,
                onBack = { filesOpen = false },
                onMenu = onMenu,
                onNote = { note = it },
            )
        }
        return
    }

    LightSystemBars(status = false)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(Brush.verticalGradient(listOf(TermBgTop, TermBg))),
    ) {
        TerminalTopBar(
            title = session?.repoName?.ifBlank { "claude code" } ?: "claude code",
            subtitle = when {
                session == null -> "загружаю…"
                session?.source == "device" -> "папка с телефона"
                session?.source == "ssh" -> "удалённый сервер"
                else -> session?.branch.orEmpty()
            },
            onMenu = onMenu,
            onBack = { if (diff != null) diff = null else onBack() },
            trailing = {
                if (onTerminal != null) {
                    val busy = session?.working == true
                    TextButton(onClick = onTerminal, enabled = !busy) {
                        Text(
                            "терминал",
                            style = TermTiny,
                            color = if (busy) TermFaint else TermAccent,
                        )
                    }
                }
                TextButton(onClick = { helpOpen = true }) {
                    Text("?", style = TermSmall, color = TermFaint)
                }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        ClaudenIcons.trash,
                        contentDescription = "Удалить сессию",
                        tint = TermFaint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            },
        )

        if (!session?.messages.isNullOrEmpty()) {
            ClaudeSessionHeader(
                cwd = sessionCwd(session),
                status = statusLabel(session?.status ?: "idle") + when (session?.source) {
                    "device" -> " · телефон"
                    "ssh" -> " · сервер"
                    else -> ""
                },
            )
        }

        if (diff != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(diffScroll)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            ) {
                DiffBlock(diff!!)
                note?.let {
                    Text(it, style = TermSmall, color = TermDim)
                }
                error?.let {
                    Text(it, style = TermSmall, color = TermRed)
                }
                Text("/back — вернуться к диалогу", style = TermTiny, color = TermFaint)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (session?.messages.isNullOrEmpty()) {
                    ClaudeWelcomeBox(
                        cwd = sessionCwd(session),
                        subtitle = session?.repoName.orEmpty().ifBlank { "Clauden Code" },
                        compact = true,
                    )
                }
                if (session?.todos?.isNotEmpty() == true) {
                    PlanBlock(session?.todos.orEmpty())
                }

                val messages = session?.messages.orEmpty().drop(hideBefore.coerceAtMost(session?.messages?.size ?: 0))
                if (messages.isEmpty()) {
                    Text(
                        text = if (session?.messages.isNullOrEmpty()) {
                            "Готовлю сессию…"
                        } else {
                            "История скрыта. Напишите задачу или /new."
                        },
                        style = TermSmall,
                        color = TermFaint,
                    )
                }
                messages.forEach { message ->
                    if (message.role == "user") {
                        UserLine(message.text)
                    } else {
                        message.steps.forEach { step -> StepLine(step) }
                        if (message.text.isNotBlank()) {
                            AssistantLine(message.text)
                        } else if (message.status == "working" && message.steps.isEmpty()) {
                            WorkingLine("Думаю…")
                        }
                    }
                }

                session?.shell
                    ?.filterNot { it.id in hiddenShellIds }
                    ?.forEach { entry -> ShellBlock(entry) }

                if (session?.working == true) {
                    WorkingLine(
                        when {
                            session?.forms?.isNotEmpty() == true -> "Жду ответа…"
                            session?.permissions?.isNotEmpty() == true -> "Нужно разрешение…"
                            session?.status == "waiting_device" &&
                                session?.source == "ssh" -> "Жду сервер…"
                            session?.status == "waiting_device" -> "Жду телефон…"
                            else -> "Работаю…"
                        },
                    )
                }
                if (reconnecting) {
                    Text("↻ Переподключаюсь к серверу…", style = TermTiny, color = TermFaint)
                }

                deviceNote?.let {
                    Text(it, style = TermSmall, color = TermGreen)
                }
                note?.let {
                    Text(it, style = TermSmall, color = TermDim)
                }
                error?.let {
                    Text(it, style = TermSmall, color = TermRed)
                }
                if (deviceBusy) {
                    WorkingLine("Синхронизирую папку…")
                }
            }
        }

        pendingPermission?.let { permission ->
            PermissionCard(
                permission = permission,
                onPick = { submitPermission(it) },
            )
        }
        pendingForm?.let { form ->
            FormCard(
                form = form,
                fieldIndex = formField,
                onPick = { answerFormValue(it) },
            )
        }

        if (slash.isNotEmpty()) {
            SlashMenu(commands = slash, onPick = { pickSlash(it) })
        }
        if (helpOpen) {
            HelpPanel(
                text = if (ssh != null) SESSION_HELP_SSH else SESSION_HELP,
                onClose = { helpOpen = false },
            )
        }
        if (ssh != null) {
            Text(
                text = "Команды на сервере исполняет телефон — держите Clauden открытым.",
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 1.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "? for shortcuts",
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier.clickable { helpOpen = true },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = buildString {
                    append(session?.model.orEmpty().substringAfter('/').ifBlank { "модель" })
                    val variant = session?.variant.orEmpty()
                    if (variant.isNotBlank()) append(" · ").append(variantLabel(variant))
                },
                style = TermTiny,
                color = TermAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 130.dp)
                    .clickable { openModels() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .background(TermPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, TermBorder, RoundedCornerShape(6.dp))
                    .clickable { filesOpen = true }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ClaudenIcons.folder,
                    contentDescription = null,
                    tint = TermAccent,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("SFTP", style = TermTiny, color = TermAccent)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (ssh != null) "! — команда на SSH-сервере" else "! — только в SSH-сессии",
                style = TermTiny,
                color = TermFaint,
            )
        }
        TerminalPromptBox(
            value = draft,
            onValueChange = { draft = it },
            onSubmit = { submit() },
            placeholder = when {
                pendingForm != null ->
                    pendingForm?.fields?.getOrNull(formField)?.title?.ifBlank { "Ответьте на вопрос" }
                        ?: "Ответьте на вопрос"
                pendingPermission != null -> "1 — разрешить · 2 — всегда · 3 — отклонить"
                session?.working == true -> "Агент работает — /stop чтобы прервать"
                else -> "Что дальше?"
            },
            enabled = session != null,
            busy = sending,
            hint = when {
                diff != null -> "/commit <сообщение> · /back"
                pendingForm != null -> "номер варианта · /stop"
                pendingPermission != null -> "1 · 2 · 3"
                session?.working == true -> "✻ работает · /stop · /help"
                else -> "/files · /diff · /commit · /help"
            },
            hintRight = when {
                session?.source == "device" -> "телефон"
                session?.source == "ssh" -> session?.remote?.root.orEmpty().ifBlank { "сервер" }
                else -> session?.branch.orEmpty()
            },
        )
    }

    if (picker == "models") {
        ModelPickerDialog(
            models = models,
            query = pickerQuery,
            loading = pickerLoading,
            current = session?.model.orEmpty(),
            currentVariant = session?.variant.orEmpty(),
            onQuery = { pickerQuery = it },
            onPick = { chooseModel(it) },
            onClose = { picker = "" },
        )
    }
    variantFor?.let { model ->
        VariantPickerDialog(
            model = model,
            current = session?.variant.orEmpty(),
            onPick = { applyModel(model, it) },
            onClose = { variantFor = null },
        )
    }
    if (picker == "agents") {
        AgentPickerDialog(
            agents = agents,
            query = pickerQuery,
            loading = pickerLoading,
            current = session?.agent.orEmpty(),
            onQuery = { pickerQuery = it },
            onPick = { chooseAgent(it) },
            onClose = { picker = "" },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить сессию?") },
            text = {
                Text(
                    "«${session?.title.orEmpty().ifBlank { session?.repoName.orEmpty() }}» " +
                        "и её папка на сервере будут удалены.",
                    style = TermSmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.remove(sessionId) }
                            .onSuccess { onBack() }
                            .onFailure { note = it.codeMessage }
                    }
                }) { Text("Удалить", color = TermRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }

    if (termuxSetup) {
        TermuxSetupDialog(
            playStore = TermuxBridge.fromPlayStore(context),
            onInstall = {
                runCatching { context.startActivity(TermuxBridge.installIntent()) }
            },
            onOpenTermux = { TermuxBridge.openTermux(context) },
            onClose = { termuxSetup = false },
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
                        style = TermSmall.copy(color = TermText),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (fs?.content.orEmpty().take(300)) +
                            if ((fs?.content?.length ?: 0) > 300) "…" else "",
                        style = TermTiny.copy(color = TermDim),
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { applyWriteJob(pending, always = true) }) {
                        Text("Всегда", fontSize = 12.sp)
                    }
                    TextButton(onClick = { applyWriteJob(pending) }) { Text("Записать") }
                }
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
                        reload()
                    }
                }) { Text("Отклонить", color = TermRed) }
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
                    if (TermuxBridge.needsSetup(result.stderr, result.error)) {
                        termuxSetup = true
                    }
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
}

private const val SESSION_HELP =
    "Диалог\n" +
        "/goal <цель> — держать цель до выполнения (pause/resume/clear)\n" +
        "/review [commit|branch|pr] — ревью изменений\n" +
        "/init — создать или обновить AGENTS.md\n" +
        "/compact — сжать контекст\n" +
        "/undo — откатить последний ход\n" +
        "/redo — вернуть откат\n" +
        "/rename <название> — переименовать сессию\n" +
        "/copy — скопировать последний ответ\n" +
        "/export — скопировать транскрипт\n" +
        "/usage — расход и токены\n" +
        "/status — состояние сессии\n" +
        "\nМодель и агент\n" +
        "/models — выбрать модель\n" +
        "/effort — уровень усилий модели\n" +
        "/agents — выбрать агента\n" +
        "\nФайлы и git\n" +
        "/files — файловый менеджер\n" +
        "/diff — показать изменения\n" +
        "/commit <сообщение> — закоммитить\n" +
        "/push — отправить в репозиторий\n" +
        "/pr <заголовок> | <описание> — создать pull request\n" +
        "\nТелефон (только файлы, Termux отключён)\n" +
        "/sync — забрать изменения на телефон\n" +
        "/upload — отправить папку телефона на сервер\n" +
        "/detach — отключить папку телефона\n" +
        "/folder — выбрать папку телефона\n" +
        "/browse — папка для просмотра агентом\n" +
        "/forget — забыть доступ к папке\n" +
        "\nСессия\n" +
        "/stop — остановить агента\n" +
        "/delete — удалить сессию\n" +
        "/new — к списку сессий\n" +
        "/back — к списку сессий\n" +
        "/menu — меню приложения\n" +
        "\nЗдесь только файлы и правки. Команды — в SSH-сессии, на вашем сервере."

private val SSH_HIDDEN_COMMANDS = setOf(
    "sync", "upload", "detach", "folder", "browse", "forget",
    "diff", "commit", "push", "pr", "review", "init",
)

private val SSH_BLOCKED_COMMANDS = SSH_HIDDEN_COMMANDS

private const val SESSION_HELP_SSH =
    "Диалог\n" +
        "/goal <цель> — держать цель до выполнения (pause/resume/clear)\n" +
        "/review [commit|branch|pr] — ревью изменений\n" +
        "/init — создать или обновить AGENTS.md\n" +
        "/compact — сжать контекст\n" +
        "/undo — откатить последний ход\n" +
        "/redo — вернуть откат\n" +
        "/rename <название> — переименовать сессию\n" +
        "/copy — скопировать последний ответ\n" +
        "/export — скопировать транскрипт\n" +
        "/usage — расход и токены\n" +
        "/status — состояние сессии\n" +
        "\nМодель и агент\n" +
        "/models — выбрать модель\n" +
        "/effort — уровень усилий модели\n" +
        "/agents — выбрать агента\n" +
        "\nФайлы и контекст\n" +
        "/files — SFTP-браузер сервера\n" +
        "/reload — перечитать AGENTS.md, скиллы и команды\n" +
        "/skills — список скиллов на сервере\n" +
        "Корень агента — в SFTP нажмите «Корень» в папке проекта.\n" +
        "\nСессия\n" +
        "/stop — остановить агента\n" +
        "/delete — удалить сессию\n" +
        "/new — к списку сессий\n" +
        "/back — к списку сессий\n" +
        "/menu — меню приложения\n" +
        "\nSSH\n" +
        "терминал — обычная SSH-консоль (кнопка сверху)\n" +
        "!команда — выполнить команду на удалённом сервере.\n" +
        "Агент читает и правит файлы прямо на сервере; пароль и ключ остаются на телефоне."

private const val SHELL_HELP =
    "/project — выбрать папку или репозиторий\n" +
        "/sessions — обновить список сессий\n" +
        "/open N — открыть сессию\n" +
        "/delete N — удалить сессию\n" +
        "/folder — выбрать папку телефона\n" +
        "/browse — папка для просмотра агентом\n" +
        "/forget — забыть доступ к папке\n" +
        "/github <token> — подключить GitHub\n" +
        "/refresh — перечитать список\n" +
        "/clear — очистить вывод\n" +
        "/menu — открыть меню приложения\n" +
        "/ssh — SSH-серверы: терминал и SFTP\n" +
        "\nПросто напишите задачу — создам сессию в выбранном проекте.\n" +
        "Агент умеет read/write/edit/shell/webfetch/websearch, строит план\n" +
        "и задаёт вопросы с вариантами. В сессии: /help — все команды.\n" +
        "Модель и усилия — в сессии: /models и /effort."

private data class SlashCommand(
    val name: String,
    val args: String = "",
    val description: String = "",
)

private val SHELL_COMMANDS = listOf(
    SlashCommand("help", "", "все команды Code"),
    SlashCommand("project", "", "выбрать папку или репозиторий"),
    SlashCommand("sessions", "", "обновить список сессий"),
    SlashCommand("open", "N", "открыть сессию по номеру"),
    SlashCommand("delete", "N", "удалить сессию"),
    SlashCommand("folder", "", "выбрать папку телефона"),
    SlashCommand("browse", "", "папка для просмотра агентом"),
    SlashCommand("forget", "", "забыть доступ к папке"),
    SlashCommand("github", "<token>", "подключить GitHub"),
    SlashCommand("refresh", "", "перечитать список"),
    SlashCommand("clear", "", "очистить вывод"),
    SlashCommand("models", "", "модель — внутри сессии"),
    SlashCommand("effort", "", "усилия — внутри сессии"),
    SlashCommand("menu", "", "меню приложения"),
    SlashCommand("ssh", "", "SSH-серверы: терминал и SFTP"),
)

private val SESSION_COMMANDS = listOf(
    SlashCommand("help", "", "все команды Code"),
    SlashCommand("goal", "<цель>", "держать цель до выполнения"),
    SlashCommand("review", "[commit|branch|pr]", "ревью изменений"),
    SlashCommand("init", "", "создать или обновить AGENTS.md"),
    SlashCommand("compact", "", "сжать контекст"),
    SlashCommand("models", "", "выбрать модель"),
    SlashCommand("effort", "", "уровень усилий модели"),
    SlashCommand("agents", "", "выбрать агента"),
    SlashCommand("undo", "", "откатить последний ход"),
    SlashCommand("redo", "", "вернуть откат"),
    SlashCommand("rename", "<название>", "переименовать сессию"),
    SlashCommand("copy", "", "скопировать последний ответ"),
    SlashCommand("export", "", "скопировать транскрипт"),
    SlashCommand("usage", "", "расход и токены"),
    SlashCommand("status", "", "состояние сессии"),
    SlashCommand("files", "", "файловый менеджер"),
    SlashCommand("diff", "", "показать изменения"),
    SlashCommand("commit", "<сообщение>", "закоммитить изменения"),
    SlashCommand("push", "", "отправить в репозиторий"),
    SlashCommand("pr", "<заголовок> | <описание>", "создать pull request"),
    SlashCommand("stop", "", "остановить агента"),
    SlashCommand("sync", "", "забрать изменения на телефон"),
    SlashCommand("upload", "", "отправить папку телефона на сервер"),
    SlashCommand("detach", "", "отключить папку телефона"),
    SlashCommand("folder", "", "выбрать папку телефона"),
    SlashCommand("browse", "", "папка для просмотра агентом"),
    SlashCommand("forget", "", "забыть доступ к папке"),
    SlashCommand("delete", "", "удалить сессию"),
    SlashCommand("new", "", "к списку сессий"),
    SlashCommand("back", "", "к списку сессий"),
    SlashCommand("menu", "", "меню приложения"),
)

private fun slashMatches(text: String, commands: List<SlashCommand>): List<SlashCommand> {
    if (!text.startsWith("/") || text.contains('\n')) return emptyList()
    val query = text.removePrefix("/").trimStart()
    if (query.contains(' ')) return emptyList()
    val needle = query.lowercase()
    return commands
        .filter { needle.isEmpty() || it.name.startsWith(needle) }
        .distinctBy { it.name }
        .take(12)
}

@Composable
internal fun TerminalTopBar(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    onBack: (() -> Unit)? = null,
    onProjects: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(46.dp)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(
                    ClaudenIcons.arrowBack,
                    contentDescription = "Назад",
                    tint = TermDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            ClaudenLogo(
                modifier = Modifier.size(15.dp),
                tint = TermAccent,
                variant = ClaudenLogoVariant.Static,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TermSmall,
                color = TermDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = TermTiny,
                    color = TermFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
        if (onProjects != null) {
            IconButton(onClick = onProjects, modifier = Modifier.size(36.dp)) {
                Icon(
                    ClaudenIcons.projects,
                    contentDescription = "Проекты",
                    tint = TermFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
            Icon(
                ClaudenIcons.menu,
                contentDescription = "Меню",
                tint = TermFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ClaudeWelcomeBox(
    cwd: String,
    subtitle: String,
    compact: Boolean = false,
    tips: Boolean = false,
    onHelp: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TermAccent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = if (compact) 20.dp else 24.dp,
                    bottom = 12.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClaudenLogo(
                    modifier = Modifier.size(if (compact) 28.dp else 44.dp),
                    tint = TermAccent,
                    variant = ClaudenLogoVariant.Static,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Welcome back!",
                        style = TermBody.copy(fontWeight = FontWeight.SemiBold),
                        color = TermText,
                    )
                    Text(
                        text = subtitle.ifBlank { "Clauden Code" },
                        style = TermTiny,
                        color = TermDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "cwd: $cwd",
                style = TermTiny,
                color = TermFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tips) {
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TermBorder))
                Spacer(Modifier.height(8.dp))
                Text("Tips for getting started", style = TermTiny, color = TermAccent)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Run /init to create AGENTS.md  ·  /help — all commands",
                    style = TermTiny,
                    color = TermDim,
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(start = 16.dp)
                .background(TermBg)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Claude Code v1.12.0", style = TermTiny, color = TermAccent)
            if (onHelp != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "?",
                    style = TermTiny,
                    color = TermFaint,
                    modifier = Modifier.clickable { onHelp() },
                )
            }
        }
    }
}

@Composable
private fun ClaudeSessionHeader(cwd: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermBgTop)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClaudenLogo(
            modifier = Modifier.size(17.dp),
            tint = TermAccent,
            variant = ClaudenLogoVariant.Static,
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Claude Code", style = TermTiny, color = TermAccent)
                Spacer(Modifier.width(8.dp))
                Text(status, style = TermTiny, color = TermFaint)
            }
            Text(
                text = "cwd: $cwd",
                style = TermTiny,
                color = TermFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HelpPanel(text: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(TermPanel, RoundedCornerShape(9.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("справка", style = TermTiny, color = TermAccent, modifier = Modifier.weight(1f))
            Text(
                text = "закрыть ×",
                style = TermTiny,
                color = TermDim,
                modifier = Modifier.clickable { onClose() },
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text, style = TermTiny, color = TermDim)
        }
    }
}

@Composable
private fun SlashMenu(
    commands: List<SlashCommand>,
    onPick: (SlashCommand) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .background(TermPanel, RoundedCornerShape(9.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(9.dp))
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "команды",
            style = TermTiny,
            color = TermFaint,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        commands.forEach { command ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(command) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "/${command.name}",
                    style = TermSmall,
                    color = TermAccent,
                    modifier = Modifier.widthIn(min = 92.dp),
                )
                if (command.args.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(command.args, style = TermTiny, color = TermFaint)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = command.description,
                    style = TermTiny,
                    color = TermDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun sessionCwd(session: CodeSession?): String = when {
    session == null -> "~"
    session.source == "device" -> "~/phone/${session.repoName}"
    session.source == "ssh" -> session.remote?.root.orEmpty().ifBlank { "~" }
    session.repo.startsWith("gh:") -> "~/github/${session.repoName}"
    else -> "~/repos/${session.repoName}"
}

@Composable
private fun SessionMeta(session: CodeSession?, device: CodeDevice?) {
    val cwd = sessionCwd(session)
    Text(
        text = "cwd: $cwd",
        style = TermTiny,
        color = TermFaint,
    )
    Text(
        text = buildString {
            append("status: ").append(statusLabel(session?.status ?: "idle"))
            if (session?.source == "device") {
                append(" · телефон")
                if (device?.attached == true) append(" · ${device.files} файлов")
            } else if (!session?.branch.isNullOrBlank()) {
                append(" · ").append(session.branch)
            }
        },
        style = TermTiny,
        color = TermFaint,
    )
}

@Composable
private fun UserLine(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF232220), RoundedCornerShape(4.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(">", style = TermBody, color = TermAccent)
            Spacer(Modifier.width(8.dp))
            Text("вы", style = TermTiny, color = TermAccent)
        }
        Spacer(Modifier.height(3.dp))
        Text(text, style = TermBody, color = TermText)
    }
}

@Composable
private fun TermDotLabel(text: String, color: Color = TermAccent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = TermTiny, color = color)
    }
}

@Composable
private fun AssistantLine(text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TermDotLabel("claude")
        Spacer(Modifier.height(3.dp))
        TerminalMarkdown(text)
    }
}

@Composable
private fun StepLine(step: CodeStep) {
    val color = when (step.status) {
        "failed" -> TermRed
        "done" -> TermDim
        else -> TermAccent
    }
    Column(Modifier.fillMaxWidth().padding(start = 17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(11.dp))
            Text(
                text = step.title.ifBlank { step.name },
                style = TermSmall,
                color = if (step.status == "failed") TermRed else TermText,
                modifier = Modifier.weight(1f),
            )
            if (step.status == "running") TermSpinner(TermAccent)
        }
        if (step.detail.isNotBlank()) {
            Row(Modifier.padding(start = 17.dp, top = 2.dp)) {
                Text("└", style = TermTiny, color = TermFaint)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = step.detail,
                    style = TermTiny,
                    color = TermDim,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DiffBlock(diff: CodeDiff) {
    TermDotLabel(
        "diff · ${diff.files.size} файл(ов)" + if (diff.truncated) " · обрезано" else "",
    )
    diff.files.take(10).forEach { file ->
        Text(
            text = "${file.status} ${file.path}  +${file.additions} −${file.deletions}",
            style = TermTiny,
            color = TermDim,
        )
    }
    if (!diff.dirty) {
        Text("Изменений нет", style = TermSmall, color = TermFaint)
    }
    val allLines = diff.patch.lines()
    val lines = allLines.take(400)
    lines.forEach { line ->
        val color = when {
            line.startsWith("@@") -> TermAccent
            line.startsWith("+++") || line.startsWith("---") -> TermFaint
            line.startsWith("+") -> TermGreen
            line.startsWith("-") -> TermRed
            else -> TermDim
        }
        Text(line, style = TermTiny, color = color)
    }
    if (allLines.size > lines.size) {
        Text("… патч обрезан", style = TermTiny, color = TermFaint)
    }
    if (diff.dirty) {
        Text("/commit <сообщение> — закоммитить", style = TermTiny, color = TermFaint)
    }
}

@Composable
private fun ShellBlock(entry: CodeShellEntry) {
    val output = listOf(entry.stdout, entry.stderr)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trimEnd()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("!", style = TermBody, color = TermGreen)
            Spacer(Modifier.width(8.dp))
            Text(entry.command, style = TermBody, color = TermText, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(3.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermPanel, RoundedCornerShape(7.dp))
                .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (output.isBlank()) "(нет вывода)" else output,
                style = TermSmall.copy(lineHeight = 17.sp),
                color = if (entry.code == 0) TermDim else TermText,
            )
            Text(
                text = "exit ${entry.code} · ${entry.durationMs} мс",
                style = TermTiny,
                color = if (entry.code == 0) TermFaint else TermRed,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun NoteBlock(text: String, color: Color) {
    Text(text, style = TermSmall, color = color)
}

@Composable
internal fun TermSpinner(tint: Color = TermAccent) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            frame = (frame + 1) % SPIN_FRAMES.size
        }
    }
    Text(SPIN_FRAMES[frame], style = TermSmall, color = tint)
}

private val SPIN_FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

@Composable
private fun TerminalPromptBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    enabled: Boolean,
    busy: Boolean,
    hint: String,
    hintRight: String,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermPanel, RoundedCornerShape(9.dp))
                .border(
                    1.dp,
                    if (focused) TermDim else TermBorder,
                    RoundedCornerShape(9.dp),
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">", style = TermBody, color = TermAccent)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TermBody,
                        color = TermFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled && !busy,
                    textStyle = TermBody.copy(color = TermText),
                    cursorBrush = SolidColor(TermAccent),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSubmit()
                            keyboard?.hide()
                        },
                    ),
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            if (busy) {
                TermSpinner()
            } else {
                IconButton(
                    onClick = {
                        onSubmit()
                        keyboard?.hide()
                    },
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        ClaudenIcons.arrowUp,
                        contentDescription = "Отправить",
                        tint = if (enabled && value.isNotBlank()) TermAccent else TermFaint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp),
        ) {
            Text(
                text = hint,
                style = TermTiny,
                color = TermFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = hintRight,
                style = TermTiny,
                color = TermFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ago(ts: Long): String {
    if (ts <= 0) return ""
    val minutes = (System.currentTimeMillis() - ts) / 60_000
    return when {
        minutes < 1 -> "сейчас"
        minutes < 60 -> "${minutes}м"
        minutes < 24 * 60 -> "${minutes / 60}ч"
        else -> "${minutes / (24 * 60)}д"
    }
}

private fun statusLabel(status: String): String = when (status) {
    "working" -> "работает"
    "waiting_device" -> "ждёт телефон"
    "failed" -> "ошибка"
    "stopped" -> "остановлено"
    "interrupted" -> "прервано"
    else -> "готово"
}

private fun statusColor(status: String): Color = when (status) {
    "working", "waiting_device" -> TermAccent
    "failed" -> TermRed
    else -> TermGreen
}

private fun CodeSession.appendDelta(piece: String): CodeSession = copy(
    messages = messages.mapIndexed { index, message ->
        if (index == messages.lastIndex && message.role != "user") {
            message.copy(text = message.text + piece)
        } else {
            message
        }
    },
)

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
private fun PlanBlock(todos: List<CodeTodo>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermPanel, RoundedCornerShape(8.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TermDotLabel("план")
        Spacer(Modifier.height(5.dp))
        todos.forEach { todo ->
            val (mark, color) = when (todo.status) {
                "completed" -> "✓" to TermGreen
                "in_progress" -> "•" to TermAccent
                "cancelled" -> "×" to TermFaint
                else -> "○" to TermDim
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(mark, style = TermSmall, color = color, modifier = Modifier.width(18.dp))
                Text(
                    text = todo.content,
                    style = TermSmall,
                    color = if (todo.status == "completed") TermFaint else TermText,
                    textDecoration = if (todo.status == "completed") {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FormCard(
    form: CodeForm,
    fieldIndex: Int,
    onPick: (String) -> Unit,
) {
    val field = form.fields.getOrNull(fieldIndex) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(TermPanel, RoundedCornerShape(9.dp))
            .border(1.dp, TermAccent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TermDotLabel(
            "вопрос" + if (form.fields.size > 1) " ${fieldIndex + 1}/${form.fields.size}" else "",
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = field.title.ifBlank { "Вопрос" },
            style = TermSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TermText,
        )
        if (field.description.isNotBlank()) {
            Text(field.description, style = TermTiny, color = TermDim)
        }
        field.options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(option.value) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = "${index + 1})",
                    style = TermSmall,
                    color = TermAccent,
                    modifier = Modifier.width(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(option.label, style = TermSmall, color = TermText)
                    if (option.description.isNotBlank()) {
                        Text(option.description, style = TermTiny, color = TermFaint)
                    }
                }
            }
        }
        if (field.custom) {
            Text(
                "Можно ввести свой ответ в поле ниже.",
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    permission: CodePermission,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(TermPanel, RoundedCornerShape(9.dp))
            .border(1.dp, TermRed.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        TermDotLabel("разрешение · ${permission.action}", color = TermRed)
        if (permission.message.isNotBlank()) {
            Text(permission.message, style = TermSmall, color = TermText)
        }
        permission.resources.take(3).forEach { resource ->
            Text(resource, style = TermTiny, color = TermDim)
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("1" to "Разрешить", "2" to "Всегда", "3" to "Отклонить").forEach { (key, label) ->
                Text(
                    text = "$key) $label",
                    style = TermSmall,
                    color = if (key == "3") TermRed else TermAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(key) }
                        .padding(vertical = 3.dp),
                )
            }
        }
    }
}

private fun providerLabel(raw: String): String = when (raw.lowercase()) {
    "clauden" -> "Clauden"
    else -> raw
}

private fun variantLabel(raw: String): String = when (raw.lowercase()) {
    "none" -> "выкл"
    "minimal" -> "мин"
    "low" -> "низк"
    "medium" -> "сред"
    "high" -> "выс"
    "xhigh" -> "оч.выс"
    "max" -> "макс"
    else -> raw
}

@Composable
private fun VariantPickerDialog(
    model: CodeModel,
    current: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Уровень усилий") },
        text = {
            Column {
                Text(model.name, style = TermSmall, color = TermAccent)
                Text(
                    "Чем выше уровень, тем дольше ответ и больше reasoning.",
                    style = TermTiny,
                    color = TermFaint,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick("") }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "По умолчанию",
                                style = TermSmall,
                                color = if (current.isBlank()) TermAccent else TermText,
                                modifier = Modifier.weight(1f),
                            )
                            if (current.isBlank()) {
                                Text("выбрано", style = TermTiny, color = TermAccent)
                            }
                        }
                    }
                    items(model.variants, key = { it }) { variant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(variant) }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                variantLabel(variant),
                                style = TermSmall,
                                color = if (variant == current) TermAccent else TermText,
                                modifier = Modifier.weight(1f),
                            )
                            Text(variant, style = TermTiny, color = TermFaint)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        },
    )
}

@Composable
private fun ModelPickerDialog(
    models: List<CodeModel>,
    query: String,
    loading: Boolean,
    current: String,
    currentVariant: String,
    onQuery: (String) -> Unit,
    onPick: (CodeModel) -> Unit,
    onClose: () -> Unit,
) {
    val filtered = remember(models, query) {
        val needle = query.trim().lowercase()
        models
            .filter {
                needle.isEmpty() ||
                    it.name.lowercase().contains(needle) ||
                    it.providerID.lowercase().contains(needle) ||
                    it.id.lowercase().contains(needle)
            }
            .distinctBy { it.key }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Модель") },
        text = {
            Column {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TermSmall.copy(color = TermText),
                    cursorBrush = SolidColor(TermAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermBg, RoundedCornerShape(6.dp))
                        .border(1.dp, TermBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("поиск: claude, gpt, gemini…", style = TermSmall, color = TermFaint)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    WorkingLine("Загружаю модели…")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(filtered, key = { it.key }) { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(model) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        style = TermSmall,
                                        color = if (model.key == current) TermAccent else TermText,
                                    )
                                    Text(
                                        text = buildString {
                                            append(providerLabel(model.providerID))
                                            if (model.variants.isNotEmpty()) {
                                                append(" · ").append(model.variants.joinToString("/") {
                                                    variantLabel(it)
                                                })
                                            }
                                        },
                                        style = TermTiny,
                                        color = TermFaint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (model.key == current) {
                                    Text(
                                        text = if (currentVariant.isBlank()) {
                                            "текущая"
                                        } else {
                                            "текущая · ${variantLabel(currentVariant)}"
                                        },
                                        style = TermTiny,
                                        color = TermAccent,
                                        maxLines = 1,
                                    )
                                } else if (model.status != "active") {
                                    Text(model.status, style = TermTiny, color = TermRed)
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "Найдено: ${filtered.size}",
                    style = TermTiny,
                    color = TermFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        },
    )
}

@Composable
private fun AgentPickerDialog(
    agents: List<CodeAgent>,
    query: String,
    loading: Boolean,
    current: String,
    onQuery: (String) -> Unit,
    onPick: (CodeAgent) -> Unit,
    onClose: () -> Unit,
) {
    val filtered = remember(agents, query) {
        val needle = query.trim().lowercase()
        agents.filter {
            needle.isEmpty() ||
                it.name.lowercase().contains(needle) ||
                it.id.lowercase().contains(needle)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Агент") },
        text = {
            Column {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TermSmall.copy(color = TermText),
                    cursorBrush = SolidColor(TermAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermBg, RoundedCornerShape(6.dp))
                        .border(1.dp, TermBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("поиск агента", style = TermSmall, color = TermFaint)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    WorkingLine("Загружаю агентов…")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filtered, key = { it.id }) { agent ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(agent) }
                                    .padding(vertical = 7.dp),
                            ) {
                                Text(
                                    text = agent.name,
                                    style = TermSmall,
                                    color = if (agent.id == current) TermAccent else TermText,
                                )
                                if (agent.description.isNotBlank()) {
                                    Text(
                                        agent.description,
                                        style = TermTiny,
                                        color = TermFaint,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        },
    )
}

private val INLINE_MD = Regex(
    "(\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|`[^`\\n]+`|~~[^~\\n]+~~|\\*[^*\\n]+\\*|_[^_\\n]+_)",
)

private val LINK_MD = Regex("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)")

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val source = LINK_MD.replace(text) { match ->
        "${match.groupValues[1]} (${match.groupValues[2]})"
    }
    var at = 0
    for (match in INLINE_MD.findAll(source)) {
        if (match.range.first > at) append(source.substring(at, match.range.first))
        val raw = match.value
        val double = raw.startsWith("**") || raw.startsWith("__") || raw.startsWith("~~")
        val body = if (double) {
            raw.substring(2, raw.length - 2)
        } else {
            raw.substring(1, raw.length - 1)
        }
        when {
            raw.startsWith("**") || raw.startsWith("__") ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TermText)) { append(body) }
            raw.startsWith("~~") ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = TermDim)) { append(body) }
            raw.startsWith("`") ->
                withStyle(
                    SpanStyle(
                        color = TermAccent,
                        background = Color(0xFF1B1917),
                        fontFamily = ClaudenFonts.mono,
                    ),
                ) { append(body) }
            else ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = TermText)) { append(body) }
        }
        at = match.range.last + 1
    }
    if (at < source.length) append(source.substring(at))
}

@Composable
private fun TerminalMarkdown(text: String, modifier: Modifier = Modifier) {
    val lines = remember(text) { text.lines() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                index++
                val code = StringBuilder()
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[index])
                    index++
                }
                index++
                CodePanel(language, code.toString())
                continue
            }
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                    val title = trimmed.dropWhile { it == '#' }.trim()
                    Text(
                        text = inlineMarkdown(title),
                        style = TermBody.copy(
                            fontSize = when (level) {
                                1 -> 15.sp
                                2 -> 14.sp
                                else -> 13.sp
                            },
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = TermText,
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        Text("•", style = TermSmall, color = TermAccent, modifier = Modifier.width(12.dp))
                        Text(
                            text = inlineMarkdown(trimmed.substring(2)),
                            style = TermBody,
                            color = TermText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Regex("^\\d+[.)]\\s").containsMatchIn(trimmed) -> {
                    val mark = trimmed.takeWhile { it.isDigit() || it == '.' || it == ')' || it == ' ' }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = TermAccent)) { append(mark) }
                            append(inlineMarkdown(trimmed.removePrefix(mark)))
                        },
                        style = TermBody,
                        color = TermText,
                    )
                }
                trimmed.startsWith("> ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("│", style = TermSmall, color = TermAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = inlineMarkdown(trimmed.substring(2)),
                            style = TermSmall,
                            color = TermDim,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                trimmed.startsWith("|") -> {
                    Text(line, style = TermTiny, color = TermDim)
                }
                else -> {
                    Text(
                        text = inlineMarkdown(line),
                        style = TermBody,
                        color = TermText,
                    )
                }
            }
            index++
        }
    }
}

@Composable
private fun CodePanel(language: String, code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermPanel, RoundedCornerShape(8.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (language.isNotBlank()) {
            Text("┌ $language", style = TermTiny, color = TermFaint)
            Spacer(Modifier.height(3.dp))
        }
        Text(
            text = code.trimEnd(),
            style = TermSmall.copy(lineHeight = 17.sp),
            color = TermText,
        )
    }
}
