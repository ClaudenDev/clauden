package space.teleopen.clauden.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shine = remember { Animatable(-2f) }
    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            shine.snapTo(-1f)
            shine.animateTo(2f, animationSpec = tween(650, easing = LinearEasing))
        }
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = if (pressed) 9.dp else 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.24f),
                spotColor = Color.Black.copy(alpha = 0.34f),
            )
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {

        Box(
            modifier = Modifier
                .matchParentSize()
                .glassPanel(shape, Color.Transparent),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.66f),
                        0.55f to Color.White.copy(alpha = 0.26f),
                        1f to Color.White.copy(alpha = 0.40f),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.32f),
                        0.38f to Color.Transparent,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val x = shine.value * size.width
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                            start = Offset(x - size.width * 0.22f, 0f),
                            end = Offset(x + size.width * 0.22f, size.height),
                        ),
                    )
                },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 13.dp),
            content = content,
        )
    }
}
