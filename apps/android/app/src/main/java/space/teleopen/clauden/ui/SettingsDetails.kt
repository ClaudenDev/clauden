package space.teleopen.clauden.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.ClaudenCatalog
import space.teleopen.clauden.data.LocationProvider
import space.teleopen.clauden.data.Provider
import space.teleopen.clauden.data.ToolAccess

@Composable
internal fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
    pagePadding: Int = 12,
    content: @Composable () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Column(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        SettingsHeader(title = title, onBack = onBack, trailing = trailing)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pagePadding.dp, end = pagePadding.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DetailLabel(text: String) {
    SettingsSectionLabel(text)
}

@Composable
fun ProfileScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings
    var fullName by remember(settings.fullName) { mutableStateOf(settings.fullName) }
    var nickname by remember(settings.nickname) { mutableStateOf(settings.nickname) }
    var note by remember(settings.preferenceNote) { mutableStateOf(settings.preferenceNote) }

    val nameDirty = fullName.trim() != settings.fullName || nickname.trim() != settings.nickname
    val noteDirty = note != settings.preferenceNote

    Column(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        SettingsHeader(title = "Профиль", onBack = vm::backToSettings)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(98.dp)
                        .clip(CircleShape)
                        .background(colors.clay),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = settings.avatarLetter,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = LocalClaudenFonts.current.sans,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = colors.onClay,
                    )
                }
            }
            Spacer(modifier = Modifier.height(13.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Transparent,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (colors.isDark) Color(0xFF343332) else colors.borderStrong),
                    modifier = Modifier
                        .height(34.dp)
                        .clickable { vm.notify("Выбор фото появится позже") },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Изменить фото",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(38.dp))
            Text(
                text = "Полное имя",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsField(value = fullName, onValueChange = { fullName = it })
            Spacer(modifier = Modifier.height(23.dp))
            Text(
                text = "Как к вам обращаться?",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsField(value = nickname, onValueChange = { nickname = it })
            Spacer(modifier = Modifier.height(19.dp))
            SettingsActionButton(
                text = "Обновить профиль",
                enabled = nameDirty,
                onClick = { vm.saveProfile(fullName, nickname, note) },
            )
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Какие личные предпочтения следует учитывать Claude в ответах?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "Ваши предпочтения будут применяться ко всем чатам.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Задавай уточняющие вопросы, прежде чем давать подробные ответы",
                height = 130.dp,
                singleLine = false,
                textSize = 17,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SettingsActionButton(
                text = "Сохранить настройки",
                enabled = nameDirty || noteDirty,
                onClick = { vm.saveProfile(fullName, nickname, note) },
            )
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun CapabilitiesScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings

    DetailScaffold(title = "Возможности", onBack = vm::backToSettings) {
        SettingsCard {
            CapabilityRow(
                icon = ClaudenIcons.globe,
                title = "Веб-поиск",
                description = "Claude будет автоматически искать в интернете, когда сочтет, что нужна актуальная информация",
                checked = settings.webSearch,
                onCheckedChange = { on -> vm.updateSettings { it.copy(webSearch = on) } },
            )
            SettingsDivider()
            CapabilityRow(
                icon = ClaudenIcons.artifacts,
                title = "Артефакты",
                description = "Требуется для выполнения кода",
                checked = settings.artifacts,
                enabled = !settings.codeExecution,
                onCheckedChange = { on -> vm.updateSettings { it.copy(artifacts = on) } },
            )
            SettingsDivider()
            CapabilityRow(
                icon = ClaudenIcons.chart,
                title = "Визуализации в чате",
                description = "Разрешить Claude создавать интерактивные визуализации, графики и диаграммы прямо в чате.",
                checked = settings.visualizations,
                badge = "БЕТА",
                onCheckedChange = { on -> vm.updateSettings { it.copy(visualizations = on) } },
            )
            SettingsDivider()
            CapabilityRow(
                icon = ClaudenIcons.doc,
                title = "Выполнение кода и создание файлов",
                description = "Разрешить Claude выполнять код, а также создавать и редактировать документы, таблицы, презентации, PDF-файлы и отчеты по данным.",
                checked = settings.codeExecution,
                onCheckedChange = { on -> vm.updateSettings { it.copy(codeExecution = on) } },
            )
            SettingsDivider()
            CapabilityRow(
                icon = ClaudenIcons.updown,
                title = "Переключение модели, если сообщение помечено системой безопасности",
                description = "Если меры безопасности помечают сообщение, чат автоматически переключается на другую модель, чтобы общение могло продолжиться. Если параметр выключен, чат будет приостановлен.",
                checked = settings.modelFallback,
                onCheckedChange = { on -> vm.updateSettings { it.copy(modelFallback = on) } },
            )
        }

        DetailLabel("Память")
        SettingsCard {
            CapabilityRow(
                icon = null,
                title = "Создание памяти на основе чатов",
                description = "Разрешить Claude создавать память на основе ваших чатов. Подробнее.",
                checked = settings.memory,
                onCheckedChange = { on -> vm.updateSettings { it.copy(memory = on) } },
            )
        }

        DetailLabel("Доступ к инструментам")
        SettingsCard {
            ToolAccessRow(
                title = "Авто",
                description = "Claude выбирает за вас",
                selected = settings.toolAccess == ToolAccess.Auto,
                onClick = { vm.updateSettings { it.copy(toolAccess = ToolAccess.Auto) } },
            )
            SettingsDivider()
            ToolAccessRow(
                title = "По запросу",
                description = "Загружается по запросу. Больше сообщений, ниже точность.",
                selected = settings.toolAccess == ToolAccess.OnDemand,
                onClick = { vm.updateSettings { it.copy(toolAccess = ToolAccess.OnDemand) } },
            )
            SettingsDivider()
            ToolAccessRow(
                title = "Всегда доступно",
                description = "Готово с самого начала. Меньше сообщений, выше точность.",
                selected = settings.toolAccess == ToolAccess.Always,
                onClick = { vm.updateSettings { it.copy(toolAccess = ToolAccess.Always) } },
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector?,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.1).sp,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = colors.control,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                ),
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        SettingsToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ToolAccessRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = if (selected) colors.blueLink else colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                color = if (selected) colors.blueLink else colors.textTertiary,
            )
        }
        if (selected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Выбрано",
                tint = colors.blueLink,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun ConnectorsScreen(state: ChatUiState, vm: ChatViewModel) {
    val settings = state.settings
    DetailScaffold(
        title = "Коннекторы",
        onBack = vm::backToSettings,
        trailing = {
            Box {
                androidx.compose.material3.IconButton(
                    onClick = { vm.notify("Каталог коннекторов появится позже") },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = ClaudenIcons.plus,
                        contentDescription = "Добавить коннектор",
                        tint = LocalClaudenColors.current.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) {
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = ClaudenIcons.connectors,
                    contentDescription = null,
                    tint = LocalClaudenColors.current.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Обнаружение коннекторов",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = LocalClaudenColors.current.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Claude поможет найти доступные коннекторы в вашем каталоге.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                        color = LocalClaudenColors.current.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SettingsToggle(
                    checked = settings.connectorDiscovery,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(connectorDiscovery = on) } },
                )
            }
        }

        SettingsCard {
            ConnectorRow(
                icon = { FigmaMark(size = 28) },
                title = "Figma",
                trailing = {
                    Surface(color = LocalClaudenColors.current.badgeBg, shape = CircleShape) {
                        Box(
                            modifier = Modifier.size(26.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "43",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                color = LocalClaudenColors.current.blueLink,
                            )
                        }
                    }
                },
                onClick = { vm.notify("Figma: 43 инструмента доступно") },
            )
            SettingsDivider()
            ConnectorRow(
                icon = { GmailMark(size = 28) },
                title = "Gmail",
                action = "Подключить",
                onClick = { vm.notify("Gmail пока не подключён") },
            )
            SettingsDivider()
            ConnectorRow(
                icon = { GoogleMark(size = 28) },
                title = "Google Calendar",
                action = "Подключить",
                onClick = { vm.notify("Google Calendar пока не подключён") },
            )
            SettingsDivider()
            ConnectorRow(
                icon = { GoogleMark(size = 28) },
                title = "Google Drive",
                action = "Подключить",
                onClick = { vm.notify("Google Drive пока не подключён") },
            )
        }
    }
}

@Composable
private fun ConnectorRow(
    icon: @Composable () -> Unit,
    title: String,
    action: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = ClaudenIcons.external,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            trailing()
        }
    }
}

@Composable
fun FigmaMark(size: Int) {
    Canvas(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Color.White),
    ) {
        val u = this.size.minDimension / 28f
        val colors = listOf(
            Color(0xFFF24E1E), Color(0xFFFF7262),
            Color(0xFFA259FF), Color(0xFF1ABCFE),
            Color(0xFF0ACF83),
        )
        val positions = listOf(
            Offset(10f, 7f), Offset(18f, 7f),
            Offset(10f, 14f), Offset(18f, 14f),
            Offset(10f, 21f),
        )
        positions.forEachIndexed { index, offset ->
            drawCircle(
                color = colors[index],
                radius = 3.4f * u,
                center = Offset(offset.x * u, offset.y * u),
            )
        }
    }
}

@Composable
fun GmailMark(size: Int) {
    Canvas(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Color.White),
    ) {
        val u = this.size.minDimension / 28f
        val path = Path().apply {
            moveTo(7f * u, 19.5f * u)
            lineTo(7f * u, 10.5f * u)
            lineTo(14f * u, 16f * u)
            lineTo(21f * u, 10.5f * u)
            lineTo(21f * u, 19.5f * u)
        }
        drawPath(path, Color(0xFFEA4335), style = Stroke(width = 2.4f * u, cap = StrokeCap.Round))
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(7f * u, 10.5f * u),
            end = Offset(7f * u, 19.5f * u),
            strokeWidth = 2.2f * u,
        )
        drawLine(
            color = Color(0xFF34A853),
            start = Offset(21f * u, 10.5f * u),
            end = Offset(21f * u, 19.5f * u),
            strokeWidth = 2.2f * u,
        )
    }
}

@Composable
fun GoogleMark(size: Int) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(colors.control),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "G",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = colors.textPrimary,
        )
    }
}

@Composable
fun PermissionsScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = fine || coarse
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        locationGranted = LocationProvider.hasPermission(context)
    }
    DetailScaffold(title = "Разрешения", onBack = vm::backToSettings) {
        SettingsCard {
            PermissionRow(
                icon = ClaudenIcons.location,
                title = "Местоположение",
                description = "Clauden спрашивает геопозицию только когда она нужна для ответа: места рядом, погода, маршруты. Можно выбрать точный или приблизительный режим.",
                action = {
                    Text(
                        text = if (locationGranted) "Разрешено" else "Разрешить",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = if (locationGranted) colors.blueLink else colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (locationGranted) Icons.Default.Check else ClaudenIcons.external,
                        contentDescription = null,
                        tint = if (locationGranted) colors.blueLink else colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = {
                    if (locationGranted) {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        runCatching { context.startActivity(intent) }
                    } else {
                        locationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                },
            )
            if (locationGranted) {
                SettingsDivider()
                PermissionRow(
                    icon = ClaudenIcons.location,
                    title = "Проверить геопозицию",
                    description = "Определит город и район, чтобы проверить работу до вопроса в чате.",
                    action = {
                        Text(
                            text = "Проверить",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = colors.blueLink,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = ClaudenIcons.chevronRight,
                            contentDescription = null,
                            tint = colors.blueLink,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = { vm.testLocation() },
                )
            }
            SettingsDivider()
            PermissionRow(
                icon = ClaudenIcons.calendar,
                title = "Календарь",
                description = "Claude может создавать события в приложении календаря и управлять ими.",
                action = {
                    Text(
                        text = "Чтение и запись",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = colors.blueLink,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = ClaudenIcons.chevronRight,
                        contentDescription = null,
                        tint = colors.blueLink,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = { vm.notify("Разрешение на календарь запросится при первом действии") },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    action: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { action() }
    }
}

@Composable
fun NotificationsScreen(state: ChatUiState, vm: ChatViewModel) {
    val settings = state.settings
    DetailScaffold(title = "Уведомления", onBack = vm::backToSettings) {
        SettingsCard {
            NotificationRow(
                icon = ClaudenIcons.personBell,
                title = "Ответы",
                description = "Получайте уведомления, когда Claude завершает ответ.",
                checked = settings.notifyReplies,
                onCheckedChange = { on -> vm.updateSettings { it.copy(notifyReplies = on) } },
            )
            SettingsDivider()
            NotificationRow(
                icon = ClaudenIcons.megaphone,
                title = "Новости продукта",
                description = "Получайте уведомления о новых функциях, советах и периодических акциях",
                checked = settings.notifyNews,
                onCheckedChange = { on -> vm.updateSettings { it.copy(notifyNews = on) } },
            )
        }
    }
}

@Composable
private fun NotificationRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SettingsToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PrivacyScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        SettingsHeader(title = "Конфиденциальность", onBack = vm::backToSettings)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Конфиденциальность данных",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Anthropic считает, что работа с данными должна быть прозрачной",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            val privacyText = remember(colors.blueLink) {
                buildAnnotatedString {
                    append("Безопасность ваших данных — один из наших приоритетов. Узнайте, как защищается ваша информация при использовании продуктов Anthropic, а подробности смотрите в ")
                    pushStringAnnotation("url", "https://privacy.anthropic.com")
                    withLinkStyle(colors0 = colors.blueLink) { append("Центре конфиденциальности") }
                    pop()
                    append(" и ")
                    pushStringAnnotation("url", "https://www.anthropic.com/legal/privacy")
                    withLinkStyle(colors0 = colors.blueLink) { append("Политике конфиденциальности") }
                    pop()
                    append(".")
                }
            }
            ClickableText(
                text = privacyText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    color = colors.textPrimary,
                ),
                onClick = { offset ->
                    privacyText.getStringAnnotations("url", offset, offset)
                        .firstOrNull()
                        ?.let { uriHandler.openUri(it.item) }
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )
            Spacer(modifier = Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "Помогите улучшить наши ИИ-модели",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                SettingsToggle(
                    checked = settings.improveModels,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(improveModels = on) } },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val memoryText = remember(colors.blueLink) {
                buildAnnotatedString {
                    append("Разрешить использование ваших чатов и сессий программирования для обучения и улучшения ИИ-моделей Anthropic. ")
                    pushStringAnnotation("url", "https://privacy.anthropic.com")
                    withLinkStyle(colors0 = colors.blueLink) { append("Подробнее") }
                    pop()
                }
            }
            ClickableText(
                text = memoryText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    color = colors.textPrimary,
                ),
                onClick = { offset ->
                    memoryText.getStringAnnotations("url", offset, offset)
                        .firstOrNull()
                        ?.let { uriHandler.openUri(it.item) }
                },
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withLinkStyle(
    colors0: Color,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    withStyle(SpanStyle(color = colors0, textDecoration = TextDecoration.Underline)) { block() }
}

@Composable
fun SharingScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings
    DetailScaffold(title = "Общий доступ", onBack = vm::backToSettings) {
        DetailLabel("Отправка из других приложений")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = ClaudenIcons.share,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Всегда отправлять в новый чат",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 23.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                SettingsToggle(
                    checked = settings.alwaysNewChat,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(alwaysNewChat = on) } },
                )
            }
        }
    }
}

@Composable
fun AccountScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val settings = state.settings

    var provider by remember { mutableStateOf(state.provider) }
    var apiKey by remember { mutableStateOf(state.apiKey) }
    var model by remember { mutableStateOf(state.model) }
    var baseUrl by remember {
        mutableStateOf(if (state.provider != Provider.Anthropic) state.baseUrl else state.customBaseUrl)
    }
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().screenBackground(colors)) {
        SettingsHeader(title = "Аккаунт", onBack = vm::backToSettings)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(57.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = settings.email,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    PlanBadge(activePlanLabel(state))
                }
            }

            if (state.claudeLinked) {
                Spacer(modifier = Modifier.height(12.dp))
                SettingsCard {
                    SettingsRow(
                        icon = ClaudenIcons.spark,
                        title = "Claude",
                        subtitle = "${claudePlanTitle(state.claudePlan)} · привязан на этом устройстве",
                        onClick = { vm.openSettingsSection(Screen.ClaudeMode) },
                    )
                }
            }

            if (state.auth.signedIn) {
                Spacer(modifier = Modifier.height(22.dp))
                DetailLabel("Вход в аккаунт")
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.auth.provider == "google") "Google" else "Email",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.clay,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (state.auth.birthday.isNotBlank()) {
                        HorizontalDivider(color = colors.border, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Дата рождения",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = state.auth.birthday,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Аккаунт защищён входом через ${if (state.auth.provider == "google") "Google" else "email"}. API-ключ вводить не нужно.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textTertiary,
                )
            } else {
                Spacer(modifier = Modifier.height(22.dp))
                DetailLabel("Провайдер")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ProviderPill(
                        label = "Clauden",
                        selected = provider == Provider.Clauden,
                        onClick = { provider = Provider.Clauden },
                    )
                    ProviderPill(
                        label = "Anthropic",
                        selected = provider == Provider.Anthropic,
                        onClick = { provider = Provider.Anthropic },
                    )
                    ProviderPill(
                        label = "Свой шлюз",
                        selected = provider == Provider.Custom,
                        onClick = { provider = Provider.Custom },
                    )
                }

                if (provider != Provider.Anthropic) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Base URL",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = if (provider == Provider.Clauden) {
                            ClaudenCatalog.baseUrl
                        } else {
                            "https://example.com"
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "API-ключ",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = if (provider == Provider.Anthropic) "sk-ant-…" else "sk-…",
                    transformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Модель",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(
                    value = model,
                    onValueChange = { model = it },
                    placeholder = "claude-sonnet-5-5",
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.loadingModels) {
                        "Загружаю список моделей…"
                    } else {
                        "Доступно моделей: ${state.models.size}"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textTertiary,
                )

                Spacer(modifier = Modifier.height(18.dp))
                SettingsActionButton(
                    text = "Сохранить и проверить",
                    enabled = !state.loadingModels,
                    onClick = {
                        vm.saveSettings(provider, baseUrl, apiKey, model)
                        vm.testConnection()
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = colors.bg300,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clickable { vm.refreshModels() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Обновить модели",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                            color = colors.textPrimary,
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(28.dp))
            DetailLabel("История")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clickable { confirmClear = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Очистить историю",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = colors.dangerSoft,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${state.conversations.size}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        SettingsConfirmDialog(
            title = "Удалить все чаты?",
            text = "История удалится на всех устройствах и из бэкапа аккаунта.",
            confirmText = "Удалить",
            onConfirm = {
                vm.clearHistory()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ProviderPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (selected) colors.control else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (selected) colors.control else colors.borderStrong),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
