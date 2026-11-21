package space.teleopen.clauden.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.data.Prefs
import space.teleopen.clauden.proxy.PrivateDns
import space.teleopen.clauden.proxy.ProxyController
import space.teleopen.clauden.proxy.ProxyUi

@Composable
fun ProxyFixBanner(modifier: Modifier = Modifier, visible: Boolean = true) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    val state by ProxyController.state.collectAsState()
    val busy = remember(state.status) { state.busy }
    val prefs = remember { Prefs(context) }

    var relayBannerHidden by remember { mutableStateOf(prefs.relayBannerDismissed) }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProxyController.connectBuiltin(context)
        } else {
            ProxyController.permissionDenied(context)
        }
    }

    if (!visible) return

    if (state.relayActive && !relayBannerHidden) {
        Surface(
            color = colors.bg300,
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Claude доступен без VPN",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            relayBannerHidden = true
                            prefs.relayBannerDismissed = true
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Скрыть",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Запросы идут через быстрый канал (${state.relayResolver}), " +
                        "VPN и разрешения системы не нужны.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Частный DNS: ${PrivateDns.describe(context)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.textTertiary,
                )
                if (!PrivateDns.enabled(context)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsActionButton(
                        text = "Как настроить вход без VPN",
                        enabled = true,
                        onClick = { ProxyUi.guideOpen = true },
                    )
                }
            }
        }
        return
    }

    if (!state.claudeBlocked) return

    Surface(
        color = colors.danger.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "claude.ai недоступен из этой сети",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.dangerSoft,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Включите быстрый канал — без VPN и без разрешений. " +
                    "Для входа в аккаунт Claude дополнительно нужен Private DNS.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsActionButton(
                text = if (busy) "Подключаю..." else "Включить быстрый канал",
                enabled = !busy,
                onClick = { ProxyController.enableRelay() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsActionButton(
                text = "Как настроить Private DNS",
                enabled = true,
                onClick = { ProxyUi.guideOpen = true },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsActionButton(
                text = "Включить прокси (VPN)",
                enabled = !busy,
                onClick = {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        consent.launch(prepare)
                    } else {
                        ProxyController.connectBuiltin(context)
                    }
                },
            )
        }
    }
}

fun openPrivateDnsSettings(context: android.content.Context) {
    val intents = listOf(
        Intent("android.settings.PRIVATE_DNS_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
        }
    }
}
