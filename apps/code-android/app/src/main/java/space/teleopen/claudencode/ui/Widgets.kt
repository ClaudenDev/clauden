package space.teleopen.claudencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Rule(color: Color = Pal.Border, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
fun Modifier.termClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    ),
)

@Composable
fun TuiBoxFrame(
    title: String? = null,
    modifier: Modifier = Modifier,
    color: Color = Pal.Accent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, color.copy(alpha = 0.45f)),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(1.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .offset(y = (-9).dp),
                ) {
                    Text(
                        text = title,
                        color = color,
                        style = mono(12.5.sp, FontWeight.Bold),
                        modifier = Modifier
                            .background(Pal.Bg)
                            .padding(horizontal = 10.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun ContextPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Pal.Cyan, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = Pal.CyanText,
            style = mono(13.sp, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 1000 -> "%.1fk".format(tokens / 1000.0)
    else -> tokens.toString()
}

internal fun agoLite(ts: Long): String {
    if (ts <= 0) return ""
    val delta = ((System.currentTimeMillis() - ts) / 1000).coerceAtLeast(0)
    return when {
        delta < 60 -> "< 1 min"
        delta < 3600 -> "${delta / 60}min"
        delta < 86400 -> "${delta / 3600}h"
        delta < 2_592_000 -> "${delta / 86400}d"
        else -> "${delta / 2_592_000}mo"
    }
}
