package space.teleopen.clauden.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConnectorOAuthScreen(
    title: String,
    authUrl: String,
    redirectUri: String,
    busy: Boolean,
    error: String,
    onCallback: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val callbackState = rememberUpdatedState(onCallback)
    val redirectState = rememberUpdatedState(redirectUri)
    var callbackSent by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onClose)

    val webView = remember {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                private fun isCallback(url: String): Boolean {
                    val target = redirectState.value
                    if (target.isNotBlank() && url.startsWith(target)) return true
                    return (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) &&
                        url.contains("/callback")
                }

                private fun intercept(url: String): Boolean {
                    if (!isCallback(url)) return false
                    if (!callbackSent) {
                        callbackSent = true
                        callbackState.value(url)
                    }
                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString().orEmpty()
                    if (intercept(url)) return true
                    if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
                        return true
                    }
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val value = url.orEmpty()
                    if (intercept(value)) return true
                    return value.isNotBlank() &&
                        !value.startsWith("http://") &&
                        !value.startsWith("https://")
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val value = url.orEmpty()
                    if (intercept(value)) {
                        view?.stopLoading()
                        return
                    }
                    progress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progress = 100
                }
            }
        }
    }

    LaunchedEffect(authUrl) {
        if (authUrl.isNotBlank()) webView.loadUrl(authUrl)
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = colors.bg, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вход · $title",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Авторизация в сервисе коннектора",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.textTertiary,
                        )
                    }
                    if (progress in 1..99 && authUrl.isNotBlank()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.clay,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (authUrl.isNotBlank()) {
                        AndroidView(
                            factory = { webView },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.clay)
                        }
                    }

                    if (busy) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                color = colors.bg,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.clay,
                                    )
                                    Text(
                                        text = "Завершаю вход…",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                        color = colors.textPrimary,
                                    )
                                }
                            }
                        }
                    }

                    if (error.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                color = colors.bg,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "Не удалось войти",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                                        color = colors.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                        ),
                                        color = colors.textTertiary,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SettingsActionButton(text = "Закрыть", onClick = onClose)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
