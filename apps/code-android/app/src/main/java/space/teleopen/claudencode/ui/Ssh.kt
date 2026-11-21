package space.teleopen.claudencode.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import space.teleopen.claudencode.data.HostKeyChangedException
import space.teleopen.claudencode.data.RemoteEntry
import space.teleopen.claudencode.data.SshConnectionHolder
import space.teleopen.claudencode.data.SshException
import space.teleopen.claudencode.data.SshHost
import space.teleopen.claudencode.data.SshSession
import space.teleopen.claudencode.data.SshStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SshHostsSheet(
    hosts: List<SshHost>,
    mode: String = "term",
    onAdd: () -> Unit,
    onEdit: (SshHost) -> Unit,
    onOpen: (SshHost) -> Unit,
    onSftp: (SshHost) -> Unit,
    onClose: () -> Unit,
    onLeave: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BottomSheet(onDismiss = onClose) {
        Column(
            modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SSH-серверы", color = Pal.White, style = type(19.sp, FontWeight.Bold, 1.2f))
                Spacer(Modifier.width(8.dp))
                Text(
                    hosts.size.toString(),
                    color = Pal.Dim,
                    style = type(13.sp, null, 1.2f),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.termClickable(onAdd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconPlus(size = 16.dp, tint = Pal.Accent)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Новый",
                        color = Pal.Accent,
                        style = type(13.sp, FontWeight.Medium, 1.2f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                when (mode) {
                    "agent" -> "Выберите сервер — агент будет работать на нём"
                    "sftp" -> "Выберите сервер — откроется SFTP"
                    else -> "Выберите сервер — откроется терминал"
                },
                color = Pal.Dim,
                style = type(11.5.sp, null, 1.35f),
            )
            onLeave?.let { leave ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "Вернуться в Clauden",
                    color = Pal.PeriSoft,
                    style = type(11.5.sp, null, 1.2f),
                    modifier = Modifier.termClickable(leave),
                )
            }
            Spacer(Modifier.height(14.dp))
            if (hosts.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Pal.Panel)
                        .border(1.dp, Pal.BorderSoft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconMonitor(size = 28.dp, tint = Pal.Dim)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Пока нет серверов",
                        color = Pal.PeriSoft,
                        style = type(13.5.sp, FontWeight.Medium, 1.3f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Добавьте хост, чтобы запускать агент на своём сервере.",
                        color = Pal.Dim,
                        style = type(11.sp, null, 1.4f),
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            } else {
                Column(
                    Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    hosts.forEach { host ->
                        HostCard(
                            host = host,
                            mode = mode,
                            onOpen = { onOpen(host) },
                            onSftp = { onSftp(host) },
                            onEdit = { onEdit(host) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun HostCard(
    host: SshHost,
    mode: String,
    onOpen: () -> Unit,
    onSftp: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.Panel)
            .border(1.dp, Pal.BorderSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Pal.PanelHi),
                contentAlignment = Alignment.Center,
            ) {
                IconMonitor(size = 19.dp, tint = Pal.Accent)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    host.label,
                    color = Pal.White,
                    style = type(14.sp, FontWeight.Bold, 1.25f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${host.user}@${host.host}:${host.port}",
                    color = Pal.Dim,
                    style = type(11.sp, null, 1.25f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .termClickable(onEdit)
                    .padding(6.dp),
            ) {
                IconDots(size = 18.dp, tint = Pal.Dim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HostPill(
                if (mode == "agent") "Агент" else "Терминал",
                primary = true,
                onClick = onOpen,
            )
            HostPill("SFTP", primary = false, onClick = onSftp)
            Spacer(Modifier.weight(1f))
            Text(
                host.authLabel,
                color = Pal.Faint,
                style = type(10.sp, null, 1.2f),
            )
        }
    }
}

@Composable
private fun HostPill(label: String, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (primary) Pal.AccentDeep else Pal.PanelHi)
            .border(
                1.dp,
                if (primary) Color(0xFF5A2616) else Pal.Border,
                RoundedCornerShape(9.dp),
            )
            .termClickable(onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTerminal(size = 13.dp, tint = if (primary) Pal.Accent else Pal.Dim)
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = if (primary) Pal.White else Pal.PeriSoft,
            style = type(11.sp, FontWeight.Medium, 1.2f),
        )
    }
}

@Composable
fun SshHostEditor(
    host: SshHost,
    onSave: (SshHost) -> Unit,
    onDelete: (SshHost) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(host.name) }
    var address by remember { mutableStateOf(host.host) }
    var port by remember { mutableStateOf(host.port.toString()) }
    var user by remember { mutableStateOf(host.user) }
    var auth by remember { mutableStateOf(host.auth) }
    var password by remember { mutableStateOf(host.password) }
    var key by remember { mutableStateOf(host.privateKey) }

    ScreenBackground {
        Column(
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .width(82.dp)
                        .termClickable(onClose),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconChevron(size = 17.dp, tint = Pal.Gray, left = true)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Назад",
                        color = Pal.PeriSoft,
                        style = type(14.sp, FontWeight.Medium, 1.2f),
                    )
                }
                Text(
                    if (host.id.isBlank()) "Новый сервер" else "Изменить сервер",
                    color = Pal.White,
                    style = type(15.sp, FontWeight.Bold, 1.2f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.width(82.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Pal.BorderSoft),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                HostField("Название", name, "Мой сервер") { name = it }
                Spacer(Modifier.height(13.dp))
                HostField("Хост", address, "192.168.1.10 или example.com") { address = it }
                Spacer(Modifier.height(13.dp))
                Row {
                    Box(Modifier.weight(1f)) {
                        HostField("Порт", port, "22") { value ->
                            port = value.filter { it.isDigit() }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1.6f)) {
                        HostField("Пользователь", user, "root") { user = it }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Авторизация", color = Pal.Dim, style = type(11.5.sp, null, 1.2f))
                Spacer(Modifier.height(7.dp))
                Row {
                    AuthChip("Пароль", auth == "password") { auth = "password" }
                    Spacer(Modifier.width(8.dp))
                    AuthChip("Приватный ключ", auth == "key") { auth = "key" }
                }
                Spacer(Modifier.height(13.dp))
                if (auth == "password") {
                    HostField("Пароль", password, "Введите пароль", mask = true) { password = it }
                } else {
                    HostMultiline("Приватный ключ (PEM/OpenSSH)", key) { key = it }
                }
                Spacer(Modifier.height(22.dp))
                PrimaryButton(
                    label = "Сохранить сервер",
                    enabled = address.isNotBlank() && user.isNotBlank(),
                ) {
                    onSave(
                        host.copy(
                            name = name.trim(),
                            host = address.trim(),
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                            user = user.trim(),
                            auth = auth,
                            password = password,
                            privateKey = key,
                            fingerprint = if (address.trim() != host.host.trim()) "" else host.fingerprint,
                        ),
                    )
                }
                if (host.id.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0xFF1D0F0E))
                            .border(1.dp, Color(0xFF4A1D1A), RoundedCornerShape(13.dp))
                            .termClickable { onDelete(host) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Удалить сервер",
                            color = Pal.Red,
                            style = type(12.5.sp, FontWeight.Medium, 1.2f),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

@Composable
private fun AuthChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Pal.White else Pal.Gray,
        style = type(12.sp, FontWeight.Medium, 1.2f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0xFF22110B) else Pal.Panel)
            .border(
                1.dp,
                if (active) Pal.Accent else Pal.Border,
                RoundedCornerShape(10.dp),
            )
            .termClickable(onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}

@Composable
private fun HostField(
    label: String,
    value: String,
    placeholder: String,
    mask: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column {
        Text(label, color = Pal.Dim, style = type(11.sp, null, 1.2f))
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Pal.Panel)
                .border(1.dp, Pal.Border, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type(13.sp, null, 1.3f).copy(color = Pal.White),
                cursorBrush = SolidColor(Pal.Accent),
                visualTransformation = if (mask) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, color = Pal.Dim, style = type(13.sp, null, 1.3f))
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HostMultiline(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = Pal.Dim, style = type(11.sp, null, 1.2f))
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Pal.Panel)
                .border(1.dp, Pal.Border, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = type(11.5.sp, null, 1.5f).copy(color = Pal.White),
                cursorBrush = SolidColor(Pal.Accent),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "-----BEGIN OPENSSH PRIVATE KEY-----",
                                color = Pal.Dim,
                                style = type(11.sp, null, 1.5f),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private object SshBuffers {
    private val map = mutableMapOf<String, StringBuilder>()

    fun of(hostId: String): StringBuilder = synchronized(this) {
        map.getOrPut(hostId) { StringBuilder() }
    }

    fun append(hostId: String, chunk: String) {
        val buffer = of(hostId)
        synchronized(buffer) {
            if (buffer.length + chunk.length > 220_000) {
                buffer.delete(0, (buffer.length + chunk.length - 220_000).coerceAtMost(buffer.length))
            }
            buffer.append(chunk)
        }
    }

    fun read(hostId: String): String = of(hostId).let { synchronized(it) { it.toString() } }

    fun clear(hostId: String) {
        synchronized(this) { map.remove(hostId) }
    }
}

fun clearSshBuffer(hostId: String) = SshBuffers.clear(hostId)

private object SshTermBus {
    private val main = Handler(Looper.getMainLooper())
    var activeHost: String? = null
    var sink: ((String) -> Unit)? = null

    fun emit(hostId: String, chunk: String) {
        main.post {
            if (activeHost == hostId) sink?.invoke(chunk)
        }
    }
}

@Composable
fun SshWorkspace(
    hosts: List<SshHost>,
    host: SshHost,
    pane: String,
    store: SshStore,
    onPane: (String) -> Unit,
    onSelect: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewHost: () -> Unit,
    onHosts: () -> Unit,
    onLeave: () -> Unit,
    onSetRoot: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember(host.id) { mutableStateOf(host) }
    var client by remember(host.id) { mutableStateOf<SshSession?>(null) }
    var state by remember(host.id) { mutableStateOf("connecting") }
    var error by remember(host.id) { mutableStateOf("") }
    var hostKey by remember(host.id) { mutableStateOf<HostKeyChangedException?>(null) }
    var attempt by remember(host.id) { mutableIntStateOf(0) }
    var cols by remember(host.id) { mutableIntStateOf(80) }
    var rows by remember(host.id) { mutableIntStateOf(24) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    val sftp = remember(host.id) { SftpUiState() }

    fun pushTerm(text: String) {
        if (text.isEmpty()) return
        val view = webView
        if (ready && view != null) {
            view.post {
                view.evaluateJavascript("window.writeTerm(${JSONObject.quote(text)})", null)
            }
        }
    }

    SideEffect {
        SshTermBus.activeHost = host.id
        SshTermBus.sink = { chunk -> pushTerm(chunk) }
    }

    LaunchedEffect(webView, ready, host.id) {
        val view = webView ?: return@LaunchedEffect
        if (ready) {
            val history = SshBuffers.read(host.id)
            if (history.isNotEmpty()) {
                view.evaluateJavascript("window.writeTerm(${JSONObject.quote(history)})", null)
            }
        }
    }

    LaunchedEffect(current.id, attempt) {
        if (hostKey != null) return@LaunchedEffect
        state = "connecting"
        error = ""
        try {
            val ssh = SshConnectionHolder.get(current)
            if (!ssh.connected) ssh.connect()
            if (current.fingerprint.isBlank() && ssh.observedFingerprint.isNotBlank()) {
                store.updateFingerprint(current.id, ssh.observedFingerprint)
                current = current.copy(fingerprint = ssh.observedFingerprint)
            }
            ssh.openShell(cols, rows)
            client = ssh
            state = "connected"
            scope.launch(Dispatchers.IO) {
                ssh.readOutput { chunk ->
                    SshBuffers.append(host.id, chunk)
                    SshTermBus.emit(host.id, chunk)
                }
                withContext(Dispatchers.Main) {
                    if (client === ssh && state == "connected") state = "closed"
                }
            }
        } catch (err: HostKeyChangedException) {
            hostKey = err
            state = "failed"
            error = "Host key changed"
        } catch (err: CancellationException) {
            throw err
        } catch (err: Throwable) {
            state = "failed"
            error = err.message ?: "Connection failed"
            client = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {

        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Pal.Bg),
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                color = Pal.White,
                style = mono(17.sp, FontWeight.Bold),
                modifier = Modifier
                    .termClickable(onLeave)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                hosts.forEach { tab ->
                    val active = tab.id == host.id
                    Row(
                        Modifier
                            .border(1.dp, if (active) Pal.Accent else Pal.Border)
                            .background(if (active) Pal.PanelHi else Pal.Panel)
                            .termClickable { onSelect(tab.id) }
                            .padding(start = 9.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⌁", color = if (active) Pal.Accent else Pal.Dim, style = mono(10.sp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            tab.label,
                            color = if (active) Pal.White else Pal.Gray,
                            style = mono(10.5.sp, if (active) FontWeight.Bold else null),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(
                            "×",
                            color = Pal.Dim,
                            style = mono(11.sp),
                            modifier = Modifier
                                .termClickable { onCloseTab(tab.id) }
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    "＋",
                    color = Pal.Accent,
                    style = mono(15.sp, FontWeight.Bold),
                    modifier = Modifier
                        .border(1.dp, Pal.Border)
                        .termClickable(onNewHost)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Text(
                "▦",
                color = Pal.Dim,
                style = mono(13.sp),
                modifier = Modifier
                    .termClickable(onHosts)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                if (pane == "sftp") "⌁ TERM" else "⇅ SFTP",
                color = if (pane == "sftp") Pal.Accent else Pal.Green,
                style = mono(10.sp, FontWeight.Bold),
                modifier = Modifier
                    .termClickable { onPane(if (pane == "sftp") "term" else "sftp") }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(color = Pal.Border)

        if (pane == "sftp") {
            SshSftpPane(
                host = current,
                ssh = client,
                state = state,
                error = error,
                sftp = sftp,
                onRetry = { attempt += 1 },
                onTerminal = { onPane("term") },
                onSetRoot = onSetRoot,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                TerminalWebView(
                    modifier = Modifier.fillMaxSize(),
                    onInput = { data ->
                        val ssh = client
                        if (ssh != null) {
                            if (ctrlArmed) {
                                val code = data.firstOrNull()?.uppercaseChar()?.code ?: return@TerminalWebView
                                ssh.write((code and 0x1F).toChar().toString())
                                ctrlArmed = false
                            } else {
                                ssh.write(data)
                            }
                        }
                    },
                    onResize = { c, r ->
                        cols = c.coerceAtLeast(20)
                        rows = r.coerceAtLeast(5)
                        client?.resize(cols, rows)
                    },
                    onReady = { ready = true },
                    onCopy = { text ->
                        if (text.isNotBlank()) copyText(context, text)
                    },
                    onCreated = { webView = it },
                    onDestroy = {
                        webView = null
                        ready = false
                    },
                )
                if (state != "connected") {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Pal.Bg.copy(alpha = 0.94f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelMascot(size = 52.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when (state) {
                                    "connecting" -> "Connecting to ${current.target}…"
                                    "failed" -> "✕ $error"
                                    "closed" -> "Session closed"
                                    else -> state
                                },
                                color = if (state == "failed") Pal.Red else Pal.White,
                                style = mono(12.5.sp),
                            )
                            Spacer(Modifier.height(12.dp))
                            if (state == "failed" || state == "closed") {
                                Text(
                                    "Retry",
                                    color = Pal.Accent,
                                    style = mono(12.sp, FontWeight.Bold),
                                    modifier = Modifier
                                        .border(1.dp, Pal.Accent)
                                        .termClickable { attempt += 1 }
                                        .padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.Panel)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SshKey("shift+tab") { client?.write("\u001b[Z") }
                SshKey("ctrl", active = ctrlArmed) { ctrlArmed = !ctrlArmed }
                SshKey("esc") { client?.write("\u001b") }
                SshKey("tab") { client?.write("\t") }
                SshKey("✂") { client?.write("\u0003"); ctrlArmed = false }
                SshKey("←") { client?.write("\u001b[D") }
                SshKey("↑") { client?.write("\u001b[A") }
                SshKey("↓") { client?.write("\u001b[B") }
                SshKey("→") { client?.write("\u001b[C") }
                SshKey("home") { client?.write("\u001b[H") }
                SshKey("end") { client?.write("\u001b[F") }
                SshKey("⌫") { client?.write("\u007f") }
                SshKey("⇅ sftp", active = false) { onPane("sftp") }
            }
        }
    }

    hostKey?.let { changed ->
        SshConfirmDialog(
            title = "Host key changed",
            body = "Expected ${changed.expected}\nObserved ${changed.observed}\n\n"
                + "Only accept if you re-installed the server.",
            confirm = "Update & reconnect",
            danger = false,
            onConfirm = {
                store.updateFingerprint(current.id, changed.observed)
                current = current.copy(fingerprint = changed.observed)
                hostKey = null
                attempt += 1
            },
            onCancel = {
                hostKey = null
                state = "failed"
                error = "Host key mismatch — connection blocked"
            },
        )
    }
}

@Composable
private fun SshKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Pal.White else Pal.Gray,
        style = mono(10.5.sp, FontWeight.Bold),
        modifier = Modifier
            .border(1.dp, if (active) Pal.Accent else Pal.Border)
            .background(if (active) Pal.PanelHi else Pal.Bg)
            .termClickable(onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

private class SftpUiState {
    var path by mutableStateOf("")
    var entries by mutableStateOf<List<RemoteEntry>>(emptyList())
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf("")
    var menu by mutableStateOf<RemoteEntry?>(null)
    var editor by mutableStateOf<SftpEditor?>(null)
    var rename by mutableStateOf<RemoteEntry?>(null)
    var deleting by mutableStateOf<RemoteEntry?>(null)
    var creating by mutableStateOf(false)
    var busy by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)
}

private data class SftpEditor(
    val path: String,
    val original: String,
    val draft: String,
)

@Composable
private fun SshSftpPane(
    host: SshHost,
    ssh: SshSession?,
    state: String,
    error: String,
    sftp: SftpUiState,
    onRetry: () -> Unit,
    onTerminal: () -> Unit,
    onSetRoot: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadTarget by remember { mutableStateOf<RemoteEntry?>(null) }

    suspend fun open(target: String) {
        val session = ssh ?: return
        sftp.loading = true
        sftp.error = ""
        try {
            val dir = if (target.isBlank()) session.pwd() else session.normalizeRemote(target)
            sftp.entries = session.list(dir)
            sftp.path = dir
            sftp.loaded = true
        } catch (err: Throwable) {
            sftp.error = err.message ?: "SFTP error"
        } finally {
            sftp.loading = false
        }
    }

    suspend fun openFile(entry: RemoteEntry) {
        val session = ssh ?: return
        sftp.busy = "Opening ${entry.name}…"
        try {
            val text = session.readText(entry.path)
            sftp.editor = SftpEditor(entry.path, text, text)
        } catch (err: Throwable) {
            sftp.note = err.message ?: "Cannot open file"
        } finally {
            sftp.busy = null
        }
    }

    LaunchedEffect(ssh) {
        if (ssh != null && !sftp.loaded && !sftp.loading) open("")
    }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val session = ssh
        if (uri == null || session == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri)
        scope.launch {
            sftp.busy = "Uploading $name…"
            try {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw SshException("Cannot open the picked file")
                    input.use { session.upload(session.joinRemote(sftp.path, name), it) }
                }
                sftp.note = "Uploaded $name"
                open(sftp.path)
            } catch (err: Throwable) {
                sftp.note = err.message ?: "Upload failed"
            } finally {
                sftp.busy = null
            }
        }
    }

    val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val entry = downloadTarget
        val session = ssh
        if (uri == null || entry == null || session == null) return@rememberLauncherForActivityResult
        scope.launch {
            sftp.busy = "Downloading ${entry.name}…"
            try {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw SshException("Cannot write to the selected folder")
                    out.use { session.download(entry.path, it) }
                }
                sftp.note = "Saved ${entry.name}"
            } catch (err: Throwable) {
                sftp.note = err.message ?: "Download failed"
            } finally {
                sftp.busy = null
            }
        }
    }

    BackHandler {
        when {
            sftp.editor != null -> sftp.editor = null
            sftp.menu != null -> sftp.menu = null
            sftp.creating -> sftp.creating = false
            sftp.rename != null -> sftp.rename = null
            sftp.deleting != null -> sftp.deleting = null
            else -> onTerminal()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹",
                    color = Pal.White,
                    style = mono(15.sp, FontWeight.Bold),
                    modifier = Modifier
                        .termClickable(onTerminal)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Text(
                    "⇅ ${host.label}",
                    color = Pal.White,
                    style = mono(11.5.sp, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                onSetRoot?.let { setRoot ->
                    Text(
                        "root",
                        color = Pal.Peri,
                        style = mono(10.sp, FontWeight.Bold),
                        modifier = Modifier
                            .border(1.dp, Pal.Border)
                            .termClickable { setRoot(sftp.path) }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (sftp.loading) "…" else "⟳",
                    color = Pal.Dim,
                    style = mono(12.5.sp),
                    modifier = Modifier
                        .termClickable { scope.launch { open(sftp.path) } }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crumb("/", active = sftp.path == "/") { scope.launch { open("/") } }
                var acc = ""
                sftp.path.split('/').filter { it.isNotBlank() }.forEach { part ->
                    acc = "$acc/$part"
                    val target = acc
                    Text("›", color = Pal.Faint, style = mono(10.sp))
                    Crumb(part, active = target == sftp.path) { scope.launch { open(target) } }
                }
                if (state != "connected") {
                    Text(
                        "  ● ${if (state == "connecting") "connecting…" else state}",
                        color = if (state == "failed") Pal.Red else Pal.Amber,
                        style = mono(9.5.sp),
                    )
                }
            }
            HorizontalDivider(color = Pal.Border)

            sftp.error.ifBlank { if (state == "failed") error else "" }.takeIf { it.isNotBlank() }?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Pal.Panel)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✕ $message", color = Pal.Red, style = mono(10.5.sp), modifier = Modifier.weight(1f))
                    Text(
                        "retry",
                        color = Pal.Accent,
                        style = mono(10.5.sp, FontWeight.Bold),
                        modifier = Modifier
                            .termClickable {
                                if (state == "failed") onRetry() else scope.launch { open(sftp.path) }
                            }
                            .padding(horizontal = 8.dp),
                    )
                }
            }
            sftp.busy?.let { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Pal.PanelHi)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✻", color = Pal.Accent, style = mono(11.sp))
                    Spacer(Modifier.width(7.dp))
                    Text(line, color = Pal.White, style = mono(10.5.sp))
                }
            }
            sftp.note?.let { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("· $line", color = Pal.Dim, style = mono(10.sp), modifier = Modifier.weight(1f))
                    Text(
                        "×",
                        color = Pal.Gray,
                        style = mono(11.sp),
                        modifier = Modifier.termClickable { sftp.note = null },
                    )
                }
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .termClickable {
                                val parent = sftp.path.trimEnd('/').substringBeforeLast('/', "")
                                scope.launch { open(parent.ifBlank { "/" }) }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↰", color = Pal.Dim, style = mono(13.sp), modifier = Modifier.width(20.dp))
                        Text("..", color = Pal.Gray, style = mono(12.sp))
                    }
                    HorizontalDivider(color = Pal.Border)
                }
                if (sftp.entries.isEmpty() && !sftp.loading && sftp.error.isBlank() && state == "connected") {
                    item {
                        Text(
                            "Empty folder",
                            color = Pal.Dim,
                            style = mono(11.5.sp),
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                items(sftp.entries, key = { it.path }) { entry ->
                    SftpRow(
                        entry = entry,
                        onOpen = {
                            scope.launch { if (entry.isDir) open(entry.path) else openFile(entry) }
                        },
                        onMenu = { sftp.menu = entry },
                    )
                    HorizontalDivider(color = Pal.Border)
                }
                if (state == "connecting" && sftp.entries.isEmpty()) {
                    item {
                        Text(
                            "Connecting to ${host.target}…",
                            color = Pal.Dim,
                            style = mono(11.sp),
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = Pal.Border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.Bg)
                    .heightIn(min = 46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SftpTool("⌂", "Home", Modifier.weight(1f)) {
                    scope.launch { open("/") }
                }
                SftpTool("＋", "Folder", Modifier.weight(1f)) { sftp.creating = true }
                SftpTool("↑", "Upload", Modifier.weight(1f)) { uploadPicker.launch(arrayOf("*/*")) }
                SftpTool("⟳", "Refresh", Modifier.weight(1f)) { scope.launch { open(sftp.path) } }
                SftpTool("⌁", "Terminal", Modifier.weight(1.1f), accent = true, onClick = onTerminal)
            }
        }

        sftp.menu?.let { entry ->
            SftpActionSheet(
                entry = entry,
                onOpen = {
                    sftp.menu = null
                    scope.launch { if (entry.isDir) open(entry.path) else openFile(entry) }
                },
                onDownload = {
                    sftp.menu = null
                    downloadTarget = entry
                    downloadPicker.launch(entry.name)
                },
                onRename = {
                    sftp.menu = null
                    sftp.rename = entry
                },
                onDelete = {
                    sftp.menu = null
                    sftp.deleting = entry
                },
                onCopy = {
                    copyText(context, entry.path)
                    sftp.note = "Path copied"
                    sftp.menu = null
                },
                onDismiss = { sftp.menu = null },
            )
        }

        sftp.editor?.let { editor ->
            val dirty = editor.draft != editor.original
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Pal.Bg),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            editor.path.substringAfterLast('/'),
                            color = Pal.White,
                            style = mono(12.sp, FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "save",
                            color = if (dirty) Pal.Green else Pal.Faint,
                            style = mono(11.5.sp, FontWeight.Bold),
                            modifier = Modifier
                                .termClickable {
                                    if (!dirty) return@termClickable
                                    val session = ssh ?: return@termClickable
                                    scope.launch {
                                        sftp.busy = "Saving…"
                                        try {
                                            session.writeText(editor.path, editor.draft)
                                            sftp.editor = editor.copy(original = editor.draft)
                                            sftp.note = "Saved ${editor.path.substringAfterLast('/')}"
                                        } catch (err: Throwable) {
                                            sftp.note = err.message ?: "Save failed"
                                        } finally {
                                            sftp.busy = null
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        Text(
                            "close",
                            color = Pal.Gray,
                            style = mono(11.sp),
                            modifier = Modifier
                                .termClickable { sftp.editor = null }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        editor.path,
                        color = Pal.Dim,
                        style = mono(9.5.sp),
                        modifier = Modifier.padding(horizontal = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HorizontalDivider(color = Pal.Border)
                    BasicTextField(
                        value = editor.draft,
                        onValueChange = { sftp.editor = editor.copy(draft = it) },
                        textStyle = mono(11.sp).copy(color = Pal.White),
                        cursorBrush = SolidColor(Pal.Accent),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (sftp.creating) {
            SshPromptDialog(
                title = "New folder",
                label = "name",
                initial = "",
                confirm = "Create",
                onConfirm = { name ->
                    sftp.creating = false
                    val target = name.trim()
                    if (target.isBlank()) return@SshPromptDialog
                    val session = ssh ?: return@SshPromptDialog
                    scope.launch {
                        sftp.busy = "Creating $target…"
                        try {
                            session.mkdir(session.joinRemote(sftp.path, target))
                            sftp.note = "Created $target"
                            open(sftp.path)
                        } catch (err: Throwable) {
                            sftp.note = err.message ?: "mkdir failed"
                        } finally {
                            sftp.busy = null
                        }
                    }
                },
                onCancel = { sftp.creating = false },
            )
        }

        sftp.rename?.let { entry ->
            SshPromptDialog(
                title = "Rename",
                label = "new name",
                initial = entry.name,
                confirm = "Rename",
                onConfirm = { name ->
                    sftp.rename = null
                    val target = name.trim()
                    if (target.isBlank() || target == entry.name) return@SshPromptDialog
                    val session = ssh ?: return@SshPromptDialog
                    scope.launch {
                        sftp.busy = "Renaming…"
                        try {
                            session.rename(entry.path, session.joinRemote(sftp.path, target))
                            sftp.note = "Renamed to $target"
                            open(sftp.path)
                        } catch (err: Throwable) {
                            sftp.note = err.message ?: "Rename failed"
                        } finally {
                            sftp.busy = null
                        }
                    }
                },
                onCancel = { sftp.rename = null },
            )
        }

        sftp.deleting?.let { entry ->
            SshConfirmDialog(
                title = "Delete ${entry.name}?",
                body = if (entry.isDir) {
                    "The folder and everything inside will be removed from ${host.host}."
                } else {
                    "The file will be removed from ${host.host}."
                },
                confirm = "Delete",
                danger = true,
                onConfirm = {
                    sftp.deleting = null
                    val session = ssh ?: return@SshConfirmDialog
                    scope.launch {
                        sftp.busy = "Deleting ${entry.name}…"
                        try {
                            session.delete(entry.path)
                            sftp.note = "Deleted ${entry.name}"
                            open(sftp.path)
                        } catch (err: Throwable) {
                            sftp.note = err.message ?: "Delete failed"
                        } finally {
                            sftp.busy = null
                        }
                    }
                },
                onCancel = { sftp.deleting = null },
            )
        }
    }
}

@Composable
private fun Crumb(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Pal.White else Pal.PeriSoft,
        style = mono(11.sp, if (active) FontWeight.Bold else null),
        maxLines = 1,
        modifier = Modifier
            .termClickable(onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
    )
}

@Composable
private fun SftpRow(
    entry: RemoteEntry,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .termClickable(onOpen)
            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            glyphFor(entry),
            color = when {
                entry.isDir -> Pal.PeriSoft
                entry.isLink -> Pal.Amber
                else -> Pal.Gray
            },
            style = mono(12.sp),
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                color = Pal.White,
                style = mono(12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.isDir) "folder" else "${formatSize(entry.size)} · ${formatDate(entry.mtime)}",
                color = Pal.Dim,
                style = mono(9.5.sp),
                maxLines = 1,
            )
        }
        Text(
            "⋮",
            color = Pal.Gray,
            style = mono(14.sp, FontWeight.Bold),
            modifier = Modifier
                .termClickable(onMenu)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SftpTool(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .termClickable(onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            icon,
            color = if (accent) Pal.Accent else Pal.White,
            style = mono(13.sp, FontWeight.Bold),
        )
        Text(
            label,
            color = if (accent) Pal.Accent else Pal.Dim,
            style = mono(8.5.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SftpActionSheet(
    entry: RemoteEntry,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .termClickable(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Pal.Bg)
                .border(1.dp, Pal.Border),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.name,
                    color = Pal.White,
                    style = mono(12.sp, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (entry.isDir) "folder" else formatSize(entry.size),
                    color = Pal.Dim,
                    style = mono(10.sp),
                )
            }
            HorizontalDivider(color = Pal.Border)
            SftpAction(if (entry.isDir) "Open folder" else "Edit file", onOpen)
            if (!entry.isDir) SftpAction("Download to phone", onDownload)
            SftpAction("Rename", onRename)
            SftpAction("Copy path", onCopy)
            SftpAction("Delete", onDelete, danger = true)
        }
    }
}

@Composable
private fun SftpAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Text(
        label,
        color = if (danger) Pal.Red else Pal.White,
        style = mono(12.5.sp),
        modifier = Modifier
            .fillMaxWidth()
            .termClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
    HorizontalDivider(color = Pal.Border)
}

@Composable
private fun SshPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(title, initial) { mutableStateOf(initial) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 26.dp)
                .fillMaxWidth()
                .background(Pal.Bg)
                .border(1.dp, Pal.Border)
                .padding(14.dp),
        ) {
            Text(title, color = Pal.White, style = mono(13.sp, FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            Text(label, color = Pal.Dim, style = mono(10.sp))
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Pal.Panel)
                    .border(1.dp, Pal.Border)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = mono(12.5.sp).copy(color = Pal.White),
                    cursorBrush = SolidColor(Pal.Accent),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "cancel",
                    color = Pal.Gray,
                    style = mono(12.sp),
                    modifier = Modifier
                        .termClickable(onCancel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    confirm,
                    color = Pal.Accent,
                    style = mono(12.sp, FontWeight.Bold),
                    modifier = Modifier
                        .termClickable { onConfirm(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SshConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 26.dp)
                .fillMaxWidth()
                .background(Pal.Bg)
                .border(1.dp, Pal.Border)
                .padding(14.dp),
        ) {
            Text(title, color = Pal.White, style = mono(13.sp, FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Text(body, color = Pal.Gray, style = mono(11.sp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "cancel",
                    color = Pal.Gray,
                    style = mono(12.sp),
                    modifier = Modifier
                        .termClickable(onCancel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    confirm,
                    color = if (danger) Pal.Red else Pal.Accent,
                    style = mono(12.sp, FontWeight.Bold),
                    modifier = Modifier
                        .termClickable(onConfirm)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalWebView(
    modifier: Modifier = Modifier,
    onInput: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    onReady: () -> Unit,
    onCopy: (String) -> Unit,
    onCreated: (WebView) -> Unit,
    onDestroy: (WebView) -> Unit,
) {
    val main = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        TerminalBridge(
            onInput = onInput,
            onResize = { c, r -> main.post { onResize(c, r) } },
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
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
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

private fun copyText(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", text))
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "upload.bin"
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index) ?: name
                }
            }
    }
    return name
}

private fun glyphFor(entry: RemoteEntry): String {
    if (entry.isDir) return "▸"
    if (entry.isLink) return "↗"
    val name = entry.name.lowercase()
    return when {
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".svg") -> "▧"
        name.endsWith(".zip") || name.endsWith(".tar") || name.endsWith(".gz") ||
            name.endsWith(".tgz") || name.endsWith(".xz") || name.endsWith(".7z") -> "▣"
        name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".py") ||
            name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".sh") ||
            name.endsWith(".go") || name.endsWith(".rs") || name.endsWith(".c") ||
            name.endsWith(".cpp") || name.endsWith(".json") || name.endsWith(".yml") ||
            name.endsWith(".yaml") -> "‹›"
        else -> "▤"
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun formatDate(millis: Long): String {
    if (millis <= 0) return ""
    return SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH).format(Date(millis))
}
