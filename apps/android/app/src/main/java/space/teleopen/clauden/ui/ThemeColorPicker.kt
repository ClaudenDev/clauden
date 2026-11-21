package space.teleopen.clauden.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import space.teleopen.clauden.data.ThemeElementStyle

val themeColorPalette = listOf(
    0xFFD97757.toInt(), 0xFFE08A6D.toInt(), 0xFFF0A38A.toInt(), 0xFFF5C2B0.toInt(),
    0xFFE8A54A.toInt(), 0xFFF2BC6E.toInt(), 0xFF6FCF97.toInt(), 0xFF3FAE74.toInt(),
    0xFF2A78D6.toInt(), 0xFF6DA7EC.toInt(), 0xFF4C8BF5.toInt(), 0xFF80C7F2.toInt(),
    0xFF6750A4.toInt(), 0xFF9A84D4.toInt(), 0xFFE57373.toInt(), 0xFFF08A8A.toInt(),
    0xFFE91E63.toInt(), 0xFFF48FB1.toInt(), 0xFF00BCD4.toInt(), 0xFF4DD0E1.toInt(),
    0xFF1F1E1C.toInt(), 0xFF3A3935.toInt(), 0xFF7B7974.toInt(), 0xFFB9B7B0.toInt(),
    0xFFE9E8E3.toInt(), 0xFFF0EFEC.toInt(), 0xFFF9F9F7.toInt(), 0xFFFFFFFF.toInt(),
)

private fun hsvColor(hue: Float, sat: Float, value: Float): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))

private fun argbToHex(argb: Int): String =
    String.format("#%06X", 0xFFFFFF and argb)

private fun parsePickerHex(raw: String): Int? {
    val value = raw.trim().removePrefix("#")
    if (value.length != 6 && value.length != 8) return null
    return runCatching { AndroidColor.parseColor("#$value") }.getOrNull()
}

@Composable
fun ThemeColorPickerDialog(
    initial: Int,
    title: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val colors = LocalClaudenColors.current
    val initialHsv = remember(initial) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial, it) }
    }
    var hue by remember(initial) { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember(initial) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(initialHsv[2]) }
    var hex by remember(initial) { mutableStateOf(argbToHex(initial)) }
    var squareWidth by remember { mutableStateOf(0) }
    var squareHeight by remember { mutableStateOf(0) }
    var hueWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    fun syncHex() {
        hex = argbToHex(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
    }

    fun applyHex(raw: String) {
        hex = raw
        val parsed = parsePickerHex(raw) ?: return
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    val preview = hsvColor(hue, sat, value)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = colors.bg,
            contentColor = colors.textPrimary,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                        .onSizeChanged {
                            squareWidth = it.width
                            squareHeight = it.height
                        }
                        .pointerInput(squareWidth, squareHeight) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun update(offset: Offset) {
                                    if (squareWidth <= 0 || squareHeight <= 0) return
                                    sat = (offset.x / squareWidth).coerceIn(0f, 1f)
                                    value = (1f - offset.y / squareHeight).coerceIn(0f, 1f)
                                    syncHex()
                                }
                                update(down.position)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change != null) {
                                        update(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, hsvColor(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val cx = size.width * sat
                    val cy = size.height * (1f - value)
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.25f),
                        radius = 11.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                                ),
                            ),
                        )
                        .border(1.dp, colors.border, CircleShape)
                        .onSizeChanged { hueWidth = it.width }
                        .pointerInput(hueWidth) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun update(offset: Offset) {
                                    if (hueWidth <= 0) return
                                    hue = ((offset.x / hueWidth) * 360f).coerceIn(0f, 359.9f)
                                    syncHex()
                                }
                                update(down.position)
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change != null) {
                                        update(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(preview)
                            .border(2.dp, colors.borderStrong, CircleShape),
                    )
                    Spacer(Modifier.size(12.dp))
                    SettingsField(
                        value = hex,
                        onValueChange = ::applyHex,
                        placeholder = "#D97757",
                        height = 52.dp,
                        textSize = 15,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Палитра",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                themeColorPalette.chunked(7).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        row.forEach { argb ->
                            val swatch = Color(argb)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.dp, colors.borderStrong, CircleShape)
                                    .clickable {
                                        val hsv = FloatArray(3).also {
                                            AndroidColor.colorToHSV(argb, it)
                                        }
                                        hue = hsv[0]
                                        sat = hsv[1]
                                        value = hsv[2]
                                        syncHex()
                                    },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            onPick(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.clay,
                            contentColor = colors.onClay,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Выбрать")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerButton(onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.height(32.dp),
    ) {
        Text(
            text = "Палитра",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = colors.textSecondary,
        )
    }
}

fun ThemeElementStyle?.orDefault(): ThemeElementStyle = this ?: ThemeElementStyle()
