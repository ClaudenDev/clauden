package space.teleopen.clauden.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.DeviceSession
import space.teleopen.clauden.data.MemoryItem
import space.teleopen.clauden.data.ToolAccess
import space.teleopen.clauden.data.UiStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun NewSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val density = LocalUiDensity.current.factor
    Column(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        SettingsHeader(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy((10f * density).dp),
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            content()
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun NewSectionLabel(text: String) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Text(
        text = text,
        style = if (material3) {
            MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        } else {
            MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        },
        color = if (material3) colors.clay else colors.textTertiary,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
internal fun ToggleBlock(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val colors = LocalClaudenColors.current
    val density = LocalUiDensity.current.factor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = (12f * density).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = colors.control, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        SettingsToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(190.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(ClaudenIcons.chevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun GeneralScreen(state: ChatUiState, vm: ChatViewModel) {
    val settings = state.settings
    var toolOpen by remember { mutableStateOf(false) }

    NewSettingsScaffold(title = "Основные", onBack = vm::backToSettings) {
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toolOpen = !toolOpen }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Доступ к инструментам",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = LocalClaudenColors.current.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Как загружать инструменты в новых чатах",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = LocalClaudenColors.current.textTertiary,
                    )
                }
                Text(
                    text = settings.toolAccessLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = LocalClaudenColors.current.textSecondary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(ClaudenIcons.chevronRight, contentDescription = null, tint = LocalClaudenColors.current.textTertiary, modifier = Modifier.size(15.dp))
            }
            if (toolOpen) {
                SettingsDivider()
                ToolAccessChoice(
                    label = "Авто",
                    description = "Clauden выбирает за вас",
                    selected = settings.toolAccess == ToolAccess.Auto,
                ) { vm.updateSettings { it.copy(toolAccess = ToolAccess.Auto) }; toolOpen = false }
                SettingsDivider()
                ToolAccessChoice(
                    label = "По запросу",
                    description = "Загружается по запросу. Больше сообщений, ниже точность.",
                    selected = settings.toolAccess == ToolAccess.OnDemand,
                ) { vm.updateSettings { it.copy(toolAccess = ToolAccess.OnDemand) }; toolOpen = false }
                SettingsDivider()
                ToolAccessChoice(
                    label = "Всегда доступно",
                    description = "Готово с самого начала. Меньше сообщений, выше точность.",
                    selected = settings.toolAccess == ToolAccess.Always,
                ) { vm.updateSettings { it.copy(toolAccess = ToolAccess.Always) }; toolOpen = false }
            }
            SettingsDivider()
            ToggleBlock(
                title = "Поиск коннекторов",
                description = "Clauden ищет коннекторы в каталоге и предлагает подходящие к разговору.",
                checked = settings.connectorDiscovery,
                onCheckedChange = { on -> vm.updateSettings { it.copy(connectorDiscovery = on) } },
            )
            SettingsDivider()
            ToggleBlock(
                title = "Переключать модель при пометке",
                description = "Если сообщение помечено системой безопасности, чат продолжит работу на другой модели. Выключено — чат встанет на паузу.",
                checked = settings.modelFallback,
                onCheckedChange = { on -> vm.updateSettings { it.copy(modelFallback = on) } },
            )
        }

        NewSectionLabel("Визуализация")
        SettingsCard {
            ToggleBlock(
                title = "Артефакты",
                description = "Код, документы и дизайн в отдельном окне рядом с разговором.",
                checked = settings.artifacts,
                onCheckedChange = { on -> vm.updateSettings { it.copy(artifacts = on) } },
            )
            SettingsDivider()
            ToggleBlock(
                title = "Артефакты с ИИ",
                description = "Приложения и интерактивные документы, которые используют Clauden внутри.",
                checked = settings.aiArtifacts,
                onCheckedChange = { on -> vm.updateSettings { it.copy(aiArtifacts = on) } },
            )
            SettingsDivider()
            ToggleBlock(
                title = "Визуализации в чате",
                description = "Графики, схемы и диаграммы прямо в ответе.",
                checked = settings.visualizations,
                onCheckedChange = { on -> vm.updateSettings { it.copy(visualizations = on) } },
            )
        }

        NewSectionLabel("Выполнение кода и файлы")
        SettingsCard {
            ToggleBlock(
                title = "Выполнение кода и создание файлов",
                description = "Clauden выполняет код, создаёт и правит документы, таблицы, презентации, PDF и отчёты.",
                checked = settings.codeExecution,
                onCheckedChange = { on -> vm.updateSettings { it.copy(codeExecution = on) } },
            )
            SettingsDivider()
            ToggleBlock(
                title = "Сетевой доступ",
                description = "Разрешить ставить пакеты и библиотеки для анализа, визуализаций и обработки файлов. Это несёт риск безопасности.",
                checked = settings.networkEgress,
                onCheckedChange = { on -> vm.updateSettings { it.copy(networkEgress = on) } },
            )
        }

        NewSectionLabel("Навыки")
        SettingsCard {
            LinkRow(title = "Навыки переехали в Customize", value = "Открыть") {
                vm.openCustomize("skills", fromSettings = true)
            }
        }
    }
}

@Composable
private fun ToolAccessChoice(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = if (selected) colors.blueLink else colors.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textTertiary,
            )
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.blueLink, modifier = Modifier.size(20.dp))
        }
    }
}

private val workOptions = listOf(
    "Разработка",
    "Дизайн",
    "Маркетинг",
    "Продукт",
    "Финансы",
    "Образование",
    "Исследования",
    "Своё дело",
)

@Composable
fun NewAccountScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings
    var fullName by remember(settings.fullName) { mutableStateOf(settings.fullName) }
    var nickname by remember(settings.nickname) { mutableStateOf(settings.nickname) }
    var work by remember(settings.work) { mutableStateOf(settings.work) }
    var note by remember(settings.preferenceNote) { mutableStateOf(settings.preferenceNote) }
    var workOpen by remember { mutableStateOf(false) }
    var logoutAllOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val dirty = fullName.trim() != settings.fullName ||
        nickname.trim() != settings.nickname ||
        work.trim() != settings.work ||
        note != settings.preferenceNote

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.saveAvatar(uri)
    }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    NewSettingsScaffold(title = "Аккаунт", onBack = vm::backToSettings) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(settings.avatarPath, settings.avatarLetter, size = 86)
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .height(32.dp)
                    .clickable { photoPicker.launch("image/*") },
            ) {
                Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Изменить фото",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = colors.textPrimary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PNG, JPEG или WebP, до 10 МБ",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }

        if (state.authMode == AuthMode.Claude && !state.auth.hasPassword) {
            Surface(
                color = colors.clay.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.openSettingsSection(Screen.ClaudeMode) },
            ) {
                Text(
                    text = "Аккаунт привязан к Claude. Установите пароль, чтобы не потерять доступ.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colors.clay,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Полное имя",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsField(value = fullName, onValueChange = { fullName = it })
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Как к вам обращаться?",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsField(value = nickname, onValueChange = { nickname = it })
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Чем вы занимаетесь?",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box {
                Surface(
                    color = colors.bg300,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.fieldBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { workOpen = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = work.ifBlank { "Выберите" },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = if (work.isBlank()) colors.textTertiary else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(ClaudenIcons.chevronRight, contentDescription = null, tint = colors.textTertiary)
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = workOpen,
                    onDismissRequest = { workOpen = false },
                    containerColor = colors.bg300,
                ) {
                    workOptions.forEach { option ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                work = option
                                workOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Инструкции для Clauden",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Clauden будет учитывать это во всех чатах и проектах аккаунта.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Например: задавай уточняющие вопросы до подробного ответа",
                height = 130.dp,
                singleLine = false,
                textSize = 15,
            )
            Spacer(modifier = Modifier.height(14.dp))
            SettingsActionButton(
                text = "Сохранить профиль",
                enabled = dirty,
                onClick = { vm.saveProfile(fullName, nickname, note, work) },
            )
        }

        NewSectionLabel("Активные сеансы")
        SettingsCard {
            if (state.sessions.isEmpty()) {
                Text(
                    text = if (state.sessionsBusy) "Загружаю…" else "Список сеансов пуст",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                state.sessions.take(4).forEachIndexed { index, session ->
                    if (index > 0) SettingsDivider()
                    SessionRow(session = session, compact = true) { vm.revokeSession(session.id) }
                }
            }
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.refreshSessions() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Обновить список",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.blueLink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Все сеансы",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.clickable { vm.openSettingsSection(Screen.Sessions) },
                )
            }
        }

        NewSectionLabel("Аккаунт")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { logoutAllOpen = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Выйти на всех устройствах",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Выйти",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textTertiary,
                )
            }
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { deleteOpen = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Удалить аккаунт",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.dangerSoft,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Удалить",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.dangerSoft,
                )
            }
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ID организации",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )
                    Text(
                        text = state.organizationId.ifBlank { state.auth.userId },
                        style = ClaudenType.mono.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    ClaudenIcons.copy,
                    contentDescription = "Скопировать",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(state.organizationId.ifBlank { state.auth.userId }))
                            vm.notify("ID организации скопирован")
                        },
                )
            }
        }
    }

    if (logoutAllOpen) {
        SettingsConfirmDialog(
            title = "Выйти на всех устройствах?",
            text = "Все сеансы, включая этот, будут завершены. Чаты останутся на устройстве.",
            confirmText = "Выйти везде",
            onConfirm = {
                logoutAllOpen = false
                vm.logoutAllDevices()
            },
            onDismiss = { logoutAllOpen = false },
        )
    }

    if (deleteOpen) {
        SettingsConfirmDialog(
            title = "Удалить аккаунт?",
            text = "Аккаунт и все сеансы будут удалены на сервере. Локальные чаты останутся, но привязаны к удалённому ID.",
            confirmText = "Удалить навсегда",
            onConfirm = {
                deleteOpen = false
                vm.deleteAccount()
            },
            onDismiss = { deleteOpen = false },
        )
    }
}

@Composable
internal fun ProfileAvatar(path: String, letter: String, size: Int) {
    val colors = LocalClaudenColors.current
    val bitmap = remember(path) {
        if (path.isBlank()) null else runCatching {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colors.clay),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Фото профиля",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (size * 0.36f).sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.onClay,
            )
        }
    }
}

@Composable
fun SessionsScreen(state: ChatUiState, vm: ChatViewModel) {
    LaunchedEffect(Unit) { vm.refreshSessions() }
    NewSettingsScaffold(title = "Сеансы", onBack = vm::backToSettings) {
        Text(
            text = "Это все устройства, где выполнен вход в аккаунт. Завершайте незнакомые сеансы.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = LocalClaudenColors.current.textTertiary,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        SettingsCard {
            if (state.sessions.isEmpty()) {
                Text(
                    text = if (state.sessionsBusy) "Загружаю…" else "Нет активных сеансов",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = LocalClaudenColors.current.textTertiary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                state.sessions.forEachIndexed { index, session ->
                    if (index > 0) SettingsDivider()
                    SessionRow(session = session, compact = false) { vm.revokeSession(session.id) }
                }
            }
        }
        SettingsActionButton(text = "Обновить", onClick = { vm.refreshSessions() })
    }
}

@Composable
private fun SessionRow(session: DeviceSession, compact: Boolean, onRevoke: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (session.device.contains("Android", true)) ClaudenIcons.android else ClaudenIcons.phone,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.device.ifBlank { "Android" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.current) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Surface(color = colors.blue.copy(alpha = 0.16f), shape = CircleShape) {
                        Text(
                            text = "текущий",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.blueLink,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = buildString {
                    if (session.platform.isNotBlank()) append(session.platform)
                    if (session.location.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(session.location)
                    }
                }.ifBlank { "Android" },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
            if (!compact) {
                Text(
                    text = "Вход: ${formatSessionTime(session.createdAt)} · активность: ${formatSessionTime(session.lastSeenAt)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }
        }
        if (!session.current || !compact) {
            Text(
                text = "Завершить",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.dangerSoft,
                modifier = Modifier
                    .clickable(onClick = onRevoke)
                    .padding(6.dp),
            )
        }
    }
}

private fun formatSessionTime(value: Long): String {
    if (value <= 0) return "—"
    return SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(value))
}

@Composable
fun MemoryScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    var clearOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.settings.memory, state.memory.size, state.memoryBusy, state.conversations.size) {
        if (state.settings.memory && state.memory.isEmpty() && !state.memoryBusy &&
            state.conversations.size >= 2
        ) {
            vm.generateMemory(quiet = true)
        }
    }

    NewSettingsScaffold(title = "Память", onBack = vm::backToSettings) {
        SettingsCard {
            ToggleBlock(
                title = "Создавать память из чатов",
                description = "Clauden запоминает устойчивые факты о вас и использует их в новых разговорах.",
                checked = state.settings.memory,
                onCheckedChange = { on -> vm.updateSettings { it.copy(memory = on) } },
            )
        }

        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Память аккаунта",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "${state.memory.size} фактов сохранено",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = colors.textTertiary,
                    )
                }
                Surface(
                    color = colors.control,
                    shape = CircleShape,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable(enabled = !state.memoryBusy && state.settings.memory) {
                            vm.generateMemory()
                        },
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.memoryBusy) "Собираю…" else "Сгенерировать",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = if (state.settings.memory) colors.textPrimary else colors.textTertiary,
                        )
                    }
                }
            }
            if (state.settings.memory) {
                SettingsDivider()
                Text(
                    text = "Clauden читает последние чаты и сохраняет только факты: имя, работу, проекты и предпочтения. Секреты не сохраняются. Память обновляется автоматически; кнопка «Сгенерировать» собирает факты сразу.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (state.memory.isNotEmpty()) {
            NewSectionLabel("Сохранённое")
            SettingsCard {
                state.memory.forEachIndexed { index, item ->
                    if (index > 0) SettingsDivider()
                    MemoryRow(item) { vm.deleteMemory(item.id) }
                }
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clearOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "Очистить всю память",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = colors.dangerSoft,
                    )
                }
            }
        } else {
            Text(
                text = "Память пока пуста. Включите её и нажмите «Сгенерировать» — Clauden разберёт последние чаты.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    if (clearOpen) {
        SettingsConfirmDialog(
            title = "Очистить память?",
            text = "Все сохранённые факты будут удалены. Это не затронет чаты.",
            confirmText = "Очистить",
            onConfirm = {
                clearOpen = false
                vm.clearMemory()
            },
            onDismiss = { clearOpen = false },
        )
    }
}

@Composable
private fun MemoryRow(item: MemoryItem, onDelete: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Удалить факт",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun ReflectScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val stats = remember(state.conversations) { reflectStats(state) }

    NewSettingsScaffold(title = "Рефлексия", onBack = vm::backToSettings) {
        Text(
            text = "Как вы работаете с Clauden — по вашим чатам на этом устройстве.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Чатов", stats.chats.toString(), Modifier.weight(1f))
            StatCard("Сообщений", stats.messages.toString(), Modifier.weight(1f))
            StatCard("Слов", stats.words.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Активных дней", stats.activeDays.toString(), Modifier.weight(1f))
            StatCard("Средний чат", "${stats.avgMessages} сообщ.", Modifier.weight(1f))
            StatCard("Артефактов", stats.artifacts.toString(), Modifier.weight(1f))
        }

        NewSectionLabel("Когда вы работаете")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ActivityChart(stats.byHour)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("00", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textTertiary)
                    Text("06", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textTertiary)
                    Text("12", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textTertiary)
                    Text("18", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textTertiary)
                    Text("23", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textTertiary)
                }
                if (state.conversations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Пик активности: ${stats.peakHour}:00. Любимый день: ${stats.topDay}.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )
                }
            }
        }

        if (stats.topics.isNotEmpty()) {
            NewSectionLabel("Темы, к которым вы возвращаетесь")
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    stats.topics.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { topic ->
                                Surface(color = colors.control, shape = CircleShape) {
                                    Text(
                                        text = topic,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                            if (row.size < 3) Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        NewSectionLabel("Наблюдения")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                stats.insights.forEach { insight ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("—", color = colors.textTertiary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalClaudenColors.current
    Surface(color = colors.bg300, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ActivityChart(values: IntArray) {
    val colors = LocalClaudenColors.current
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
        val gap = 3f
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
            val height = size.height * (value.toFloat() / max)
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = if (value == max) colors.clay else colors.control,
                topLeft = Offset(left, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height.coerceAtLeast(2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
        }
    }
}

private data class ReflectStats(
    val chats: Int,
    val messages: Int,
    val words: Int,
    val activeDays: Int,
    val avgMessages: Int,
    val artifacts: Int,
    val byHour: IntArray,
    val peakHour: Int,
    val topDay: String,
    val topics: List<String>,
    val insights: List<String>,
)

private fun reflectStats(state: ChatUiState): ReflectStats {
    val days = mutableSetOf<String>()
    val hours = IntArray(24)
    val weekdays = IntArray(7)
    val wordCounts = mutableMapOf<String, Int>()
    val stop = setOf(
        "это", "что", "как", "для", "или", "мне", "меня", "тебе", "тебя", "если",
        "так", "все", "ещё", "еще", "уже", "было", "есть", "быть", "только", "можно",
        "нужно", "надо", "при", "без", "над", "под", "про", "через", "чтобы", "когда",
        "потом", "сейчас", "здесь", "там", "этот", "эта", "эти", "тот", "она", "они",
        "the", "and", "for", "with", "you", "your", "this", "that",
    )
    var messages = 0
    var words = 0
    var artifacts = 0
    val formats = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val weekdayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    state.conversations.forEach { chat ->
        chat.messages.forEach { message ->
            messages += 1
            words += message.content.split(Regex("\\s+")).count { it.isNotBlank() }
            if (message.role == "assistant" && message.content.contains("```")) artifacts += 1
            val date = Date(if (message.ts > 0) message.ts else chat.updatedAt)
            days += formats.format(date)
            val calendar = Calendar.getInstance().apply { time = date }
            hours[calendar.get(Calendar.HOUR_OF_DAY)] += 1
            weekdays[calendar.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - 2 }] += 1
            if (message.role == "user") {
                message.content.lowercase()
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.length >= 4 && it !in stop }
                    .forEach { wordCounts[it] = (wordCounts[it] ?: 0) + 1 }
            }
        }
    }
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val topDayIndex = weekdays.indices.maxByOrNull { weekdays[it] } ?: 0
    val topics = wordCounts.entries.sortedByDescending { it.value }.take(9).map { it.key }
    val avg = if (state.conversations.isEmpty()) 0 else messages / state.conversations.size
    val insights = buildList {
        if (state.conversations.isEmpty()) {
            add("Пока нет чатов — статистика появится после первых разговоров.")
        } else {
            add("Больше всего сообщений приходится на ${peak}:00–${(peak + 1) % 24}:00.")
            add("Самый активный день — ${weekdayNames[topDayIndex]}. Средний чат: $avg сообщений.")
            if (artifacts > 0) add("В ответах найдено $artifacts блоков кода и артефактов.")
            if (words > 0) add("Всего слов в переписке: $words.")
        }
    }
    return ReflectStats(
        chats = state.conversations.size,
        messages = messages,
        words = words,
        activeDays = days.size,
        avgMessages = avg,
        artifacts = artifacts,
        byHour = hours,
        peakHour = peak,
        topDay = weekdayNames[topDayIndex],
        topics = topics,
        insights = insights,
    )
}

@Composable
fun TimeFocusScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings
    val context = LocalContext.current
    var intervalOpen by remember { mutableStateOf(false) }
    val intervals = listOf(15, 30, 45, 60, 90)

    NewSettingsScaffold(title = "Время и фокус", onBack = vm::backToSettings) {
        SettingsCard {
            ToggleBlock(
                title = "Напоминания о перерыве",
                description = "Мягкое уведомление отвлечься от экрана. Его можно отложить на 15 минут.",
                checked = settings.breakReminders,
                onCheckedChange = { on -> vm.updateSettings { it.copy(breakReminders = on) } },
            )
            if (settings.breakReminders) {
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { intervalOpen = true }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Напоминать каждые",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = settings.breakIntervalLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(ClaudenIcons.chevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(15.dp))
                }
                if (intervalOpen) {
                    SettingsDivider()
                    intervals.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.updateSettings { it.copy(breakInterval = minutes, breakReminders = true) }
                                    intervalOpen = false
                                }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$minutes минут",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = if (settings.breakInterval == minutes) colors.blueLink else colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (settings.breakInterval == minutes) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = colors.blueLink, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        NewSectionLabel("Тихие часы")
        SettingsCard {
            ToggleBlock(
                title = "Тихие часы",
                description = "В это время Clauden не присылает напоминания о перерывах.",
                checked = settings.quietHours,
                onCheckedChange = { on -> vm.updateSettings { it.copy(quietHours = on) } },
            )
            if (settings.quietHours) {
                SettingsDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Дни",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { index, label ->
                            val day = index + 1
                            val active = settings.quietDays.isEmpty() || day in settings.quietDays
                            Surface(
                                color = if (active) colors.textPrimary else colors.control,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        val current = if (settings.quietDays.isEmpty()) {
                                            (1..7).toSet()
                                        } else {
                                            settings.quietDays
                                        }
                                        val next = if (day in current) current - day else current + day
                                        vm.updateSettings {
                                            it.copy(quietDays = if (next.isEmpty()) emptySet() else next)
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (active) colors.bg else colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeBox(
                            label = "С",
                            value = settings.quietStart,
                            modifier = Modifier.weight(1f),
                        ) { minutes ->
                            vm.updateSettings { it.copy(quietStart = minutes) }
                        }
                        TimeBox(
                            label = "До",
                            value = settings.quietEnd,
                            modifier = Modifier.weight(1f),
                        ) { minutes ->
                            vm.updateSettings { it.copy(quietEnd = minutes) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Сейчас: ${settings.quietRangeLabel}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.textTertiary,
                    )
                }
            }
        }

        NewSectionLabel("Отложенные запросы")
        SettingsCard {
            LinkRow(
                title = "Запросы по расписанию",
                value = if (state.scheduled.isEmpty()) "Нет" else "${state.scheduled.size}",
            ) {
                vm.openSettingsSection(Screen.Scheduled)
            }
        }

        Text(
            text = "Напоминания приходят только если разрешены уведомления. Отключить их можно в системных настройках приложения.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }

}

@Composable
private fun TimeBox(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    Surface(
        color = colors.control,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(46.dp)
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onChange(hour * 60 + minute) },
                    value / 60,
                    value % 60,
                    true,
                ).show()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%02d:%02d".format(value / 60, value % 60),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
            )
        }
    }
}
