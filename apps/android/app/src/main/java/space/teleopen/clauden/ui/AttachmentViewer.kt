package space.teleopen.clauden.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.teleopen.clauden.data.Attachment
import java.io.File

private const val MAX_VIEW_TEXT = 250_000

@Composable
fun AttachmentViewer(attachment: Attachment, onDismiss: () -> Unit) {
    val colors = LocalClaudenColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(attachment.id) { decodeAttachmentBitmap(attachment) }
    val text = remember(attachment.id) { attachmentText(attachment) }
    var saving by remember(attachment.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = colors.bg, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    AttachmentViewerHeader(
                        title = attachment.name,
                        showCopy = text != null,
                        onClose = onDismiss,
                        onCopy = {
                            if (text != null) {
                                clipboard.setText(AnnotatedString(text))
                            }
                        },
                        onShare = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { shareAttachment(context, attachment) }
                                        .getOrDefault(false)
                                }
                                if (!ok) notify(context, "Не удалось поделиться файлом")
                            }
                        },
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            bitmap != null -> Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = attachment.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            text != null -> AttachmentTextContent(text)
                            else -> AttachmentNoPreview(attachment)
                        }
                    }

                }

                DownloadPill(
                    label = if (saving) "Сохранение…" else "Скачать",
                    enabled = !saving,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 72.dp),
                    onClick = {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { saveAttachment(context, attachment) }
                                        .getOrDefault(false)
                                }
                                saving = false
                                notify(
                                    context,
                                    if (ok) "Сохранено в Загрузки"
                                    else "Не удалось сохранить файл",
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AttachmentViewerHeader(
    title: String,
    showCopy: Boolean,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart).size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = colors.textPrimary,
                modifier = Modifier.size(25.dp),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCopy) {
                IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = ClaudenIcons.copy,
                        contentDescription = "Копировать",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = ClaudenIcons.share,
                    contentDescription = "Поделиться",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 104.dp),
        )
    }
}

@Composable
private fun AttachmentTextContent(text: String) {
    val colors = LocalClaudenColors.current
    val lines = remember(text) {
        val shown = if (text.length > MAX_VIEW_TEXT) {
            text.take(MAX_VIEW_TEXT) + "\n\n… Файл показан не полностью."
        } else {
            text
        }
        shown.split('\n').map { if (it.isEmpty()) " " else it }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(count = lines.size) { index ->
            Text(
                text = lines[index],
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = LocalClaudenFonts.current.sans,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                ),
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun AttachmentNoPreview(attachment: Attachment) {
    val colors = LocalClaudenColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.bg300, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ClaudenIcons.doc,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = extensionLabelFor(attachment.name),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = humanAttachmentSize(attachmentSizeBytes(attachment)),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Предпросмотр недоступен. Скачайте файл, чтобы открыть его.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DownloadPill(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Surface(
        color = colors.control,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, colors.borderStrong),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ClaudenIcons.download,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textPrimary,
            )
        }
    }
}

private fun attachmentText(attachment: Attachment): String? {
    val mime = attachment.mime.lowercase()
    val ext = attachment.name.substringAfterLast('.', "").lowercase()
    if (mime.startsWith("image/")) return null
    if (ext in BINARY_EXTENSIONS) return null
    val known = mime.startsWith("text/") ||
        mime in TEXT_MIME ||
        ext in TEXT_EXTENSIONS
    val bytes = runCatching { Base64.decode(attachment.base64, Base64.DEFAULT) }.getOrNull()
        ?: return null
    if (bytes.isEmpty()) return ""
    val probe = bytes.copyOf(minOf(bytes.size, 8_192))
    if (probe.any { it == 0.toByte() }) return null
    val decoded = String(bytes, Charsets.UTF_8)
    if (!known) {
        val broken = decoded.count { it == '\uFFFD' }
        if (broken > decoded.length / 100 + 1) return null
        val control = decoded.count { it < ' ' && it != '\n' && it != '\r' && it != '\t' }
        if (control > decoded.length / 50 + 1) return null
    }
    return decoded
}

private val TEXT_MIME = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-javascript",
    "application/x-sh",
    "application/sql",
    "application/x-yaml",
    "application/yaml",
)

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "mdx", "json", "jsonl", "csv", "tsv", "xml", "html", "htm",
    "css", "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "go",
    "rs", "php", "swift", "c", "h", "cpp", "cc", "hpp", "cs", "sh", "bash", "zsh", "sql",
    "yaml", "yml", "toml", "ini", "cfg", "conf", "env", "log", "srt", "vtt", "gradle",
    "properties", "gitignore", "dockerfile", "makefile", "tex", "rst", "vue", "svelte",
)

private val BINARY_EXTENSIONS = setOf(
    "pdf", "zip", "rar", "7z", "gz", "tar", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "apk", "jar", "mp3", "wav", "ogg", "m4a", "mp4", "mov", "webm", "mkv", "avi",
    "heic", "dng", "psd", "ttf", "otf", "woff", "woff2", "exe", "dmg", "iso",
)

private fun extensionLabelFor(name: String): String {
    val ext = name.substringAfterLast('.', "").uppercase()
    return if (ext.isBlank()) "ФАЙЛ" else ext.take(5)
}

private fun humanAttachmentSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", bytes / 1048576.0)
    bytes >= 1024 -> "${bytes / 1024} КБ"
    else -> "$bytes Б"
}

private fun safeFileName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_")
        .trim()
        .take(120)
    return base.ifBlank { "file" }
}

private fun attachmentMime(attachment: Attachment): String {
    val mime = attachment.mime.trim()
    if (mime.isNotBlank() && mime != "application/octet-stream") return mime
    return when (attachment.name.substringAfterLast('.', "").lowercase()) {
        "txt" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "zip" -> "application/zip"
        else -> mime.ifBlank { "application/octet-stream" }
    }
}

private fun attachmentBytes(attachment: Attachment): ByteArray =
    Base64.decode(attachment.base64, Base64.DEFAULT)

private fun saveAttachment(context: Context, attachment: Attachment): Boolean {
    val bytes = attachmentBytes(attachment)
    val name = safeFileName(attachment.name)
    val mime = attachmentMime(attachment)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }
    val file = cacheFile(context, name, bytes) ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Сохранить файл"))
    return true
}

private fun shareAttachment(context: Context, attachment: Attachment): Boolean {
    val name = safeFileName(attachment.name)
    val file = cacheFile(context, name, attachmentBytes(attachment)) ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = attachmentMime(attachment)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться файлом"))
    return true
}

private fun cacheFile(context: Context, name: String, bytes: ByteArray): File? =
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, name).apply { writeBytes(bytes) }
    }.getOrNull()

private fun notify(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
