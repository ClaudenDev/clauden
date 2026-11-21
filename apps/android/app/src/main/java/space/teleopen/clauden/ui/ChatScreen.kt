package space.teleopen.clauden.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.speech.RecognizerIntent
import android.webkit.MimeTypeMap
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.MainActivity
import space.teleopen.clauden.PendingLocation
import space.teleopen.clauden.R
import space.teleopen.clauden.Screen
import space.teleopen.clauden.proxy.ProxyController
import space.teleopen.clauden.proxy.ProxyUi
import space.teleopen.clauden.data.Attachment
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.CalendarRepository
import space.teleopen.clauden.data.ChatMessage
import space.teleopen.clauden.data.ChatFile
import space.teleopen.clauden.data.ChatStep
import space.teleopen.clauden.data.ClaudeCatalog
import space.teleopen.clauden.data.ClaudenCatalog
import space.teleopen.clauden.data.Conversation
import space.teleopen.clauden.data.CustomApi
import space.teleopen.clauden.data.Effort
import space.teleopen.clauden.data.Provider
import space.teleopen.clauden.data.RemoteModel
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeIconMode
import space.teleopen.clauden.data.UiStyle

private val checkBlue = Color(0xFF4C8DF6)
private val badgeBg = Color(0xFF24344D)
private val badgeText = Color(0xFFA7C8FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(state: ChatUiState, vm: ChatViewModel, onMenu: () -> Unit) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val tts = rememberTtsController()
    var showModels by remember { mutableStateOf(false) }
    var input by rememberSaveable(state.activeId) { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf(emptyList<Attachment>()) }
    var openedAttachment by remember { mutableStateOf<Attachment?>(null) }
    var pendingCalendarSend by remember { mutableStateOf<Pair<String, List<Attachment>>?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var voiceActive by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var projectPickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val pending = pendingCalendarSend
        pendingCalendarSend = null
        if (pending != null) {
            val granted = result[Manifest.permission.READ_CALENDAR] == true
            scope.launch {
                val calendar = if (granted) {
                    withContext(Dispatchers.IO) { CalendarRepository.snapshot(context) }
                } else {
                    ""
                }
                vm.send(pending.first, pending.second, calendar)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.onLocationPermissionResult(fine || coarse)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val attachments = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> runCatching { makeImageAttachment(context, uri) }.getOrNull() }
                }
                if (attachments.isNotEmpty()) {
                    val merged = mergeAttachments(pendingAttachments, attachments)
                    if (merged.size < pendingAttachments.size + attachments.size) {
                        vm.notify("Добавлены не все фото: общий лимит вложений 14 МБ")
                    }
                    pendingAttachments = merged
                } else {
                    vm.notify("Не удалось прочитать изображения")
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val attachments = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            if (mime.startsWith("image/")) makeImageAttachment(context, uri)
                            else makeFileAttachment(context, uri)
                        }.getOrNull()
                    }
                }
                if (attachments.isNotEmpty()) {
                    val merged = mergeAttachments(pendingAttachments, attachments)
                    if (merged.size < pendingAttachments.size + attachments.size) {
                        vm.notify("Добавлены не все файлы: общий лимит вложений 14 МБ")
                    }
                    pendingAttachments = merged
                } else {
                    vm.notify("Не удалось прикрепить файлы (каждый до 8 МБ)")
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        cameraUri = null
        if (success && uri != null) {
            scope.launch {
                val attachment = withContext(Dispatchers.IO) {
                    makeImageAttachment(context, uri, "Снимок")
                }
                if (attachment != null) {
                    pendingAttachments = mergeAttachments(pendingAttachments, listOf(attachment))
                } else {
                    vm.notify("Не удалось прочитать снимок")
                }
            }
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceActive = true
        } else {
            vm.notify("Разрешите доступ к микрофону в настройках приложения")
        }
    }

    LaunchedEffect(state.activeId) {
        pendingAttachments = emptyList()
        voiceActive = false
    }

    val conversation = state.active
    val activeProject = state.projects.firstOrNull {
        it.id == (conversation?.projectId ?: state.draftProjectId)
    }

    BackHandler { vm.openChats() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        ChatTopBar(
            title = activeProject?.title ?: conversation?.title ?: "Clauden",
            inProject = activeProject != null,
            onMenu = onMenu,
            onNewChat = { vm.newChat() },
            chat = conversation,
            menuOpen = menuOpen,
            onMenuOpen = { menuOpen = true },
            onMenuDismiss = { menuOpen = false },
            onShare = {
                conversation?.let { chat ->
                    shareText(context, shareChatText(chat))
                }
            },
            onPin = {
                menuOpen = false
                conversation?.let { vm.togglePin(it.id) }
            },
            onRename = {
                menuOpen = false
                renaming = true
            },
            onAddToProject = {
                menuOpen = false
                projectPickerOpen = true
            },
            onAddShortcut = {
                menuOpen = false
                if (conversation != null) {
                    val ok = addChatShortcut(context, conversation)
                    vm.notify(
                        if (ok) "Добавляю ярлык на главный экран"
                        else "Лончер не поддерживает ярлыки",
                    )
                }
            },
            onDelete = {
                menuOpen = false
                conversation?.let { vm.deleteChat(it.id) }
                vm.openChats()
            },
        )

        ProxyFixBanner(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            visible = state.authMode == AuthMode.Claude,
        )

        fun submitText(text: String, attachments: List<Attachment>) {
            if (isFreeClaudenPlan(state)) {

                vm.showFreePlanBlocked()
                return
            }
            input = ""
            voiceActive = false
            pendingAttachments = emptyList()
            if (needsCalendar(text) && !hasCalendarPermission(context)) {
                pendingCalendarSend = text to attachments
                calendarPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    )
                )
            } else {
                scope.launch {
                    val calendar = if (hasCalendarPermission(context)) {
                        withContext(Dispatchers.IO) {
                            CalendarRepository.snapshot(context)
                        }
                    } else {
                        ""
                    }
                    vm.send(text, attachments, calendar)
                }
            }
        }

        val composer: @Composable (Boolean) -> Unit = { inline ->
            if (voiceActive) {
                VoiceBar(
                    initialText = "",
                    onCancel = { voiceActive = false },
                    onSend = { spoken ->
                        val text = listOf(input, spoken)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        submitText(text, pendingAttachments)
                    },
                    onError = vm::notify,
                    inline = inline,
                )
            } else {
                Composer(
                    state = state,
                    value = input,
                    onValueChange = { input = it },
                    onSend = { submitText(input, pendingAttachments) },
                    onStop = vm::stop,
                    attachments = pendingAttachments,
                    onAdd = { showAttachSheet = true },
                    onOpenAttachment = { openedAttachment = it },
                    onRemoveAttachment = { id ->
                        pendingAttachments = pendingAttachments.filterNot { it.id == id }
                    },
                    onVoice = {
                        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) voiceActive = true
                        else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onModels = { showModels = true },
                    inline = inline,
                )
            }
        }

        if (state.messages.isEmpty()) {
            EmptyChat(
                modifier = Modifier.weight(1f),
                projectTitle = activeProject?.title,
                onMode = { mode ->
                    when (mode) {
                        "Написать" -> input = "Помоги написать: "
                        "Учиться" -> input = "Объясни простыми словами: "
                        "Код" -> input = "Напиши код: "
                        "Из Drive" -> filePicker.launch("*/*")
                        "Календарь" -> input = "Что у меня в календаре? "
                    }
                },
                composer = { composer(true) },
            )
            state.pendingLocation
                ?.takeIf { it.chatId == state.activeId }
                ?.let { pending ->
                    LocationPermissionCard(
                        pending = pending,
                        onAllow = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        onDeny = vm::denyLocation,
                    )
                }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    state = state,
                    vm = vm,
                    ttsText = tts.speakingText,
                    onSpeak = { tts.toggle(it) },
                    onRetry = vm::retryLast,
                    onOpenAttachment = { openedAttachment = it },
                )
            }
            state.pendingLocation
                ?.takeIf { it.chatId == state.activeId }
                ?.let { pending ->
                    LocationPermissionCard(
                        pending = pending,
                        onAllow = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        onDeny = vm::denyLocation,
                    )
                }
            composer(false)
        }

        AnimatedVisibility(visible = state.error != null) {
            Banner(
                text = state.error.orEmpty(),
                background = colors.danger.copy(alpha = 0.12f),
                textColor = colors.danger,
                onClose = vm::dismissError,
            )
        }
        AnimatedVisibility(visible = state.notice != null) {
            Banner(
                text = state.notice.orEmpty(),
                background = colors.bg300,
                textColor = colors.textSecondary,
                onClose = vm::dismissNotice,
            )
        }

    }

    if (showAttachSheet) {
        AttachmentSheet(
            projects = state.projects,
            selectedProjectId = state.active?.projectId.orEmpty(),
            webSearch = state.settings.webSearch,
            onWebSearchChange = { enabled ->
                vm.updateSettings { it.copy(webSearch = enabled) }
            },
            onCamera = {
                showAttachSheet = false
                val uri = newCameraUri(context)
                cameraUri = uri
                cameraLauncher.launch(uri)
            },
            onPhotos = {
                showAttachSheet = false
                imagePicker.launch("image/*")
            },
            onFiles = {
                showAttachSheet = false
                filePicker.launch("*/*")
            },
            onProject = { projectId ->
                state.active?.let { vm.setChatProject(it.id, projectId) }
            },
            onConnectors = {
                showAttachSheet = false
                vm.openCustomize("connectors")
            },
            onDismiss = { showAttachSheet = false },
        )
    }

    if (showModels) {
        ModelSheet(
            state = state,
            onDismiss = { showModels = false },
            onSelect = {
                vm.selectModel(it)
                showModels = false
            },
            onEffort = vm::setEffort,
            onCustomSelect = { apiId, model ->
                vm.selectCustomModel(apiId, model)
                showModels = false
            },
            onManageCustom = {
                showModels = false
                vm.openSettingsSection(Screen.CustomApi)
            },
            onSubscribe = {
                showModels = false
                vm.openSettingsSection(Screen.Billing)
            },
            onClaude = {
                showModels = false
                if (state.claudeLinked) vm.switchEngine(AuthMode.Claude)
                else vm.openClaudeLogin()
            },
        )
    }

    if (renaming && conversation != null) {
        RenameDialog(
            chat = conversation,
            onDismiss = { renaming = false },
            onSave = { title ->
                vm.renameChat(conversation.id, title)
                renaming = false
            },
        )
    }

    if (projectPickerOpen && conversation != null) {
        ProjectPickerDialog(
            projects = state.projects,
            selectedId = conversation.projectId,
            onDismiss = { projectPickerOpen = false },
            onPick = { projectId ->
                vm.setChatProject(conversation.id, projectId)
                projectPickerOpen = false
            },
        )
    }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2600)
            vm.dismissNotice()
        }
    }

    openedAttachment?.let { attachment ->
        AttachmentViewer(attachment = attachment, onDismiss = { openedAttachment = null })
    }
}

private fun shareChatText(conversation: Conversation): String = buildString {
    appendLine(conversation.title)
    conversation.messages.forEach { message ->
        appendLine()
        append(if (message.role == "user") "Вы: " else "Clauden: ")
        appendLine(message.content)
    }
}

private fun addChatShortcut(context: Context, chat: Conversation): Boolean {
    val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
    if (!manager.isRequestPinShortcutSupported) return false
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("clauden://chat/${chat.id}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val shortcut = ShortcutInfo.Builder(context, "chat-${chat.id}")
        .setShortLabel(chat.title.ifBlank { "Чат" }.take(40))
        .setLongLabel(chat.title.ifBlank { "Чат" }.take(80))
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    return runCatching {
        manager.requestPinShortcut(shortcut, null)
        true
    }.getOrDefault(false)
}

@Composable
private fun ChatTopBar(
    title: String,
    inProject: Boolean = false,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    chat: Conversation?,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onShare: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onAddToProject: () -> Unit,
    onAddShortcut: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val proxyState by ProxyController.state.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(if (material3) 64.dp else 48.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeIconButton(
            id = "header.menu",
            icon = ClaudenIcons.menu,
            contentDescription = "Меню",
            onClick = onMenu,
            size = 44.dp,
            iconSize = 22.dp,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = if (material3) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inProject) {
                Icon(
                    imageVector = ClaudenIcons.projects,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = if (material3) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (material3) TextAlign.Start else TextAlign.Center,
            )
        }
        ProxyShieldButton(
            state = proxyState,
            onClick = { ProxyUi.open = true },
            size = 32,
            modifier = Modifier.padding(end = 2.dp),
        )

        ThemeIconButton(
            id = "header.new_chat",
            icon = Icons.Default.Add,
            contentDescription = "Новый чат",
            onClick = onNewChat,
            size = 40.dp,
            iconSize = 21.dp,
        )
        if (chat != null) {
            val currentChat = chat
            Box {
                ThemeIconButton(
                    id = "chat.more",
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Ещё",
                    onClick = onMenuOpen,
                    size = 40.dp,
                    iconSize = 19.dp,
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss,
                    containerColor = colors.bg300,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.widthIn(min = 286.dp),
                ) {
                    Text(
                        text = currentChat.title.ifBlank { title },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                        ),
                        color = colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    )
                    ChatMenuItem(
                        label = if (currentChat.pinned) "Открепить" else "Закрепить",
                        icon = ClaudenIcons.pin,
                    ) {
                        onMenuDismiss()
                        onPin()
                    }
                    ChatMenuItem("Поделиться", ClaudenIcons.share) {
                        onMenuDismiss()
                        onShare()
                    }
                    ChatMenuItem("Переименовать", ClaudenIcons.pencil) {
                        onMenuDismiss()
                        onRename()
                    }
                    ChatMenuItem("Добавить в проект", ClaudenIcons.projects) {
                        onMenuDismiss()
                        onAddToProject()
                    }
                    ChatMenuItem("Добавить на главный экран", ClaudenIcons.home) {
                        onMenuDismiss()
                        onAddShortcut()
                    }
                    ChatMenuItem("Удалить", ClaudenIcons.trash, danger = true) {
                        onMenuDismiss()
                        onDelete()
                    }
                }
            }
        }
    }
}

private val streamCodeFenceRegex = Regex(
    "```(?:html|htm|svg|xml|css|js|javascript)[\\s\\S]*?```",
    RegexOption.IGNORE_CASE,
)

@Composable
private fun MessageList(
    state: ChatUiState,
    vm: ChatViewModel,
    ttsText: String?,
    onSpeak: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
) {
    val listState = rememberLazyListState()
    val restoring = state.recoveringId != null && state.recoveringId == state.activeId
    val lastAssistantTs = state.messages.lastOrNull { it.role == "assistant" }?.ts
    val lastRole = state.messages.lastOrNull()?.role
    var knownSize by remember(state.activeId) { mutableIntStateOf(state.messages.size) }

    LaunchedEffect(state.activeId) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex, scrollOffset = 100_000)
        }
    }

    LaunchedEffect(state.activeId, state.messages.size, lastRole) {
        val messages = state.messages
        val grew = messages.size > knownSize
        knownSize = messages.size
        if (messages.isEmpty() || !grew) return@LaunchedEffect
        val last = messages.last()
        when {

            last.role == "assistant" && state.streaming ->
                listState.scrollToItem(messages.lastIndex)

            last.role == "user" ->
                listState.scrollToItem(messages.lastIndex, scrollOffset = 100_000)

            else -> listState.scrollToItem(messages.lastIndex, scrollOffset = 100_000)
        }
    }

    val latestArtifactAt = remember(state.messages) {
        latestArtifactMessageTs(state.messages)
    }
    val latestArtifactMap = remember(state.messages) {
        latestArtifacts(state.messages)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = state.messages,
            key = { message -> message.ts.toString() + message.role },
        ) { message ->
            MessageItem(
                message = message,
                state = state,
                vm = vm,
                fileBase = state.baseUrl,
                streaming = state.streaming,
                restoring = restoring,
                isLastAssistant = message.role == "assistant" && message.ts == lastAssistantTs,
                canRetry = state.canRetry,
                speaking = ttsText == message.content,
                latestArtifactAt = latestArtifactAt,
                latestArtifactMap = latestArtifactMap,
                onSpeak = onSpeak,
                onRetry = onRetry,
                onOpenAttachment = onOpenAttachment,
            )
        }
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    state: ChatUiState,
    vm: ChatViewModel,
    streaming: Boolean,
    restoring: Boolean,
    fileBase: String,
    isLastAssistant: Boolean,
    canRetry: Boolean,
    speaking: Boolean,
    latestArtifactAt: Map<String, Long>,
    latestArtifactMap: Map<String, ChatArtifact>,
    onSpeak: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var vote by remember(message.ts) { mutableIntStateOf(0) }
    var thinkingOpen by remember(message.ts) { mutableStateOf(false) }
    var promptExpanded by remember(message.ts) { mutableStateOf(false) }
    var sourcesOpen by remember(message.ts) { mutableStateOf(false) }
    var stepsOpen by remember(message.ts) { mutableStateOf(false) }
    var openStep by remember(message.ts) { mutableStateOf<space.teleopen.clauden.data.ChatStep?>(null) }
    val restoringThis = restoring && isLastAssistant
    val streamingThis = (streaming || restoringThis) && isLastAssistant
    val userStyle = LocalCustomTheme.current.elements["message.user"]
    val assistantStyle = LocalCustomTheme.current.elements["message.assistant"]
    val selecting = LocalThemeEditState.current.selectionMode
    val userBg = userStyle?.background?.let(::Color) ?: colors.bubble
    val userContent = userStyle?.content?.let(::Color) ?: colors.textPrimary
    val userBorder = userStyle?.border?.let(::Color)
    val assistantBg = assistantStyle?.background?.let(::Color)
    val assistantContent = assistantStyle?.content?.let(::Color) ?: colors.textPrimary
    val assistantBorder = assistantStyle?.border?.let(::Color)
    val assistantClick: (() -> Unit)? = if (selecting) {
        { ThemeController.selectElement("message.assistant", "Ответ ИИ") }
    } else {
        null
    }
    val userClick: (() -> Unit)? = if (selecting) {
        { ThemeController.selectElement("message.user", "Ваше сообщение") }
    } else {
        null
    }

    if (message.role == "user") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            if (message.attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    message.attachments.forEach { attachment ->
                        AttachmentFileCard(
                            attachment = attachment,
                            onOpen = { onOpenAttachment(attachment) },
                        )
                    }
                }
                if (message.content.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
            }
            if (message.content.isNotBlank()) {
                val longPrompt = message.content.length > 700 ||
                    message.content.count { it == '\n' } > 12
                val bubbleShape = LocalClaudenShapes.current.bubble
                Surface(
                    color = userBg,
                    contentColor = userContent,
                    shape = bubbleShape,
                    border = userBorder?.let { BorderStroke(1.dp, it) },
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .glassPanel(bubbleShape, colors.bg.copy(alpha = 0.04f))
                        .then(
                            if (userClick != null) {
                                Modifier.clickable(onClick = userClick)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (longPrompt && !promptExpanded) {

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = ClaudenIcons.doc,
                                    contentDescription = null,
                                    tint = userContent.copy(alpha = 0.72f),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Длинный запрос",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = userContent,
                                    )
                                    Text(
                                        text = "${message.content.length} символов",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = userContent.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = LocalClaudenFonts.current.sans,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                ),
                                color = userContent,
                                modifier = if (longPrompt) {
                                    Modifier
                                        .heightIn(max = 420.dp)
                                        .verticalScroll(rememberScrollState())
                                } else {
                                    Modifier
                                },
                            )
                        }
                        if (longPrompt) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (promptExpanded) "Свернуть" else "Показать полностью",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = colors.clay,
                                modifier = Modifier.clickable { promptExpanded = !promptExpanded },
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = messageStamp(message.ts),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                SmallAction(
                    icon = ClaudenIcons.copy,
                    description = "Копировать",
                    onClick = { clipboard.setText(AnnotatedString(message.content)) },
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (assistantBg != null || assistantBorder != null) {
                    Modifier
                        .clip(LocalClaudenShapes.current.bubble)
                        .background(assistantBg ?: Color.Transparent)
                        .then(
                            assistantBorder?.let {
                                Modifier.border(1.dp, it, LocalClaudenShapes.current.bubble)
                            } ?: Modifier,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier
                },
            )
            .then(
                if (assistantClick != null) {
                    Modifier.clickable(onClick = assistantClick)
                } else {
                    Modifier
                },
            ),
    ) {
        val claudeMode = state.authMode == AuthMode.Claude
        val hasActivity = message.steps.isNotEmpty() ||
            message.thinkingMs > 0 ||
            message.thinking.isNotBlank() ||
            streamingThis ||
            restoringThis
        if (claudeMode) {

            if (message.steps.isNotEmpty()) {
                ClaudeStepsLink(
                    steps = message.steps,
                    running = streamingThis,
                    onOpen = { stepsOpen = true },
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else if (streamingThis) {
                ThinkingRow(label = if (restoringThis) "Работаю…" else "Думаю…")
            }
        } else if (hasActivity) {
            if (message.steps.isNotEmpty()) {

                ClaudeStepsLink(
                    steps = message.steps,
                    running = streamingThis,
                    onOpen = { stepsOpen = true },
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else {

                ThinkingBlock(
                    steps = message.steps,
                    thinking = message.thinking,
                    thinkingMs = message.thinkingMs,
                    running = streamingThis,
                    restoring = restoringThis,
                    answering = streamingThis && message.content.isNotBlank(),
                    elapsedMs = message.liveElapsedMs,
                    startedAt = message.ts,
                    lively = streamingThis,
                    expanded = thinkingOpen,
                    onToggle = { thinkingOpen = !thinkingOpen },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (message.files.isNotEmpty()) {
            message.files.forEach { file ->
                val appear = remember(message.ts, file.url) {
                    MutableTransitionState(!streamingThis)
                }
                LaunchedEffect(appear) {
                    if (!appear.targetState) appear.targetState = true
                }
                AnimatedVisibility(
                    visibleState = appear,
                    enter = fadeIn(tween(240)) +
                        expandVertically(tween(260), expandFrom = Alignment.Top),
                ) {
                    FileCard(file = file, baseUrl = fileBase)
                }
            }
        }
        if (message.actions.isNotEmpty()) {
            message.actions.forEach { action ->
                val appear = remember(message.ts, action.id) {
                    MutableTransitionState(!streamingThis)
                }
                LaunchedEffect(appear) {
                    if (!appear.targetState) appear.targetState = true
                }
                AnimatedVisibility(
                    visibleState = appear,
                    enter = fadeIn(tween(240)) +
                        expandVertically(tween(260), expandFrom = Alignment.Top),
                ) {
                    ActionCard(action)
                }
            }
        }
        if (message.content.isBlank()) {
            if (streamingThis && !hasActivity && !restoringThis && message.actions.isEmpty()) {
                ThinkingRow(label = "Думаю…")
            }
            if (message.interrupted && !streamingThis) {
                Spacer(modifier = Modifier.height(6.dp))
                InterruptedBlock(onRetry = onRetry)
            }
            return@Column
        }
        val hasWidget = message.actions.any { it.type == "widget" }
        val allArtifacts = if (streamingThis) {
            emptyList()
        } else {
            remember(message.content) { parseArtifacts(message.content) }
        }
        val artifacts = allArtifacts.filter { artifact ->
            val latestAt = latestArtifactAt[artifactKey(artifact)]
            latestAt == null || latestAt == message.ts
        }
        val supersededArtifacts = allArtifacts.filter { artifact ->
            val latestAt = latestArtifactAt[artifactKey(artifact)]
            latestAt != null && latestAt != message.ts
        }

        val visibleContent = if (streamingThis) {
            var text = stripArtifacts(message.content)
            if (hasWidget) {
                text = text.replace(streamCodeFenceRegex, "")
            }
            text
        } else {
            remember(message.content, hasWidget) {
                var text = stripArtifacts(message.content)
                if (hasWidget) {

                    text = text.replace(streamCodeFenceRegex, "").trim()
                }
                text
            }
        }
        var openedArtifact by remember { mutableStateOf<ChatArtifact?>(null) }

        Column(modifier = Modifier.padding(start = 8.dp, end = 32.dp)) {
            if (visibleContent.isNotBlank()) {
                val bodyStyle = ClaudenType.serifBody.copy(

                    fontFamily = FontFamily.SansSerif,

                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                )
                if (streamingThis) {
                    Text(
                        text = visibleContent,
                        style = bodyStyle,
                        color = assistantContent,
                    )
                    val custom = LocalCustomTheme.current
                    if (custom.enabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeGenerationIndicator(
                            animation = custom.textAnimation,
                            color = colors.clay,
                            modifier = Modifier.padding(start = 2.dp),
                            customPath = custom.messageLoadingPath,
                        )
                    }
                } else {
                    MarkdownText(
                        text = visibleContent,
                        onOpenCode = { openedArtifact = it },
                        style = bodyStyle,
                        textColor = assistantContent,
                    )
                }
            }
            if (artifacts.isNotEmpty()) {
                if (visibleContent.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    artifacts.forEach { artifact ->
                        ArtifactCard(artifact = artifact) {
                            openedArtifact = artifact
                        }
                    }
                }
            }
            if (supersededArtifacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                supersededArtifacts.forEach { old ->
                    Text(
                        text = "Артефакт «${old.title}» обновлён · открыть новую версию",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.clay,
                        modifier = Modifier
                            .clickable {
                                openedArtifact = latestArtifactMap[artifactKey(old)] ?: old
                            }
                            .padding(vertical = 3.dp),
                    )
                }
            }
        }

        if (message.sources.isNotEmpty()) {
            Box(modifier = Modifier.padding(start = 4.dp, end = 32.dp)) {
                SourcesRow(sources = message.sources) { sourcesOpen = true }
            }
        }
        if (sourcesOpen) {
            SourcesSheet(sources = message.sources) { sourcesOpen = false }
        }
        if (stepsOpen) {
            ClaudeStepsSheet(
                steps = message.steps,
                onOpenStep = { step ->
                    stepsOpen = false
                    openStep = step
                },
                onDismiss = { stepsOpen = false },
            )
        }
        openStep?.let { step ->
            ClaudeStepSheet(
                step = step,
                onDismiss = {
                    openStep = null
                    stepsOpen = true
                },
            )
        }

        openedArtifact?.let { artifact ->
            ArtifactViewer(
                artifact = artifact,
                chatId = state.activeId.orEmpty(),
                state = state,
                vm = vm,
                onDismiss = { openedArtifact = null },
            )
        }
        if (message.interrupted && !streamingThis) {
            Spacer(modifier = Modifier.height(10.dp))
            InterruptedBlock(onRetry = onRetry)
        }
        if (!streamingThis) {
            Row(
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallAction(
                    icon = ClaudenIcons.copy,
                    description = "Копировать",
                    onClick = { clipboard.setText(AnnotatedString(message.content)) },
                )
                SmallAction(
                    icon = ClaudenIcons.share,
                    description = "Поделиться",
                    onClick = { shareText(context, message.content) },
                )
                SmallAction(
                    icon = ClaudenIcons.play,
                    description = "Озвучить",
                    tint = if (speaking) colors.clay else colors.textTertiary,
                    onClick = { onSpeak(message.content) },
                )
                SmallAction(
                    icon = ClaudenIcons.thumbUp,
                    description = "Нравится",
                    tint = if (vote == 1) colors.clay else colors.textTertiary,
                    onClick = { vote = if (vote == 1) 0 else 1 },
                )
                SmallAction(
                    icon = ClaudenIcons.thumbDown,
                    description = "Не нравится",
                    tint = if (vote == -1) colors.clay else colors.textTertiary,
                    onClick = { vote = if (vote == -1) 0 else -1 },
                )
                if (canRetry && isLastAssistant) {
                    SmallAction(
                        icon = Icons.Default.Refresh,
                        description = "Повторить",
                        onClick = onRetry,
                    )
                }
            }
            Text(
                text = "Clauden — это ИИ, который может ошибаться.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 8.dp, end = 32.dp),
            )
        }
    }
}

@Composable
private fun InterruptedBlock(onRetry: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ClaudenIcons.info,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Ответ Claude был прерван.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier.clickable(onClick = onRetry),
            ) {
                Text(
                    text = "Повторить",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = LocalClaudenColors.current.textTertiary,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun FileCard(file: ChatFile, baseUrl: String) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val local = remember(file.localPath) {
        file.localPath.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }
    val url = when {
        file.url.isBlank() -> ""
        file.url.startsWith("http", ignoreCase = true) -> file.url
        else -> baseUrl.trimEnd('/') + file.url
    }
    val size = file.size.takeIf { it > 0 } ?: local?.length() ?: 0L
    val ext = file.name.substringAfterLast('.', "").uppercase()
    val isVideo = ext in setOf("MP4", "MOV", "WEBM", "M4V")
    val mime = file.mime.takeIf { it.contains('/') }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        ?: "application/octet-stream"
    val icon = when {
        isVideo -> ClaudenIcons.play
        ext == "PDF" || ext.startsWith("DOC") || ext.startsWith("PPT") -> ClaudenIcons.artifacts
        else -> ClaudenIcons.code
    }
    Surface(
        color = colors.field,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp, top = 4.dp, bottom = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = local != null || url.isNotBlank()) {
                    if (local != null) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.files",
                            local,
                        )
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(intent) }
                    } else if (url.isNotBlank()) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                }
                .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        ) {
            val badge = remember(file.url) { MutableTransitionState(false) }
            LaunchedEffect(badge) { badge.targetState = true }
            val badgeScale by animateFloatAsState(
                targetValue = if (badge.targetState) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "file-ready",
            )
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.clay.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.clay,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .scale(badgeScale)
                        .clip(CircleShape)
                        .background(colors.clay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Готово",
                        tint = colors.onClay,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = (if (isVideo) "Видео" else ext.ifBlank { "FILE" }) +
                        " · " + humanSize(size) +
                        if (local == null && url.isBlank()) " · недоступен" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
            }
            if (local != null || url.isNotBlank()) {
                IconButton(
                    onClick = {
                        val send = if (local != null) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                local,
                            )
                            Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        } else {
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Поделиться файлом"))
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = ClaudenIcons.share,
                        contentDescription = "Поделиться файлом",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", bytes / 1048576.0)
    bytes >= 1024 -> "${bytes / 1024} КБ"
    else -> "$bytes Б"
}

private fun actionLabel(step: ChatStep): String {
    val title = step.title.ifBlank { step.name }
    val name = step.name.lowercase()
    val lower = title.lowercase()
    return when {
        name == "websearch" || lower.startsWith("ищу:") -> "Ищу в интернете"
        name == "webfetch" || lower.startsWith("открываю:") -> "Открываю страницу"
        name == "read" || lower.startsWith("читаю") -> "Читаю файлы"
        name == "write" || name == "edit" || lower.startsWith("правлю") ||
            lower.startsWith("создаю") -> "Работаю с файлами"
        name == "shell" || name == "bash" -> "Запускаю код"
        name == "task" || name == "agent" -> "Запускаю подзадачу"
        name == "glob" || name == "grep" -> "Ищу в файлах"
        name == "skill" -> "Загружаю навык"
        else -> "Работаю"
    }
}

private data class ActionGroup(
    val key: String,
    val label: String,
    val count: Int,
    val failed: Boolean,
)

private fun groupActions(steps: List<ChatStep>): List<ActionGroup> {
    val order = mutableListOf<String>()
    val labels = mutableMapOf<String, String>()
    val counts = mutableMapOf<String, Int>()
    val failed = mutableSetOf<String>()
    steps.forEach { step ->

        if (step.name.lowercase() == "skill") return@forEach
        val key = actionLabel(step)
        if (key !in labels) {
            labels[key] = key
            order += key
        }
        counts[key] = (counts[key] ?: 0) + 1
        if (step.status == "failed") failed += key
    }
    return order.map { key ->
        ActionGroup(
            key = key,
            label = labels.getValue(key),
            count = counts.getValue(key),
            failed = key in failed,
        )
    }
}

private fun actionIcon(key: String): ImageVector = when (key) {
    "Ищу в интернете", "Открываю страницу" -> ClaudenIcons.globe
    "Запускаю код" -> ClaudenIcons.code
    "Работаю с файлами", "Читаю файлы", "Ищу в файлах" -> ClaudenIcons.doc
    else -> ClaudenIcons.spark
}

@Composable
private fun ThinkingBlock(
    steps: List<ChatStep>,
    thinking: String,
    thinkingMs: Long,
    running: Boolean,
    restoring: Boolean,
    answering: Boolean,
    elapsedMs: Long = 0L,
    startedAt: Long = 0L,
    lively: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val visibleSteps = remember(steps) {
        steps.filterNot { it.name.equals("skill", ignoreCase = true) }
    }
    val active = visibleSteps.lastOrNull { it.status == "running" }
    val failed = visibleSteps.any { it.status == "failed" }

    val longRender = visibleSteps.any { step ->
        val haystack = "${step.name} ${step.title} ${step.detail}".lowercase()
        listOf("video", "видео", "render", "рендер", "ffmpeg", "mp4")
            .any { haystack.contains(it) }
    }

    var tick by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(running, restoring) {
        while (running || restoring) {
            tick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val liveElapsed = when {
        elapsedMs > 0 -> elapsedMs
        startedAt > 0 && (running || restoring) ->
            (tick - startedAt).coerceAtLeast(0L)
        else -> 0L
    }
    val label = when {
        restoring -> {

            val base = active?.let { actionLabel(it) + "…" } ?: "Работаю…"
            if (liveElapsed > 0) base + " " + durationLabel(liveElapsed) else base
        }
        answering -> if (thinkingMs > 0) "Думал " + durationLabel(thinkingMs) else "Думаю…"
        active != null -> {
            val base = actionLabel(active) + "…"
            if (liveElapsed > 10_000) base + " " + durationLabel(liveElapsed) else base
        }
        running -> "Думаю…"
        thinkingMs > 0 -> "Думал " + durationLabel(thinkingMs)
        else -> "Готово"
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = tween(180),
        label = "chevron",
    )
    val groups = remember(steps) { groupActions(steps) }
    val reasoning = thinking.trim()
    val expandable = groups.isNotEmpty() || reasoning.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(end = 32.dp)) {
        Surface(
            color = colors.bg300.copy(alpha = if (colors.isDark) 1f else 0.72f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = expandable, onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (failed && !running) {
                    Icon(
                        imageVector = ClaudenIcons.warning,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    ClaudenLogo(
                        modifier = Modifier.size(18.dp),
                        tint = if (running || lively) colors.clay else colors.textTertiary,
                        variant = when {
                            running -> ClaudenLogoVariant.Spin
                            lively -> ClaudenLogoVariant.Eyes
                            else -> ClaudenLogoVariant.Static
                        },
                        animated = running || lively,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (expandable) {
                    Icon(
                        imageVector = ClaudenIcons.chevronRight,
                        contentDescription = if (expanded) "Свернуть" else "Подробнее",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(chevron),
                    )
                }
            }
        }

        if (running && !restoring && liveElapsed > 60_000L) {
            Text(
                text = if (longRender) {
                    "Рендер видео может занять 10–15 минут. Остановка прервёт задачу."
                } else {
                    "Долгая задача может занять несколько минут. Остановка прервёт её."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded && expandable,
            enter = fadeIn(tween(160)) + expandVertically(tween(220), expandFrom = Alignment.Top),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (reasoning.isNotEmpty()) {
                    Text(
                        text = if (running) "Размышляю…" else "Размышления",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    Text(
                        text = reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    if (groups.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                }
                if (groups.isNotEmpty()) {
                    Text(
                        text = "Действия",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                    groups.forEach { group ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 3.dp, bottom = 3.dp),
                        ) {
                            Icon(
                                imageVector = actionIcon(group.key),
                                contentDescription = null,
                                tint = if (group.failed) colors.textSecondary else colors.textTertiary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            if (group.count > 1) {
                                Text(
                                    text = "${group.count}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    color = colors.textTertiary,
                                )
                            }
                            if (group.failed) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = ClaudenIcons.warning,
                                    contentDescription = "Была ошибка",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun durationLabel(ms: Long): String = when {
    ms >= 60 * 60_000 -> "${ms / (60 * 60_000)} ч"
    ms >= 60_000 -> "${ms / 60_000} мин"
    ms >= 10_000 -> "${ms / 1000} с"
    ms >= 1_000 -> "${(ms / 100) / 10f} с"
    else -> "меньше секунды"
}

@Composable
private fun ThinkingRow(
    label: String = "Думаю…",
    variant: ClaudenLogoVariant = ClaudenLogoVariant.Spin,
) {
    val colors = LocalClaudenColors.current
    val custom = LocalCustomTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        if (custom.enabled) {
            ThemeThinkingLogo(
                animation = custom.thinkingAnimation,
                enabled = true,
                tint = colors.clay,
                modifier = Modifier.size(22.dp),
                customPath = custom.thinkingLoadingPath.ifBlank { custom.messageLoadingPath },
            )
        } else {
            ClaudenLogo(
                modifier = Modifier.size(22.dp),
                tint = colors.clay,
                variant = variant,
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun ClaudenHero(modifier: Modifier = Modifier) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val shape = LocalClaudenShapes.current.hero
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (material3) {
                    Brush.verticalGradient(
                        listOf(colors.bubble, colors.bg300),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFF211A16), Color(0xFF120F0E)),
                    )
                }
            )
            .then(
                if (material3) Modifier else Modifier.border(1.dp, Color(0x18FFFFFF), shape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        ClaudenLogo(
            modifier = Modifier.size(92.dp),
            tint = if (material3) colors.clay else Color(0xFFF7F0E9),
            variant = ClaudenLogoVariant.Jelly,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(
    modifier: Modifier = Modifier,
    projectTitle: String? = null,
    onMode: (String) -> Unit,
    composer: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val imeVisible = WindowInsets.isImeVisible

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.45f))

        AnimatedVisibility(
            visible = !imeVisible,
            modifier = Modifier.fillMaxWidth(),
            enter = fadeIn(tween(240)) + expandVertically(tween(280), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom),
        ) {
            ClaudenHero(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(198.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (projectTitle != null) {
                buildAnnotatedString {
                    append("Чем помочь с проектом ")
                    withStyle(SpanStyle(color = colors.clay)) { append(projectTitle) }
                    append("?")
                }
            } else {
                AnnotatedString("Чем помочь?")
            },
            style = if (LocalUiStyle.current == UiStyle.Material3) {
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Normal,
                )
            } else {
                ClaudenType.serifTitle.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                )
            },
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        composer()
        Spacer(modifier = Modifier.height(16.dp))
        ModeTabs(onPick = onMode)
        Spacer(modifier = Modifier.weight(0.85f))
    }
}

private data class ModeChip(val label: String, val icon: ImageVector)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeTabs(onPick: (String) -> Unit) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    val chips = listOf(
        ModeChip("Написать", Icons.Default.Edit),
        ModeChip("Учиться", ClaudenIcons.doc),
        ModeChip("Код", ClaudenIcons.code),
        ModeChip("Из Drive", ClaudenIcons.projects),
        ModeChip("Календарь", ClaudenIcons.calendar),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val themeId = "chat.mode:" + chip.label.trim().lowercase()
            val style = LocalCustomTheme.current.elements[themeId]
            val selecting = LocalThemeEditState.current.selectionMode
            val action: () -> Unit = if (selecting) {
                { ThemeController.selectElement(themeId, "Подсказка «${chip.label}»") }
            } else {
                { onPick(chip.label) }
            }
            val customBg = style?.background?.let(::Color)
            val customContent = style?.content?.let(::Color)
            val customBorder = style?.border?.let(::Color)
            val iconPath = LocalCustomTheme.current.icons[themeId]
            val fullImage = iconPath != null &&
                style?.iconMode != ThemeIconMode.Inline &&
                style?.iconMode != null
            if (fullImage) {
                ThemeElementFrame(
                    style = style,
                    iconPath = iconPath,
                    fallback = customBg ?: Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .clickable(onClick = action),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = customContent ?: colors.textSecondary,
                        )
                    }
                }
            } else if (material3) {
                SuggestionChip(
                    onClick = action,
                    label = {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = customContent ?: colors.textSecondary,
                        )
                    },
                    icon = {
                        if (iconPath != null) {
                            ThemeAssetImage(
                                path = iconPath,
                                modifier = Modifier.size(18.dp),
                                contentDescription = null,
                                contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                                    ContentScale.Crop
                                } else {
                                    ContentScale.Fit
                                },
                            )
                        } else {
                            Icon(
                                imageVector = chip.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = customBg ?: Color.Transparent,
                        labelColor = customContent ?: colors.textSecondary,
                        iconContentColor = customContent ?: colors.textTertiary,
                    ),
                    border = BorderStroke(1.dp, customBorder ?: colors.border),
                )
            } else {
                ThemeElementFrame(
                    style = style,
                    iconPath = null,
                    fallback = customBg ?: Color.Transparent,
                    shape = LocalClaudenShapes.current.chip,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable(onClick = action),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        if (iconPath != null) {
                            ThemeAssetImage(
                                path = iconPath,
                                modifier = Modifier.size(16.dp),
                                contentDescription = null,
                                contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                                    ContentScale.Crop
                                } else {
                                    ContentScale.Fit
                                },
                            )
                        } else {
                            Icon(
                                imageVector = chip.icon,
                                contentDescription = null,
                                tint = customContent ?: colors.textTertiary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
                            ),
                            color = customContent ?: colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    state: ChatUiState,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    attachments: List<Attachment>,
    onAdd: () -> Unit,
    onOpenAttachment: (Attachment) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoice: () -> Unit,
    onModels: () -> Unit,
    inline: Boolean = false,
) {
    val colors = LocalClaudenColors.current
    val busy = state.streaming ||
        (state.recoveringId != null && state.recoveringId == state.activeId)
    val canSend = (value.isNotBlank() || attachments.isNotEmpty()) && !busy
    val freeClauden = isFreeClaudenPlan(state)
    val modelLabel = if (freeClauden) {
        "Clauden по подписке"
    } else {
        listOf(shortModelName(state), state.effort.label)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    val activeProject = state.projects.firstOrNull {
        it.id == (state.active?.projectId ?: state.draftProjectId)
    }
    val placeholder = activeProject?.let {
        "Общайтесь с Clauden в проекте «${it.title}»..."
    } ?: "Ответьте Clauden…"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (inline) {
                    Modifier
                } else {
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .navigationBarsPadding()
                        .imePadding()
                }
            ),
    ) {
        val material3 = LocalUiStyle.current == UiStyle.Material3
        val selecting = LocalThemeEditState.current.selectionMode
        val sendStyle = LocalCustomTheme.current.elements["chat.send"]
        val modelStyle = LocalCustomTheme.current.elements["chat.model"]
        val modelIconPath = LocalCustomTheme.current.icons["chat.model"]
        val modelIconMode = modelStyle?.iconMode ?: ThemeIconMode.Inline
        val modelFullImage = modelIconPath != null && modelIconMode != ThemeIconMode.Inline
        val sendIconPath = LocalCustomTheme.current.icons["chat.send"]
        val sendIconMode = sendStyle?.iconMode ?: ThemeIconMode.Inline
        val sendAction: () -> Unit = {
            if (selecting) {
                ThemeController.selectElement("chat.send", "Кнопка отправки")
            } else {
                when {
                    busy -> onStop()
                    canSend -> onSend()
                }
            }
        }
        val modelAction: () -> Unit = {
            if (selecting) {
                ThemeController.selectElement("chat.model", "Выбор модели")
            } else {
                onModels()
            }
        }
        val composerShape = if (material3) {
            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
        } else {
            RoundedCornerShape(26.dp)
        }
        Surface(
            color = if (material3) colors.field else colors.bg300.copy(alpha = 0.16f),
            shape = composerShape,
            border = if (material3) null else BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(composerShape, colors.bg300.copy(alpha = 0.06f)),
        ) {
            Column {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
            ) {
                if (attachments.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                    ) {
                        attachments.forEach { attachment ->
                            AttachmentPreviewCard(
                                attachment = attachment,
                                onRemove = { onRemoveAttachment(attachment.id) },
                                onOpen = { onOpenAttachment(attachment) },
                            )
                        }
                    }
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = LocalClaudenFonts.current.sans,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(colors.clay),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp)
                        .padding(horizontal = 2.dp),
                    maxLines = 8,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = LocalClaudenFonts.current.sans,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                    ),
                                    color = colors.textTertiary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (material3) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(48.dp),
                    ) {
                        FilledTonalIconButton(onClick = onAdd) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Добавить вложение",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        if (modelFullImage) {
                            ThemeElementFrame(
                                style = modelStyle,
                                iconPath = modelIconPath,
                                fallback = colors.control,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable(onClick = modelAction),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                ) {
                                    Text(
                                        text = modelLabel.ifBlank { "Модель" },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = modelStyle?.content?.let(::Color) ?: colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 150.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        ClaudenIcons.chevronRight,
                                        contentDescription = null,
                                        tint = modelStyle?.content?.let(::Color) ?: colors.textTertiary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(90f),
                                    )
                                }
                            }
                        } else {
                            AssistChip(
                                onClick = modelAction,
                                label = {
                                    Text(
                                        text = modelLabel.ifBlank { "Модель" },
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 150.dp),
                                    )
                                },
                                leadingIcon = if (modelIconPath != null) {
                                    {
                                        ThemeAssetImage(
                                            path = modelIconPath,
                                            modifier = Modifier.size(18.dp),
                                            contentDescription = null,
                                            contentScale = if (modelIconMode == ThemeIconMode.Fill) {
                                                ContentScale.Crop
                                            } else {
                                                ContentScale.Fit
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                                trailingIcon = {
                                    Icon(
                                        ClaudenIcons.chevronRight,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(90f),
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = modelStyle?.background?.let(::Color) ?: colors.control,
                                    labelColor = modelStyle?.content?.let(::Color) ?: colors.textPrimary,
                                    leadingIconContentColor = modelStyle?.content?.let(::Color) ?: colors.textPrimary,
                                    trailingIconContentColor = modelStyle?.content?.let(::Color)
                                        ?: colors.textTertiary,
                                ),
                                border = null,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = onVoice) {
                            Icon(
                                imageVector = ClaudenIcons.mic,
                                contentDescription = "Голосовой ввод",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = sendAction,
                            enabled = selecting || canSend || busy,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (sendIconMode != ThemeIconMode.Inline && sendIconPath != null) {
                                    Color.Transparent
                                } else {
                                    sendStyle?.background?.let(::Color) ?: colors.clay
                                },
                                contentColor = sendStyle?.content?.let(::Color) ?: colors.onClay,
                                disabledContainerColor = colors.control,
                                disabledContentColor = colors.textTertiary,
                            ),
                        ) {
                            if (busy && !selecting) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(sendStyle?.content?.let(::Color) ?: colors.onClay),
                                )
                            } else if (sendIconPath != null) {
                                ThemeAssetImage(
                                    path = sendIconPath,
                                    modifier = if (sendIconMode == ThemeIconMode.Inline) {
                                        Modifier.size(22.dp)
                                    } else {
                                        Modifier.fillMaxSize()
                                    },
                                    contentDescription = "Отправить",
                                    contentScale = if (sendIconMode == ThemeIconMode.Inline) {
                                        ContentScale.Fit
                                    } else {
                                        ContentScale.Crop
                                    },
                                )
                            } else {
                                Icon(
                                    ClaudenIcons.arrowUp,
                                    contentDescription = "Отправить",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(42.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Добавить вложение",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ThemeElementFrame(
                            style = modelStyle,
                            iconPath = modelIconPath.takeIf { modelFullImage },
                            fallback = colors.control,
                            shape = CircleShape,
                            modifier = Modifier
                                .height(36.dp)
                                .clickable(onClick = modelAction),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                if (!modelFullImage && modelIconPath != null) {
                                    ThemeAssetImage(
                                        path = modelIconPath,
                                        modifier = Modifier.size(16.dp),
                                        contentDescription = null,
                                        contentScale = if (modelIconMode == ThemeIconMode.Fill) {
                                            ContentScale.Crop
                                        } else {
                                            ContentScale.Fit
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = modelLabel.ifBlank { "Модель" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = LocalClaudenFonts.current.sans,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = modelStyle?.content?.let(::Color) ?: colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    ClaudenIcons.chevronRight,
                                    contentDescription = null,
                                    tint = modelStyle?.content?.let(::Color) ?: colors.textTertiary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .rotate(90f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.control)
                                .clickable(onClick = onVoice),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ClaudenIcons.mic,
                                contentDescription = "Голосовой ввод",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (sendIconMode != ThemeIconMode.Inline && sendIconPath != null) {
                                        Color.Transparent
                                    } else if (selecting || canSend || busy) {
                                        sendStyle?.background?.let(::Color) ?: colors.clay
                                    } else {
                                        colors.control
                                    }
                                )
                                .clickable(onClick = sendAction),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                busy && !selecting -> Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(sendStyle?.content?.let(::Color) ?: colors.onClay),
                                )

                                sendIconPath != null -> ThemeAssetImage(
                                    path = sendIconPath,
                                    modifier = if (sendIconMode == ThemeIconMode.Inline) {
                                        Modifier.size(20.dp)
                                    } else {
                                        Modifier.fillMaxSize()
                                    },
                                    contentDescription = "Отправить",
                                    contentScale = if (sendIconMode == ThemeIconMode.Inline) {
                                        ContentScale.Fit
                                    } else {
                                        ContentScale.Crop
                                    },
                                )

                                else -> Icon(
                                    ClaudenIcons.arrowUp,
                                    contentDescription = "Отправить",
                                    tint = if (canSend || selecting) {
                                        sendStyle?.content?.let(::Color) ?: colors.onClay
                                    } else {
                                        colors.textTertiary
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (material3) {
                HorizontalDivider(
                    color = if (value.isNotBlank()) colors.clay else colors.borderStrong,
                    thickness = 1.dp,
                )
            }
        }
    }
    }
}

private fun newCameraUri(context: Context): Uri = cameraOutputUri(context)

private fun makeAttachment(context: Context, uri: Uri): Attachment? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull() ?: return null

    val longest = max(decoded.width, decoded.height).toFloat()
    val scale = if (longest > 1600f) 1600f / longest else 1f
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }

    val output = ByteArrayOutputStream()
    val compressed = runCatching {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
    }.getOrDefault(false)
    if (bitmap !== decoded) decoded.recycle()
    bitmap.recycle()
    if (!compressed) return null

    val bytes = output.toByteArray()
    if (bytes.isEmpty() || bytes.size > 4_000_000) return null
    return Attachment(
        id = UUID.randomUUID().toString(),
        name = "Изображение",
        mime = "image/jpeg",
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private fun hasCalendarPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private fun needsCalendar(text: String): Boolean {
    val lower = text.lowercase()
    val explicit = listOf(
        "календар",
        "встреч",
        "событи",
        "созвон",
        "расписан",
        "напомни",
        "calendar",
        "meeting",
        "schedule",
        "event",
    ).any { lower.contains(it) }
    if (explicit) return true

    val day = listOf("завтра", "сегодня", "понедельник", "вторник", "среду", "четверг", "пятниц", "суббот", "воскресень")
        .any { lower.contains(it) }
    val personal = listOf("у меня", "мой ", "мои ", "занят", "свобод", "расписан")
        .any { lower.contains(it) }
    return day && personal
}

private fun hasCyrillic(text: String): Boolean =
    text.any { it in '\u0400'..'\u04FF' }

private fun messageStamp(ts: Long): String {
    if (ts <= 0L) return ""
    return SimpleDateFormat("d MMM", Locale("ru")).format(Date(ts))
}

private fun shortModelName(state: ChatUiState): String {
    val label = state.models.firstOrNull { it.id == state.model }?.label
        ?: ClaudenCatalog.labelFor(state.model)
    return label.removePrefix("Claude ").trim()
}

private fun isFreeClaudenPlan(state: ChatUiState): Boolean =
    state.authMode == AuthMode.Clauden &&
        state.provider == Provider.Clauden &&
        (state.settings.plan.isBlank() ||
            state.settings.plan.equals("Free", ignoreCase = true))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onEffort: (Effort) -> Unit,
    onCustomSelect: (String, RemoteModel) -> Unit,
    onManageCustom: () -> Unit,
    onSubscribe: () -> Unit,
    onClaude: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    var view by rememberSaveable { mutableStateOf(ModelSheetView.Main) }
    var customProviderId by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val claudeMode = state.authMode == AuthMode.Claude
    val customOnly = state.provider == Provider.Custom && state.auth.token.isBlank()
    val mainModels = when {
        customOnly -> state.models

        claudeMode -> ClaudeCatalog.modelsForPlan(state.claudePlan, state.models)
        state.provider == Provider.Anthropic -> state.models
        else -> ClaudenCatalog.mainModels
    }
    val moreModels = if (claudeMode || customOnly || state.provider == Provider.Anthropic) {
        emptyList()
    } else {
        ClaudenCatalog.legacyModels
    }
    val showMore = if (claudeMode) {
        state.customApis.isNotEmpty()
    } else {
        state.customApis.isNotEmpty() || (!customOnly && state.provider != Provider.Anthropic)
    }
    val locksApply = state.authMode == AuthMode.Clauden && !customOnly &&
        state.provider != Provider.Anthropic

    BackHandler(enabled = view != ModelSheetView.Main) {
        view = if (view == ModelSheetView.Custom) ModelSheetView.More else ModelSheetView.Main
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
        dragHandle = if (LocalUiStyle.current == UiStyle.Material3) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            if (isFreeClaudenPlan(state) && view == ModelSheetView.Main) {
                FreeClaudenContent(
                    onClose = onDismiss,
                    onSubscribe = onSubscribe,
                    onClaude = onClaude,
                    onMore = { view = ModelSheetView.More },
                )
            } else {
                when (view) {
                    ModelSheetView.Main -> ModelMainContent(
                        state = state,
                        models = mainModels,
                        showMore = showMore,
                        locksApply = locksApply,
                        onClose = onDismiss,
                        onSelect = onSelect,
                        onMore = { view = ModelSheetView.More },
                        onEffort = { view = ModelSheetView.Effort },
                    )

                    ModelSheetView.Effort -> EffortContent(
                        state = state,
                        onBack = { view = ModelSheetView.Main },
                        onSelect = onEffort,
                    )

                    ModelSheetView.More -> MoreModelsContent(
                        state = state,
                        models = moreModels,
                        locksApply = locksApply,
                        onBack = { view = ModelSheetView.Main },
                        onSelect = onSelect,
                        onProvider = {
                            customProviderId = it
                            view = ModelSheetView.Custom
                        },
                        onManage = onManageCustom,
                    )

                    ModelSheetView.Custom -> CustomModelsContent(
                        state = state,
                        api = state.customApis.firstOrNull { it.id == customProviderId },
                        onBack = { view = ModelSheetView.More },
                        onSelect = onCustomSelect,
                        onManage = onManageCustom,
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeClaudenContent(
    onClose: () -> Unit,
    onSubscribe: () -> Unit,
    onClaude: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    SheetHeader(
        title = "Clauden",
        onLeft = onClose,
        leftIsBack = false,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
        Text(
            text = "Clauden по подписке",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "На тарифе Free модели Clauden недоступны. Бесплатно можно " +
                "работать через свой аккаунт Claude — откройте режим Claude.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(22.dp))
        WhitePillButton(text = "Оформить подписку", onClick = onSubscribe)
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = colors.field,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClaude),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "Войти через Claude",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = ClaudenIcons.chevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = colors.field,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onMore),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "Свой API",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "OpenRouter, Qwen, Groq…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
                Icon(
                    imageVector = ClaudenIcons.chevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

private enum class ModelSheetView { Main, Effort, More, Custom }

@Composable
private fun ModelMainContent(
    state: ChatUiState,
    models: List<RemoteModel>,
    showMore: Boolean,
    locksApply: Boolean,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
    onEffort: () -> Unit,
) {
    val colors = LocalClaudenColors.current

    SheetHeader(
        title = "Выбор модели",
        onLeft = onClose,
        leftIsBack = false,
        loading = state.loadingModels,
    )
    Spacer(modifier = Modifier.height(8.dp))

    ModelGroup {
        if (models.isEmpty()) {
            Text(
                text = "Модели не загружены. Проверьте ключ в настройках.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(18.dp),
            )
        } else {
            models.forEachIndexed { index, model ->
                val locked = locksApply &&
                    ClaudenCatalog.isLockedForPlan(state.settings.plan, model.id)
                ModelCardRow(
                    model = model,
                    selected = model.id == state.model,
                    locked = locked,
                    showDescription = true,
                    onClick = { onSelect(model.id) },
                )
                if (index != models.lastIndex) {
                    HorizontalDivider(color = colors.border)
                }
            }
        }
    }

    if (state.authMode == AuthMode.Clauden &&
        state.provider == Provider.Clauden &&
        isFreeClaudenPlan(state)
    ) {
        LockedModelsHint()
    } else if (locksApply && !ClaudenCatalog.isMaxUltraPlan(state.settings.plan)) {
        Text(
            text = "Fable 5.2 доступна только на тарифах Max и Ultra.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }

    if (showMore) {
        Spacer(modifier = Modifier.height(10.dp))
        SheetActionRow(text = "Другие модели", onClick = onMore)
    }

    Spacer(modifier = Modifier.height(10.dp))
    SheetActionRow(
        text = "Уровень усилий",
        value = state.effort.label,
        onClick = onEffort,
    )

    if (state.provider != Provider.Custom) {
        Text(
            text = buildAnnotatedString {
                append("Fable 5.2 доступна только на Max и Ultra. Fable 5.1 — на Max и Ultra ")
                append("или с кредитами использования в плане Pro. ")
                withStyle(SpanStyle(color = colors.blueLink)) { append("Подробнее") }
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun EffortContent(
    state: ChatUiState,
    onBack: () -> Unit,
    onSelect: (Effort) -> Unit,
) {
    val colors = LocalClaudenColors.current

    SheetHeader(
        title = "Уровень усилий",
        onLeft = onBack,
        leftIsBack = true,
    )
    Spacer(modifier = Modifier.height(8.dp))

    ModelGroup {
        Effort.entries.forEachIndexed { index, effort ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(effort) }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = effort.label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = colors.textPrimary,
                )
                if (effort == Effort.Medium) {
                    Spacer(modifier = Modifier.width(9.dp))
                    Surface(color = colors.control, shape = RoundedCornerShape(50)) {
                        Text(
                            text = "Рекомендуется",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                if (effort == Effort.Maximum) {
                    Spacer(modifier = Modifier.width(9.dp))
                    WarningPill()
                }
                Spacer(modifier = Modifier.weight(1f))
                if (effort == state.effort) {
                    Icon(
                        imageVector = ClaudenIcons.check,
                        contentDescription = "Выбрано",
                        tint = colors.blueLink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (index != Effort.entries.lastIndex) {
                HorizontalDivider(color = colors.border)
            }
        }
    }

    Text(
        text = "Чем выше уровень усилий, тем более тщательные ответы — но это " +
            "занимает больше времени и быстрее расходует лимиты использования.",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        color = colors.textTertiary,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
    )
}

@Composable
private fun MoreModelsContent(
    state: ChatUiState,
    models: List<RemoteModel>,
    locksApply: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onProvider: (String) -> Unit,
    onManage: () -> Unit,
) {
    val colors = LocalClaudenColors.current

    SheetHeader(
        title = "Другие модели",
        onLeft = onBack,
        leftIsBack = true,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (models.isNotEmpty()) {
        ModelGroup {
            models.forEachIndexed { index, model ->
                val locked = locksApply &&
                    ClaudenCatalog.isLockedForPlan(state.settings.plan, model.id)
                ModelCardRow(
                    model = model,
                    selected = model.id == state.model,
                    locked = locked,
                    showDescription = true,
                    onClick = { onSelect(model.id) },
                )
                if (index != models.lastIndex) {
                    HorizontalDivider(color = colors.border)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = "Свои API",
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = 22.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (state.customApis.isEmpty()) {
        Text(
            text = "OpenRouter, Qwen, Groq, локальный сервер — любой " +
                "OpenAI-совместимый API. Ключ хранится только на телефоне.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
        )
    } else {
        ModelGroup {
            state.customApis.forEachIndexed { index, api ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProvider(api.id) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = api.label,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${api.modelCount} моделей · ${api.baseUrl}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = ClaudenIcons.chevronRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (index != state.customApis.lastIndex) {
                    HorizontalDivider(color = colors.border)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    SheetActionRow(text = "Настроить свой API", onClick = onManage)

    if (locksApply && isFreeClaudenPlan(state)) {
        LockedModelsHint()
    }
}

@Composable
private fun CustomModelsContent(
    state: ChatUiState,
    api: CustomApi?,
    onBack: () -> Unit,
    onSelect: (String, RemoteModel) -> Unit,
    onManage: () -> Unit,
) {
    val colors = LocalClaudenColors.current

    SheetHeader(
        title = api?.label ?: "Свой API",
        onLeft = onBack,
        leftIsBack = true,
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (api == null) {
        Text(
            text = "Провайдер не найден. Добавьте его в настройках.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
    } else if (api.models.isEmpty()) {
        Text(
            text = "У этого API ещё нет списка моделей. Откройте настройки и " +
                "нажмите «Загрузить модели».",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
    } else {
        Text(
            text = api.baseUrl,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModelGroup {
            api.models.forEachIndexed { index, model ->
                val selected = state.provider == Provider.Custom &&
                    state.customApiId == api.id &&
                    state.model == model.id
                ModelCardRow(
                    model = model,
                    selected = selected,
                    locked = false,
                    showDescription = true,
                    onClick = { onSelect(api.id, model) },
                )
                if (index != api.models.lastIndex) {
                    HorizontalDivider(color = colors.border)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    SheetActionRow(text = "Настроить провайдера", onClick = onManage)
}

@Composable
private fun LockedModelsHint() {
    val colors = LocalClaudenColors.current
    Text(
        text = "Модели с замком доступны на Pro, Max и Ultra.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = colors.textTertiary,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

@Composable
private fun SheetHeader(
    title: String,
    onLeft: () -> Unit,
    leftIsBack: Boolean,
    loading: Boolean = false,
) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .height(46.dp),
    ) {
        IconButton(
            onClick = onLeft,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(42.dp),
        ) {
            Icon(
                imageVector = if (leftIsBack) ClaudenIcons.arrowBack else Icons.Default.Close,
                contentDescription = if (leftIsBack) "Назад" else "Закрыть",
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
        if (loading) {
            ClaudenLogo(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(20.dp),
                tint = colors.clay,
                variant = ClaudenLogoVariant.SpinTick,
            )
        }
    }
}

@Composable
private fun ModelGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.field),
        content = content,
    )
}

@Composable
private fun ModelCardRow(
    model: RemoteModel,
    selected: Boolean,
    locked: Boolean,
    showDescription: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val badge = modelBadge(model.id)
    val description = if (showDescription) modelDescription(model.id) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.5f else 1f)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.textTertiary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (locked) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = ClaudenIcons.lock,
                contentDescription = "Доступно на платном тарифе",
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        } else if (selected) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = ClaudenIcons.check,
                contentDescription = "Выбрано",
                tint = colors.blueLink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    text: String,
    onClick: () -> Unit,
    value: String = "",
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.field,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = colors.textTertiary,
                )
            }
            Icon(
                imageVector = ClaudenIcons.chevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun WarningPill() {
    val amber = Color(0xFFE8A54A)
    Surface(
        color = amber.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = ClaudenIcons.warning,
                contentDescription = null,
                tint = amber,
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "5× и более использования",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = amber,
            )
        }
    }
}

private fun modelBadge(id: String): String? = when {
    id == "claude-fable-5-2" -> "Max/Ultra"
    id.startsWith("claude-fable") -> "Pro, Max или Ultra"
    id.startsWith("claude-opus") -> "Pro"
    else -> null
}

private fun modelDescription(id: String): String = when (id) {
    "claude-fable-5-2" -> "Новейшая модель для самых сложных задач"
    "claude-fable-5-1" -> "Для самых сложных задач"
    "claude-opus-5-5" -> "Для сложной работы и повседневных задач"
    "claude-sonnet-5-5" -> "Наиболее эффективен для более простых задач"
    "claude-haiku-5-5" -> "Быстрее всего для кратких ответов"
    "claude-fable-5" -> "Fable доступен в плане Pro с кредитами использования, " +
        "которые оплачиваются отдельно от плана. Планы Max, Ultra и Premium team " +
        "включают его в пределах 50% недельных лимитов."
    else -> ""
}

@Composable
private fun LocationPermissionCard(
    pending: PendingLocation,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.clay.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ClaudenIcons.location,
                    contentDescription = null,
                    tint = colors.clay,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        pending.denied -> "Доступ к геопозиции не выдан"
                        pending.needsPermission -> "Clauden просит доступ к геопозиции"
                        pending.resolving -> "Определяю местоположение…"
                        else -> "Готовлю ответ с учётом геопозиции…"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textPrimary,
                )
                if (pending.reason.isNotBlank() && pending.needsPermission) {
                    Text(
                        text = pending.reason.take(140),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.textTertiary,
                    )
                } else if (pending.needsPermission) {
                    Text(
                        text = "Только для этого вопроса. Можно выбрать приблизительный или точный режим в системном окне.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.textTertiary,
                    )
                }
            }
            if (pending.needsPermission) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = colors.textPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(30.dp)
                            .clickable(onClick = onAllow),
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Разрешить",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = colors.bg,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Не сейчас",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                        modifier = Modifier
                            .clickable(onClick = onDeny)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    background: Color,
    textColor: Color,
    onClose: () -> Unit,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
