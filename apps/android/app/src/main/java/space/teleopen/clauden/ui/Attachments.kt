package space.teleopen.clauden.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.Attachment
import space.teleopen.clauden.data.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    projects: List<Project>,
    selectedProjectId: String,
    webSearch: Boolean,
    onWebSearchChange: (Boolean) -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onProject: (String) -> Unit,
    onConnectors: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var projectPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.drawerBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "Добавление в чат",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachTile(
                    label = "Камера",
                    icon = ClaudenIcons.camera,
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                )
                AttachTile(
                    label = "Фото",
                    icon = ClaudenIcons.image,
                    onClick = onPhotos,
                    modifier = Modifier.weight(1f),
                )
                AttachTile(
                    label = "Файлы",
                    icon = ClaudenIcons.doc,
                    onClick = onFiles,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SheetCard {
                SheetRow(
                    icon = ClaudenIcons.projects,
                    title = "Добавить в проект",
                    subtitle = projects.firstOrNull { it.id == selectedProjectId }?.title ?: "Нет",
                    onClick = { projectPicker = true },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SheetCard {
                SheetRow(
                    icon = ClaudenIcons.globe,
                    title = "Веб-поиск",
                    trailing = {
                        SettingsToggle(
                            checked = webSearch,
                            onCheckedChange = onWebSearchChange,
                        )
                    },
                )
                HorizontalDivider(color = colors.border, modifier = Modifier.padding(horizontal = 14.dp))
                SheetRow(
                    icon = ClaudenIcons.grid,
                    title = "Коннекторы",
                    onClick = onConnectors,
                )
            }
        }
    }

    if (projectPicker) {
        AlertDialog(
            onDismissRequest = { projectPicker = false },
            containerColor = colors.bg,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Добавить в проект", color = colors.textPrimary) },
            text = {
                Column {
                    ProjectOption(
                        title = "Без проекта",
                        selected = selectedProjectId.isBlank(),
                        onClick = {
                            onProject("")
                            projectPicker = false
                        },
                    )
                    projects.forEach { project ->
                        ProjectOption(
                            title = project.title,
                            selected = selectedProjectId == project.id,
                            onClick = {
                                onProject(project.id)
                                projectPicker = false
                            },
                        )
                    }
                    if (projects.isEmpty()) {
                        Text(
                            text = "Проектов пока нет — создайте их в разделе «Проекты».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { projectPicker = false }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun AttachTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(106.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(colors.control, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.bg300,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 68.dp else 58.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.control, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = ClaudenIcons.chevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ProjectOption(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.clay,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

fun attachmentSizeBytes(attachment: Attachment): Long =
    (attachment.base64.length.toLong() * 3L) / 4L

fun mergeAttachments(
    current: List<Attachment>,
    added: List<Attachment>,
    maxCount: Int = 12,
    maxBytes: Long = 14_000_000L,
): List<Attachment> {
    val out = current.toMutableList()
    var total = out.sumOf { attachmentSizeBytes(it) }
    for (attachment in added) {
        if (out.size >= maxCount) break
        val size = attachmentSizeBytes(attachment)
        if (total + size > maxBytes) break
        out += attachment
        total += size
    }
    return out
}

fun cameraOutputUri(context: Context): Uri {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

@Composable
fun AttachmentFileCard(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val colors = LocalClaudenColors.current
    val bitmap = remember(attachment.id) { decodeAttachmentBitmap(attachment) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(width = 122.dp, height = 122.dp)
            .clip(shape)
            .background(colors.bg300)
            .border(1.dp, colors.borderStrong, shape)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = extensionLabel(attachment.name),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                text = fileDisplayName(attachment.name),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Убрать вложение",
                    tint = colors.bg,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
fun AttachmentPreviewCard(
    attachment: Attachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    AttachmentFileCard(
        attachment = attachment,
        modifier = modifier,
        onRemove = onRemove,
        onOpen = onOpen,
    )
}

private fun extensionLabel(name: String): String {
    val ext = name.substringAfterLast('.', "").uppercase()
    return if (ext.isBlank()) "FILE" else ext.take(4)
}

private fun fileDisplayName(name: String): String {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.length - 1) return name
    return name.substring(0, dot).ifBlank { name }
}

internal fun decodeAttachmentBitmap(attachment: Attachment): ImageBitmap? {
    if (!attachment.mime.startsWith("image/")) return null
    return runCatching {
        val bytes = Base64.decode(attachment.base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

fun makeImageAttachment(context: Context, uri: Uri, name: String = "Изображение"): Attachment? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull() ?: return null

    val longest = maxOf(decoded.width, decoded.height).toFloat()
    val scale = if (longest > 1600f) 1600f / longest else 1f
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }

    val output = ByteArrayOutputStream()
    val compressed = runCatching {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
    }.getOrDefault(false)
    if (bitmap !== decoded) decoded.recycle()
    bitmap.recycle()
    if (!compressed) return null

    val bytes = output.toByteArray()
    if (bytes.isEmpty() || bytes.size > 4_000_000) return null
    return Attachment(
        id = UUID.randomUUID().toString(),
        name = name,
        mime = "image/jpeg",
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

fun makeFileAttachment(context: Context, uri: Uri): Attachment? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Файл"
    var size = -1L
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
    }
    if (size > 8_000_000L) return null

    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.size > 8_000_000) return null

    return Attachment(
        id = UUID.randomUUID().toString(),
        name = name,
        mime = mime,
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}
