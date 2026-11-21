package space.teleopen.clauden.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.ClaudeProblem
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.CLAUDE_IMPORT_VERSION
import space.teleopen.clauden.data.ClaudeErrorKind

@Composable
fun ClaudeModeScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    var unlinkOpen by remember { mutableStateOf(false) }
    var switchOpen by remember { mutableStateOf(false) }
    var passwordOpen by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.importClaudeFromFiles(uris)
    }
    val linked = state.claudeLinked
    val active = state.authMode == AuthMode.Claude
    val paid = !state.settings.plan.isBlank() && !state.settings.plan.equals("Free", true)

    NewSettingsScaffold(title = "Режим Claude", onBack = vm::backToSettings) {
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (active) "Сейчас работает Claude" else "Сейчас работает Clauden",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (active) {
                        "Запросы идут с устройства напрямую в claude.ai. Лимиты считает Anthropic."
                    } else {
                        "Ответы готовит шлюз Clauden в рамках тарифа. Аккаунт Claude не используется."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.textSecondary,
                )
            }
        }

        ProxyFixBanner()

        if (state.authError != null) {
            Surface(
                color = colors.danger.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.dismissAuthError() },
            ) {
                Text(
                    text = state.authError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.dangerSoft,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        NewSectionLabel("Аккаунт Claude")
        SettingsCard {
            if (linked) {
                SettingsRow(
                    icon = ClaudenIcons.person,
                    title = state.claudeEmail.ifBlank { "Аккаунт привязан" },
                    subtitle = "Тариф Claude: ${claudePlanName(state.claudePlan)}",
                )
                SettingsDivider()
                SettingsRow(
                    icon = ClaudenIcons.spark,
                    title = when {
                        active && paid -> "Переключиться на Clauden"
                        active -> "Clauden по подписке"
                        else -> "Переключиться на Claude"
                    },
                    subtitle = when {
                        active && paid -> "Вернуться к ответам через шлюз Clauden"
                        active -> "Модели Clauden доступны на Pro, Max и Ultra"
                        else -> "Работать своим аккаунтом Anthropic"
                    },
                    onClick = {
                        when {
                            active && paid -> vm.switchEngine(AuthMode.Clauden)
                            active -> vm.openSettingsSection(Screen.Billing)
                            paid -> switchOpen = true
                            else -> vm.switchEngine(AuthMode.Claude)
                        }
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = ClaudenIcons.arrowBack,
                    title = "Войти в Claude заново",
                    subtitle = "Если сессия истекла или сменился аккаунт",
                    onClick = vm::openClaudeLogin,
                )
                SettingsDivider()
                SettingsRow(
                    icon = ClaudenIcons.logout,
                    title = "Отвязать аккаунт Claude",
                    subtitle = "Стереть cookie и перейти на Clauden",
                    onClick = { unlinkOpen = true },
                )
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Можно войти своим аккаунтом claude.ai и пользоваться бесплатным Claude " +
                            "с интерфейсом Clauden. Пароль и cookie останутся на устройстве.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingsActionButton(
                        text = "Войти через Claude",
                        onClick = vm::openClaudeLogin,
                    )
                }
            }
        }

        BanRiskSection(
            state = state,
            onCheck = vm::checkBanRisk,
            onClear = vm::clearBanRisk,
        )

        NewSectionLabel("Чаты из Claude")
        SettingsCard {
            SettingsRow(
                icon = ClaudenIcons.chats,
                title = "Импортировать чаты",
                subtitle = "Перенести переписку, артефакты и файлы",
                onClick = vm::importClaudeChats,
            )
            SettingsDivider()
            SettingsRow(
                icon = ClaudenIcons.documentPlus,
                title = "Перенести без входа",
                subtitle = "ZIP/JSON: conversations, frames, projects, memories",
                onClick = { exportPicker.launch(arrayOf("*/*")) },
            )
            SettingsDivider()
            SettingsRow(
                icon = ClaudenIcons.download,
                title = "Загрузить бэкап",
                subtitle = "Восстановить чаты на новом устройстве",
                onClick = vm::restoreClaudeChats,
            )
        }

        val outdatedImports = state.conversations.any {
            it.claudeConversationId.isNotBlank() &&
                it.claudeFilesChecked &&
                it.claudeImportVersion < CLAUDE_IMPORT_VERSION
        }
        if (outdatedImports) {
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Импорт обновлён",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "В чатах, перенесённых старой версией, не хватает части " +
                            "сообщений и вложений. Нажмите «Импортировать чаты» ещё раз — " +
                            "приложение перечитает их и дошлёт файлы.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = colors.textSecondary,
                    )
                }
            }
        }

        if (state.claudeImportBusy || state.claudeImportTotal > 0 || state.claudeImportFinished) {
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.claudeImportBusy) "Импорт из Claude" else "Импорт завершён",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (state.claudeImportTotal > 0) {
                        LinearProgressIndicator(
                            progress = {
                                state.claudeImportDone.toFloat() /
                                    state.claudeImportTotal.coerceAtLeast(1).toFloat()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = colors.clay,
                            trackColor = colors.control,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${state.claudeImportDone} из ${state.claudeImportTotal}" +
                                state.claudeImportTitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
                                state.claudeImportFiles.takeIf { it > 0 }
                                    ?.let { " · файлов: $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            color = colors.textTertiary,
                        )
                    } else if (state.claudeImportFiles > 0) {
                        Text(
                            text = "Файлов: ${state.claudeImportFiles}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            color = colors.textTertiary,
                        )
                    }
                    if (state.claudeImportError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.claudeImportError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            color = colors.dangerSoft,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (state.claudeImportBusy) "Остановить" else "Закрыть",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.clay,
                        modifier = Modifier.clickable {
                            if (state.claudeImportBusy) {
                                vm.cancelClaudeImport()
                            } else {
                                vm.dismissClaudeImport()
                            }
                        },
                    )
                }
            }
        }

        val fileImport = state.claudeFileImport
        if (fileImport.busy || fileImport.finished) {
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (fileImport.busy) "Перенос из файлов" else "Перенос завершён",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (fileImport.busy) {
                        if (fileImport.total > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    fileImport.done.toFloat() /
                                        fileImport.total.coerceAtLeast(1).toFloat()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = colors.clay,
                                trackColor = colors.control,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${fileImport.done} из ${fileImport.total}" +
                                    fileImport.title.takeIf { it.isNotBlank() }
                                        ?.let { " · $it" }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                ),
                                color = colors.textTertiary,
                            )
                        } else {
                            Text(
                                text = "Читаю выбранные файлы…",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                ),
                                color = colors.textTertiary,
                            )
                        }
                    } else {
                        if (fileImport.error != null) {
                            Text(
                                text = fileImport.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                ),
                                color = colors.dangerSoft,
                            )
                        } else {
                            Text(
                                text = "Чатов: ${fileImport.chats} · Проектов: ${fileImport.projects} · " +
                                    "Памяти: ${fileImport.memories} · Артефактов: ${fileImport.artifacts}" +
                                    if (fileImport.files > 0) " · Файлов: ${fileImport.files}" else "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                ),
                                color = colors.textSecondary,
                            )
                            if (fileImport.skipped > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Служебных файлов пропущено: ${fileImport.skipped}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    ),
                                    color = colors.textTertiary,
                                )
                            }
                            fileImport.warnings.take(3).forEach { warning ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    ),
                                    color = colors.textTertiary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Закрыть",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.clay,
                            modifier = Modifier.clickable { vm.dismissClaudeFileImport() },
                        )
                    }
                }
            }
        }

        NewSectionLabel("Защита аккаунта")
        SettingsCard {
            if (state.auth.hasPassword) {
                SettingsRow(
                    icon = ClaudenIcons.shield,
                    title = "Пароль установлен",
                    subtitle = "Можно войти по email, даже если Claude станет недоступен",
                    onClick = { passwordOpen = true },
                )
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Аккаунт привязан к Claude. Установите пароль, чтобы не потерять доступ " +
                            "и покупку, если аккаунт Claude перестанет работать.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionButton(
                        text = "Установить пароль",
                        onClick = { passwordOpen = true },
                    )
                }
            }
        }

        if (linked) {
            NewSectionLabel("Поведение Claude")
            SettingsCard {
                ToggleBlock(
                    title = "Расширенное мышление",
                    description = "Claude будет дольше думать над сложными задачами",
                    checked = state.settings.claudeThinking,
                    onCheckedChange = { enabled ->
                        vm.updateSettings { it.copy(claudeThinking = enabled) }
                    },
                    icon = ClaudenIcons.spark,
                )
            }

            NewSectionLabel("Диагностика")
            SettingsCard {
                SettingsRow(
                    icon = ClaudenIcons.doc,
                    title = "Диагностика Claude",
                    subtitle = "Скопировать формат событий для разработчика",
                    onClick = vm::copyClaudeDiagnostics,
                )
            }
        }
    }

    if (unlinkOpen) {
        SettingsConfirmDialog(
            title = "Отвязать аккаунт Claude?",
            text = "Сессия Claude будет стёрта с устройства, приложение перейдёт на Clauden. " +
                "Сам аккаунт Anthropic останется прежним.",
            confirmText = "Отвязать",
            onConfirm = {
                unlinkOpen = false
                vm.unlinkClaude()
            },
            onDismiss = { unlinkOpen = false },
        )
    }

    if (switchOpen) {
        SettingsConfirmDialog(
            title = "Переключиться на Claude?",
            text = "В этом режиме ваша подписка Clauden не используется. Запросы пойдут " +
                "в ваш аккаунт Claude (${claudePlanTitle(state.claudePlan)}), лимиты считает Anthropic.",
            confirmText = "Переключиться",
            danger = false,
            onConfirm = {
                switchOpen = false
                vm.switchEngine(AuthMode.Claude)
            },
            onDismiss = { switchOpen = false },
        )
    }

    if (passwordOpen) {
        PasswordSetupDialog(
            hasPassword = state.auth.hasPassword,
            busy = state.authBusy,
            error = state.authError,
            onDismiss = {
                passwordOpen = false
                vm.dismissAuthError()
            },
            onSave = { password -> vm.setClaudePassword(password) },
        )
    }
}

@Composable
fun PasswordSetupDialog(
    hasPassword: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val clean = password.trim()
        when {
            clean.length < 8 -> localError = "Пароль должен быть не короче 8 символов"
            clean != repeat.trim() -> localError = "Пароли не совпадают"
            else -> {
                localError = null
                onSave(clean)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .blockClicks(),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = if (hasPassword) "Новый пароль" else "Защитите аккаунт",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Пароль нужен для входа по email, если аккаунт Claude станет недоступен.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                PasswordField(
                    value = password,
                    placeholder = "Пароль",
                    onChange = {
                        password = it
                        localError = null
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                PasswordField(
                    value = repeat,
                    placeholder = "Повторите пароль",
                    onChange = {
                        repeat = it
                        localError = null
                    },
                )
                val message = localError ?: error
                if (message != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.dangerSoft,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Отмена",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = colors.textPrimary,
                        shape = CircleShape,
                        modifier = Modifier.clickable(enabled = !busy, onClick = ::submit),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = colors.bg,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "Сохранить",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = colors.bg,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val colors = LocalClaudenColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textTertiary,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.borderStrong,
            unfocusedBorderColor = colors.border,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = colors.clay,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ClaudeSwitchedDialog(onConfirm: () -> Unit) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onConfirm),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .blockClicks(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Подписка активна",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Теперь вы работаете через Clauden — быстрее и без лимитов Anthropic. " +
                        "Ваш аккаунт Claude больше не используется и в безопасности.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    color = colors.textPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(onClick = onConfirm),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Перейти в Clauden",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.bg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClaudeProblemDialog(
    problem: ClaudeProblem,
    onDismiss: () -> Unit,
    onRelogin: () -> Unit,
    onClauden: () -> Unit,
    onRetry: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val title = when (problem.kind) {
        ClaudeErrorKind.Unauthorized -> "Сессия Claude истекла"
        ClaudeErrorKind.Blocked -> "Claude недоступен из вашей сети"
        ClaudeErrorKind.RateLimit -> "Лимит аккаунта Claude исчерпан"
        ClaudeErrorKind.Throttled -> "Claude просит подождать"
        ClaudeErrorKind.ServerError -> "Claude временно недоступен"
        ClaudeErrorKind.Network -> "Нет связи с Claude"
        ClaudeErrorKind.Other -> "Claude не ответил"
    }
    val primaryLabel = when (problem.kind) {
        ClaudeErrorKind.Unauthorized -> "Войти заново"
        ClaudeErrorKind.Blocked, ClaudeErrorKind.RateLimit -> "Перейти в Clauden"
        else -> "Повторить"
    }
    val primaryAction: () -> Unit = when (problem.kind) {
        ClaudeErrorKind.Unauthorized -> onRelogin
        ClaudeErrorKind.Blocked, ClaudeErrorKind.RateLimit -> onClauden
        else -> onRetry
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .blockClicks(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (problem.kind == ClaudeErrorKind.Throttled) {
                        val wait = ((problem.retryAt - System.currentTimeMillis()) / 1000L)
                            .coerceAtLeast(0L)
                        val whenText = when {
                            problem.retryAt <= 0L -> "Подождите минуту и нажмите «Повторить»."
                            wait < 60L -> "Повторить можно через ${wait.coerceAtLeast(1L)} сек."
                            else -> "Повторить можно через ${(wait + 59L) / 60L} мин."
                        }
                        "Claude временно ограничил частоту запросов — это не исчерпанный тариф. $whenText"
                    } else {
                        problem.message.ifBlank {
                            "Можно продолжить в Clauden — аккаунт Anthropic не понадобится."
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = colors.textSecondary,
                )
                if (problem.kind == ClaudeErrorKind.RateLimit) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Точное время сброса показывает claude.ai. В Clauden лимиты другие.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textTertiary,
                    )
                }
                if (problem.kind == ClaudeErrorKind.Throttled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Это временное ограничение частоты запросов, а не исчерпанный тариф. " +
                            "Если не помогает — смените модель или подождите пару минут.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    color = colors.textPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(onClick = primaryAction),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = primaryLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.bg,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Скопировать диагностику",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = colors.clay,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDiagnostics)
                        .padding(vertical = 8.dp),
                )
                Text(
                    text = "Позже",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
