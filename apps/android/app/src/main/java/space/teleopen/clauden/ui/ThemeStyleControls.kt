package space.teleopen.clauden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.AppSettings
import space.teleopen.clauden.data.ButtonStyle
import space.teleopen.clauden.data.MotionStyle
import space.teleopen.clauden.data.ThemeStyle
import space.teleopen.clauden.data.ToggleStyle
import space.teleopen.clauden.data.UiDensity

@Composable
fun ThemeStylePacks(
    selected: ThemeStyle,
    onSelect: (ThemeStyle) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ThemeStyle.entries.forEach { style ->
            StylePackCard(
                style = style,
                selected = selected == style,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(style) },
            )
        }
    }
}

@Composable
fun ThemeStyleControls(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    OptionsRow(
        title = "Кнопки",
        hint = "Форма и материал кнопок",
        options = ButtonStyle.entries.map { it.label to (settings.buttonStyle == it) },
        onSelect = { index ->
            onUpdate { it.copy(buttonStyle = ButtonStyle.entries[index]) }
        },
    )
    SettingsDivider()
    OptionsRow(
        title = "Тумблеры",
        hint = "Переключатели в стиле iOS или классические",
        options = ToggleStyle.entries.map { it.label to (settings.toggleStyle == it) },
        onSelect = { index ->
            onUpdate { it.copy(toggleStyle = ToggleStyle.entries[index]) }
        },
    )
    SettingsDivider()
    OptionsRow(
        title = "Плотность",
        hint = "Сколько воздуха между элементами",
        options = UiDensity.entries.map { it.label to (settings.uiDensity == it) },
        onSelect = { index ->
            onUpdate { it.copy(uiDensity = UiDensity.entries[index]) }
        },
    )
    SettingsDivider()
    OptionsRow(
        title = "Анимации",
        hint = "Плавность нажатий и переключений",
        options = MotionStyle.entries.map { it.label to (settings.motionStyle == it) },
        onSelect = { index ->
            onUpdate { it.copy(motionStyle = MotionStyle.entries[index]) }
        },
    )
    SettingsDivider()
    SliderRow(
        title = "Скругление",
        hint = "Карточки, поля и панели",
        value = settings.cornerRadius,
        valueRange = 6..28,
        suffix = "px",
    ) { value -> onUpdate { it.copy(cornerRadius = value) } }
    SettingsDivider()
    SliderRow(
        title = "Стекло",
        hint = "Сила размытия фона в стиле iOS Glass",
        value = settings.glassLevel,
        valueRange = 0..100,
        suffix = "%",
    ) { value -> onUpdate { it.copy(glassLevel = value) } }
    SettingsDivider()
    SliderRow(
        title = "Размер текста",
        hint = "Масштаб сообщений и интерфейса",
        value = settings.textScale,
        valueRange = 85..125,
        suffix = "%",
    ) { value -> onUpdate { it.copy(textScale = value) } }
    SettingsDivider()
    SettingsRow(
        icon = ClaudenIcons.sliders,
        title = "Сбросить оформление",
        subtitle = "Классический стиль и стандартные значения",
        onClick = {
            onUpdate {
                it.copy(
                    themeStyle = ThemeStyle.Classic,
                    accentOverride = 0,
                    cornerRadius = 12,
                    glassLevel = 65,
                    buttonStyle = ButtonStyle.Soft,
                    toggleStyle = ToggleStyle.Classic,
                    textScale = 100,
                    uiDensity = UiDensity.Comfortable,
                    motionStyle = MotionStyle.Full,
                )
            }
        },
    )
}

@Composable
private fun StylePackCard(
    style: ThemeStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val shape = RoundedCornerShape(14.dp)
    val previewAccent = when (style) {
        ThemeStyle.Classic -> Color(0xFFD97757)
        ThemeStyle.Glass -> Color(0xFFE8E8ED)
        ThemeStyle.Noir -> Color(0xFFC85B3C)
    }
    val preview = when (style) {
        ThemeStyle.Classic -> Brush.linearGradient(
            listOf(Color(0xFF262626), Color(0xFF191919)),
        )
        ThemeStyle.Glass -> Brush.linearGradient(
            listOf(Color(0xFFE9E9EE), Color(0xFF9A9AA4), Color(0xFF2A2A2E)),
        )
        ThemeStyle.Noir -> Brush.linearGradient(
            listOf(Color(0xFF18181D), Color(0xFF000000)),
        )
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.bg300)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.clay else colors.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(preview),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.55f)),
                )
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(previewAccent),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun OptionsRow(
    title: String,
    hint: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(9.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEachIndexed { index, (label, selected) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) colors.clay else colors.control)
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (selected) colors.onClay else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    hint: String,
    value: Int,
    valueRange: IntRange,
    suffix: String,
    onValueChange: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.control)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textTertiary,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.clay,
                activeTrackColor = colors.clay,
                inactiveTrackColor = colors.control,
            ),
        )
    }
}
