package space.teleopen.clauden.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import space.teleopen.clauden.data.ChatMessage
import space.teleopen.clauden.Screen
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.Attachment
import space.teleopen.clauden.data.CalendarRepository
import space.teleopen.clauden.data.SpeechInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VoicePhase { Idle, Listening, Thinking, Speaking }

@Composable
fun VoiceBar(
    initialText: String,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
    onError: (String) -> Unit,
    inline: Boolean = false,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var transcript by remember { mutableStateOf(initialText) }
    var listening by remember { mutableStateOf(true) }
    var levels by remember { mutableStateOf(List(30) { 0f }) }
    var localError by remember { mutableStateOf<String?>(null) }

    val speech = remember {
        SpeechInput(
            context,
            object : SpeechInput.Listener {
                override fun onReady() {
                    listening = true
                    localError = null
                }

                override fun onRms(level: Float) {
                    levels = levels.drop(1) + level
                }

                override fun onPartial(text: String) {
                    transcript = text
                }

                override fun onFinal(text: String) {
                    if (text.isNotBlank()) transcript = text
                    listening = false
                }

                override fun onError(message: String, code: Int) {
                    listening = false
                    if (message.isNotBlank()) {
                        localError = message
                        onError(message)
                    }
                }

                override fun onEnd() {
                    listening = false
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        if (speech.available) speech.start() else {
            localError = "На телефоне нет сервиса распознавания речи"
            listening = false
        }
    }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }

    fun stopListening() {
        speech.stop()
        listening = false
    }

    fun resumeListening() {
        localError = null
        transcript = ""
        listening = true
        speech.start()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(
                if (inline) Modifier
                else Modifier.navigationBarsPadding().imePadding()
            ),
    ) {
        Text(
            text = transcript.ifBlank {
                when {
                    localError != null -> localError.orEmpty()
                    listening -> "Говорите…"
                    else -> "Нажмите на микрофон, чтобы продолжить"
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = if (localError != null) colors.dangerSoft else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )

        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(40.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl(
                    size = 52.dp,
                    background = colors.control,
                    onClick = {
                        speech.cancel()
                        onCancel()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Отменить",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                VoiceWaveform(
                    levels = levels,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .padding(horizontal = 6.dp),
                )

                CircleControl(
                    size = 46.dp,
                    background = colors.control,
                    onClick = {
                        if (listening) stopListening() else resumeListening()
                    },
                ) {
                    if (listening) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.textPrimary),
                        )
                    } else {
                        Icon(
                            imageVector = ClaudenIcons.mic,
                            contentDescription = "Продолжить",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                CircleControl(
                    size = 50.dp,
                    background = if (transcript.isNotBlank()) colors.clay else colors.control,
                    onClick = {
                        if (transcript.isNotBlank()) {
                            speech.cancel()
                            onSend(transcript.trim())
                        }
                    },
                ) {
                    Icon(
                        imageVector = ClaudenIcons.arrowUp,
                        contentDescription = "Отправить",
                        tint = if (transcript.isNotBlank()) Color.White else colors.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleControl(
    size: androidx.compose.ui.unit.Dp,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun VoiceWaveform(
    levels: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas
        val count = levels.size
        val gap = size.width / count
        val barWidth = (gap * 0.42f).coerceAtLeast(2f)
        val minHeight = 3.dp.toPx()
        levels.forEachIndexed { index, value ->
            val amplitude = value.coerceIn(0f, 1f)
            val height = (minHeight + amplitude * (size.height - minHeight)).coerceAtLeast(minHeight)
            val x = index * gap + gap / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x - barWidth / 2f, (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
fun VoiceModeScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    onClose: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val tts = rememberTtsController()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(VoicePhase.Idle) }
    var partial by remember { mutableStateOf("") }
    var levels by remember { mutableStateOf(List(34) { 0f }) }
    var error by remember { mutableStateOf<String?>(null) }
    var speakerOn by rememberSaveable { mutableStateOf(true) }
    var attachments by remember { mutableStateOf(emptyList<Attachment>()) }
    var showAttachments by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var volumeMenu by remember { mutableStateOf(false) }
    var pendingUtterance by remember { mutableStateOf<String?>(null) }
    var restartToken by remember { mutableIntStateOf(0) }
    var wasStreaming by remember { mutableStateOf(false) }
    var lastSpokenTs by remember { mutableLongStateOf(0L) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val messages = state.messages
    val transcriptMessages = messages.filter {
        it.content.isNotBlank() || it.attachments.isNotEmpty()
    }.takeLast(8)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val made = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> runCatching { makeImageAttachment(context, uri) }.getOrNull() }
                }
                if (made.isNotEmpty()) attachments = mergeAttachments(attachments, made)
                else error = "Не удалось прочитать изображения"
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val made = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            if (mime.startsWith("image/")) makeImageAttachment(context, uri)
                            else makeFileAttachment(context, uri)
                        }.getOrNull()
                    }
                }
                if (made.isNotEmpty()) attachments = mergeAttachments(attachments, made)
                else error = "Не удалось прикрепить файлы (каждый до 8 МБ)"
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
                if (attachment != null) attachments = mergeAttachments(attachments, listOf(attachment))
            }
        }
    }

    val speech = remember {
        SpeechInput(
            context,
            object : SpeechInput.Listener {
                override fun onReady() {
                    error = null
                }

                override fun onRms(level: Float) {
                    levels = levels.drop(1) + level
                }

                override fun onPartial(text: String) {
                    if (text.isNotBlank()) partial = text
                }

                override fun onFinal(text: String) {
                    if (text.isNotBlank()) {
                        partial = text
                        pendingUtterance = text
                    } else {
                        restartToken++
                    }
                }

                override fun onError(message: String, code: Int) {
                    if (code == android.speech.SpeechRecognizer.ERROR_CLIENT) return
                    if (message.isNotBlank() && code != android.speech.SpeechRecognizer.ERROR_NO_MATCH &&
                        code != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        error = message
                    }
                    if (phase == VoicePhase.Listening) restartToken++
                }

                override fun onEnd() {
                    if (phase == VoicePhase.Listening && pendingUtterance == null) {
                        restartToken++
                    }
                }
            },
        )
    }

    fun beginListening() {
        if (state.streaming) return
        partial = ""
        error = null
        phase = VoicePhase.Listening
        restartToken++
    }

    fun stopListening() {
        speech.cancel()
        phase = VoicePhase.Idle
    }

    fun sendVoice(text: String) {
        if (text.isBlank() || state.streaming) {
            pendingUtterance = null
            if (phase == VoicePhase.Thinking) beginListening()
            return
        }
        pendingUtterance = null
        partial = ""
        phase = VoicePhase.Thinking
        val queued = attachments
        attachments = emptyList()
        scope.launch {
            val calendar = if (CalendarRepository.hasReadPermission(context)) {
                withContext(Dispatchers.IO) { CalendarRepository.snapshot(context) }
            } else {
                ""
            }
            vm.send(text, queued, calendar)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            error = "Разрешите доступ к микрофону в настройках приложения"
            phase = VoicePhase.Idle
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(restartToken) {
        if (phase != VoicePhase.Listening) return@LaunchedEffect
        delay(260)
        if (phase == VoicePhase.Listening && pendingUtterance == null) {
            speech.start()
        }
    }

    LaunchedEffect(pendingUtterance) {
        pendingUtterance?.let { text ->
            sendVoice(text)
        }
    }

    LaunchedEffect(state.streaming) {
        if (state.streaming) {
            wasStreaming = true
            speech.cancel()
            phase = VoicePhase.Thinking
        } else if (wasStreaming) {
            wasStreaming = false
            val last = state.messages.lastOrNull { it.role == "assistant" && it.content.isNotBlank() }
            if (last != null && last.ts > lastSpokenTs) {
                lastSpokenTs = last.ts
                if (speakerOn) {
                    phase = VoicePhase.Speaking
                    val spoken = plainForSpeech(last.content)
                    if (spoken.isBlank()) {
                        beginListening()
                    } else {
                        tts.speak(spoken) {
                            if (phase == VoicePhase.Speaking) beginListening()
                        }
                    }
                } else {
                    beginListening()
                }
            } else {
                beginListening()
            }
        }
    }

    LaunchedEffect(transcriptMessages.size, partial) {
        if (transcriptMessages.isNotEmpty()) {
            runCatching { listState.scrollToItem(transcriptMessages.size - 1) }
        }
    }

    BackHandler { onClose() }
    DisposableEffect(Unit) {
        onDispose {
            speech.destroy()
            tts.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Surface(
                        color = colors.bg300,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(42.dp)
                            .clickable { volumeMenu = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = ClaudenIcons.volume,
                                contentDescription = "Звук",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = ClaudenIcons.chevronRight,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(top = 1.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = volumeMenu,
                        onDismissRequest = { volumeMenu = false },
                        containerColor = colors.popupBg,
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (speakerOn) "Выключить озвучку" else "Включить озвучку") },
                            onClick = {
                                speakerOn = !speakerOn
                                if (!speakerOn) {
                                    tts.stop()
                                    if (phase == VoicePhase.Speaking) beginListening()
                                }
                                volumeMenu = false
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.bg300)
                        .clickable {
                            vm.closeVoiceMode()
                            vm.openSettings()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ClaudenIcons.sliders,
                        contentDescription = "Настройки",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Поговорим, ${state.settings.nickname.ifBlank { "друг" }}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = when (phase) {
                    VoicePhase.Listening -> partial.ifBlank { "Слушаю…" }
                    VoicePhase.Thinking -> "Думаю…"
                    VoicePhase.Speaking -> "Говорю…"
                    VoicePhase.Idle -> error ?: "Нажмите на микрофон"
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
                color = if (error != null) colors.dangerSoft else colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = transcriptMessages) { message ->
                    VoiceTranscriptRow(message = message)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
                contentAlignment = Alignment.Center,
            ) {
                VoiceOrb(
                    phase = phase,
                    onClick = {
                        when (phase) {
                            VoicePhase.Listening -> stopListening()
                            VoicePhase.Speaking -> {
                                tts.stop()
                                beginListening()
                            }
                            VoicePhase.Thinking -> Unit
                            VoicePhase.Idle -> {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) beginListening() else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    },
                )
            }

            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { attachment ->
                        AttachmentPreviewCard(
                            attachment = attachment,
                            onRemove = {
                                attachments = attachments.filterNot { it.id == attachment.id }
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl(
                    size = 48.dp,
                    background = colors.bg300,
                    onClick = { showAttachments = true },
                ) {
                    Icon(
                        imageVector = ClaudenIcons.plus,
                        contentDescription = "Добавить",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box {
                    Surface(
                        color = colors.bg300,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(44.dp)
                            .clickable { modelMenu = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.model
                                    .let { id -> state.models.firstOrNull { it.id == id }?.label ?: id }
                                    .removePrefix("Claude ")
                                    .ifBlank { "Модель" },
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 190.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = ClaudenIcons.chevronRight,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(top = 1.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = modelMenu,
                        onDismissRequest = { modelMenu = false },
                        containerColor = colors.popupBg,
                        modifier = Modifier.widthIn(min = 250.dp),
                    ) {
                        state.models.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model.label.removePrefix("Claude "),
                                        color = colors.textPrimary,
                                    )
                                },
                                leadingIcon = {
                                    if (model.id == state.model) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colors.clay,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    vm.selectModel(model.id)
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                CircleControl(
                    size = 48.dp,
                    background = colors.bg300,
                    onClick = {
                        speakerOn = !speakerOn
                        if (!speakerOn) {
                            tts.stop()
                            if (phase == VoicePhase.Speaking) beginListening()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (speakerOn) ClaudenIcons.volume else ClaudenIcons.micSlash,
                        contentDescription = "Озвучка",
                        tint = if (speakerOn) colors.textPrimary else colors.textTertiary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                CircleControl(
                    size = 52.dp,
                    background = colors.textPrimary,
                    onClick = onClose,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = if (colors.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }

    if (showAttachments) {
        AttachmentSheet(
            projects = state.projects,
            selectedProjectId = state.active?.projectId.orEmpty(),
            webSearch = state.settings.webSearch,
            onWebSearchChange = { enabled ->
                vm.updateSettings { it.copy(webSearch = enabled) }
            },
            onCamera = {
                showAttachments = false
                val uri = cameraOutputUri(context)
                cameraUri = uri
                cameraLauncher.launch(uri)
            },
            onPhotos = {
                showAttachments = false
                imagePicker.launch("image/*")
            },
            onFiles = {
                showAttachments = false
                filePicker.launch("*/*")
            },
            onProject = { projectId ->
                state.active?.let { vm.setChatProject(it.id, projectId) }
                showAttachments = false
            },
            onConnectors = {
                showAttachments = false
                vm.closeVoiceMode()
                vm.openCustomize("connectors")
            },
            onDismiss = { showAttachments = false },
        )
    }
}

@Composable
private fun VoiceTranscriptRow(message: ChatMessage) {
    val colors = LocalClaudenColors.current
    if (message.role == "user") {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = colors.textSecondary,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceOrb(phase: VoicePhase, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    val transition = rememberInfiniteTransition(label = "voice-orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    val active = phase == VoicePhase.Listening || phase == VoicePhase.Speaking

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension * 0.31f
            if (active) {
                repeat(3) { ring ->
                    val progress = (pulse + ring / 3f) % 1f
                    drawCircle(
                        color = colors.clay.copy(alpha = (1f - progress) * 0.16f),
                        radius = base + progress * base * 0.7f,
                        center = center,
                    )
                }
            }
            drawCircle(
                color = colors.bg300,
                radius = base,
                center = center,
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (phase == VoicePhase.Listening) colors.clay else colors.control)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                VoicePhase.Listening, VoicePhase.Speaking -> Icon(
                    imageVector = ClaudenIcons.mic,
                    contentDescription = "Микрофон",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
                VoicePhase.Thinking -> Icon(
                    imageVector = ClaudenIcons.waveform,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(34.dp),
                )
                VoicePhase.Idle -> Icon(
                    imageVector = ClaudenIcons.mic,
                    contentDescription = "Микрофон",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

fun plainForSpeech(markdown: String): String {
    var text = markdown
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s*>\\s?"), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("__([^_]+)__"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("_([^_]+)_"), "$1")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (text.length > 1400) {
        val cut = text.take(1400)
        val dot = cut.lastIndexOf('.')
        text = if (dot > 800) cut.take(dot + 1) else cut
    }
    return text
}
