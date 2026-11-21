package space.teleopen.clauden.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import space.teleopen.clauden.data.ClaudeApi
import space.teleopen.clauden.data.cookieValue
import space.teleopen.clauden.proxy.ProxyController
import java.util.Locale
import kotlin.coroutines.resume

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ClaudeLoginScreen(
    busy: Boolean,
    error: String?,
    onCaptured: (sessionKey: String, cfClearance: String, userAgent: String) -> Unit,
    onBack: () -> Unit,
    onClauden: () -> Unit,
    linkPasswordFor: String = "",
    linkPasswordError: String? = null,
    onLinkPassword: (String) -> Unit = {},
    onCancelLinkPassword: () -> Unit = {},
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var blocked by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var captureSent by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    val webView = remember {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)

        manager.setCookie("https://claude.ai", "sessionKey=; Max-Age=0; path=/")
        manager.flush()

        WebView(context).apply {
            setBackgroundColor(0xFF131313.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true

            settings.userAgentString = realisticUserAgent(context)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progress = 0
                    blocked = false
                    failure = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progress = 100
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {

                    if (request?.isForMainFrame != true) return
                    failure = "Не удалось открыть claude.ai: " +
                        (error?.description?.toString()?.takeIf { it.isNotBlank() } ?: "нет связи") +
                        ". Включите Private DNS в настройках Android " +
                        "или прокси в приложении — потом попробуйте снова."
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame != true) return
                    val code = errorResponse?.statusCode ?: 0
                    if (code == 403 || code == 429) {
                        blocked = true
                    } else if (code >= 500) {
                        failure = "Claude ответил ошибкой $code. Обычно помогает повторить позже."
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {

        ProxyController.attach(context)
        ProxyController.probeClaude()
    }

    LaunchedEffect(reloadKey) {
        captureSent = false
        blocked = false
        failure = null
        progress = 0
        webView.loadUrl("https://claude.ai/login")

        delay(20_000)
        val page = webView.pageDiagnostics()
        val text = page?.second.orEmpty()
        val lower = text.lowercase(Locale.US)
        val challenge = lower.contains("just a moment") ||
            lower.contains("verifying you are human") ||
            lower.contains("checking your browser") ||
            lower.contains("enable javascript")
        failure = when {
            page == null -> null
            challenge -> "Cloudflare не пропустил это устройство. " +
                "Попробуйте другую сеть или VPN (США, Великобритания, Германия)."
            text.trim().length < 20 -> "Claude вернул пустую страницу. " +
                "Обычно помогает сменить VPN или сеть, затем повторить."
            else -> null
        }
    }

    LaunchedEffect(webView, busy, error) {
        if (busy) return@LaunchedEffect
        while (!captureSent) {
            val url = webView.url.orEmpty()
            val onLogin = url.contains("/login", ignoreCase = true) ||
                url.contains("/signup", ignoreCase = true) ||
                url.contains("/onboarding", ignoreCase = true) ||
                url.contains("/magic-link", ignoreCase = true)
            if (onLogin) {
                delay(800)
                continue
            }
            val cookies = CookieManager.getInstance().getCookie("https://claude.ai")
            val key = cookieValue(cookies, "sessionKey")
            if (!key.isNullOrBlank()) {
                captureSent = true
                val clearance = cookieValue(cookies, "cf_clearance").orEmpty()
                onCaptured(key, clearance, webView.settings.userAgentString)
                return@LaunchedEffect
            }
            delay(800)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        ClaudenIcons.arrowBack,
                        contentDescription = "Назад",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "Вход в Claude",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "В Clauden",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.clay,
                    modifier = Modifier
                        .clickable(onClick = onClauden)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            if (progress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(colors.border),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(2.dp)
                            .background(colors.clay),
                    )
                }
            }

            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                ProxyFixBanner()
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )

                if (busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.bg.copy(alpha = 0.82f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = colors.clay,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Подключаем аккаунт Claude…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                val problemText = failure ?: if (blocked) {
                    "Anthropic закрывает доступ из некоторых стран и с VPN-адресов. " +
                        "Смените страну VPN (США, Великобритания, Германия) " +
                        "и нажмите «Попробовать снова»."
                } else {
                    null
                }

                if (problemText != null && !busy) {
                    Surface(
                        color = colors.bg300,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (blocked) {
                                    "Claude недоступен из вашей сети"
                                } else {
                                    "Claude не открылся"
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = problemText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = colors.textPrimary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { reloadKey += 1 },
                                    ) {
                                        Text(
                                            text = "Попробовать снова",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = colors.bg,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clickable(onClick = onClauden)
                                        .padding(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = "В Clauden",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = colors.clay,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (error != null && !busy) {
                Surface(
                    color = colors.danger.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = colors.dangerSoft,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Попробовать снова",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.clay,
                                modifier = Modifier.clickable {
                                    reloadKey += 1
                                },
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                            Text(
                                text = "Войти в Clauden",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.clay,
                                modifier = Modifier.clickable(onClick = onClauden),
                            )
                        }
                    }
                }
            }

            Text(
                text = "Claude может быть недоступен из некоторых сетей. " +
                    "Если вход через Google не открывается, войдите по email. " +
                    "Пароль и cookie остаются на этом устройстве.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        if (linkPasswordFor.isNotBlank()) {
            LinkPasswordDialog(
                email = linkPasswordFor,
                error = linkPasswordError,
                busy = busy,
                onConfirm = onLinkPassword,
                onCancel = onCancelLinkPassword,
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }
}

private fun realisticUserAgent(context: android.content.Context): String {
    val raw = runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) }
        .getOrNull()
        .orEmpty()
    if (!raw.contains("Chrome/")) return ClaudeApi.DEFAULT_UA
    return raw
        .replace("; wv", "")
        .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        .trim()
}

private suspend fun WebView.pageDiagnostics(): Pair<String, String>? =
    suspendCancellableCoroutine { cont ->
        evaluateJavascript(
            "JSON.stringify({t:document.title||''," +
                "b:((document.body&&document.body.innerText)||'').slice(0,600)})",
        ) { raw ->
            val json = runCatching {
                org.json.JSONTokener(raw).nextValue() as? String
            }.getOrNull()
            val obj = json?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            cont.resume(
                obj?.let { it.optString("t") to it.optString("b") },
            )
        }
    }

@Composable
private fun LinkPasswordDialog(
    email: String,
    error: String?,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    var value by remember(email) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.bg300,
        title = {
            Text(
                text = "Привязать Claude",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "Email $email уже зарегистрирован в Clauden. " +
                        "Введите пароль — Claude привяжется к этому же аккаунту, " +
                        "чаты и тариф останутся на месте.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = {
                        Text(
                            text = "Пароль Clauden",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = colors.textTertiary,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.borderStrong,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = colors.clay,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = colors.dangerSoft,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Не помните пароль? Закройте окно, войдите по коду из письма " +
                        "и повторите вход в Claude.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    color = colors.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = !busy && value.isNotBlank(),
            ) {
                Text("Привязать", color = colors.clay)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Отмена", color = colors.textSecondary)
            }
        },
    )
}
