package space.teleopen.clauden.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.TextAnimation
import space.teleopen.clauden.data.ThemeAssets
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeWallpaper
import space.teleopen.clauden.data.ThinkingAnimation
import space.teleopen.clauden.data.WallpaperKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WallpaperPreset(
    val title: String,
    val wallpaper: ThemeWallpaper,
)

private val wallpaperPresets = listOf(
    WallpaperPreset("Нет", ThemeWallpaper(kind = WallpaperKind.None)),
    WallpaperPreset(
        "Тёплые",
        ThemeWallpaper(
            kind = WallpaperKind.Gradient,
            start = 0xFFF9F9F7.toInt(),
            end = 0xFFF2DED3.toInt(),
        ),
    ),
    WallpaperPreset(
        "Закат",
        ThemeWallpaper(
            kind = WallpaperKind.Gradient,
            start = 0xFFF3C7B6.toInt(),
            end = 0xFFE7A68E.toInt(),
        ),
    ),
    WallpaperPreset(
        "Ночь",
        ThemeWallpaper(
            kind = WallpaperKind.Gradient,
            start = 0xFF191919.toInt(),
            end = 0xFF2B2420.toInt(),
        ),
    ),
    WallpaperPreset(
        "Мята",
        ThemeWallpaper(
            kind = WallpaperKind.Gradient,
            start = 0xFFE7F2EC.toInt(),
            end = 0xFFCDE6D8.toInt(),
        ),
    ),
    WallpaperPreset(
        "Небо",
        ThemeWallpaper(
            kind = WallpaperKind.Gradient,
            start = 0xFFE6EEF8.toInt(),
            end = 0xFFC9DDF2.toInt(),
        ),
    ),
    WallpaperPreset(
        "Персик",
        ThemeWallpaper(
            kind = WallpaperKind.Solid,
            start = 0xFFF6E7DE.toInt(),
            end = 0xFFF6E7DE.toInt(),
        ),
    ),
)

private fun isVideoPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
        lower.endsWith(".mkv") || lower.endsWith(".3gp") ||
        lower.endsWith(".mov") || lower.endsWith(".m4v")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val themeState by ThemeController.state.collectAsStateWithLifecycle()
    val theme = themeState.theme
    val context = LocalContext.current
    var name by remember { mutableStateOf(theme.name) }
    var accentPicker by remember { mutableStateOf(false) }
    var wallpaperStartPicker by remember { mutableStateOf(false) }
    var wallpaperEndPicker by remember { mutableStateOf(false) }
    var loadingTarget by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    ThemeAssets.import(context, uri, "wallpaper")
                }
                if (path != null) {
                    ThemeController.setWallpaper(
                        ThemeController.state.value.theme.wallpaper.copy(
                            kind = if (isVideoPath(path)) WallpaperKind.Video else WallpaperKind.Image,
                            imagePath = path,
                        ),
                    )
                } else {
                    vm.notify("Не удалось прочитать файл обоев")
                }
            }
        }
    }
    val mascotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    ThemeAssets.import(context, uri, "mascot")
                }
                if (path != null) ThemeController.setMascot(path)
                else vm.notify("Не удалось прочитать картинку маскота")
            }
        }
    }
    val loadingPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = loadingTarget
        if (uri != null && target.isNotBlank()) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    ThemeAssets.import(context, uri, "loading-$target")
                }
                if (path != null) {
                    when (target) {
                        "message" -> ThemeController.setMessageLoading(path)
                        "steps" -> ThemeController.setStepsLoading(path)
                        "thinking" -> ThemeController.setThinkingLoading(path)
                    }
                } else {
                    vm.notify("Не удалось прочитать анимацию")
                }
                loadingTarget = ""
            }
        } else {
            loadingTarget = ""
        }
    }

    LaunchedEffect(Unit) {
        ThemeController.startEditing(createIfMissing = true)
        name = ThemeController.state.value.theme.name
    }

    if (accentPicker) {
        ThemeColorPickerDialog(
            initial = theme.accent,
            title = "Акцентный цвет",
            onDismiss = { accentPicker = false },
            onPick = {
                ThemeController.setAccent(it)
                accentPicker = false
            },
        )
    }
    if (wallpaperStartPicker) {
        ThemeColorPickerDialog(
            initial = theme.wallpaper.start,
            title = if (theme.wallpaper.kind == WallpaperKind.Solid) {
                "Цвет обоев"
            } else {
                "Первый цвет градиента"
            },
            onDismiss = { wallpaperStartPicker = false },
            onPick = {
                ThemeController.setWallpaper(theme.wallpaper.copy(start = it))
                wallpaperStartPicker = false
            },
        )
    }
    if (wallpaperEndPicker) {
        ThemeColorPickerDialog(
            initial = theme.wallpaper.end,
            title = "Второй цвет градиента",
            onDismiss = { wallpaperEndPicker = false },
            onPick = {
                ThemeController.setWallpaper(theme.wallpaper.copy(end = it))
                wallpaperEndPicker = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        SettingsHeader(
            title = "Создать тему",
            onBack = { vm.backToSettings() },
            trailing = {
                TextButton(
                    onClick = {
                        ThemeController.setEditing(false)
                        vm.backToSettings()
                    },
                ) {
                    Text("Готово", color = colors.clay)
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Обои, акцент, анимации, маскот и свои иконки. " +
                    "Карандаш на экране: «Выбрать» → любой элемент интерфейса. " +
                    "В редакторе можно растянуть картинку на всю кнопку.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )

            SettingsSectionLabel("Стиль")
            ThemeStylePacks(
                selected = state.settings.themeStyle,
                onSelect = { style ->
                    vm.updateSettings { it.copy(themeStyle = style) }
                },
            )

            SettingsSectionLabel("Ручная настройка")
            SettingsCard {
                ThemeStyleControls(
                    settings = state.settings,
                    onUpdate = vm::updateSettings,
                )
            }

            SettingsSectionLabel("Название темы")
            SettingsField(
                value = name,
                onValueChange = {
                    name = it
                    ThemeController.setName(it)
                },
                placeholder = "Моя тема",
                height = 56.dp,
                textSize = 16,
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionLabel("Обои")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                wallpaperPresets.forEach { preset ->
                    WallpaperPresetCard(
                        preset = preset,
                        selected = theme.wallpaper.kind == preset.wallpaper.kind &&
                            theme.wallpaper.start == preset.wallpaper.start &&
                            theme.wallpaper.end == preset.wallpaper.end,
                        onClick = {
                            ThemeController.setWallpaper(
                                preset.wallpaper.copy(
                                    dim = theme.wallpaper.dim,
                                    blur = theme.wallpaper.blur,
                                    tilt = theme.wallpaper.tilt,
                                ),
                            )
                        },
                    )
                }
            }
            SettingsCard {
                SettingsRow(
                    icon = ClaudenIcons.image,
                    title = "Свои обои",
                    subtitle = when {
                        theme.wallpaper.kind == WallpaperKind.Video ->
                            "Видео: ${File(theme.wallpaper.imagePath).name}"
                        theme.wallpaper.kind == WallpaperKind.Image ->
                            "Картинка: ${File(theme.wallpaper.imagePath).name}"
                        else -> "Фото, GIF, WebP или MP4/WebM"
                    },
                    onClick = {
                        wallpaperPicker.launch(arrayOf("image/*", "video/*"))
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = ClaudenIcons.image,
                    title = "Сплошной цвет",
                    subtitle = "Один цвет на весь фон",
                    onClick = {
                        ThemeController.setWallpaper(
                            theme.wallpaper.copy(
                                kind = WallpaperKind.Solid,
                                start = theme.wallpaper.start,
                                end = theme.wallpaper.start,
                                imagePath = "",
                            ),
                        )
                    },
                    trailing = {
                        if (theme.wallpaper.kind == WallpaperKind.Solid) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(theme.wallpaper.start))
                                    .border(1.dp, colors.borderStrong, CircleShape),
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingsRow(
                    icon = ClaudenIcons.image,
                    title = "Градиент",
                    subtitle = "Свой переход из двух цветов",
                    onClick = {
                        ThemeController.setWallpaper(
                            theme.wallpaper.copy(
                                kind = WallpaperKind.Gradient,
                                imagePath = "",
                            ),
                        )
                    },
                    trailing = {
                        if (theme.wallpaper.kind == WallpaperKind.Gradient) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(theme.wallpaper.start),
                                                Color(theme.wallpaper.end),
                                            ),
                                        ),
                                    )
                                    .border(1.dp, colors.borderStrong, CircleShape),
                            )
                        }
                    },
                )
            }

            if (theme.wallpaper.kind == WallpaperKind.Solid ||
                theme.wallpaper.kind == WallpaperKind.Gradient
            ) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = if (theme.wallpaper.kind == WallpaperKind.Solid) {
                                "Цвет обоев"
                            } else {
                                "Цвета градиента"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        WallpaperColorRow(
                            label = if (theme.wallpaper.kind == WallpaperKind.Solid) "Фон" else "Начало",
                            argb = theme.wallpaper.start,
                            onOpen = { wallpaperStartPicker = true },
                        )
                        if (theme.wallpaper.kind == WallpaperKind.Gradient) {
                            Spacer(Modifier.height(8.dp))
                            WallpaperColorRow(
                                label = "Конец",
                                argb = theme.wallpaper.end,
                                onOpen = { wallpaperEndPicker = true },
                            )
                        }
                    }
                }
            }

            if (theme.wallpaper.kind != WallpaperKind.None) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = "Затемнение",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        Slider(
                            value = theme.wallpaper.dim,
                            onValueChange = {
                                ThemeController.setWallpaper(theme.wallpaper.copy(dim = it))
                            },
                            valueRange = 0f..0.7f,
                        )
                        Text(
                            text = "Размытие",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        Slider(
                            value = theme.wallpaper.blur,
                            onValueChange = { ThemeController.setWallpaperBlur(it) },
                            valueRange = 0f..24f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Наклон фона",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Обои плавно тянутся за наклоном телефона",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary,
                                )
                            }
                            SettingsToggle(
                                checked = theme.wallpaper.tilt,
                                onCheckedChange = ThemeController::setWallpaperTilt,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionLabel("Акцент")
            SettingsCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Цвет кнопок, переключателей и выделений",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        ColorPickerButton(onClick = { accentPicker = true })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        themePresetColors.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (theme.accent == argb) 3.dp else 1.dp,
                                        color = if (theme.accent == argb) colors.clay else colors.borderStrong,
                                        shape = CircleShape,
                                    )
                                    .clickable { ThemeController.setAccent(argb) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (theme.accent == argb) {
                                    Text(
                                        text = "✓",
                                        color = contrastOn(Color(argb)),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionLabel("Анимации")
            Text(
                text = "Генерация текста",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                textAnimationOptions.forEach { (animation, label) ->
                    FilterChip(
                        selected = theme.textAnimation == animation,
                        onClick = { ThemeController.setTextAnimation(animation) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.control,
                            selectedLabelColor = colors.textPrimary,
                            labelColor = colors.textSecondary,
                        ),
                    )
                }
            }
            noAnimationWarning(theme.textAnimation == TextAnimation.None &&
                theme.messageLoadingPath.isBlank())
            Text(
                text = "Шаги и размышления",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                thinkingAnimationOptions.forEach { (animation, label) ->
                    FilterChip(
                        selected = theme.thinkingAnimation == animation,
                        onClick = { ThemeController.setThinkingAnimation(animation) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.control,
                            selectedLabelColor = colors.textPrimary,
                            labelColor = colors.textSecondary,
                        ),
                    )
                }
            }
            SettingsCard {
                LoadingImageRow(
                    title = "Своя анимация текста",
                    path = theme.messageLoadingPath,
                    subtitle = theme.messageLoadingPath
                        .takeIf { it.isNotBlank() }
                        ?.let { File(it).name }
                        ?: "GIF, WebP или PNG вместо точек",
                    onClick = {
                        loadingTarget = "message"
                        loadingPicker.launch(arrayOf("image/*"))
                    },
                    onClear = if (theme.messageLoadingPath.isNotBlank()) {
                        { ThemeController.setMessageLoading("") }
                    } else {
                        null
                    },
                )
                SettingsDivider()
                LoadingImageRow(
                    title = "Своя анимация шагов",
                    path = theme.stepsLoadingPath,
                    subtitle = theme.stepsLoadingPath
                        .takeIf { it.isNotBlank() }
                        ?.let { File(it).name }
                        ?: "GIF, WebP или PNG для строки шагов",
                    onClick = {
                        loadingTarget = "steps"
                        loadingPicker.launch(arrayOf("image/*"))
                    },
                    onClear = if (theme.stepsLoadingPath.isNotBlank()) {
                        { ThemeController.setStepsLoading("") }
                    } else {
                        null
                    },
                )
                SettingsDivider()
                LoadingImageRow(
                    title = "Своя анимация «Думаю…»",
                    path = theme.thinkingLoadingPath,
                    subtitle = theme.thinkingLoadingPath
                        .takeIf { it.isNotBlank() }
                        ?.let { File(it).name }
                        ?: "GIF, WebP или PNG вместо логотипа",
                    onClick = {
                        loadingTarget = "thinking"
                        loadingPicker.launch(arrayOf("image/*"))
                    },
                    onClear = if (theme.thinkingLoadingPath.isNotBlank()) {
                        { ThemeController.setThinkingLoading("") }
                    } else {
                        null
                    },
                )
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionLabel("Маскот")
            SettingsCard {
                SettingsRow(
                    icon = ClaudenIcons.image,
                    title = "Картинка маскота",
                    subtitle = theme.mascotPath
                        .takeIf { it.isNotBlank() }
                        ?.let { File(it).name }
                        ?: "PNG, GIF или WebP",
                    onClick = { mascotPicker.launch(arrayOf("image/*")) },
                )
                SettingsDivider()
                SettingsRow(
                    icon = ClaudenIcons.spark,
                    title = "Показывать на экране",
                    subtitle = "Перетаскивание и размер — в режиме «Переместить»",
                    trailing = {
                        SettingsToggle(
                            checked = theme.mascotVisible,
                            onCheckedChange = ThemeController::setMascotVisible,
                        )
                    },
                )
                if (theme.mascotPath.isNotBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            text = "Размер маскота",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        Slider(
                            value = theme.mascotSize,
                            onValueChange = ThemeController::setMascotSize,
                            valueRange = 48f..360f,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionLabel("Свои иконки и блоки")
            Text(
                text = "Карандаш → «Выбрать» → блок сообщения, строка или кнопка. " +
                    "В редакторе есть палитра, HEX и режим «На всю кнопку» для своей картинки. " +
                    "Доступны блоки вашего сообщения и ответа ИИ.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(10.dp))
            SettingsActionButton(
                text = "Сохранить и выйти",
                onClick = {
                    ThemeController.setEditing(false)
                    vm.backToSettings()
                },
            )
            OutlinedButton(
                onClick = { ThemeController.activate(!themeState.active) },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(if (themeState.active) "Выключить тему" else "Включить тему")
            }
            TextButton(
                onClick = { ThemeController.deleteTheme() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить тему", color = colors.danger)
            }
        }
    }
}

@Composable
private fun noAnimationWarning(show: Boolean) {
    if (!show) return
    Text(
        text = "Сейчас анимация выключена: во время генерации ничего не будет видно. " +
            "Выберите точки/пульс/волну или добавьте свою картинку ниже.",
        style = MaterialTheme.typography.bodySmall,
        color = LocalClaudenColors.current.dangerSoft,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun WallpaperColorRow(
    label: String,
    argb: Int,
    onOpen: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, colors.borderStrong, CircleShape)
                .clickable(onClick = onOpen),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        ColorPickerButton(onClick = onOpen)
    }
}

@Composable
private fun LoadingImageRow(
    title: String,
    path: String,
    subtitle: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val colors = LocalClaudenColors.current
    SettingsRow(
        icon = ClaudenIcons.spark,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            if (path.isNotBlank()) {
                ThemeAssetImage(
                    path = path,
                    modifier = Modifier.size(30.dp),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = ClaudenIcons.trash,
                        contentDescription = "Убрать",
                        tint = colors.danger,
                    )
                }
            }
        },
    )
}

@Composable
private fun WallpaperPresetCard(
    preset: WallpaperPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val wallpaper = preset.wallpaper
    val brush = when (wallpaper.kind) {
        WallpaperKind.None -> Brush.linearGradient(listOf(colors.bg, colors.bg300))
        WallpaperKind.Solid -> Brush.linearGradient(
            listOf(Color(wallpaper.start), Color(wallpaper.start)),
        )
        else -> Brush.linearGradient(
            listOf(Color(wallpaper.start), Color(wallpaper.end)),
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) colors.clay else colors.borderStrong,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (wallpaper.kind == WallpaperKind.None) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = preset.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.clay else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val textAnimationOptions = listOf(
    TextAnimation.Dots to "Точки",
    TextAnimation.Pulse to "Пульс",
    TextAnimation.Wave to "Волна",
    TextAnimation.None to "Нет",
)

private val thinkingAnimationOptions = listOf(
    ThinkingAnimation.Spin to "Вращение",
    ThinkingAnimation.Pulse to "Пульс",
    ThinkingAnimation.Bounce to "Прыжок",
    ThinkingAnimation.None to "Нет",
)
