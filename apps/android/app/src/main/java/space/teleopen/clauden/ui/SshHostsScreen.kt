package space.teleopen.clauden.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.teleopen.clauden.data.CodeApi
import space.teleopen.clauden.data.SshConnectionHolder
import space.teleopen.clauden.data.SshHost
import space.teleopen.clauden.data.SshStore

@Composable
internal fun CodeModeTabs(
    active: String,
    onPhone: () -> Unit,
    onSsh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(TermPanel, RoundedCornerShape(9.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ModeTab("Телефон", active == "phone", Modifier.weight(1f), onPhone)
        ModeTab("SSH", active == "ssh", Modifier.weight(1f), onSsh)
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = TermSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) TermAccent else TermDim,
        modifier = modifier
            .background(
                if (selected) TermAccent.copy(alpha = 0.12f) else TermPanel,
                RoundedCornerShape(7.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
internal fun SshHostsScreen(
    api: CodeApi,
    onBack: () -> Unit,
    onConnect: (SshHost) -> Unit,
    onTerminal: (SshHost) -> Unit,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SshStore(context) }
    var hosts by remember { mutableStateOf(store.hosts()) }
    var editing by remember { mutableStateOf<SshHost?>(null) }
    var deleting by remember { mutableStateOf<SshHost?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg),
    ) {
        TerminalTopBar(
            title = "SSH",
            subtitle = if (hosts.isEmpty()) "серверы" else "${hosts.size} серверов",
            onBack = onBack,
            onMenu = onMenu,
            trailing = {
                IconButton(
                    onClick = { editing = SshHost() },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        ClaudenIcons.plus,
                        contentDescription = "Новый сервер",
                        tint = TermAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        CodeModeTabs(
            active = "ssh",
            onPhone = onBack,
            onSsh = {},
        )

        if (hosts.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyHosts(onAdd = { editing = SshHost() })
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(hosts, key = { it.id }) { host ->
                    SshHostCard(
                        host = host,
                        onConnect = { onConnect(host) },
                        onTerminal = { onTerminal(host) },
                        onEdit = { editing = host },
                        onDelete = { deleting = host },
                    )
                }
                item {
                    Text(
                        text = "+ Новый сервер",
                        style = TermSmall,
                        color = TermAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = SshHost() }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        }

        Text(
            text = "Нажмите сервер — откроется Clauden-агент на этом сервере. " +
                "Кнопка >_ — обычный терминал и SFTP. Пароли и ключи шифруются на телефоне, " +
                "первое подключение запоминает отпечаток сервера.",
            style = TermTiny,
            color = TermFaint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }

    editing?.let { initial ->
        SshHostDialog(
            initial = initial,
            onSave = { host ->
                val previous = initial.agentSession
                store.save(host)
                SshConnectionHolder.close(host.id)
                hosts = store.hosts()
                editing = null
                if (previous.isNotBlank() && previous != host.agentSession) {
                    scope.launch { runCatching { api.remove(previous) } }
                }
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { host ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить сервер?") },
            text = { Text("${host.label}\n${host.target}", style = TermSmall) },
            confirmButton = {
                TextButton(onClick = {
                    val agentSession = host.agentSession
                    store.remove(host.id)
                    SshConnectionHolder.close(host.id)
                    hosts = store.hosts()
                    deleting = null
                    if (agentSession.isNotBlank()) {
                        scope.launch { runCatching { api.remove(agentSession) } }
                    }
                }) { Text("Удалить", color = TermRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun EmptyHosts(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(TermAccent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ClaudenIcons.terminal,
                contentDescription = null,
                tint = TermAccent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("SSH-серверы", style = TermBody.copy(fontWeight = FontWeight.SemiBold), color = TermText)
        Text(
            "Clauden-агент на сервере, обычный терминал и SFTP. Вход по паролю или ключу.",
            style = TermSmall,
            color = TermDim,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "+ Добавить сервер",
            style = TermSmall,
            color = TermAccent,
            modifier = Modifier
                .padding(top = 14.dp)
                .background(TermAccent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .clickable { onAdd() }
                .padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun SshHostCard(
    host: SshHost,
    onConnect: () -> Unit,
    onTerminal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermPanel, RoundedCornerShape(10.dp))
            .border(1.dp, TermBorder, RoundedCornerShape(10.dp))
            .clickable { onConnect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(TermAccent.copy(alpha = 0.13f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ClaudenIcons.terminal,
                contentDescription = null,
                tint = TermAccent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = host.label,
                style = TermSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TermText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = host.target,
                style = TermTiny,
                color = TermDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (host.auth == "key") ClaudenIcons.key else ClaudenIcons.lock,
                    contentDescription = null,
                    tint = TermFaint,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(host.authLabel, style = TermTiny, color = TermFaint)
            }
        }
        IconButton(onClick = onTerminal, modifier = Modifier.size(32.dp)) {
            Icon(
                ClaudenIcons.terminal,
                contentDescription = "Обычный терминал",
                tint = TermAccent,
                modifier = Modifier.size(15.dp),
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                ClaudenIcons.pencil,
                contentDescription = "Изменить",
                tint = TermFaint,
                modifier = Modifier.size(15.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                ClaudenIcons.trash,
                contentDescription = "Удалить",
                tint = TermFaint,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun SshHostDialog(
    initial: SshHost,
    onSave: (SshHost) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.user) }
    var auth by remember { mutableStateOf(initial.auth) }
    var password by remember { mutableStateOf(initial.password) }
    var privateKey by remember { mutableStateOf(initial.privateKey) }
    var passphrase by remember { mutableStateOf(initial.passphrase) }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val keyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader().readText().take(128 * 1024)
                        }.orEmpty()
                    }.getOrDefault("")
                }
                if (text.isNotBlank()) {
                    privateKey = text
                    error = ""
                } else {
                    error = "Не удалось прочитать ключ"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = TermBg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text = if (initial.id.isBlank()) "Новый SSH-сервер" else "Изменить сервер",
                    style = TermBody.copy(fontWeight = FontWeight.SemiBold),
                    color = TermText,
                )
                Spacer(Modifier.height(10.dp))
                SshField("Название", name, { name = it }, "Мой сервер")
                SshField("Хост", host, { host = it }, "example.com")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SshField("Порт", port, { port = it }, "22", Modifier.weight(1f), KeyboardType.Number)
                    SshField("Пользователь", user, { user = it }, "root", Modifier.weight(2f))
                }
                Spacer(Modifier.height(6.dp))
                Text("Авторизация", style = TermTiny, color = TermFaint)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(TermPanel, RoundedCornerShape(8.dp))
                        .border(1.dp, TermBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    AuthTab("Пароль", auth == "password", Modifier.weight(1f)) { auth = "password" }
                    AuthTab("Ключ", auth == "key", Modifier.weight(1f)) { auth = "key" }
                }

                if (auth == "password") {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SshField(
                            label = "Пароль",
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "••••••••",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Password,
                            password = !showPassword,
                        )
                        Text(
                            text = if (showPassword) "скрыть" else "показать",
                            style = TermTiny,
                            color = TermAccent,
                            modifier = Modifier
                                .clickable { showPassword = !showPassword }
                                .padding(start = 8.dp, top = 16.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Приватный ключ", style = TermTiny, color = TermFaint)
                    BasicTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        textStyle = TermTiny.copy(color = TermText, lineHeight = 15.sp),
                        cursorBrush = SolidColor(TermAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 180.dp)
                            .padding(top = 4.dp)
                            .background(TermPanel, RoundedCornerShape(7.dp))
                            .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
                            .padding(9.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Импортировать файл ключа",
                            style = TermTiny,
                            color = TermAccent,
                            modifier = Modifier
                                .clickable { keyPicker.launch(arrayOf("*/*")) }
                                .padding(vertical = 6.dp),
                        )
                    }
                    SshField(
                        label = "Пароль ключа (если есть)",
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        placeholder = "пусто",
                        keyboardType = KeyboardType.Password,
                        password = true,
                    )
                }

                error.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = TermTiny, color = TermRed)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(onClick = {
                        val portValue = port.toIntOrNull()
                        when {
                            host.isBlank() -> error = "Укажите хост"
                            user.isBlank() -> error = "Укажите пользователя"
                            portValue == null || portValue !in 1..65535 -> error = "Порт 1–65535"
                            auth == "password" && password.isBlank() -> error = "Введите пароль"
                            auth == "key" && privateKey.isBlank() -> error = "Вставьте ключ"
                            else -> onSave(
                                initial.copy(
                                    name = name.trim().ifBlank { host.trim() },
                                    host = host.trim(),
                                    port = portValue,
                                    user = user.trim(),
                                    auth = auth,
                                    password = password,
                                    privateKey = privateKey,
                                    passphrase = passphrase,
                                    fingerprint = if (
                                        initial.host == host.trim() && initial.port == portValue
                                    ) {
                                        initial.fingerprint
                                    } else {
                                        ""
                                    },
                                    agentSession = if (
                                        initial.host == host.trim() &&
                                        initial.port == portValue &&
                                        initial.user == user.trim()
                                    ) {
                                        initial.agentSession
                                    } else {
                                        ""
                                    },
                                ),
                            )
                        }
                    }) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
private fun SshField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    Column(modifier = modifier.padding(top = 7.dp)) {
        Text(label, style = TermTiny, color = TermFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TermSmall.copy(color = TermText),
            cursorBrush = SolidColor(TermAccent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(TermPanel, RoundedCornerShape(7.dp))
                        .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 9.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = TermSmall, color = TermFaint)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun AuthTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = TermSmall,
        color = if (selected) TermAccent else TermDim,
        modifier = modifier
            .background(
                if (selected) TermAccent.copy(alpha = 0.12f) else TermPanel,
                RoundedCornerShape(6.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
