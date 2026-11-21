package space.teleopen.clauden.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import space.teleopen.clauden.data.HostKeyChangedException
import space.teleopen.clauden.data.SshConnectionHolder
import space.teleopen.clauden.data.SshHost
import space.teleopen.clauden.data.SshSession
import space.teleopen.clauden.data.SshStore

@Composable
internal fun SshTerminalScreen(
    host: SshHost,
    onBack: () -> Unit,
    onAgent: () -> Unit,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val store = remember { SshStore(context) }
    val view = LocalView.current

    var currentHost by remember { mutableStateOf(host) }
    var attempt by remember { mutableIntStateOf(0) }
    var client by remember { mutableStateOf<SshSession?>(null) }
    var state by remember { mutableStateOf("connecting") }
    var error by remember { mutableStateOf("") }
    var pane by remember { mutableStateOf("term") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var terminalReady by remember { mutableStateOf(false) }
    var hostKeyPrompt by remember { mutableStateOf<HostKeyChangedException?>(null) }
    var cols by remember { mutableIntStateOf(80) }
    var rows by remember { mutableIntStateOf(24) }
    var note by remember { mutableStateOf<String?>(null) }

    val output = remember {
        MutableSharedFlow<String>(
            extraBufferCapacity = 512,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val pending = remember { StringBuilder() }

    fun writeTerminal(text: String) {
        val target = webView
        if (target != null && terminalReady) {
            target.evaluateJavascript("window.writeTerm(${JSONObject.quote(text)})", null)
        } else if (pending.length < 300_000) {
            pending.append(text)
        }
    }

    LaunchedEffect(Unit) {
        output.collect { writeTerminal(it) }
    }

    LaunchedEffect(webView, terminalReady) {
        val view = webView
        if (view != null && terminalReady && pending.isNotEmpty()) {
            val text = pending.toString()
            pending.clear()
            view.evaluateJavascript("window.writeTerm(${JSONObject.quote(text)})", null)
        }
    }

    LaunchedEffect(currentHost.id, attempt) {
        if (hostKeyPrompt != null) return@LaunchedEffect
        state = "connecting"
        error = ""
        note = null
        pane = "term"
        client?.closeShell()
        if (attempt > 0) SshConnectionHolder.close(currentHost.id)
        val ssh = SshConnectionHolder.get(currentHost)
        try {
            ssh.ensureConnected(forceProbe = true)
            if (currentHost.fingerprint.isBlank() && ssh.observedFingerprint.isNotBlank()) {
                store.updateFingerprint(currentHost.id, ssh.observedFingerprint)
                currentHost = currentHost.copy(fingerprint = ssh.observedFingerprint)
            }
            ssh.openShell(cols, rows)
            client = ssh
            state = "connected"
            scope.launch(Dispatchers.IO) {
                ssh.readOutput { chunk -> output.tryEmit(chunk) }
                withContext(Dispatchers.Main) {
                    if (client === ssh && state == "connected") {
                        state = "closed"
                        error = "Соединение закрыто"
                    }
                }
            }
        } catch (err: CancellationException) {
            ssh.close()
            throw err
        } catch (err: HostKeyChangedException) {
            ssh.close()
            SshConnectionHolder.close(currentHost.id)
            hostKeyPrompt = err
            state = "error"
            error = "Отпечаток SSH-сервера изменился"
        } catch (err: Throwable) {
            ssh.close()
            SshConnectionHolder.close(currentHost.id)
            client = null
            state = "error"
            error = err.message ?: "Не удалось подключиться"
        }
    }

    val currentClient by rememberUpdatedState(client)
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            currentClient?.closeShell()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val current = client
        if (state != "connected" || current == null) return@LifecycleEventEffect
        if (!current.connected) {
            attempt += 1
        } else {
            scope.launch {
                val probe = withContext(Dispatchers.IO) {
                    runCatching { current.exec("true", "", 5) }.getOrNull()
                }
                val alive = probe != null && !probe.timedOut
                if (!alive) attempt += 1
            }
        }
    }

    fun disconnect() {
        client?.close()
        client = null
        state = "closed"
        error = "Соединение закрыто"
    }

    fun focusKeyboard() {
        val target = webView ?: return
        target.requestFocus()
        target.evaluateJavascript("window.focusTerm()", null)
        val manager = context.getSystemService(InputMethodManager::class.java)
        manager?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg),
    ) {
        TerminalTopBar(
            title = currentHost.label,
            subtitle = currentHost.target,
            onBack = {
                disconnect()
                onBack()
            },
            onMenu = onMenu,
            trailing = {
                TextButton(onClick = onAgent) {
                    Text("агент", style = TermTiny, color = TermAccent)
                }
                TextButton(onClick = {
                    pane = if (pane == "term") "files" else "term"
                }) {
                    Text(
                        text = if (pane == "term") "SFTP" else "терминал",
                        style = TermTiny,
                        color = TermAccent,
                    )
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        when (state) {
                            "connected" -> TermGreen
                            "connecting" -> TermAccent
                            else -> TermRed
                        },
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = when (state) {
                    "connected" -> "подключено"
                    "connecting" -> "подключаюсь…"
                    "closed" -> "закрыто"
                    else -> "ошибка"
                },
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "копировать",
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier
                    .clickable {
                        webView?.evaluateJavascript("window.copyTerm()", null)
                        note = "Если текст выделен — он в буфере"
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                text = "вставить",
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier
                    .clickable {
                        val text = clipboard.getText()?.text
                        if (!text.isNullOrEmpty()) client?.write(text)
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                text = "клавиатура",
                style = TermTiny,
                color = TermFaint,
                modifier = Modifier
                    .clickable { focusKeyboard() }
                    .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SshTerminalWebView(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        webView?.post {
                            webView?.evaluateJavascript("window.fitTerm()", null)
                        }
                    },
                onInput = { data -> client?.write(data) },
                onResize = { nextCols, nextRows ->
                    cols = nextCols
                    rows = nextRows
                    client?.resize(nextCols, nextRows)
                },
                onReady = {
                    terminalReady = true
                    webView?.let { view ->
                        if (pending.isNotEmpty()) {
                            val text = pending.toString()
                            pending.clear()
                            view.evaluateJavascript(
                                "window.writeTerm(${JSONObject.quote(text)})",
                                null,
                            )
                        }
                        view.evaluateJavascript("window.fitTerm()", null)
                    }
                },
                onCopy = { text ->
                    clipboard.setText(AnnotatedString(text))
                    note = "Скопировано"
                },
                onCreated = { webView = it },
                onDestroy = {
                    if (webView === it) webView = null
                    terminalReady = false
                    it.destroy()
                },
            )

            if (pane == "files") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TermBg),
                ) {
                    SshFilesPane(
                        client = client,
                        host = currentHost,
                        onNote = { note = it },
                    )
                }
            }

            if (state != "connected") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TermBg.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state == "connecting") {
                            TermSpinner(TermAccent)
                            Spacer(Modifier.height(10.dp))
                            Text("Подключаюсь к ${currentHost.target}", style = TermSmall, color = TermDim)
                        } else {
                            Text(
                                text = if (state == "closed") "Соединение закрыто" else "Не удалось подключиться",
                                style = TermBody.copy(fontWeight = FontWeight.SemiBold),
                                color = if (state == "closed") TermDim else TermRed,
                            )
                            if (error.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = error,
                                    style = TermSmall,
                                    color = TermDim,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Повторить",
                                    style = TermSmall,
                                    color = TermAccent,
                                    modifier = Modifier
                                        .background(TermAccent.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
                                        .clickable { attempt += 1 }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                                Text(
                                    text = "Назад",
                                    style = TermSmall,
                                    color = TermDim,
                                    modifier = Modifier
                                        .background(TermPanel, RoundedCornerShape(7.dp))
                                        .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
                                        .clickable { onBack() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        note?.let {
            Text(
                text = it,
                style = TermTiny,
                color = TermDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
            )
        }

        if (pane == "term" && state == "connected") {
            SshKeyBar(onKey = { client?.write(it) })
        }
    }

    hostKeyPrompt?.let { changed ->
        AlertDialog(
            onDismissRequest = { hostKeyPrompt = null },
            title = { Text("Отпечаток сервера изменился") },
            text = {
                Column {
                    Text(
                        "Раньше сервер отдавал один ключ, сейчас другой. Это бывает после " +
                            "переустановки SSH, но может быть и подменой.",
                        style = TermSmall,
                        color = TermText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Было: ${changed.expected}", style = TermTiny, color = TermFaint)
                    Text("Сейчас: ${changed.observed}", style = TermTiny, color = TermAccent)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Доверяй только если сам менял ключ на сервере.",
                        style = TermTiny,
                        color = TermDim,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.updateFingerprint(currentHost.id, changed.observed)
                    currentHost = currentHost.copy(fingerprint = changed.observed)
                    hostKeyPrompt = null
                    attempt += 1
                }) { Text("Доверять", color = TermRed) }
            },
            dismissButton = {
                TextButton(onClick = { hostKeyPrompt = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SshKeyBar(onKey: (String) -> Unit) {
    val keys = listOf(
        "Esc" to "\u001b",
        "Tab" to "\t",
        "Ctrl+C" to "\u0003",
        "Ctrl+D" to "\u0004",
        "Ctrl+Z" to "\u001a",
        "←" to "\u001b[D",
        "↑" to "\u001b[A",
        "↓" to "\u001b[B",
        "→" to "\u001b[C",
        "/" to "/",
        "-" to "-",
        "|" to "|",
        "~" to "~",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        keys.forEach { (label, value) ->
            Text(
                text = label,
                style = TermTiny,
                color = TermDim,
                modifier = Modifier
                    .background(TermPanel, RoundedCornerShape(5.dp))
                    .border(1.dp, TermBorder, RoundedCornerShape(5.dp))
                    .clickable { onKey(value) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SshTerminalWebView(
    modifier: Modifier,
    onInput: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    onReady: () -> Unit,
    onCopy: (String) -> Unit,
    onCreated: (WebView) -> Unit,
    onDestroy: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        TerminalBridge(
            onInput = onInput,
            onResize = { cols, rows -> main.post { onResize(cols, rows) } },
            onReady = { main.post { onReady() } },
            onCopy = { text -> main.post { onCopy(text) } },
        )
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#0A0A09"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                isFocusable = true
                isFocusableInTouchMode = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(bridge, "AndroidTerminal")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        main.post { onReady() }
                    }
                }
                loadUrl("file:///android_asset/ssh/terminal.html")
                onCreated(this)
            }
        },
        update = { },
        onRelease = { view ->
            runCatching {
                view.removeJavascriptInterface("AndroidTerminal")
                view.loadUrl("about:blank")
            }
            onDestroy(view)
        },
    )
}

class TerminalBridge(
    private val onInput: (String) -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onReady: () -> Unit,
    private val onCopy: (String) -> Unit,
) {
    @JavascriptInterface
    fun onData(data: String) {
        onInput(data)
    }

    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        onResize.invoke(cols, rows)
    }

    @JavascriptInterface
    fun onReady() {
        onReady.invoke()
    }

    @JavascriptInterface
    fun onCopy(text: String) {
        onCopy.invoke(text)
    }
}
