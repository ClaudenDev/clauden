package space.teleopen.clauden.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import space.teleopen.clauden.data.ChatSource
import java.net.URI
import java.util.concurrent.TimeUnit

private fun domainOf(url: String): String = runCatching {
    URI(url).host.orEmpty().removePrefix("www.")
}.getOrDefault("")

private fun sourceTitle(source: ChatSource): String =
    source.title.ifBlank { domainOf(source.url).ifBlank { source.url } }

private fun sourceCount(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "$count источников"
        mod10 == 1 -> "$count источник"
        mod10 in 2..4 -> "$count источника"
        else -> "$count источников"
    }
}

@Composable
fun SourcesRow(sources: List<ChatSource>, onOpen: () -> Unit) {
    if (sources.isEmpty()) return
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown = sources.take(3)
        Box(
            modifier = Modifier
                .width((20 * shown.size + 9).dp)
                .height(24.dp),
        ) {
            shown.forEachIndexed { index, source ->
                Box(
                    modifier = Modifier
                        .padding(start = (index * 15).dp)
                        .zIndex((shown.size - index).toFloat()),
                ) {
                    SourceAvatar(source = source, size = 24, ring = true)
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = sourceCount(sources.size),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SourceAvatar(source: ChatSource, size: Int, ring: Boolean = false) {
    val colors = LocalClaudenColors.current
    val domain = domainOf(source.url)
    var bitmap by remember(domain) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(domain) {
        bitmap = if (domain.isBlank()) null else SourceIcons.load(domain)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(
                if (ring) {
                    Modifier
                        .background(colors.bg, CircleShape)
                        .border(2.dp, colors.bg, CircleShape)
                } else {
                    Modifier
                }
            )
            .background(colors.control, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = domain.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (size * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(sources: List<ChatSource>, onDismiss: () -> Unit) {
    val colors = LocalClaudenColors.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unique = remember(sources) { sources.distinctBy { it.url } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(44.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Источники",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            HorizontalDivider(color = colors.border)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(unique, key = { it.url }) { source ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (source.url.isNotBlank()) {
                                    runCatching { uriHandler.openUri(source.url) }
                                }
                            }
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = sourceTitle(source),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                            ),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceAvatar(source = source, size = 23)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = domainOf(source.url),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider(color = colors.border)
                    }
                }
            }
        }
    }
}

private object SourceIcons {
    private val cache = LruCache<String, Bitmap>(64)
    private val client = OkHttpClient.Builder()
        .proxySelector(space.teleopen.clauden.proxy.ProxyController.selector)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun load(domain: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(domain)?.let { return@withContext it }
        val urls = listOf(
            "https://icons.duckduckgo.com/ip3/$domain.ico",
            "https://www.google.com/s2/favicons?domain=$domain&sz=64",
        )
        for (url in urls) {
            val bitmap = runCatching {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
            if (bitmap != null) {
                cache.put(domain, bitmap)
                return@withContext bitmap
            }
        }
        null
    }
}
