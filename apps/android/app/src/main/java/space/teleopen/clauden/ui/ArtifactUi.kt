package space.teleopen.clauden.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.ClaudenCatalog
import space.teleopen.clauden.data.SpeechInput

@Composable
fun ArtifactCard(
    artifact: ChatArtifact,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.field,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (artifact.isHtml()) ClaudenIcons.globe else ClaudenIcons.code,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artifact.type,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = ClaudenIcons.chevronRight,
                contentDescription = "Открыть артефакт",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun shareArtifact(context: Context, artifact: ChatArtifact) {
    val svg = artifact.type.contains("svg", ignoreCase = true)
    val extension = when {
        svg -> "svg"
        artifact.isHtml() -> "html"
        else -> "txt"
    }
    val safeName = artifact.title
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trim()
        .take(60)
        .ifBlank { "artifact" }
    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val file = java.io.File(dir, "$safeName.$extension")
    file.writeText(artifact.content)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (extension == "txt") "text/plain" else if (svg) "image/svg+xml" else "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, artifact.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться файлом"))
}

@Composable
fun ArtifactViewer(
    artifact: ChatArtifact,
    chatId: String,
    state: ChatUiState,
    vm: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val html = artifact.isHtml()
    val codeArtifact = artifact.type.equals("Код", ignoreCase = true) ||
        artifact.type.contains("code", ignoreCase = true)
    var showCode by remember(artifact.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var input by remember(artifact.id) { mutableStateOf("") }

    val composer = @Composable {
        ArtifactComposer(
            state = state,
            input = input,
            onInput = { input = it },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty() && !state.streaming) {
                    onDismiss()
                    vm.sendArtifactEdit(chatId, artifact.title, text, artifact.id)
                }
            },
            onOpenChat = {
                onDismiss()
                if (chatId.isNotBlank()) vm.openChat(chatId)
            },
            onVoice = {
                onDismiss()
                if (chatId.isNotBlank()) vm.openChat(chatId)
                vm.openVoiceMode()
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Ещё",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = colors.bg300,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Поделиться") },
                                leadingIcon = { Icon(ClaudenIcons.share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    shareArtifact(context, artifact)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Копировать") },
                                leadingIcon = { Icon(ClaudenIcons.copy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    clipboard.setText(AnnotatedString(artifact.content))
                                },
                            )
                            if (html) {
                                DropdownMenuItem(
                                    text = { Text(if (showCode) "Показать результат" else "Показать код") },
                                    leadingIcon = { Icon(ClaudenIcons.code, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        showCode = !showCode
                                    },
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        codeArtifact -> CodeArtifactContent(artifact.content)
                        html && showCode -> CodeArtifactContent(artifact.content)
                        html -> ArtifactWebView(
                            artifact = artifact,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        else -> MarkdownText(
                            text = artifact.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 18.sp,
                                lineHeight = 28.sp,
                                color = colors.textPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                composer()
            }
        }
        }
    }
}

@Composable
private fun CodeArtifactContent(code: String) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = code,
            style = ClaudenType.mono.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.textPrimary,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun ArtifactComposer(
    state: ChatUiState,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onOpenChat: () -> Unit,
    onVoice: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }

    val speech = remember {
        SpeechInput(
            context,
            object : SpeechInput.Listener {
                override fun onReady() {
                    listening = true
                }

                override fun onRms(level: Float) = Unit

                override fun onPartial(text: String) {
                    if (text.isNotBlank()) onInput(text)
                }

                override fun onFinal(text: String) {
                    if (text.isNotBlank()) onInput(text)
                    listening = false
                }

                override fun onError(message: String, code: Int) {
                    listening = false
                }

                override fun onEnd() {
                    listening = false
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { speech.destroy() }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) speech.start() else Unit
    }

    val modelLabel = listOf(
        state.models.firstOrNull { it.id == state.model }?.label
            ?: ClaudenCatalog.labelFor(state.model),
        state.effort.label,
    ).filter { it.isNotBlank() }.joinToString(" ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
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
                    maxLines = 6,
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    text = "Ответьте Clauden…",
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(36.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenChat),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Открыть чат",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = colors.control,
                        shape = CircleShape,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                if (listening) speech.stop() else speech.start()
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = ClaudenIcons.mic,
                            contentDescription = "Голосовой ввод",
                            tint = if (listening) colors.clay else colors.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.textPrimary)
                            .clickable(enabled = !state.streaming) {
                                if (input.isBlank()) onVoice() else onSend()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (input.isBlank()) {
                                ClaudenIcons.waveform
                            } else {
                                ClaudenIcons.arrowUp
                            },
                            contentDescription = if (input.isBlank()) "Голосовой режим" else "Отправить",
                            tint = colors.bg,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactWebView(artifact: ChatArtifact, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val webView = remember(artifact.id) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
        }
    }
    val document = remember(artifact.id, artifact.content) {
        if (artifact.type.contains("svg", ignoreCase = true)) {
            "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>html,body{margin:0;padding:16px;background:#fff}</style></head><body>" +
                artifact.content +
                "</body></html>"
        } else {
            artifact.content
        }
    }

    DisposableEffect(webView) {
        webView.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
        onDispose { webView.destroy() }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
    )
}
