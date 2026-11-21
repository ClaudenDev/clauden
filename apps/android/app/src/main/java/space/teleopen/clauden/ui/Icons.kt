package space.teleopen.clauden.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ClaudenIcons {

    private fun vector(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

    private fun ImageVector.Builder.stroked(
        width: Float = 1.6f,
        block: PathBuilder.() -> Unit,
    ) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

    private fun ImageVector.Builder.filled(block: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = block)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = r * 0.5523f
        moveTo(cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        close()
    }

    private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        quadTo(x + w, y, x + w, y + r)
        lineTo(x + w, y + h - r)
        quadTo(x + w, y + h, x + w - r, y + h)
        lineTo(x + r, y + h)
        quadTo(x, y + h, x, y + h - r)
        lineTo(x, y + r)
        quadTo(x, y, x + r, y)
        close()
    }

    val menu: ImageVector = vector("Menu") {
        stroked(2f) {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(14f, 17f)
        }
    }

    val sliders: ImageVector = vector("Sliders") {
        stroked(1.7f) {
            moveTo(3.5f, 8.5f)
            lineTo(20.5f, 8.5f)
            moveTo(3.5f, 15.5f)
            lineTo(20.5f, 15.5f)
        }
        filled {
            circle(16f, 8.5f, 2.1f)
            circle(8f, 15.5f, 2.1f)
        }
    }

    val chatBubble: ImageVector = vector("ChatBubble") {
        stroked(1.6f) {
            moveTo(10f, 4.8f)
            lineTo(14.4f, 4.8f)
            quadTo(20f, 4.8f, 20f, 10.2f)
            lineTo(20f, 11.4f)
            quadTo(20f, 17.2f, 14.4f, 17.2f)
            lineTo(11.6f, 17.2f)
            lineTo(8.4f, 20f)
            lineTo(8.4f, 17.2f)
            lineTo(5.4f, 17.2f)
            quadTo(4f, 17.2f, 4f, 15.8f)
            lineTo(4f, 10.2f)
            quadTo(4f, 4.8f, 10f, 4.8f)
            close()
        }
    }

    val chats: ImageVector = vector("Chats") {
        stroked(1.6f) {
            moveTo(11.4f, 4.2f)
            lineTo(18.6f, 4.2f)
            quadTo(22f, 4.2f, 22f, 7.6f)
            lineTo(22f, 9.4f)
            quadTo(22f, 12.8f, 18.6f, 12.8f)
            lineTo(16.2f, 12.8f)
            lineTo(14f, 14.8f)
            lineTo(14f, 12.8f)
            lineTo(11.4f, 12.8f)
            quadTo(8f, 12.8f, 8f, 9.4f)
            lineTo(8f, 7.6f)
            quadTo(8f, 4.2f, 11.4f, 4.2f)
            close()

            circle(6.2f, 16.6f, 3.3f)
            moveTo(8.7f, 19.1f)
            lineTo(10.8f, 21.2f)
        }
    }

    val projects: ImageVector = vector("Projects") {
        stroked(1.6f) {
            roundedRect(3.8f, 4.4f, 16.4f, 4.4f, 1.8f)
            roundedRect(5f, 8.8f, 14f, 10.8f, 2.4f)
            moveTo(10f, 12.6f)
            lineTo(14f, 12.6f)
        }
    }

    val projectEmpty: ImageVector = ImageVector.Builder(
        name = "ProjectEmpty",
        defaultWidth = 96.dp,
        defaultHeight = 96.dp,
        viewportWidth = 96f,
        viewportHeight = 96f,
    ).apply {
        stroked(1.8f) {
            moveTo(24f, 16f)
            lineTo(52f, 16f)
            quadTo(64f, 16f, 64f, 28f)
            lineTo(64f, 42f)
            quadTo(64f, 54f, 52f, 54f)
            lineTo(36f, 54f)
            lineTo(24f, 64f)
            lineTo(24f, 54f)
            lineTo(20f, 54f)
            quadTo(10f, 54f, 10f, 42f)
            lineTo(10f, 28f)
            quadTo(10f, 16f, 24f, 16f)
            close()
            moveTo(58f, 44f)
            lineTo(74f, 44f)
            quadTo(86f, 44f, 86f, 56f)
            lineTo(86f, 66f)
            quadTo(86f, 78f, 74f, 78f)
            lineTo(64f, 78f)
            lineTo(58f, 86f)
            lineTo(58f, 78f)
            lineTo(56f, 78f)
            quadTo(48f, 78f, 48f, 68f)
            lineTo(48f, 56f)
            quadTo(48f, 44f, 58f, 44f)
            close()
        }
    }.build()

    val documentPlus: ImageVector = ImageVector.Builder(
        name = "DocumentPlus",
        defaultWidth = 96.dp,
        defaultHeight = 96.dp,
        viewportWidth = 96f,
        viewportHeight = 96f,
    ).apply {
        stroked(2f) {
            moveTo(36f, 12f)
            lineTo(56f, 12f)
            lineTo(66f, 22f)
            lineTo(66f, 74f)
            lineTo(30f, 74f)
            lineTo(30f, 18f)
            quadTo(30f, 12f, 36f, 12f)
            close()
            moveTo(56f, 12f)
            lineTo(56f, 22f)
            lineTo(66f, 22f)
            moveTo(58f, 52f)
            lineTo(58f, 78f)
            moveTo(45f, 65f)
            lineTo(71f, 65f)
        }
    }.build()

    val terminal: ImageVector = vector("Terminal") {
        stroked(1.6f) {
            roundedRect(3.4f, 4.6f, 17.2f, 14.8f, 2.2f)
            moveTo(7.2f, 9.2f)
            lineTo(10.6f, 12f)
            lineTo(7.2f, 14.8f)
            moveTo(12.4f, 14.8f)
            lineTo(17f, 14.8f)
        }
    }

    val key: ImageVector = vector("Key") {
        stroked(1.6f) {
            circle(8.2f, 9.2f, 4.2f)
            moveTo(11.2f, 12.2f)
            lineTo(20.2f, 21.2f)
            moveTo(15.2f, 16.2f)
            lineTo(17.4f, 14f)
            moveTo(17.6f, 18.6f)
            lineTo(19.8f, 16.4f)
        }
    }

    val code: ImageVector = vector("Code") {
        stroked(1.6f) {
            moveTo(8f, 7.5f)
            lineTo(3.5f, 12f)
            lineTo(8f, 16.5f)
            moveTo(16f, 7.5f)
            lineTo(20.5f, 12f)
            lineTo(16f, 16.5f)
            moveTo(13.5f, 5f)
            lineTo(10.5f, 19f)
        }
    }

    val artifacts: ImageVector = vector("Artifacts") {
        stroked(1.5f) {
            roundedRect(3.4f, 4.6f, 11.4f, 11.4f, 3.4f)
            roundedRect(9.2f, 8.2f, 11.4f, 11.4f, 3.4f)
        }
    }

    val personPlus: ImageVector = vector("PersonPlus") {
        stroked(1.6f) {
            moveTo(3.5f, 19.5f)
            quadTo(3.5f, 14.5f, 9.5f, 14.5f)
            quadTo(13.2f, 14.5f, 14.8f, 16.2f)
            moveTo(9.5f, 11f)
            circle(9.5f, 7.5f, 3.4f)
            moveTo(17.5f, 4.5f)
            lineTo(17.5f, 10.5f)
            moveTo(14.5f, 7.5f)
            lineTo(20.5f, 7.5f)
        }
    }

    val share: ImageVector = vector("Share") {
        stroked(1.6f) {
            moveTo(12f, 15.5f)
            lineTo(12f, 3.5f)
            moveTo(8f, 7.5f)
            lineTo(12f, 3.5f)
            lineTo(16f, 7.5f)
            moveTo(6f, 11f)
            lineTo(4f, 11f)
            lineTo(4f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 11f)
            lineTo(18f, 11f)
        }
    }

    val play: ImageVector = vector("Play") {
        stroked(1.6f) {
            moveTo(8f, 4.5f)
            lineTo(19f, 12f)
            lineTo(8f, 19.5f)
            close()
        }
    }

    val thumbUp: ImageVector = vector("ThumbUp") {
        stroked(1.5f) {
            moveTo(8f, 9.5f)
            lineTo(10.6f, 4.2f)
            quadTo(11f, 3.2f, 12.2f, 3.2f)
            quadTo(13.6f, 3.2f, 13.6f, 4.8f)
            lineTo(13.6f, 8.5f)
            lineTo(18f, 8.5f)
            quadTo(20.2f, 8.5f, 19.6f, 10.7f)
            lineTo(18.1f, 18.2f)
            quadTo(17.8f, 20f, 15.8f, 20f)
            lineTo(8f, 20f)
            close()
            moveTo(8f, 9.5f)
            lineTo(4.2f, 9.5f)
            lineTo(4.2f, 20f)
            lineTo(8f, 20f)
        }
    }

    val thumbDown: ImageVector = vector("ThumbDown") {
        stroked(1.5f) {
            moveTo(8f, 14.5f)
            lineTo(10.6f, 19.8f)
            quadTo(11f, 20.8f, 12.2f, 20.8f)
            quadTo(13.6f, 20.8f, 13.6f, 19.2f)
            lineTo(13.6f, 15.5f)
            lineTo(18f, 15.5f)
            quadTo(20.2f, 15.5f, 19.6f, 13.3f)
            lineTo(18.1f, 5.8f)
            quadTo(17.8f, 4f, 15.8f, 4f)
            lineTo(8f, 4f)
            close()
            moveTo(8f, 14.5f)
            lineTo(4.2f, 14.5f)
            lineTo(4.2f, 4f)
            lineTo(8f, 4f)
        }
    }

    val camera: ImageVector = vector("Camera") {
        stroked(1.6f) {
            moveTo(4f, 8.2f)
            quadTo(4f, 6.4f, 5.8f, 6.4f)
            lineTo(8.2f, 6.4f)
            lineTo(9.4f, 4.6f)
            lineTo(14.6f, 4.6f)
            lineTo(15.8f, 6.4f)
            lineTo(18.2f, 6.4f)
            quadTo(20f, 6.4f, 20f, 8.2f)
            lineTo(20f, 18f)
            quadTo(20f, 19.8f, 18.2f, 19.8f)
            lineTo(5.8f, 19.8f)
            quadTo(4f, 19.8f, 4f, 18f)
            close()
            circle(12f, 13f, 3.6f)
        }
    }

    val image: ImageVector = vector("Image") {
        stroked(1.6f) {
            roundedRect(4f, 4.6f, 16f, 14.8f, 3f)
            circle(8.8f, 9.4f, 1.4f)
            moveTo(5.4f, 17.2f)
            lineTo(10.6f, 12.4f)
            lineTo(14.2f, 15.6f)
            lineTo(16.8f, 13.2f)
            lineTo(18.8f, 15f)
        }
    }

    val grid: ImageVector = vector("Grid") {
        stroked(1.6f) {
            roundedRect(4f, 4f, 6.6f, 6.6f, 1.6f)
            roundedRect(13.4f, 4f, 6.6f, 6.6f, 1.6f)
            roundedRect(4f, 13.4f, 6.6f, 6.6f, 1.6f)
            roundedRect(13.4f, 13.4f, 6.6f, 6.6f, 1.6f)
        }
    }

    val volume: ImageVector = vector("Volume") {
        stroked(1.6f) {
            moveTo(4.2f, 9.4f)
            lineTo(7.6f, 9.4f)
            lineTo(12.2f, 5.6f)
            lineTo(12.2f, 18.4f)
            lineTo(7.6f, 14.6f)
            lineTo(4.2f, 14.6f)
            close()
            moveTo(15.4f, 9.2f)
            quadTo(17.2f, 12f, 15.4f, 14.8f)
            moveTo(18f, 6.8f)
            quadTo(21.4f, 12f, 18f, 17.2f)
        }
    }

    val micSlash: ImageVector = vector("MicSlash") {
        stroked(1.6f) {
            moveTo(12f, 13.5f)
            quadTo(14.4f, 13.5f, 14.4f, 11f)
            lineTo(14.4f, 6f)
            quadTo(14.4f, 3.6f, 12f, 3.6f)
            quadTo(9.6f, 3.6f, 9.6f, 6f)
            lineTo(9.6f, 8f)
            moveTo(6.2f, 11.2f)
            quadTo(6.2f, 17.4f, 12f, 17.4f)
            quadTo(13.6f, 17.4f, 14.8f, 16.6f)
            moveTo(12f, 17.4f)
            lineTo(12f, 20.6f)
            moveTo(4.5f, 4.5f)
            lineTo(19.5f, 19.5f)
        }
    }

    val mic: ImageVector = vector("Mic") {
        stroked(1.6f) {
            moveTo(12f, 13.5f)
            quadTo(14.4f, 13.5f, 14.4f, 11f)
            lineTo(14.4f, 6f)
            quadTo(14.4f, 3.6f, 12f, 3.6f)
            quadTo(9.6f, 3.6f, 9.6f, 6f)
            lineTo(9.6f, 11f)
            quadTo(9.6f, 13.5f, 12f, 13.5f)
            close()
            moveTo(6.2f, 11.2f)
            quadTo(6.2f, 17.4f, 12f, 17.4f)
            quadTo(17.8f, 17.4f, 17.8f, 11.2f)
            moveTo(12f, 17.4f)
            lineTo(12f, 20.6f)
        }
    }

    val waveform: ImageVector = vector("Waveform") {
        stroked(2f) {
            moveTo(4f, 10f)
            lineTo(4f, 14f)
            moveTo(8f, 7f)
            lineTo(8f, 17f)
            moveTo(12f, 4f)
            lineTo(12f, 20f)
            moveTo(16f, 7f)
            lineTo(16f, 17f)
            moveTo(20f, 10f)
            lineTo(20f, 14f)
        }
    }

    val pin: ImageVector = vector("Pin") {
        filled {
            moveTo(16f, 12f)
            lineTo(16f, 4f)
            lineTo(17f, 4f)
            lineTo(17f, 2f)
            lineTo(7f, 2f)
            lineTo(7f, 4f)
            lineTo(8f, 4f)
            lineTo(8f, 12f)
            lineTo(6f, 14f)
            lineTo(6f, 16f)
            lineTo(11.2f, 16f)
            lineTo(11.2f, 22f)
            lineTo(12.8f, 22f)
            lineTo(12.8f, 16f)
            lineTo(18f, 16f)
            lineTo(18f, 14f)
            close()
        }
    }

    val check: ImageVector = vector("Check") {
        stroked(2.1f) {
            moveTo(4.4f, 12.6f)
            lineTo(9.4f, 17.6f)
            lineTo(19.6f, 6.4f)
        }
    }

    val chevronRight: ImageVector = vector("ChevronRight") {
        stroked(1.8f) {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }

    val lock: ImageVector = vector("Lock") {
        stroked(1.5f) {
            roundedRect(4.5f, 10f, 15f, 10.5f, 2f)
            moveTo(7.5f, 10f)
            lineTo(7.5f, 7f)
            quadTo(7.5f, 3f, 12f, 3f)
            quadTo(16.5f, 3f, 16.5f, 7f)
            lineTo(16.5f, 10f)
            moveTo(12f, 14f)
            lineTo(12f, 16.5f)
        }
    }

    val copy: ImageVector = vector("Copy") {
        stroked(1.5f) {
            roundedRect(3.5f, 3.5f, 12f, 12f, 2f)
            roundedRect(8.5f, 8.5f, 12f, 12f, 2f)
        }
    }

    val arrowBack: ImageVector = vector("ArrowBack") {
        stroked(1.9f) {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    val arrowUp: ImageVector = vector("ArrowUp") {
        stroked(2f) {
            moveTo(12f, 19f)
            lineTo(12f, 5f)
            moveTo(5.8f, 11.2f)
            lineTo(12f, 5f)
            lineTo(18.2f, 11.2f)
        }
    }

    val stop: ImageVector = vector("Stop") {
        filled {
            roundedRect(7f, 7f, 10f, 10f, 2f)
        }
    }

    val clock: ImageVector = vector("Clock") {
        stroked(1.5f) {
            circle(12f, 12f, 8.6f)
            moveTo(12f, 7.4f)
            lineTo(12f, 12.4f)
            lineTo(15.4f, 14.4f)
        }
    }

    val warning: ImageVector = vector("Warning") {
        stroked(1.5f) {
            moveTo(12f, 4.2f)
            lineTo(21f, 19.4f)
            lineTo(3f, 19.4f)
            close()
            moveTo(12f, 9.6f)
            lineTo(12f, 14f)
            moveTo(12f, 16.6f)
            lineTo(12f, 16.7f)
        }
    }

    val spark: ImageVector = vector("Spark") {
        stroked(1.7f) {
            moveTo(12f, 3.5f)
            lineTo(12f, 20.5f)
            moveTo(3.5f, 12f)
            lineTo(20.5f, 12f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val person: ImageVector = vector("Person") {
        stroked(1.6f) {
            circle(12f, 7.4f, 3.7f)
            moveTo(4.4f, 20.2f)
            curveTo(4.8f, 15.9f, 7.9f, 13.9f, 12f, 13.9f)
            curveTo(16.1f, 13.9f, 19.2f, 15.9f, 19.6f, 20.2f)
        }
    }

    val dollar: ImageVector = vector("Dollar") {
        stroked(1.5f) {
            moveTo(16.2f, 7.1f)
            curveTo(15.2f, 5.4f, 13.6f, 4.6f, 11.9f, 4.6f)
            curveTo(9.1f, 4.6f, 7.4f, 6.1f, 7.4f, 8.2f)
            curveTo(7.4f, 10.4f, 9.1f, 11.4f, 12f, 12.1f)
            curveTo(14.9f, 12.8f, 16.6f, 13.9f, 16.6f, 16f)
            curveTo(16.6f, 18.1f, 14.8f, 19.6f, 12f, 19.6f)
            curveTo(10.2f, 19.6f, 8.5f, 18.7f, 7.5f, 17f)
            moveTo(12f, 3.2f)
            lineTo(12f, 20.8f)
        }
    }

    val connectors: ImageVector = vector("Connectors") {
        stroked(1.5f) {
            roundedRect(3.4f, 3.4f, 7.4f, 7.4f, 2.6f)
            roundedRect(13.2f, 3.4f, 7.4f, 7.4f, 2.6f)
            roundedRect(3.4f, 13.2f, 7.4f, 7.4f, 2.6f)
            roundedRect(13.2f, 13.2f, 7.4f, 7.4f, 2.6f)
        }
    }

    val android: ImageVector = vector("Android") {
        stroked(1.5f) {
            moveTo(4.8f, 16.6f)
            curveTo(4.8f, 11.9f, 8.1f, 8.4f, 12f, 8.4f)
            curveTo(15.9f, 8.4f, 19.2f, 11.9f, 19.2f, 16.6f)
            close()
            moveTo(7.7f, 8.9f)
            lineTo(6.3f, 6.2f)
            moveTo(16.3f, 8.9f)
            lineTo(17.7f, 6.2f)
        }
        filled {
            circle(9.3f, 13f, 0.75f)
            circle(14.7f, 13f, 0.75f)
        }
    }

    val moon: ImageVector = vector("Moon") {
        stroked(1.6f) {
            moveTo(20.2f, 14.9f)
            curveTo(18.9f, 17.7f, 16.1f, 19.5f, 13f, 19.2f)
            curveTo(9.2f, 18.8f, 6.5f, 15.4f, 6.9f, 11.6f)
            curveTo(7.2f, 8.6f, 9.2f, 6.1f, 12f, 5.2f)
            curveTo(10.5f, 8.6f, 11.5f, 12.6f, 14.2f, 14.6f)
            curveTo(15.9f, 15.8f, 18.1f, 16f, 20.2f, 14.9f)
            close()
        }
    }

    val moonPlus: ImageVector = vector("MoonPlus") {
        stroked(1.6f) {
            moveTo(19.6f, 12.4f)
            curveTo(18.7f, 15.4f, 16f, 17.6f, 12.8f, 17.8f)
            curveTo(8.9f, 18f, 5.7f, 15f, 5.5f, 11.2f)
            curveTo(5.3f, 8.1f, 7.2f, 5.3f, 10f, 4.2f)
            curveTo(8.5f, 7.4f, 9.4f, 11.3f, 12.1f, 13.4f)
            curveTo(13.3f, 14.3f, 14.8f, 14.8f, 16.4f, 14.9f)
            moveTo(18.4f, 16.6f)
            lineTo(18.4f, 21.6f)
            moveTo(15.9f, 19.1f)
            lineTo(20.9f, 19.1f)
        }
    }

    val font: ImageVector = vector("Font") {
        stroked(1.7f) {
            moveTo(4.2f, 5.4f)
            lineTo(11.4f, 5.4f)
            moveTo(7.8f, 5.4f)
            lineTo(7.8f, 18.6f)
            moveTo(13.6f, 11.4f)
            lineTo(19.8f, 11.4f)
            moveTo(16.7f, 11.4f)
            lineTo(16.7f, 18.6f)
        }
    }

    val phone: ImageVector = vector("Phone") {
        stroked(1.5f) {
            roundedRect(8f, 3.4f, 8f, 17.2f, 2.2f)
            moveTo(11f, 17.4f)
            lineTo(13f, 17.4f)
            moveTo(5.2f, 8.8f)
            curveTo(4.3f, 10.3f, 4.3f, 13.7f, 5.2f, 15.2f)
            moveTo(2.6f, 7.4f)
            curveTo(1.4f, 9.5f, 1.4f, 14.5f, 2.6f, 16.6f)
            moveTo(18.8f, 8.8f)
            curveTo(19.7f, 10.3f, 19.7f, 13.7f, 18.8f, 15.2f)
            moveTo(21.4f, 7.4f)
            curveTo(22.6f, 9.5f, 22.6f, 14.5f, 21.4f, 16.6f)
        }
    }

    val personBell: ImageVector = vector("PersonBell") {
        stroked(1.5f) {
            circle(8.4f, 8.2f, 4f)
            moveTo(2.4f, 20.4f)
            curveTo(2.8f, 16.2f, 5.3f, 14.2f, 8.4f, 14.2f)
            curveTo(10.2f, 14.2f, 11.6f, 14.9f, 12.6f, 16.1f)
            moveTo(21.4f, 19.4f)
            lineTo(15.4f, 19.4f)
            curveTo(16.4f, 18.4f, 16.9f, 17.1f, 16.9f, 15.6f)
            lineTo(16.9f, 14.7f)
            curveTo(16.9f, 12.4f, 18.1f, 11f, 19.8f, 11f)
            curveTo(21.5f, 11f, 22.7f, 12.4f, 22.7f, 14.7f)
            lineTo(22.7f, 15.6f)
            curveTo(22.7f, 17.1f, 23.2f, 18.4f, 24.2f, 19.4f)
        }
    }

    val bell: ImageVector = vector("Bell") {
        stroked(1.5f) {
            moveTo(18.2f, 16.8f)
            lineTo(5.8f, 16.8f)
            curveTo(7.1f, 15.5f, 7.7f, 14f, 7.7f, 12.1f)
            lineTo(7.7f, 10.6f)
            curveTo(7.7f, 7.6f, 9.6f, 5.4f, 12f, 5.4f)
            curveTo(14.4f, 5.4f, 16.3f, 7.6f, 16.3f, 10.6f)
            lineTo(16.3f, 12.1f)
            curveTo(16.3f, 14f, 16.9f, 15.5f, 18.2f, 16.8f)
            close()
            moveTo(10.2f, 19.4f)
            curveTo(10.7f, 20.2f, 11.3f, 20.6f, 12f, 20.6f)
            curveTo(12.7f, 20.6f, 13.3f, 20.2f, 13.8f, 19.4f)
        }
    }

    val megaphone: ImageVector = vector("Megaphone") {
        stroked(1.5f) {
            moveTo(4f, 9.6f)
            lineTo(7.6f, 9.6f)
            lineTo(14.4f, 5.2f)
            lineTo(14.4f, 18.8f)
            lineTo(7.6f, 14.4f)
            lineTo(4f, 14.4f)
            close()
            moveTo(6.4f, 14.4f)
            lineTo(7.6f, 20f)
            lineTo(10.4f, 20f)
            lineTo(9.5f, 15.2f)
            moveTo(18.2f, 9.4f)
            lineTo(20.6f, 9.4f)
            moveTo(17.4f, 12f)
            lineTo(19.8f, 12.8f)
            moveTo(17.4f, 6f)
            lineTo(19.8f, 5.2f)
        }
    }

    val shield: ImageVector = vector("Shield") {
        stroked(1.5f) {
            moveTo(12f, 3.2f)
            lineTo(19.4f, 6.2f)
            lineTo(19.4f, 11.6f)
            curveTo(19.4f, 16.3f, 16.3f, 19.6f, 12f, 21f)
            curveTo(7.7f, 19.6f, 4.6f, 16.3f, 4.6f, 11.6f)
            lineTo(4.6f, 6.2f)
            close()
            circle(12f, 10.4f, 1.9f)
            moveTo(8.9f, 15.5f)
            curveTo(9.3f, 13.7f, 10.4f, 13.1f, 12f, 13.1f)
            curveTo(13.6f, 13.1f, 14.7f, 13.7f, 15.1f, 15.5f)
        }
    }

    val link: ImageVector = vector("Link") {
        stroked(1.6f) {
            moveTo(10.2f, 13.8f)
            curveTo(11.5f, 15.1f, 13.6f, 15.1f, 14.9f, 13.8f)
            lineTo(17.7f, 11f)
            curveTo(19f, 9.7f, 19f, 7.6f, 17.7f, 6.3f)
            curveTo(16.4f, 5f, 14.3f, 5f, 13f, 6.3f)
            lineTo(11.6f, 7.7f)
            moveTo(13.8f, 10.2f)
            curveTo(12.5f, 8.9f, 10.4f, 8.9f, 9.1f, 10.2f)
            lineTo(6.3f, 13f)
            curveTo(5f, 14.3f, 5f, 16.4f, 6.3f, 17.7f)
            curveTo(7.6f, 19f, 9.7f, 19f, 11f, 17.7f)
            lineTo(12.4f, 16.3f)
        }
    }

    val location: ImageVector = vector("Location") {
        stroked(1.5f) {
            moveTo(20.4f, 3.6f)
            lineTo(3.9f, 10.8f)
            lineTo(10.6f, 13.4f)
            lineTo(13.2f, 20.1f)
            close()
        }
    }

    val calendar: ImageVector = vector("Calendar") {
        stroked(1.5f) {
            roundedRect(3.8f, 5.4f, 16.4f, 15f, 2.4f)
            moveTo(3.8f, 10f)
            lineTo(20.2f, 10f)
            moveTo(8.2f, 3.2f)
            lineTo(8.2f, 6.4f)
            moveTo(15.8f, 3.2f)
            lineTo(15.8f, 6.4f)
        }
    }

    val globe: ImageVector = vector("Globe") {
        stroked(1.5f) {
            circle(12f, 12f, 8.6f)
            moveTo(3.4f, 12f)
            lineTo(20.6f, 12f)
            moveTo(12f, 3.4f)
            curveTo(15.2f, 6.6f, 15.6f, 17.4f, 12f, 20.6f)
            moveTo(12f, 3.4f)
            curveTo(8.8f, 6.6f, 8.4f, 17.4f, 12f, 20.6f)
        }
    }

    val chart: ImageVector = vector("Chart") {
        stroked(1.6f) {
            moveTo(4.4f, 20f)
            lineTo(4.4f, 13.6f)
            moveTo(9.8f, 20f)
            lineTo(9.8f, 8.2f)
            moveTo(15.2f, 20f)
            lineTo(15.2f, 11.4f)
            moveTo(20.6f, 20f)
            lineTo(20.6f, 4.4f)
        }
    }

    val folder: ImageVector = vector("Folder") {
        stroked(1.6f) {
            moveTo(3.6f, 7.4f)
            lineTo(3.6f, 5.9f)
            quadTo(3.6f, 4.9f, 4.6f, 4.9f)
            lineTo(9.1f, 4.9f)
            lineTo(10.9f, 7.1f)
            lineTo(19.4f, 7.1f)
            quadTo(20.4f, 7.1f, 20.4f, 8.1f)
            lineTo(20.4f, 18.1f)
            quadTo(20.4f, 19.1f, 19.4f, 19.1f)
            lineTo(4.6f, 19.1f)
            quadTo(3.6f, 19.1f, 3.6f, 18.1f)
            close()
        }
    }

    val doc: ImageVector = vector("Doc") {
        stroked(1.5f) {
            moveTo(13.4f, 3.4f)
            lineTo(6.4f, 3.4f)
            lineTo(6.4f, 20.6f)
            lineTo(17.6f, 20.6f)
            lineTo(17.6f, 7.6f)
            close()
            moveTo(13.4f, 3.4f)
            lineTo(13.4f, 7.6f)
            lineTo(17.6f, 7.6f)
            moveTo(9.4f, 12f)
            lineTo(14.6f, 12f)
            moveTo(9.4f, 15.4f)
            lineTo(14.6f, 15.4f)
        }
    }

    val updown: ImageVector = vector("UpDown") {
        stroked(1.6f) {
            moveTo(7.4f, 3.8f)
            lineTo(7.4f, 20.2f)
            moveTo(4.2f, 7f)
            lineTo(7.4f, 3.8f)
            lineTo(10.6f, 7f)
            moveTo(16.6f, 20.2f)
            lineTo(16.6f, 3.8f)
            moveTo(13.4f, 17f)
            lineTo(16.6f, 20.2f)
            lineTo(19.8f, 17f)
        }
    }

    val logout: ImageVector = vector("Logout") {
        stroked(1.6f) {
            moveTo(10.2f, 4f)
            lineTo(5.2f, 4f)
            lineTo(5.2f, 20f)
            lineTo(10.2f, 20f)
            moveTo(14.4f, 8f)
            lineTo(18.4f, 12f)
            lineTo(14.4f, 16f)
            moveTo(18.4f, 12f)
            lineTo(9.2f, 12f)
        }
    }

    val external: ImageVector = vector("External") {
        stroked(1.5f) {
            moveTo(14f, 4.2f)
            lineTo(19.8f, 4.2f)
            lineTo(19.8f, 10f)
            moveTo(19.8f, 4.2f)
            lineTo(11.6f, 12.4f)
            moveTo(17.6f, 13.8f)
            lineTo(17.6f, 19.8f)
            lineTo(4.2f, 19.8f)
            lineTo(4.2f, 6.4f)
            lineTo(10.2f, 6.4f)
        }
    }

    val info: ImageVector = vector("Info") {
        stroked(1.5f) {
            circle(12f, 12f, 8.8f)
            moveTo(12f, 10.8f)
            lineTo(12f, 16.4f)
        }
        filled {
            circle(12f, 7.6f, 0.9f)
        }
    }

    val download: ImageVector = vector("Download") {
        stroked(1.7f) {
            moveTo(12f, 3.8f)
            lineTo(12f, 15.2f)
            moveTo(7.4f, 10.8f)
            lineTo(12f, 15.4f)
            lineTo(16.6f, 10.8f)
            moveTo(4.8f, 20.2f)
            lineTo(19.2f, 20.2f)
        }
    }

    val home: ImageVector = vector("Home") {
        stroked(1.6f) {
            moveTo(3.8f, 10.6f)
            lineTo(12f, 3.8f)
            lineTo(20.2f, 10.6f)
            moveTo(6f, 9.2f)
            lineTo(6f, 20.2f)
            lineTo(18f, 20.2f)
            lineTo(18f, 9.2f)
            moveTo(10f, 20.2f)
            lineTo(10f, 14.2f)
            lineTo(14f, 14.2f)
            lineTo(14f, 20.2f)
        }
    }

    val pencil: ImageVector = vector("Pencil") {
        stroked(1.6f) {
            moveTo(4.6f, 19.4f)
            lineTo(5.7f, 15.3f)
            lineTo(15.9f, 5.1f)
            quadTo(16.8f, 4.2f, 17.7f, 5.1f)
            lineTo(18.9f, 6.3f)
            quadTo(19.8f, 7.2f, 18.9f, 8.1f)
            lineTo(8.7f, 18.3f)
            close()
            moveTo(14.5f, 6.5f)
            lineTo(17.5f, 9.5f)
        }
    }

    val trash: ImageVector = vector("Trash") {
        stroked(1.6f) {
            moveTo(4.2f, 6.4f)
            lineTo(19.8f, 6.4f)
            moveTo(9f, 6.4f)
            lineTo(9f, 4.4f)
            lineTo(15f, 4.4f)
            lineTo(15f, 6.4f)
            moveTo(6.2f, 6.4f)
            lineTo(7.2f, 20f)
            lineTo(16.8f, 20f)
            lineTo(17.8f, 6.4f)
            moveTo(10.2f, 10.5f)
            lineTo(10.6f, 16.6f)
            moveTo(13.8f, 10.5f)
            lineTo(13.4f, 16.6f)
        }
    }

    val plus: ImageVector = vector("Plus") {
        stroked(1.7f) {
            moveTo(12f, 4.4f)
            lineTo(12f, 19.6f)
            moveTo(4.4f, 12f)
            lineTo(19.6f, 12f)
        }
    }

    val sun: ImageVector = vector("Sun") {
        stroked(1.6f) {
            circle(12f, 12f, 4f)
            moveTo(12f, 3f)
            lineTo(12f, 5.2f)
            moveTo(12f, 18.8f)
            lineTo(12f, 21f)
            moveTo(3f, 12f)
            lineTo(5.2f, 12f)
            moveTo(18.8f, 12f)
            lineTo(21f, 12f)
            moveTo(5.6f, 5.6f)
            lineTo(7.2f, 7.2f)
            moveTo(16.8f, 16.8f)
            lineTo(18.4f, 18.4f)
            moveTo(18.4f, 5.6f)
            lineTo(16.8f, 7.2f)
            moveTo(7.2f, 16.8f)
            lineTo(5.6f, 18.4f)
        }
    }
}
