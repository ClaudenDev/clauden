package space.teleopen.clauden.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.teleopen.clauden.data.CustomTheme
import space.teleopen.clauden.data.TextAnimation
import space.teleopen.clauden.data.ThemeAssets
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeElementStyle
import space.teleopen.clauden.data.ThemeIconMode
import space.teleopen.clauden.data.ThemeStyle
import space.teleopen.clauden.data.ThemeWallpaper
import space.teleopen.clauden.data.ThinkingAnimation
import space.teleopen.clauden.data.WallpaperKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

val LocalCustomTheme = staticCompositionLocalOf { CustomTheme() }

data class ThemeEditState(
    val editing: Boolean = false,
    val selectionMode: Boolean = false,
    val moveMode: Boolean = false,
    val selectedId: String = "",
    val selectedLabel: String = "",
)

val LocalThemeEditState = staticCompositionLocalOf { ThemeEditState() }

@Composable
fun elementStyle(id: String): ThemeElementStyle? =
    LocalCustomTheme.current.elements[id]

@Composable
fun elementIcon(id: String): String? =
    LocalCustomTheme.current.icons[id]?.takeIf { it.isNotBlank() }

@Composable
fun Modifier.screenBackground(colors: ClaudenColors): Modifier {
    val theme = LocalCustomTheme.current
    if (theme.hasWallpaper) return background(Color.Transparent)
    return when (LocalThemeStyle.current) {
        ThemeStyle.Glass ->
            if (LocalHazeState.current != null) {
                background(Color.Transparent)
            } else {
                background(glassBackgroundBrush(colors))
            }
        ThemeStyle.Noir -> background(
            Brush.verticalGradient(
                listOf(lerp(colors.bg, colors.clay, 0.12f), colors.bg, colors.bg),
            ),
        )
        ThemeStyle.Classic -> background(colors.bg)
    }
}

@Composable
fun headerBackground(colors: ClaudenColors): Color = when {
    LocalCustomTheme.current.hasWallpaper -> Color.Transparent
    LocalThemeStyle.current == ThemeStyle.Glass -> colors.bg.copy(alpha = 0.72f)
    else -> colors.bg
}

fun contrastOn(color: Color): Color =
    if (color.luminance() > 0.55f) Color(0xFF161616) else Color.White

@Composable
fun ThemeElementFrame(
    style: ThemeElementStyle?,
    iconPath: String?,
    fallback: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = style?.background?.let(::Color) ?: fallback
    val border = style?.border?.let(::Color)
    val fullImage = iconPath != null && style?.iconMode != ThemeIconMode.Inline
    Box(

        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
    ) {
        if (fullImage) {
            ThemeAssetImage(
                path = iconPath,
                modifier = Modifier.matchParentSize(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
            if (style?.background != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(background.copy(alpha = 0.22f)),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(background),
            )
        }
        content()
    }
}

@Composable
fun ThemeIconButton(
    id: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val colors = LocalClaudenColors.current
    val style = LocalCustomTheme.current.elements[id]
    val iconPath = LocalCustomTheme.current.icons[id]
    val selecting = LocalThemeEditState.current.selectionMode
    val fullImage = iconPath != null &&
        style?.iconMode != ThemeIconMode.Inline &&
        style?.iconMode != null
    val action: () -> Unit = if (selecting) {
        { ThemeController.selectElement(id, contentDescription) }
    } else {
        onClick
    }
    ThemeElementFrame(
        style = style,
        iconPath = iconPath.takeIf { fullImage },
        fallback = Color.Transparent,
        shape = CircleShape,
        modifier = modifier.size(size),
    ) {
        IconButton(
            onClick = action,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (fullImage) {

            } else if (iconPath != null) {
                ThemeAssetImage(
                    path = iconPath,
                    modifier = Modifier.size(iconSize),
                    contentDescription = contentDescription,
                    contentScale = if (style?.iconMode == ThemeIconMode.Fill) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = style?.content?.let(::Color) ?: colors.textPrimary,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
fun ThemeWallpaperBackground(theme: CustomTheme) {
    val wallpaper = theme.wallpaper
    if (wallpaper.kind == WallpaperKind.None) return
    val colors = LocalClaudenColors.current
    val bitmap = remember(wallpaper.kind, wallpaper.imagePath) {
        if (wallpaper.kind == WallpaperKind.Image) {
            wallpaper.imagePath.takeIf { it.isNotBlank() }
                ?.let { decodeSampledBitmap(it, 2048) }
        } else {
            null
        }
    }
    val wallpaperIsDark = when (wallpaper.kind) {
        WallpaperKind.Image -> remember(bitmap) {
            bitmap?.let { averageLuminance(it) < 0.45f } ?: false
        }

        WallpaperKind.Solid -> Color(wallpaper.start).luminance() < 0.45f
        WallpaperKind.Gradient -> (
            Color(wallpaper.start).luminance() + Color(wallpaper.end).luminance()
            ) / 2f < 0.45f

        WallpaperKind.Video, WallpaperKind.None -> false
    }
    val tilt = if (wallpaper.tilt) rememberDeviceTilt() else Offset.Zero

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (wallpaper.tilt) {
                        Modifier.graphicsLayer(
                            scaleX = 1.20f,
                            scaleY = 1.20f,
                            translationX = with(LocalDensity.current) { -(18.dp * tilt.x).toPx() },
                            translationY = with(LocalDensity.current) { -(18.dp * tilt.y).toPx() },
                        )
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wallpaper.blur > 0f) {
                        Modifier.blur(wallpaper.blur.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            when (wallpaper.kind) {
                WallpaperKind.Solid -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(wallpaper.start)),
                )

                WallpaperKind.Gradient -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(wallpaper.start), Color(wallpaper.end)),
                            ),
                        ),
                )

                WallpaperKind.Image -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(wallpaper.start), Color(wallpaper.end)),
                                ),
                            ),
                    )
                    if (wallpaper.imagePath.isNotBlank()) {
                        ThemeAssetImage(
                            path = wallpaper.imagePath,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                WallpaperKind.Video -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(wallpaper.start), Color(wallpaper.end)),
                                ),
                            ),
                    )
                    if (wallpaper.imagePath.isNotBlank()) {
                        ThemeVideoWallpaper(
                            path = wallpaper.imagePath,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                WallpaperKind.None -> Unit
            }
        }

        if (wallpaper.kind == WallpaperKind.Video) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (colors.isDark) {
                            Color.Black.copy(alpha = 0.12f)
                        } else {
                            Color.White.copy(alpha = 0.20f)
                        },
                    ),
            )
        }

        if (!colors.isDark && wallpaperIsDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.66f)),
            )
        } else if (colors.isDark && !wallpaperIsDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f)),
            )
        }
        if (wallpaper.dim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaper.dim.coerceIn(0f, 0.85f))),
            )
        }
    }
}

@Composable
private fun ThemeVideoWallpaper(path: String, modifier: Modifier = Modifier) {

    val frame by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull()?.coerceAtLeast(800L) ?: 8_000L
                val interval = 125L
                var position = 0L
                while (isActive) {
                    val scaled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        runCatching {
                            retriever.getScaledFrameAtTime(
                                position * 1000,
                                MediaMetadataRetriever.OPTION_CLOSEST,
                                640,
                                1136,
                            )
                        }.getOrNull()
                    } else {
                        null
                    }
                    val bitmap = scaled ?: runCatching {
                        retriever.getFrameAtTime(
                            position * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                        )
                    }.getOrNull()
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) { value = bitmap }
                    }
                    delay(interval)
                    position = (position + interval) % duration
                }
            } catch (_: Throwable) {

            } finally {
                runCatching { retriever.release() }
            }
        }
    }
    if (frame != null) {
        Image(
            bitmap = frame!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
private fun rememberDeviceTilt(): Offset {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)

                tilt = Offset(
                    x = tilt.x * 0.82f + x * 0.18f,
                    y = tilt.y * 0.82f + y * 0.18f,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}

private fun decodeSampledBitmap(path: String, maxDimension: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private fun averageLuminance(bitmap: android.graphics.Bitmap): Float {
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    var sum = 0f
    var count = 0
    var x = 0
    while (x < bitmap.width) {
        var y = 0
        while (y < bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = pixel ushr 24 and 0xFF
            if (alpha >= 32) {
                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
                count++
            }
            y += stepY
        }
        x += stepX
    }
    return if (count == 0) 1f else sum / count
}

@Composable
fun ThemeAssetImage(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val drawable = remember(path) { loadThemeDrawable(context, path) }
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = contentScale.toImageViewScaleType()
                this.contentDescription = contentDescription
                isClickable = false
                isFocusable = false
            }
        },
        update = { view ->
            view.scaleType = contentScale.toImageViewScaleType()
            if (drawable != null) {
                view.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) {
                    runCatching { drawable.start() }
                }
            }
        },
        modifier = modifier,
    )
}

private fun ContentScale.toImageViewScaleType(): ImageView.ScaleType = when (this) {
    ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
    ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
    else -> ImageView.ScaleType.FIT_CENTER
}

private fun loadThemeDrawable(context: Context, path: String): Drawable? {
    if (path.isBlank()) return null
    val file = java.io.File(path)
    if (!file.isFile) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
        }.getOrNull()?.let { return it }
    }
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    return BitmapDrawable(context.resources, bitmap)
}

@Composable
fun ThemeMascotOverlay(theme: CustomTheme, edit: ThemeEditState) {
    if (!theme.enabled || !theme.mascotVisible) return
    val colors = LocalClaudenColors.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        var liveSize by remember(theme.mascotSize) { mutableFloatStateOf(theme.mascotSize) }
        var fx by remember(theme.mascotX) { mutableFloatStateOf(theme.mascotX) }
        var fy by remember(theme.mascotY) { mutableFloatStateOf(theme.mascotY) }
        val size = liveSize.dp
        val maxX = if (maxWidth > size) maxWidth - size else 0.dp
        val maxY = if (maxHeight > size) maxHeight - size else 0.dp
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .offset(x = maxX * fx, y = maxY * fy)
                .size(size)
                .then(
                    if (edit.editing) {
                        Modifier.pointerInput(edit.editing, maxWidth, maxHeight) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val dx = with(density) {
                                        (maxWidth - liveSize.dp).coerceAtLeast(0.dp).toPx()
                                    }
                                    val dy = with(density) {
                                        (maxHeight - liveSize.dp).coerceAtLeast(0.dp).toPx()
                                    }
                                    if (dx > 1f) {
                                        fx = (fx + pan.x / dx).coerceIn(0f, 1f)
                                    }
                                    if (dy > 1f) {
                                        fy = (fy + pan.y / dy).coerceIn(0f, 1f)
                                    }
                                    if (zoom != 1f) {
                                        liveSize = (liveSize * zoom).coerceIn(48f, 360f)
                                    }
                                    event.changes.forEach { change -> change.consume() }
                                } while (event.changes.any { it.pressed })
                                ThemeController.setMascotSize(liveSize)
                                ThemeController.setMascotPosition(fx, fy)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (theme.mascotPath.isNotBlank()) {
                ThemeAssetImage(
                    path = theme.mascotPath,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = "Маскот темы",
                    contentScale = ContentScale.Fit,
                )
            } else {
                ClaudenLogo(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    tint = colors.clay,
                    variant = ClaudenLogoVariant.Jelly,
                )
            }
        }
    }
}

@Composable
fun ThemePencilOverlay() {
    val state by ThemeController.state.collectAsStateWithLifecycle()
    if (!state.editing) return
    val colors = LocalClaudenColors.current
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pencil = 56.dp
        val maxX = if (maxWidth > pencil) maxWidth - pencil else 0.dp
        val maxY = if (maxHeight > pencil) maxHeight - pencil else 0.dp
        val density = LocalDensity.current
        var fx by remember(state.pencilX) { mutableFloatStateOf(state.pencilX) }
        var fy by remember(state.pencilY) { mutableFloatStateOf(state.pencilY) }

        Box(
            modifier = Modifier
                .offset(x = maxX * fx, y = maxY * fy)
                .size(pencil)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(scheme.primary)
                .clickable { menuOpen = true }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { ThemeController.setPencilPosition(fx, fy) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { maxX.toPx() }
                            val dy = with(density) { maxY.toPx() }
                            if (dx > 1f) fx = (fx + dragAmount.x / dx).coerceIn(0f, 1f)
                            if (dy > 1f) fy = (fy + dragAmount.y / dy).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Редактор темы",
                tint = scheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                offset = DpOffset(0.dp, 8.dp),
                containerColor = scheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text("Выбрать") },
                    leadingIcon = { Icon(ClaudenIcons.spark, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        ThemeController.setSelectionMode(true)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Переместить") },
                    leadingIcon = { Icon(ClaudenIcons.updown, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        ThemeController.setMoveMode(true)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(ClaudenIcons.trash, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        ThemeController.deletePicked()
                    },
                )
                HorizontalDivider(color = colors.border)
                DropdownMenuItem(
                    text = { Text("Готово") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        ThemeController.setEditing(false)
                    },
                )
            }
        }

        if (state.selectionMode) {
            Surface(
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .padding(horizontal = 14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Нажмите на кнопку, блок или строку",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { ThemeController.setSelectionMode(false) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else if (state.moveMode) {
            Surface(
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .padding(horizontal = 14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Тащите маскота, второй палец — размер",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { ThemeController.setMoveMode(false) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Готово", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

val themePresetColors = themeColorPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeElementEditorSheet() {
    val state by ThemeController.state.collectAsStateWithLifecycle()
    if (state.selectedId.isBlank()) return
    val id = state.selectedId
    val theme = LocalCustomTheme.current
    val colors = LocalClaudenColors.current
    val style = theme.elements[id] ?: ThemeElementStyle()
    val iconPath = theme.icons[id].orEmpty()
    var hex by remember(id) { mutableStateOf("") }
    var colorTarget by remember(id) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            ThemeAssets.import(context, uri, "icon-$id")?.let { path ->
                ThemeController.setElementIcon(id, path)
            }
        }
    }

    fun update(transform: (ThemeElementStyle) -> ThemeElementStyle) {
        ThemeController.setElementStyle(id, transform(style))
    }

    colorTarget?.let { target ->
        val initial = when (target) {
            "background" -> style.background
            "content" -> style.content
            else -> style.border
        } ?: 0xFFD97757.toInt()
        ThemeColorPickerDialog(
            initial = initial,
            title = when (target) {
                "background" -> "Цвет фона"
                "content" -> "Цвет текста и иконки"
                else -> "Цвет рамки"
            },
            onDismiss = { colorTarget = null },
            onPick = { argb ->
                when (target) {
                    "background" -> update { it.copy(background = argb) }
                    "content" -> update { it.copy(content = argb) }
                    else -> update { it.copy(border = argb) }
                }
                colorTarget = null
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = { ThemeController.clearSelection() },
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = state.selectedLabel.ifBlank { "Элемент" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = id,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            ThemeElementFrame(
                style = style,
                iconPath = iconPath.takeIf { it.isNotBlank() },
                fallback = colors.bg300,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Так будет выглядеть элемент",
                    style = MaterialTheme.typography.bodyLarge,
                    color = style.content?.let(::Color) ?: colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            ColorSwatchRow(
                label = "Фон",
                selected = style.background,
                onPick = { picked -> update { it.copy(background = picked) } },
                onOpenPicker = { colorTarget = "background" },
            )
            Spacer(Modifier.height(12.dp))
            ColorSwatchRow(
                label = "Текст и иконка",
                selected = style.content,
                onPick = { picked -> update { it.copy(content = picked) } },
                onOpenPicker = { colorTarget = "content" },
            )
            Spacer(Modifier.height(12.dp))
            ColorSwatchRow(
                label = "Рамка",
                selected = style.border,
                onPick = { picked -> update { it.copy(border = picked) } },
                onOpenPicker = { colorTarget = "border" },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Свой HEX-цвет фона",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            SettingsField(
                value = hex,
                onValueChange = { value ->
                    hex = value
                    val parsed = parseHexColor(value)
                    if (parsed != null) update { it.copy(background = parsed) }
                },
                placeholder = "#D97757",
                height = 52.dp,
                textSize = 15,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Своя иконка / картинка",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (iconPath.isBlank()) {
                    "Можно выбрать PNG, GIF или WebP."
                } else {
                    "Картинка выбрана. Выберите, как её вписать."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (iconPath.isNotBlank()) {
                    ThemeAssetImage(
                        path = iconPath,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.weight(1f))
                if (iconPath.isNotBlank()) {
                    IconButton(onClick = { ThemeController.setElementIcon(id, "") }) {
                        Icon(ClaudenIcons.trash, contentDescription = "Убрать иконку", tint = colors.danger)
                    }
                }
                OutlinedButton(
                    onClick = { iconPicker.launch(arrayOf("image/*")) },
                    shape = CircleShape,
                ) {
                    Text(if (iconPath.isBlank()) "Выбрать" else "Заменить")
                }
            }
            if (iconPath.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        ThemeIconMode.Inline to "Иконка",
                        ThemeIconMode.Fill to "Заполнить",
                        ThemeIconMode.Background to "На всю кнопку",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = style.iconMode == mode,
                            onClick = { update { it.copy(iconMode = mode) } },
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
                Text(
                    text = "«На всю кнопку» растягивает картинку на весь фон кнопки. " +
                        "«Заполнить» — растягивает внутри области иконки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { ThemeController.clearElement(id) },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Сбросить")
                }
                Button(
                    onClick = { ThemeController.clearSelection() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.clay,
                        contentColor = colors.onClay,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Готово")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColorSwatchRow(
    label: String,
    selected: Int?,
    onPick: (Int?) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            ColorPickerButton(onClick = onOpenPicker)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.bg300)
                    .border(1.dp, colors.borderStrong, CircleShape)
                    .clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "По умолчанию",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            themePresetColors.forEach { argb ->
                val color = Color(argb)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected == argb) 3.dp else 1.dp,
                            color = if (selected == argb) colors.clay else colors.borderStrong,
                            shape = CircleShape,
                        )
                        .clickable { onPick(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == argb) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = contrastOn(color),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun parseHexColor(raw: String): Int? {
    val value = raw.trim().removePrefix("#")
    if (value.length != 6 && value.length != 8) return null
    return runCatching {
        android.graphics.Color.parseColor("#$value")
    }.getOrNull()
}

@Composable
fun ThemeGenerationIndicator(
    animation: TextAnimation,
    color: Color,
    modifier: Modifier = Modifier,
    customPath: String = "",
) {
    if (customPath.isNotBlank()) {
        ThemeAssetImage(
            path = customPath,
            modifier = modifier.size(24.dp),
            contentDescription = "Анимация загрузки",
            contentScale = ContentScale.Fit,
        )
        return
    }
    if (animation == TextAnimation.None) return
    val transition = rememberInfiniteTransition(label = "theme-generation")
    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            when (animation) {
                TextAnimation.Dots -> {
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(560),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 130),
                        ),
                        label = "dot-alpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = alpha)),
                    )
                }

                TextAnimation.Pulse -> {
                    val scale by transition.animateFloat(
                        initialValue = 0.65f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(620),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dot-scale",
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .clip(CircleShape)
                            .background(color),
                    )
                }

                TextAnimation.Wave -> {
                    val shift by transition.animateFloat(
                        initialValue = 2.5f,
                        targetValue = -2.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(520),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 110),
                        ),
                        label = "dot-wave",
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .offset(y = shift.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }

                TextAnimation.None -> Unit
            }
        }
    }
}

@Composable
fun ThemeThinkingLogo(
    animation: ThinkingAnimation,
    enabled: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    customPath: String = "",
) {
    if (enabled && customPath.isNotBlank()) {
        ThemeAssetImage(
            path = customPath,
            modifier = modifier,
            contentDescription = "Анимация размышления",
            contentScale = ContentScale.Fit,
        )
        return
    }
    if (!enabled) {
        ClaudenLogo(modifier = modifier, tint = tint, variant = ClaudenLogoVariant.Spin)
        return
    }
    if (animation == ThinkingAnimation.Spin) {
        ClaudenLogo(modifier = modifier, tint = tint, variant = ClaudenLogoVariant.SpinTick)
        return
    }
    val transition = rememberInfiniteTransition(label = "theme-thinking")
    val scale by transition.animateFloat(
        initialValue = if (animation == ThinkingAnimation.Pulse) 0.85f else 1f,
        targetValue = if (animation == ThinkingAnimation.Pulse) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-scale",
    )
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animation == ThinkingAnimation.Bounce) -4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(380),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-shift",
    )
    ClaudenLogo(
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationY = shift,
        ),
        tint = tint,
        variant = ClaudenLogoVariant.Static,
        animated = false,
    )
}
