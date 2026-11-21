package space.teleopen.clauden.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import space.teleopen.clauden.data.ThemeStyle

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

fun glassBackgroundBrush(colors: ClaudenColors): Brush = if (colors.isDark) {
    Brush.verticalGradient(
        listOf(Color(0xFF1C1C1E), Color(0xFF0E0E10), Color(0xFF050506)),
    )
} else {
    Brush.verticalGradient(
        listOf(Color(0xFFF5F5F8), Color(0xFFE9E9EE), Color(0xFFDEDEE4)),
    )
}

@Composable
fun GlassBackdrop(
    colors: ClaudenColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glassBackgroundBrush(colors)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.62f
            val glow = if (colors.isDark) Color.White else Color.Black
            val glowAlpha = if (colors.isDark) 0.13f else 0.07f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.10f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width * 0.16f, size.height * 0.10f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = glowAlpha * 0.7f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.34f),
                    radius = radius * 0.9f,
                ),
                radius = radius * 0.9f,
                center = Offset(size.width * 0.88f, size.height * 0.34f),
            )

            val bokeh = listOf(
                0.18f to 0.30f, 0.42f to 0.18f, 0.72f to 0.22f, 0.88f to 0.46f,
                0.12f to 0.58f, 0.34f to 0.74f, 0.62f to 0.62f, 0.80f to 0.82f,
                0.24f to 0.90f, 0.52f to 0.44f,
            )
            bokeh.forEachIndexed { index, (x, y) ->
                val dotRadius = size.minDimension * (0.018f + (index % 4) * 0.012f)
                val dotCenter = Offset(size.width * x, size.height * y)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = if (colors.isDark) 0.16f else 0.09f),
                            Color.Transparent,
                        ),
                        center = dotCenter,
                        radius = dotRadius,
                    ),
                    radius = dotRadius,
                    center = dotCenter,
                )
            }
        }
    }
}

@Composable
fun Modifier.glassPanel(
    shape: Shape,
    tint: Color = Color.Transparent,
): Modifier {
    if (LocalThemeStyle.current != ThemeStyle.Glass) return this
    val state = LocalHazeState.current ?: return this
    val blur = (10f + LocalGlassLevel.current.coerceIn(0, 100) * 0.34f).dp
    return this.hazeChild(
        state = state,
        shape = shape,
        style = HazeStyle(
            backgroundColor = Color.Transparent,
            tint = HazeTint(tint),
            blurRadius = blur,
            noiseFactor = 0.04f,
        ),
    )
}
