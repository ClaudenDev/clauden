package space.teleopen.clauden.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.proxy.PrivateDns

@Composable
fun PrivateDnsGuideScreen(onClose: () -> Unit) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var status by remember { mutableStateOf(PrivateDns.describe(context)) }
    var copied by remember { mutableStateOf(false) }
    val hostname = PrivateDnsGuide.HOSTNAME

    BackHandler(onBack = onClose)

    Surface(color = colors.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(title = "Настройка без VPN", onBack = onClose)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Как это работает",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Чтобы окно входа в Claude открывалось без VPN, системе " +
                                "нужен DNS-резолвер, который отдаёт адрес защищённого " +
                                "релея. Он включается один раз в настройках телефона " +
                                "и работает дальше сам. Обычные запросы Clauden " +
                                "приложение уже отправляет через быстрый канал — " +
                                "отдельно ничего настраивать не нужно.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = colors.textSecondary,
                        )
                    }
                }

                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Что нажать",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GuideStep(context, 1, "Откройте «Настройки» телефона.")
                        GuideStep(context, 2, "Найдите «Частный DNS»:\n" +
                            "• Обычный Android: Сеть и интернет → Частный DNS\n" +
                            "• Samsung: Подключения → Другие настройки подключения → Частный DNS\n" +
                            "• Xiaomi: Подключение и общий доступ → Частный DNS")
                        GuideStep(context, 3, "Выберите «Имя хоста провайдера частного DNS».")
                        GuideStep(context, 4, "Впишите адрес и сохраните:")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = hostname,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsActionButton(
                            text = if (copied) "Скопировано" else "Скопировать адрес",
                            enabled = true,
                            onClick = {
                                copyToClipboard(context, hostname)
                                copied = true
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GuideStep(context, 5, "Вернитесь в Clauden — вход в Claude заработает без VPN.")
                    }
                }

                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Состояние",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Частный DNS: $status",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsActionButton(
                            text = "Открыть настройки Private DNS",
                            enabled = true,
                            onClick = { openPrivateDnsSettings(context) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsActionButton(
                            text = "Проверить ещё раз",
                            enabled = true,
                            onClick = { status = PrivateDns.describe(context) },
                        )
                    }
                }

                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Если что-то не работает",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Частный DNS и VPN-прокси в приложении не нужны " +
                                "одновременно: включите что-то одно. Если Private DNS " +
                                "настроен, приложение не будет объявлять свои " +
                                "DNS-серверы туннелю — так они не мешают друг другу.\n" +
                                "• Сеть должна пропускать DNS-over-TLS (порт 853). " +
                                "Если вход в Claude не открывается — проверьте это в " +
                                "настройках сети оператора.\n" +
                                "• После смены DNS приложение может потребовать " +
                                "перезапуск — просто закройте и откройте Clauden.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuideStep(context: Context, number: Int, text: String) {
    val colors = LocalClaudenColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = colors.bg300,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.width(24.dp).height(24.dp),
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Clauden", text))
    } catch (_: Throwable) {
    }
}

object PrivateDnsGuide {

    const val HOSTNAME = "dns.example.com"
}
