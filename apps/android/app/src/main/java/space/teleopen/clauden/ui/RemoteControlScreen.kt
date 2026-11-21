package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import space.teleopen.clauden.data.CodeApi
import space.teleopen.clauden.data.CodeException
import space.teleopen.clauden.data.CodeForm
import space.teleopen.clauden.data.CodeMessage
import space.teleopen.clauden.data.CodePermission
import space.teleopen.clauden.data.CodeSession
import space.teleopen.clauden.data.CodeStep
import space.teleopen.clauden.data.CodeTodo
import space.teleopen.clauden.data.RemoteApi
import space.teleopen.clauden.data.RemoteLink
import space.teleopen.clauden.data.RemoteSnapshot

@Composable
fun RemoteControlScreen(
    baseUrl: String,
    authToken: String,
    signedIn: Boolean,
    claudeMode: Boolean,
    codeAccess: Boolean,
    token: String,
    onTokenChange: (String) -> Unit,
    pendingToken: String,
    onConsumePending: () -> Unit,
    onMenu: () -> Unit,
) {

    LaunchedEffect(pendingToken) {
        if (pendingToken.isBlank()) return@LaunchedEffect
        val parsed = remoteToken(pendingToken)
        if (parsed.isNotBlank()) onTokenChange(parsed)
        onConsumePending()
    }

    when {
        !signedIn -> RemoteBlocked(
            "Войдите в аккаунт Clauden — Code работает через наш сервер.",
        )
        claudeMode -> RemoteBlocked(
            "Сейчас включён режим Claude. Code работает через сервер Clauden — " +
                "переключите движок в настройках или оформите подписку.",
        )
        !codeAccess -> RemoteBlocked(
            "Code доступен только с подпиской, оформленной в Clauden. " +
                "Подписка, выданная вручную или купленная в Claude, Code не открывает.",
        )
        token.isBlank() -> RemoteConnect(
            baseUrl = baseUrl,
            authToken = authToken,
            signedIn = signedIn,
            onOpen = onTokenChange,
        )
        else -> RemoteLive(
            baseUrl = baseUrl,
            token = token,
            onLeave = { onTokenChange("") },
        )
    }
}

@Composable
private fun RemoteBlocked(text: String) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = colors.card,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Code",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RemoteConnect(
    baseUrl: String,
    authToken: String,
    signedIn: Boolean,
    onOpen: (String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val scope = rememberCoroutineScope()
    val api = remember(baseUrl, authToken) { CodeApi(baseUrl, authToken) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<CodeSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf("") }
    var listError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(signedIn, refreshKey) {
        if (!signedIn) return@LaunchedEffect
        loading = true
        listError = null
        runCatching { api.sessions() }
            .onSuccess { list ->
                sessions = list.sortedWith(
                    compareByDescending<CodeSession> { it.working }
                        .thenByDescending { it.updatedAt },
                )
            }
            .onFailure { listError = it.codeMessage }
        loading = false
    }

    fun connect() {
        val parsed = remoteToken(input)
        if (parsed.isBlank()) {
            error = "Не понял ссылку. Нужна ссылка вида https://clauden.best/r/…"
            return
        }
        error = null
        onOpen(parsed)
    }

    fun openSession(session: CodeSession) {
        if (opening.isNotBlank()) return
        opening = session.id
        listError = null
        scope.launch {
            runCatching { api.link(session.id) ?: api.createLink(session.id, 1440) }
                .onSuccess { link ->
                    if (link.token.isBlank()) {
                        listError = "Сервер не выдал ссылку на сессию"
                    } else {
                        input = link.url.ifBlank { "https://clauden.best/r/${link.token}" }
                        onOpen(link.token)
                    }
                }
                .onFailure { listError = it.codeMessage }
            opening = ""
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
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
                        "Remote Control",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Живая сессия Clauden Code: сообщения, шаги агента и запросы " +
                            "разрешений прямо с телефона.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                        },
                        placeholder = {
                            Text(
                                "https://clauden.best/r/…",
                                color = colors.textTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = remoteFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.danger,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { connect() },
                        enabled = input.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.clay,
                            contentColor = Color.White,
                            disabledContainerColor = colors.buttonDisabled,
                            disabledContentColor = colors.buttonDisabledText,
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Text("Подключиться", fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ссылку присылает сама сессия Code: команда /remote-control.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                    )
                }
            }
        }

        if (signedIn) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Живые сессии",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { refreshKey += 1 },
                        enabled = !loading,
                    ) {
                        Text(
                            if (loading) "Обновляю…" else "Обновить",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = if (loading) colors.textTertiary else colors.clay,
                        )
                    }
                }
            }
            if (loading && sessions.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.clay,
                        )
                    }
                }
            }
            listError?.let {
                item {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.danger,
                    )
                }
            }
            items(sessions, key = { it.id }) { session ->
                RemoteSessionRow(
                    session = session,
                    busy = opening == session.id,
                    onClick = { openSession(session) },
                )
            }
            if (!loading && sessions.isEmpty() && listError == null) {
                item {
                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Пока нет живых сессий. Запустите сессию в Clauden Code " +
                                "на компьютере — она появится здесь.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Войдите в аккаунт Clauden, чтобы видеть свои сессии. " +
                            "Ссылку из сообщения можно открыть и без входа.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteSessionRow(
    session: CodeSession,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title.ifBlank {
                        session.repoName.ifBlank { "Сессия Code" }
                    },
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
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = colors.clay,
                    )
                } else {
                    RemoteStatusPill(session.status)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                sessionSubtitle(session),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
private fun RemoteStatusPill(status: String) {
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
private fun RemoteLive(
    baseUrl: String,
    token: String,
    onLeave: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val api = remember(baseUrl, token) { RemoteApi(baseUrl, token) }
    var snapshot by remember(token) { mutableStateOf<RemoteSnapshot?>(null) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var expired by remember(token) { mutableStateOf(false) }
    var reconnecting by remember(token) { mutableStateOf(false) }
    var loading by remember(token) { mutableStateOf(true) }
    var draft by rememberSaveable(token) { mutableStateOf("") }
    var sending by remember(token) { mutableStateOf(false) }
    var busy by remember(token) { mutableStateOf("") }
    var refreshKey by remember(token) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val session = snapshot?.session

    fun applySession(next: CodeSession) {
        val current = snapshot
        snapshot = if (current == null) RemoteSnapshot(next, RemoteLink()) else current.copy(session = next)
    }

    fun fail(err: Throwable) {
        error = err.codeMessage
        if ((err as? CodeException)?.status == 404) expired = true
    }

    fun stop() {
        scope.launch {
            runCatching { api.stop() }
                .onFailure { fail(it) }
            runCatching { api.snapshot() }
                .onSuccess { snapshot = it }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isBlank() || sending || session?.working == true) return
        scope.launch {
            sending = true
            error = null
            runCatching { api.send(text) }
                .onSuccess {
                    applySession(it)
                    draft = ""
                }
                .onFailure { fail(it) }
            sending = false
        }
    }

    fun decide(permission: CodePermission, decision: String) {
        if (busy.isNotBlank()) return
        busy = permission.id
        scope.launch {
            runCatching { api.permission(permission.id, decision) }
                .onSuccess { applySession(it) }
                .onFailure { fail(it) }
            busy = ""
        }
    }

    fun answer(form: CodeForm, values: Map<String, String>) {
        if (busy.isNotBlank()) return
        busy = form.id
        scope.launch {
            runCatching { api.form(form.id, values) }
                .onSuccess { applySession(it) }
                .onFailure { fail(it) }
            busy = ""
        }
    }

    BackHandler { onLeave() }

    LaunchedEffect(token, refreshKey) {
        while (isActive) {
            try {
                if (snapshot == null) {
                    snapshot = api.snapshot()
                    loading = false
                    error = null
                    expired = false
                }
                api.stream(onOpen = {
                    reconnecting = false
                    loading = false
                }) { name, data ->
                    val current = snapshot?.session
                    when (name) {
                        "snapshot" -> runCatching {
                            json.decodeFromJsonElement<RemoteSnapshot>(data)
                        }.onSuccess { snapshot = it }
                        "delta" -> {
                            val piece = runCatching {
                                data.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                            }.getOrNull() ?: return@stream
                            if (current != null) {
                                snapshot = snapshot?.copy(session = current.appendDelta(piece))
                            }
                        }
                        "step" -> {
                            val step = runCatching {
                                json.decodeFromJsonElement<CodeStep>(data)
                            }.getOrNull() ?: return@stream
                            if (current != null) {
                                snapshot = snapshot?.copy(session = current.upsertStep(step))
                            }
                        }
                        "status" -> {
                            val status = runCatching {
                                data.jsonObject["status"]?.jsonPrimitive?.contentOrNull
                            }.getOrNull() ?: return@stream
                            val message = runCatching {
                                data.jsonObject["error"]?.jsonPrimitive?.contentOrNull
                            }.getOrNull().orEmpty()
                            if (current != null) {
                                snapshot = snapshot?.copy(
                                    session = current.copy(status = status, error = message),
                                )
                            }
                            if (status != "working") {
                                scope.launch {
                                    runCatching { api.snapshot() }
                                        .onSuccess { snapshot = it }
                                }
                            }
                        }
                        "permissions" -> runCatching {
                            json.decodeFromJsonElement<List<CodePermission>>(data)
                        }.onSuccess { list ->
                            if (current != null) {
                                snapshot = snapshot?.copy(session = current.copy(permissions = list))
                            }
                        }
                        "forms" -> runCatching {
                            json.decodeFromJsonElement<List<CodeForm>>(data)
                        }.onSuccess { list ->
                            if (current != null) {
                                snapshot = snapshot?.copy(session = current.copy(forms = list))
                            }
                        }
                        "todos" -> runCatching {
                            json.decodeFromJsonElement<List<CodeTodo>>(data)
                        }.onSuccess { list ->
                            if (current != null) {
                                snapshot = snapshot?.copy(session = current.copy(todos = list))
                            }
                        }
                    }
                }

                delay(1000)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Throwable) {
                if (err is CodeException && err.status == 404) {
                    expired = true
                    error = "Ссылка недействительна или истекла"
                    loading = false
                    break
                }
                loading = false
                if (snapshot == null) error = err.codeMessage
                reconnecting = true
                delay(2500)
            }
        }
    }

    val messageCount = session?.messages?.size ?: 0
    LaunchedEffect(
        messageCount,
        session?.messages?.lastOrNull()?.text?.length,
        session?.permissions?.size,
        session?.forms?.size,
    ) {
        val total = messageCount +
            (session?.permissions?.size ?: 0) +
            (session?.forms?.size ?: 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onLeave) {
                Icon(
                    ClaudenIcons.arrowBack,
                    contentDescription = "Отключиться",
                    tint = colors.textPrimary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session?.title?.ifBlank { session.repoName } ?: "Remote Control",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    session?.let { sessionSubtitle(it) }.orEmpty().ifBlank { "живая сессия Code" },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            session?.let { RemoteStatusPill(it.status) }
            Spacer(Modifier.width(8.dp))
            if (reconnecting) {
                Text(
                    "Переподключаюсь…",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refreshKey += 1 }) {
                Text(
                    "Обновить",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.clay,
                )
            }
            TextButton(onClick = onLeave) {
                Text(
                    "Отключиться",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            }
        }
        if (session?.working == true) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.clay,
                trackColor = colors.card,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Агент работает…",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.clay,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { stop() }) {
                    Text(
                        "Стоп",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.danger,
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                expired -> RemoteProblem(
                    title = "Ссылка больше не работает",
                    text = error ?: "Ссылка недействительна или истекла. Попросите новую.",
                    action = "К подключению",
                    onAction = onLeave,
                )
                session == null && loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = colors.clay,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Подключаюсь к сессии…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
                session == null -> RemoteProblem(
                    title = "Не удалось открыть сессию",
                    text = error ?: "Проверьте интернет и повторите.",
                    action = "Повторить",
                    onAction = { refreshKey += 1 },
                    secondary = "Отключиться",
                    onSecondary = onLeave,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (session.messages.isEmpty() &&
                        session.permissions.isEmpty() &&
                        session.forms.isEmpty()
                    ) {
                        item {
                            Text(
                                "Сообщений пока нет. Напишите агенту — он работает в этой сессии.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = colors.textTertiary,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    itemsIndexed(
                        session.messages,
                        key = { index, message -> message.id.ifBlank { "m$index" } },
                    ) { _, message ->
                        if (message.role == "user") {
                            RemoteUserBubble(message.text)
                        } else {
                            RemoteAgentBlock(message)
                        }
                    }
                    items(session.permissions, key = { "p${it.id}" }) { permission ->
                        RemotePermissionCard(
                            permission = permission,
                            busy = busy == permission.id,
                            onDecide = { decide(permission, it) },
                        )
                    }
                    items(session.forms, key = { "f${it.id}" }) { form ->
                        RemoteFormCard(
                            form = form,
                            busy = busy == form.id,
                            onAnswer = { answer(form, it) },
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }

        error?.let {
            if (session != null && !expired) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }

        if (session != null && !expired) {
            HorizontalDivider(color = colors.border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            "Сообщение агенту…",
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    colors = remoteFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { send() },
                    enabled = draft.isNotBlank() && !sending && session.working != true,
                ) {
                    Icon(
                        ClaudenIcons.arrowUp,
                        contentDescription = "Отправить",
                        tint = if (draft.isNotBlank() && !sending && session.working != true) {
                            colors.clay
                        } else {
                            colors.textTertiary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteProblem(
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.clay,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(action, fontSize = 14.sp)
            }
            if (secondary != null && onSecondary != null) {
                TextButton(onClick = onSecondary) {
                    Text(secondary, fontSize = 14.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RemoteUserBubble(text: String) {
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
private fun RemoteAgentBlock(message: CodeMessage) {
    val colors = LocalClaudenColors.current
    var open by rememberSaveable(message.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (message.steps.isNotEmpty()) {
            val done = message.steps.count { it.status == "done" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (open) "▾" else "▸",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Действия · $done/${message.steps.size}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                )
            }
            if (open) {
                message.steps.forEach { step -> RemoteStepRow(step) }
                Spacer(Modifier.height(6.dp))
            }
        }
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
private fun RemoteStepRow(step: CodeStep) {
    val colors = LocalClaudenColors.current
    val tint = when (step.status) {
        "failed" -> colors.danger
        "done" -> colors.textTertiary
        else -> colors.clay
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 1.dp, bottom = 1.dp),
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
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = if (step.status == "failed") colors.danger else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemotePermissionCard(
    permission: CodePermission,
    busy: Boolean,
    onDecide: (String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Разрешение" + permission.action.takeIf { it.isNotBlank() }
                    ?.let { " · $it" }
                    .orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.danger,
            )
            if (permission.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    permission.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textPrimary,
                )
            }
            permission.resources.take(3).forEach { resource ->
                Text(
                    resource,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDecide("reject") }, enabled = !busy) {
                    Text(
                        "Отклонить",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDecide("always") }, enabled = !busy) {
                    Text(
                        "Всегда",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = colors.textSecondary,
                    )
                }
                Button(
                    onClick = { onDecide("once") },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.clay,
                        contentColor = Color.White,
                        disabledContainerColor = colors.buttonDisabled,
                        disabledContentColor = colors.buttonDisabledText,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Разрешить", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RemoteFormCard(
    form: CodeForm,
    busy: Boolean,
    onAnswer: (Map<String, String>) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val answers = remember(form.id) { mutableStateMapOf<String, String>() }
    val requiredReady = form.fields
        .filter { it.required }
        .all { !answers[it.key].isNullOrBlank() }

    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.clay.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Вопрос агента",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.clay,
            )
            Text(
                form.title.ifBlank { "Нужен ответ" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
            )
            form.fields.forEach { field ->
                Spacer(Modifier.height(8.dp))
                Text(
                    field.title.ifBlank { "Ответ" },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textPrimary,
                )
                if (field.description.isNotBlank()) {
                    Text(
                        field.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                    )
                }
                field.options.forEach { option ->
                    val selected = answers[field.key] == option.value
                    Surface(
                        color = if (selected) colors.drawerPill else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .border(
                                1.dp,
                                if (selected) colors.clay else colors.border,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { answers[field.key] = option.value },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                option.label.ifBlank { option.value },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = colors.textPrimary,
                            )
                            if (option.description.isNotBlank()) {
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }
                }
                if (field.options.isEmpty() || field.custom) {
                    OutlinedTextField(
                        value = answers[field.key].orEmpty(),
                        onValueChange = { answers[field.key] = it },
                        placeholder = {
                            Text(
                                if (field.custom && field.options.isNotEmpty()) {
                                    "Свой ответ"
                                } else {
                                    "Ответ"
                                },
                                color = colors.textTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = remoteFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onAnswer(answers.toMap()) },
                enabled = !busy && answers.values.any { it.isNotBlank() } && requiredReady,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.clay,
                    contentColor = Color.White,
                    disabledContainerColor = colors.buttonDisabled,
                    disabledContentColor = colors.buttonDisabledText,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Text("Ответить", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun remoteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalClaudenColors.current.borderStrong,
    unfocusedBorderColor = LocalClaudenColors.current.border,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = LocalClaudenColors.current.clay,
    focusedTextColor = LocalClaudenColors.current.textPrimary,
    unfocusedTextColor = LocalClaudenColors.current.textPrimary,
)

private fun remoteToken(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    val marker = "/r/"
    val at = value.indexOf(marker)
    val token = if (at >= 0) {
        value.substring(at + marker.length)
    } else {
        value.substringAfterLast('/')
    }.substringBefore('/').substringBefore('?').substringBefore('#').trim()
    if (!Regex("^[a-z0-9]{16,64}$").matches(token)) return ""
    return token
}

private fun sessionSubtitle(session: CodeSession): String {
    val parts = listOf(session.repoName, session.branch)
        .filter { it.isNotBlank() }
    return parts.joinToString(" · ").ifBlank {
        when (session.source) {
            "device" -> "папка с телефона"
            "ssh" -> "удалённый сервер"
            else -> "сессия Code"
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
