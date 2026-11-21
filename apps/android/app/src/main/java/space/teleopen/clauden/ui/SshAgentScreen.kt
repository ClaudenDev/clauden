package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.teleopen.clauden.data.CodeApi
import space.teleopen.clauden.data.CodeSession
import space.teleopen.clauden.data.HostKeyChangedException
import space.teleopen.clauden.data.SshConnectionHolder
import space.teleopen.clauden.data.SshHost
import space.teleopen.clauden.data.SshSession
import space.teleopen.clauden.data.SshStore
import space.teleopen.clauden.data.collectRemoteContext

@Composable
internal fun SshAgentScreen(
    api: CodeApi,
    signedIn: Boolean,
    claudeMode: Boolean,
    host: SshHost,
    onBack: () -> Unit,
    onTerminal: () -> Unit,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SshStore(context) }
    val scope = rememberCoroutineScope()
    var currentHost by remember { mutableStateOf(host) }
    var client by remember { mutableStateOf<SshSession?>(null) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var root by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf("connecting") }
    var error by remember { mutableStateOf("") }
    var hostKeyPrompt by remember { mutableStateOf<HostKeyChangedException?>(null) }

    LaunchedEffect(host.id, attempt) {
        val keepUi = state == "ready" && sessionId != null && client != null
        if (!keepUi) {
            state = "connecting"
            error = ""
            sessionId = null
            root = ""
        }

        if (!signedIn) {
            state = "error"
            error = "Войдите в аккаунт Clauden — агенту нужен сервер Clauden."
            return@LaunchedEffect
        }
        if (claudeMode) {
            state = "error"
            error = "Включён режим Claude. Переключите движок на Clauden в настройках, " +
                "чтобы агент работал на SSH-сервере."
            return@LaunchedEffect
        }

        if (attempt > 0) SshConnectionHolder.close(currentHost.id)
        val ssh = SshConnectionHolder.get(currentHost)
        try {

            ssh.ensureConnected(forceProbe = true)
            if (currentHost.fingerprint.isBlank() && ssh.observedFingerprint.isNotBlank()) {
                store.updateFingerprint(currentHost.id, ssh.observedFingerprint)
                currentHost = currentHost.copy(fingerprint = ssh.observedFingerprint)
            }
            var session: CodeSession? = null
            val saved = currentHost.agentSession
            if (saved.isNotBlank()) {
                session = runCatching { api.session(saved) }
                    .getOrNull()
                    ?.takeIf {
                        it.source == "ssh" && it.remote?.target == currentHost.target
                    }
            }

            val knownRoot = session?.remote?.root?.takeIf { it.isNotBlank() }
            val pwd = if (knownRoot != null) {
                ""
            } else {
                runCatching { ssh.pwd() }.getOrElse {
                    runCatching { ssh.exec("pwd", "", 10).stdout.trim() }.getOrDefault("/")
                }
            }
            val projectRoot = knownRoot ?: pwd

            var created = false
            if (session == null) {
                val initialContext = runCatching {
                    collectRemoteContext(ssh, projectRoot, pwd)
                }.getOrNull()
                session = api.createSsh(
                    currentHost.label,
                    currentHost.target,
                    projectRoot,
                    initialContext?.toJson().orEmpty(),
                )
                store.updateAgentSession(currentHost.id, session.id)
                currentHost = currentHost.copy(agentSession = session.id)
                created = true
            }

            val old = client
            client = ssh
            root = projectRoot
            sessionId = session.id
            state = "ready"
            if (old != null && old !== ssh) old.close()

            if (!created) {
                scope.launch {
                    val home = pwd.ifBlank {
                        runCatching { ssh.pwd() }.getOrDefault(projectRoot)
                    }
                    val context = runCatching {
                        collectRemoteContext(ssh, projectRoot, home)
                    }.getOrNull() ?: return@launch
                    runCatching {
                        api.updateRemote(session.id, projectRoot, context.toJson())
                    }
                }
            }
        } catch (err: CancellationException) {
            ssh.close()
            SshConnectionHolder.close(currentHost.id)
            throw err
        } catch (err: HostKeyChangedException) {
            ssh.close()
            SshConnectionHolder.close(currentHost.id)
            if (!keepUi) {
                client = null
                state = "error"
            } else {
                state = "error"
            }
            error = "Отпечаток SSH-сервера изменился"
            hostKeyPrompt = err
        } catch (err: Throwable) {
            ssh.close()
            SshConnectionHolder.close(currentHost.id)
            if (!keepUi) client = null
            state = "error"
            error = err.message ?: "Не удалось подключиться к серверу"
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val current = client
        if (state != "ready" || current == null) return@LifecycleEventEffect
        if (!current.connected) {
            attempt += 1
        } else {
            scope.launch {
                val probe = withContext(Dispatchers.IO) {
                    runCatching { current.exec("true", root, 5) }.getOrNull()
                }
                val alive = probe != null && !probe.timedOut
                if (!alive) attempt += 1
            }
        }
    }

    val readyId = sessionId
    val ssh = client
    if (state == "ready" && readyId != null && ssh != null) {
        TerminalSession(
            api = api,
            sessionId = readyId,
            onBack = onBack,
            onMenu = onMenu,
            ssh = ssh,
            sshHost = currentHost,
            sshRoot = root,
            onTerminal = onTerminal,
        )
        return
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg),
    ) {
        TerminalTopBar(
            title = currentHost.label,
            subtitle = if (state == "connecting") "агент · подключаюсь" else "агент · ошибка",
            onBack = onBack,
            onMenu = onMenu,
            trailing = {
                TextButton(onClick = onTerminal) {
                    Text("терминал", style = TermTiny, color = TermAccent)
                }
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Подключаюсь к ${currentHost.target}",
                        style = TermSmall,
                        color = TermDim,
                    )
                    Text(
                        text = "Проверяю доступ и готовлю агента…",
                        style = TermTiny,
                        color = TermFaint,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        text = "Агент не запустился",
                        style = TermBody.copy(fontWeight = FontWeight.SemiBold),
                        color = TermRed,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = TermSmall,
                        color = TermDim,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Повторить",
                            style = TermSmall,
                            color = TermAccent,
                            modifier = Modifier
                                .background(
                                    TermAccent.copy(alpha = 0.12f),
                                    RoundedCornerShape(7.dp),
                                )
                                .clickable { attempt += 1 }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        Text(
                            text = "Терминал",
                            style = TermSmall,
                            color = TermDim,
                            modifier = Modifier
                                .background(TermPanel, RoundedCornerShape(7.dp))
                                .border(1.dp, TermBorder, RoundedCornerShape(7.dp))
                                .clickable { onTerminal() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Обычный SSH-терминал работает и без Clauden-аккаунта.",
                        style = TermTiny,
                        color = TermFaint,
                    )
                }
            }
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
