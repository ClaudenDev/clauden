package space.teleopen.clauden.ui

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.BuildConfig
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.FontStyle
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeMode
import space.teleopen.clauden.data.UiStyle
import space.teleopen.clauden.proxy.ProxyController
import space.teleopen.clauden.proxy.ProxyUi

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    state: ChatUiState,
    vm: ChatViewModel,
    onMenu: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val settings = state.settings
    val proxyState by ProxyController.state.collectAsState()
    val themeState by ThemeController.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var themeOpen by remember { mutableStateOf(false) }
    var styleOpen by remember { mutableStateOf(false) }
    var fontOpen by remember { mutableStateOf(false) }
    var logoutOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val paidPlan = !settings.plan.isBlank() && !settings.plan.equals("Free", true)
    val preferences = listOf(
        SettingsEntry("Основные", settings.toolAccessLabel, ClaudenIcons.sliders) {
            vm.openSettingsSection(Screen.General)
        },
        SettingsEntry("Аккаунт", state.settings.email, ClaudenIcons.person) {
            vm.openSettingsSection(Screen.Account)
        },
        SettingsEntry(
            "Режим Claude",
            when {
                state.authMode == AuthMode.Claude ->
                    "${claudePlanTitle(state.claudePlan)} · активен"
                state.claudeLinked ->
                    "${claudePlanTitle(state.claudePlan)} · сейчас Clauden"
                else -> "Не подключён"
            },
            ClaudenIcons.spark,
        ) {
            vm.openSettingsSection(Screen.ClaudeMode)
        },
        SettingsEntry(
            "Свой API",
            if (state.customApis.isEmpty()) {
                "OpenRouter, Qwen, Groq…"
            } else {
                "${state.customApis.size} API · " +
                    "${state.customApis.sumOf { it.modelCount }} моделей"
            },
            ClaudenIcons.link,
        ) {
            vm.openSettingsSection(Screen.CustomApi)
        },
        SettingsEntry(
            "Прокси",
            proxySettingsSubtitle(proxyState),
            ClaudenIcons.shield,
        ) {
            ProxyUi.open = true
        },
        SettingsEntry("Конфиденциальность", "Данные и модели", ClaudenIcons.shield) {
            vm.openSettingsSection(Screen.Privacy)
        },
        SettingsEntry("Оплата", "Clauden · ${settings.plan.ifBlank { "Free" }}", ClaudenIcons.dollar) {
            vm.openSettingsSection(Screen.Billing)
        },
    ) + (if (paidPlan) listOf(
        SettingsEntry("Использование", "Лимиты тарифа", ClaudenIcons.chart) {
            vm.openSettingsSection(Screen.Usage)
        },
    ) else listOf<SettingsEntry>()) + (if (state.auth.canIssueKeys || state.auth.codeAccess) listOf(
        SettingsEntry(
            "Получить API",
            if (state.apiKeys.isEmpty()) "Ключ для Cursor, Claude Code…" else "${state.apiKeys.size} ключ(а)",
            ClaudenIcons.key,
        ) { vm.openApiKeys() },
    ) else listOf<SettingsEntry>()) + listOf(
        SettingsEntry(
            "Возможности",
            "${settings.capabilitiesEnabled} включено",
            ClaudenIcons.updown,
        ) { vm.openSettingsSection(Screen.Capabilities) },
        SettingsEntry(
            "Память",
            if (settings.memory) "Включена · ${state.memory.size}" else "Выключена",
            ClaudenIcons.doc,
        ) { vm.openSettingsSection(Screen.Memory) },
        SettingsEntry("Рефлексия", "Статистика и наблюдения", ClaudenIcons.chart) {
            vm.openSettingsSection(Screen.Reflect)
        },
        SettingsEntry("Время и фокус", if (settings.breakReminders) settings.breakIntervalLabel else "Перерывы выключены", ClaudenIcons.clock) {
            vm.openSettingsSection(Screen.TimeFocus)
        },
    )

    val customize = listOf(
        SettingsEntry("Навыки", "${state.customize.enabledSkills.size} активно", ClaudenIcons.spark) {
            vm.openCustomize("skills", fromSettings = true)
        },
        SettingsEntry("Коннекторы", "${state.customize.installedConnectors.size} добавлено", ClaudenIcons.connectors) {
            vm.openCustomize("connectors", fromSettings = true)
        },
        SettingsEntry("Плагины", "${state.customize.enabledPlugins.size} активно", ClaudenIcons.grid) {
            vm.openCustomize("plugins", fromSettings = true)
        },
    )

    val appearance = listOf(
        SettingsEntry(
            "Цветовая тема",
            when (settings.themeMode) {
                ThemeMode.System -> "Системная"
                ThemeMode.Light -> "Светлая"
                ThemeMode.Dark -> "Темная"
            },
            ClaudenIcons.moon,
        ) { themeOpen = true },
        SettingsEntry(
            "Стиль интерфейса",
            when (settings.uiStyle) {
                UiStyle.Claude -> "Claude"
                UiStyle.Material3 -> "Material 3"
            },
            ClaudenIcons.grid,
        ) { styleOpen = true },
        SettingsEntry(
            "Стиль шрифта",
            when (settings.fontStyle) {
                FontStyle.Default -> "По умолчанию"
                FontStyle.System -> "Системный"
            },
            ClaudenIcons.font,
        ) { fontOpen = true },
        SettingsEntry(
            "Создать тему",
            when {
                themeState.exists && themeState.active ->
                    themeState.theme.name.ifBlank { "Моя тема" }
                themeState.exists -> "Тема выключена"
                else -> "Стиль, обои, цвета, анимации, маскот"
            },
            ClaudenIcons.moonPlus,
        ) {
            ThemeController.startEditing(createIfMissing = true)
            vm.openSettingsSection(Screen.ThemeStudio)
        },
    )

    val app = listOf(
        SettingsEntry(
            "Тактильный отклик",
            if (settings.haptics) "Включен" else "Выключен",
            ClaudenIcons.phone,
        ) { vm.updateSettings { it.copy(haptics = !it.haptics) } },
        SettingsEntry("Уведомления", "Ответы и новости", ClaudenIcons.bell) {
            vm.openSettingsSection(Screen.Notifications)
        },
        SettingsEntry("Разрешения", "Календарь, местоположение", ClaudenIcons.android) {
            vm.openSettingsSection(Screen.Permissions)
        },
        SettingsEntry("Общий доступ", "Ссылки и отправка", ClaudenIcons.share) {
            vm.openSettingsSection(Screen.Sharing)
        },
        SettingsEntry("Подключение", "Провайдер и API", ClaudenIcons.link) {
            vm.openSettingsSection(Screen.Connection)
        },
        SettingsEntry(
            "Обновления",
            state.update.latest?.let { "Доступна версия ${it.versionName}" }
                ?: "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            ClaudenIcons.download,
        ) { vm.checkForUpdate(force = true, userInitiated = true) },
    )

    val allEntries = preferences + customize + appearance + app
    val q = query.trim()
    fun shown(entries: List<SettingsEntry>): List<SettingsEntry> =
        if (q.isBlank()) entries else entries.filter {
            it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
        }

    Box(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = "Настройки",
                onMenu = onMenu,
                trailing = {
                    Box {
                        IconButton(onClick = { infoOpen = true }, modifier = Modifier.size(42.dp)) {
                            Icon(
                                ClaudenIcons.info,
                                contentDescription = "Информация",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = infoOpen,
                            onDismissRequest = { infoOpen = false },
                            modifier = Modifier.width(292.dp),
                            containerColor = colors.bg300,
                            shape = RoundedCornerShape(20.dp),
                            offset = DpOffset(0.dp, 10.dp),
                        ) {
                            InfoMenuItem("Публичная оферта", ClaudenIcons.external) {
                                infoOpen = false
                                uriHandler.openUri("https://clauden.best/offer")
                            }
                            InfoMenuItem("Пользовательское соглашение", ClaudenIcons.external) {
                                infoOpen = false
                                uriHandler.openUri("https://clauden.best/terms")
                            }
                            InfoMenuItem("Политика конфиденциальности", ClaudenIcons.external) {
                                infoOpen = false
                                uriHandler.openUri("https://clauden.best/privacy")
                            }
                            InfoMenuItem("Тарифы и цены", ClaudenIcons.external) {
                                infoOpen = false
                                uriHandler.openUri("https://clauden.best/pricing")
                            }
                            InfoMenuItem("Оплата и возврат", ClaudenIcons.external) {
                                infoOpen = false
                                uriHandler.openUri("https://clauden.best/pricing#refund")
                            }
                            InfoMenuItem("Поддержка", ClaudenIcons.external) {
                                infoOpen = false
                                uriHandler.openUri("mailto:support@clauden.best")
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
                                    .height(1.dp)
                                    .background(colors.border),
                            )
                            Text(
                                text = "Самозанятый Зайцев Станислав Алексеевич · ИНН 362201956766 · г. Воронеж",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = colors.textTertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                                textAlign = TextAlign.End,
                            )
                            Text(
                                text = "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textTertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                SettingsSearchField(query = query, onValueChange = { query = it })

                if (q.isBlank()) {
                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(57.dp)
                                .clickable { vm.openSettingsSection(Screen.Account) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = settings.email.ifBlank { state.auth.email },
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            PlanBadge(activePlanLabel(state))
                        }
                    }
                    if (!paidPlan) {
                        Spacer(modifier = Modifier.height(12.dp))
                        UpgradeCard(
                            onUpgrade = { vm.openSettingsSection(Screen.Billing) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                val pref = shown(preferences)
                if (pref.isNotEmpty()) {
                    SectionLabel("Настройки")
                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        pref.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(
                                icon = entry.icon,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                onClick = entry.onClick,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val cus = shown(customize)
                if (cus.isNotEmpty()) {
                    SectionLabel("Customize")
                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        cus.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(
                                icon = entry.icon,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                onClick = entry.onClick,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val look = shown(appearance)
                if (look.isNotEmpty()) {
                    SectionLabel("Оформление")
                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        look.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(
                                icon = entry.icon,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                onClick = entry.onClick,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val rest = shown(app)
                if (rest.isNotEmpty()) {
                    SectionLabel("Приложение")
                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        rest.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(
                                icon = entry.icon,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                onClick = entry.onClick,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (q.isNotBlank() && shown(allEntries).isEmpty()) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
                    )
                }

                if (q.isBlank()) {
                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clickable { logoutOpen = true }
                                .padding(start = 14.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = ClaudenIcons.logout,
                                contentDescription = null,
                                tint = colors.dangerSoft,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Выйти",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                color = colors.dangerSoft,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                        SettingsRow(
                            icon = ClaudenIcons.info,
                            title = "О нас",
                            subtitle = "Телеграм и поддержка",
                            onClick = { aboutOpen = true },
                        )
                    }
                }
            }
        }

        if (themeOpen) {
            SettingsPopup(title = "Цветовая тема", onDismiss = { themeOpen = false }) {
                PopupOption(
                    icon = ClaudenIcons.phone,
                    label = "Системная",
                    selected = settings.themeMode == ThemeMode.System,
                    onClick = {
                        vm.updateSettings { it.copy(themeMode = ThemeMode.System) }
                        themeOpen = false
                    },
                )
                PopupOption(
                    icon = ClaudenIcons.sun,
                    label = "Светлая",
                    selected = settings.themeMode == ThemeMode.Light,
                    onClick = {
                        vm.updateSettings { it.copy(themeMode = ThemeMode.Light) }
                        themeOpen = false
                    },
                )
                PopupOption(
                    icon = ClaudenIcons.moon,
                    label = "Темная",
                    selected = settings.themeMode == ThemeMode.Dark,
                    onClick = {
                        vm.updateSettings { it.copy(themeMode = ThemeMode.Dark) }
                        themeOpen = false
                    },
                )
            }
        }

        if (styleOpen) {
            SettingsPopup(title = "Стиль интерфейса", onDismiss = { styleOpen = false }) {
                PopupOption(
                    icon = ClaudenIcons.spark,
                    label = "Claude",
                    subtitle = "Фирменный вид Clauden",
                    selected = settings.uiStyle == UiStyle.Claude,
                    onClick = {
                        vm.updateSettings { it.copy(uiStyle = UiStyle.Claude) }
                        styleOpen = false
                    },
                )
                PopupOption(
                    icon = ClaudenIcons.grid,
                    label = "Material 3",
                    subtitle = "Тональный стиль Android",
                    selected = settings.uiStyle == UiStyle.Material3,
                    onClick = {
                        vm.updateSettings { it.copy(uiStyle = UiStyle.Material3) }
                        styleOpen = false
                    },
                )
            }
        }

        if (fontOpen) {
            SettingsPopup(title = "Стиль шрифта", onDismiss = { fontOpen = false }) {
                PopupOption(
                    icon = null,
                    letterIcon = "A",
                    label = "По умолчанию",
                    selected = settings.fontStyle == FontStyle.Default,
                    onClick = {
                        vm.updateSettings { it.copy(fontStyle = FontStyle.Default) }
                        fontOpen = false
                    },
                )
                PopupOption(
                    icon = ClaudenIcons.phone,
                    label = "Системный",
                    selected = settings.fontStyle == FontStyle.System,
                    onClick = {
                        vm.updateSettings { it.copy(fontStyle = FontStyle.System) }
                        fontOpen = false
                    },
                )
            }
        }

        if (logoutOpen) {
            SettingsConfirmDialog(
                title = "Выйти из аккаунта?",
                text = "Чаты останутся на устройстве и вернутся, когда вы снова войдёте в этот аккаунт.",
                confirmText = "Выйти",
                onConfirm = {
                    vm.logout()
                    logoutOpen = false
                },
                onDismiss = { logoutOpen = false },
            )
        }

        if (aboutOpen) {
            SettingsPopup(title = "О нас", onDismiss = { aboutOpen = false }) {
                AboutLinkRow(
                    title = "Телеграм канал",
                    subtitle = "@ClaudenApp · новости и обновления",
                    url = "https://t.me/ClaudenApp",
                ) { aboutOpen = false }
                AboutLinkRow(
                    title = "Телеграм чат",
                    subtitle = "Сообщество Clauden",
                    url = "https://t.me/+hNcFDcTTr6BkODFi",
                ) { aboutOpen = false }
                AboutLinkRow(
                    title = "Поддержка",
                    subtitle = "@ClaudenSup",
                    url = "https://t.me/ClaudenSup",
                ) { aboutOpen = false }
            }
        }
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    subtitle: String,
    url: String,
    onOpen: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable {
                onOpen()
                runCatching { uriHandler.openUri(url) }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ClaudenIcons.external,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onValueChange: (String) -> Unit) {    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Surface(
        color = colors.bg300,
        shape = if (material3) CircleShape else RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Поиск настроек",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = colors.textTertiary,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.clay),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Text(
        text = text,
        style = if (material3) {
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        } else {
            MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        },
        color = if (material3) colors.clay else colors.textTertiary,
        modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun InfoMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
            color = colors.textPrimary,
        )
    }
}

@Composable
fun SettingsPopup(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.popupBg,
            shape = LocalClaudenShapes.current.dialog,
            modifier = Modifier
                .width(307.dp)
                .blockClicks(),
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                content()
            }
        }
    }
}

@Composable
private fun PopupOption(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    letterIcon: String? = null,
    subtitle: String? = null,
) {
    val colors = LocalClaudenColors.current
    val material3 = LocalUiStyle.current == UiStyle.Material3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 50.dp else 60.dp)
            .then(if (material3) Modifier.padding(horizontal = 4.dp) else Modifier)
            .clip(if (material3) RoundedCornerShape(12.dp) else RectangleShape)
            .background(
                if (material3 && selected) colors.control else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (material3) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) colors.blueText else colors.textPrimary,
                    modifier = Modifier.size(23.dp),
                )

                letterIcon != null -> Text(
                    text = letterIcon,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (selected) colors.blueText else colors.textPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.width(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (material3) 17.sp else 20.sp,
                ),
                color = if (selected) colors.blueText else colors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Выбрано",
                tint = colors.blueText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun SettingsConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val colors = LocalClaudenColors.current
    if (LocalUiStyle.current == UiStyle.Material3) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (danger) colors.danger else colors.clay,
                    ),
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.textSecondary,
                    ),
                ) {
                    Text("Отмена")
                }
            },
            shape = LocalClaudenShapes.current.dialog,
            containerColor = colors.bg300,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
        return
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
            shape = LocalClaudenShapes.current.dialog,
            modifier = Modifier
                .width(320.dp)
                .blockClicks(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(20.dp))
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
                        color = if (danger) colors.dangerSoft.copy(alpha = 0.16f) else colors.control,
                        shape = CircleShape,
                        modifier = Modifier.clickable(onClick = onConfirm),
                    ) {
                        Text(
                            text = confirmText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (danger) colors.dangerSoft else colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
