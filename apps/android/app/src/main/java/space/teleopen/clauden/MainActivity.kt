package space.teleopen.clauden

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import space.teleopen.clauden.data.CustomTheme
import space.teleopen.clauden.data.ThemeController
import space.teleopen.clauden.data.ThemeMode
import space.teleopen.clauden.data.ThemeStyle
import space.teleopen.clauden.proxy.ProxyController
import space.teleopen.clauden.proxy.ProxyUi
import space.teleopen.clauden.ui.AuthFlow
import space.teleopen.clauden.ui.ClaudenApp
import space.teleopen.clauden.ui.ClaudenTheme
import space.teleopen.clauden.ui.GlassBackdrop
import space.teleopen.clauden.ui.LocalClaudenColors
import space.teleopen.clauden.ui.LocalHazeState
import space.teleopen.clauden.ui.LocalThemeEditState
import space.teleopen.clauden.ui.PrivateDnsGuideScreen
import space.teleopen.clauden.ui.ProxyScreen
import space.teleopen.clauden.ui.ProxyShieldButton
import space.teleopen.clauden.ui.ThemeEditState
import space.teleopen.clauden.ui.ThemeElementEditorSheet
import space.teleopen.clauden.ui.ThemeMascotOverlay
import space.teleopen.clauden.ui.ThemePencilOverlay
import space.teleopen.clauden.ui.ThemeWallpaperBackground
import space.teleopen.clauden.ui.UpdateSheet

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeController.attach(this)
        space.teleopen.clauden.alarm.ClaudenReplyNotifications.ensureChannels(this)
        handleAuthIntent(intent)
        handleChatIntent(intent)
        handleRemoteIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("clauden", MODE_PRIVATE)
            if (!prefs.getBoolean("notifications_requested", false)) {
                prefs.edit().putBoolean("notifications_requested", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            val proxyState = ProxyController.state.collectAsStateWithLifecycle().value
            val themeState = ThemeController.state.collectAsStateWithLifecycle().value
            val activeTheme = if (themeState.active && themeState.exists) {
                themeState.theme.copy(enabled = true)
            } else {
                CustomTheme()
            }
            val editState = ThemeEditState(
                editing = themeState.editing,
                selectionMode = themeState.selectionMode,
                moveMode = themeState.moveMode,
                selectedId = themeState.selectedId,
                selectedLabel = themeState.selectedLabel,
            )
            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                viewModel.onAppResumed()
                ProxyController.refresh()
            }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppBackgrounded() }
            LaunchedEffect(Unit) {
                ProxyController.attach(this@MainActivity)
                ProxyController.autoConnect(this@MainActivity)
            }
            val systemDark = isSystemInDarkTheme()
            val dark = when (state.settings.themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }
            val hazeState = remember { HazeState() }
            val glassStyle = state.settings.themeStyle == ThemeStyle.Glass
            CompositionLocalProvider(
                LocalThemeEditState provides editState,
                LocalHazeState provides hazeState,
            ) {
            ClaudenTheme(
                darkTheme = dark,
                uiStyle = state.settings.uiStyle,
                fontStyle = state.settings.fontStyle,
                customTheme = activeTheme,
                themeStyle = state.settings.themeStyle,
                accentOverride = state.settings.accentOverride,
                cornerRadius = state.settings.cornerRadius,
                glassLevel = state.settings.glassLevel,
                buttonStyle = state.settings.buttonStyle,
                toggleStyle = state.settings.toggleStyle,
                textScale = state.settings.textScale,
                uiDensity = state.settings.uiDensity,
                motionStyle = state.settings.motionStyle,
            ) {
                val colors = LocalClaudenColors.current
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (glassStyle) Modifier.haze(hazeState) else Modifier),
                    ) {
                        ThemeWallpaperBackground(activeTheme)
                        if (glassStyle && !activeTheme.hasWallpaper) {
                            GlassBackdrop(colors)
                        }
                    }
                    val signedIn = state.auth.signedIn && state.auth.onboarded
                    if (signedIn) {
                        ClaudenApp(state = state, vm = viewModel)
                    } else {
                        AuthFlow(state = state, vm = viewModel)
                    }
                    UpdateSheet(
                        update = state.update,
                        installedVersion = BuildConfig.VERSION_NAME,
                        onStart = viewModel::startUpdate,
                        onDismiss = viewModel::dismissUpdate,
                    )
                    if (!signedIn) {
                        ProxyShieldButton(
                            state = proxyState,
                            onClick = { ProxyUi.open = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 10.dp, end = 12.dp),
                        )
                    }
                    if (ProxyUi.open) {
                        ProxyScreen(
                            state = proxyState,
                            onClose = { ProxyUi.open = false },
                        )
                    }
                    if (ProxyUi.guideOpen) {
                        PrivateDnsGuideScreen(onClose = { ProxyUi.guideOpen = false })
                    }
                    ThemeMascotOverlay(theme = activeTheme, edit = editState)
                    ThemePencilOverlay()
                    ThemeElementEditorSheet()
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        handleChatIntent(intent)
        handleRemoteIntent(intent)
    }

    private fun handleChatIntent(intent: Intent?) {
        val safeIntent = intent ?: return
        val id = safeIntent.getStringExtra(space.teleopen.clauden.alarm.ScheduledPromptReceiver.EXTRA_OPEN_CHAT)
            .orEmpty()
        if (id.isNotBlank()) {
            safeIntent.removeExtra(space.teleopen.clauden.alarm.ScheduledPromptReceiver.EXTRA_OPEN_CHAT)
            viewModel.openChatFromNotification(id)
            return
        }

        val data = safeIntent.data ?: return
        if (!data.scheme.equals("clauden", ignoreCase = true)) return
        if (!data.host.equals("chat", ignoreCase = true)) return
        val chatId = data.pathSegments.firstOrNull().orEmpty()
        safeIntent.data = null
        if (chatId.isNotBlank()) viewModel.openChatFromNotification(chatId)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme.equals("clauden", ignoreCase = true)) return
        if (!data.host.equals("auth", ignoreCase = true)) return
        val payload = data.getQueryParameter("payload").orEmpty()
        val sig = data.getQueryParameter("sig").orEmpty()
        intent.data = null
        if (payload.isNotBlank() && sig.isNotBlank()) {
            viewModel.signInGoogleBridge(payload, sig)
        }
    }

    private fun handleRemoteIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme.orEmpty().lowercase()
        if (scheme != "https" && scheme != "http") return
        if (!data.host.equals("clauden.best", ignoreCase = true)) return
        val segments = data.pathSegments
        if (segments.size < 2 || !segments[0].equals("r", ignoreCase = true)) return
        val token = segments[1].trim()
        intent.data = null
        if (token.isNotBlank()) viewModel.openRemote(token)
    }
}
