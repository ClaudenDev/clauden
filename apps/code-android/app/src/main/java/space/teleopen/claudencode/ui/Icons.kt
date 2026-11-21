package space.teleopen.claudencode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun IconFrame(
    size: Dp,
    tint: Color,
    modifier: Modifier,
    width: Float = 1.7f,
    fill: Boolean = false,
    block: DrawScope.(Float) -> Unit,
) {
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
            block(width)
        }
    }
}

@Composable
fun IconPhone(size: Dp = 22.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.6f) { w ->
        drawRoundRect(
            color = tint,
            topLeft = Offset(6.5f, 2f),
            size = androidx.compose.ui.geometry.Size(11f, 20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.6f),
            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawLine(
            tint,
            Offset(10.5f, 18.4f),
            Offset(13.5f, 18.4f),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }

@Composable
fun IconMonitor(size: Dp = 22.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.6f) { w ->
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.5f, 4f),
            size = androidx.compose.ui.geometry.Size(19f, 13f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawLine(tint, Offset(12f, 17f), Offset(12f, 20.5f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(8f, 20.5f), Offset(16f, 20.5f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconFolder(size: Dp = 22.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.6f) { w ->
        val path = Path().apply {
            moveTo(3f, 19.5f)
            lineTo(3f, 6.5f)
            quadraticBezierTo(3f, 4.5f, 5f, 4.5f)
            lineTo(8.5f, 4.5f)
            lineTo(10.5f, 7f)
            lineTo(19f, 7f)
            quadraticBezierTo(21f, 7f, 21f, 9f)
            lineTo(21f, 17.5f)
            quadraticBezierTo(21f, 19.5f, 19f, 19.5f)
            lineTo(5f, 19.5f)
            quadraticBezierTo(3f, 19.5f, 3f, 19.5f)
            close()
        }
        drawPath(path, tint, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

@Composable
fun IconFolderOpen(size: Dp = 22.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.6f) { w ->
        val path = Path().apply {
            moveTo(3f, 19.5f)
            lineTo(3f, 6.5f)
            quadraticBezierTo(3f, 4.5f, 5f, 4.5f)
            lineTo(8.5f, 4.5f)
            lineTo(10.5f, 7f)
            lineTo(18f, 7f)
            quadraticBezierTo(20f, 7f, 20f, 9f)
            lineTo(20f, 10f)
        }
        drawPath(path, tint, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val front = Path().apply {
            moveTo(3f, 19.5f)
            lineTo(5.5f, 11.5f)
            quadraticBezierTo(6f, 10f, 7.5f, 10f)
            lineTo(21f, 10f)
            quadraticBezierTo(22.2f, 10f, 21.8f, 11.4f)
            lineTo(20f, 18f)
            quadraticBezierTo(19.6f, 19.5f, 18f, 19.5f)
            close()
        }
        drawPath(front, tint, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

@Composable
fun IconBranch(size: Dp = 18.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.6f) { w ->
        drawCircle(tint, radius = 2.1f, center = Offset(6.5f, 5.5f), style = Stroke(w))
        drawCircle(tint, radius = 2.1f, center = Offset(6.5f, 18.5f), style = Stroke(w))
        drawCircle(tint, radius = 2.1f, center = Offset(17.5f, 8.5f), style = Stroke(w))
        drawLine(tint, Offset(6.5f, 7.6f), Offset(6.5f, 16.4f), strokeWidth = w, cap = StrokeCap.Round)
        val curve = Path().apply {
            moveTo(6.5f, 12.5f)
            cubicTo(11.5f, 12.5f, 17.5f, 13f, 17.5f, 10.6f)
        }
        drawPath(curve, tint, style = Stroke(width = w, cap = StrokeCap.Round))
    }

@Composable
fun IconCube(size: Dp = 18.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.5f) { w ->
        val hex = Path().apply {
            moveTo(12f, 2.8f)
            lineTo(20f, 7.2f)
            lineTo(20f, 16.8f)
            lineTo(12f, 21.2f)
            lineTo(4f, 16.8f)
            lineTo(4f, 7.2f)
            close()
        }
        drawPath(hex, tint, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, Offset(12f, 12f), Offset(20f, 7.2f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(12f, 12f), Offset(4f, 7.2f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(12f, 12f), Offset(12f, 21.2f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconClock(size: Dp = 22.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.6f) { w ->
        drawCircle(tint, radius = 8.8f, center = Offset(12f, 12f), style = Stroke(w))
        drawLine(tint, Offset(12f, 6.8f), Offset(12f, 12.3f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(12f, 12.3f), Offset(15.4f, 14.4f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconArrowRight(size: Dp = 16.dp, tint: Color = Pal.Accent, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.8f) { w ->
        drawLine(tint, Offset(4f, 12f), Offset(19f, 12f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(13f, 6f), Offset(19f, 12f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(13f, 18f), Offset(19f, 12f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconChevron(
    size: Dp = 18.dp,
    tint: Color = Pal.Gray,
    modifier: Modifier = Modifier,
    left: Boolean = false,
    up: Boolean = false,
) = IconFrame(size, tint, modifier, 1.9f) { w ->
    when {
        up -> {
            drawLine(tint, Offset(5f, 15f), Offset(12f, 8f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(tint, Offset(19f, 15f), Offset(12f, 8f), strokeWidth = w, cap = StrokeCap.Round)
        }
        left -> {
            drawLine(tint, Offset(15f, 5f), Offset(8f, 12f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(tint, Offset(15f, 19f), Offset(8f, 12f), strokeWidth = w, cap = StrokeCap.Round)
        }
        else -> {
            drawLine(tint, Offset(9f, 5f), Offset(16f, 12f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(tint, Offset(9f, 19f), Offset(16f, 12f), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun IconPlay(size: Dp = 16.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier) {
        val path = Path().apply {
            moveTo(7.6f, 4.8f)
            lineTo(19.2f, 12f)
            lineTo(7.6f, 19.2f)
            close()
        }
        drawPath(path, tint)
    }

@Composable
fun IconPlus(size: Dp = 24.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.9f) { w ->
        drawLine(tint, Offset(12f, 4.5f), Offset(12f, 19.5f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(4.5f, 12f), Offset(19.5f, 12f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconDots(size: Dp = 24.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier) {
        listOf(5f, 12f, 19f).forEach { x ->
            drawCircle(tint, radius = 1.55f, center = Offset(x, 12f))
        }
    }

@Composable
fun IconStar(
    size: Dp = 22.dp,
    tint: Color = Pal.Accent,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) = IconFrame(size, tint, modifier, 1.7f) { w ->
    val path = Path().apply {
        moveTo(12f, 2.6f)
        lineTo(14.9f, 8.7f)
        lineTo(21.6f, 9.6f)
        lineTo(16.7f, 14.2f)
        lineTo(17.9f, 20.9f)
        lineTo(12f, 17.7f)
        lineTo(6.1f, 20.9f)
        lineTo(7.3f, 14.2f)
        lineTo(2.4f, 9.6f)
        lineTo(9.1f, 8.7f)
        close()
    }
    if (filled) {
        drawPath(path, tint)
    } else {
        drawPath(path, tint, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconTerminal(size: Dp = 20.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.8f) { w ->
        drawLine(tint, Offset(3.5f, 6.5f), Offset(9f, 12f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(3.5f, 17.5f), Offset(9f, 12f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(12f, 17.5f), Offset(20.5f, 17.5f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconWaveform(size: Dp = 26.dp, tint: Color = Pal.Accent, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.9f) { w ->
        val bars = listOf(
            3.4f to (7f to 17f),
            7.8f to (10f to 14f),
            12f to (4.5f to 19.5f),
            16.2f to (9f to 15f),
            20.4f to (6.5f to 17.5f),
        )
        bars.forEach { (x, span) ->
            drawLine(
                tint,
                Offset(x, span.first),
                Offset(x, span.second),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
    }

@Composable
fun IconQuestion(size: Dp = 20.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.5f) { w ->
        drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = Stroke(w))
        val hook = Path().apply {
            moveTo(9.3f, 9.2f)
            cubicTo(9.3f, 6.6f, 14.7f, 6.4f, 14.7f, 9.6f)
            cubicTo(14.7f, 12f, 12f, 11.8f, 12f, 14.7f)
        }
        drawPath(hook, tint, style = Stroke(width = w, cap = StrokeCap.Round))
        drawCircle(tint, radius = 1.05f, center = Offset(12f, 17.8f))
    }

@Composable
fun IconUp(size: Dp = 20.dp, tint: Color = Pal.White, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.9f) { w ->
        drawLine(tint, Offset(12f, 19f), Offset(12f, 5.5f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(5.5f, 12f), Offset(12f, 5.5f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(18.5f, 12f), Offset(12f, 5.5f), strokeWidth = w, cap = StrokeCap.Round)
    }

private val githubPath: Path by lazy {
    PathParser().parsePathString(
        "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385" +
            ".6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61" +
            "C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236" +
            " 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605" +
            "-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22" +
            "-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405" +
            " 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176" +
            ".765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22" +
            " 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297" +
            "c0-6.627-5.373-12-12-12",
    ).toPath()
}

@Composable
fun IconGitHub(size: Dp = 20.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier) {
        drawPath(githubPath, tint)
    }

@Composable
fun IconSend(size: Dp = 26.dp, tint: Color = Pal.Accent, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier) {
        val path = Path().apply {
            moveTo(2.4f, 21f)
            lineTo(22.6f, 12f)
            lineTo(2.4f, 3f)
            lineTo(2.3f, 10f)
            lineTo(16.4f, 12f)
            lineTo(2.3f, 14f)
            close()
        }
        drawPath(path, tint)
    }

@Composable
fun IconCheckRing(size: Dp = 22.dp, tint: Color = Pal.Green, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.7f) { w ->
        drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = Stroke(w))
        drawLine(tint, Offset(7.4f, 12.2f), Offset(10.7f, 15.4f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(10.7f, 15.4f), Offset(16.7f, 8.8f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconCheck(size: Dp = 18.dp, tint: Color = Pal.Green, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 2f) { w ->
        drawLine(tint, Offset(4.5f, 12.5f), Offset(9.5f, 17.5f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(9.5f, 17.5f), Offset(19.5f, 6.5f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconClose(size: Dp = 18.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.8f) { w ->
        drawLine(tint, Offset(6f, 6f), Offset(18f, 18f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(18f, 6f), Offset(6f, 18f), strokeWidth = w, cap = StrokeCap.Round)
    }

@Composable
fun IconStop(size: Dp = 18.dp, tint: Color = Pal.Red, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(6f, 6f),
            size = androidx.compose.ui.geometry.Size(12f, 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f),
        )
    }

@Composable
fun IconFile(size: Dp = 20.dp, tint: Color = Pal.Gray, modifier: Modifier = Modifier) =
    IconFrame(size, tint, modifier, 1.5f) { w ->
        val path = Path().apply {
            moveTo(6f, 2.8f)
            lineTo(14f, 2.8f)
            lineTo(19f, 7.8f)
            lineTo(19f, 21.2f)
            lineTo(6f, 21.2f)
            close()
            moveTo(14f, 2.8f)
            lineTo(14f, 7.8f)
            lineTo(19f, 7.8f)
        }
        drawPath(path, tint, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
