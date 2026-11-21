package space.teleopen.clauden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.Screen
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.UsageWindow
import space.teleopen.clauden.data.UsageSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun UsageScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val usage = state.usage

    if (state.authMode == AuthMode.Claude) {
        DetailScaffold(title = "Использование", onBack = vm::backToSettings) {
            SettingsCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Считает Anthropic",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "В режиме Claude запросы идут с устройства напрямую в claude.ai, " +
                            "поэтому лимиты и время сброса считает Anthropic. Проценты нашего " +
                            "тарифа здесь не применяются.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingsActionButton(
                        text = "Открыть режим Claude",
                        onClick = { vm.openSettingsSection(Screen.ClaudeMode) },
                    )
                }
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        vm.loadUsage(force = true)
    }

    DetailScaffold(title = "Использование", onBack = vm::backToSettings) {
        when {
            usage == null && state.usageError != null -> UsageError(
                message = state.usageError.orEmpty(),
                onRetry = { vm.loadUsage(force = true) },
            )

            usage == null -> {
                Text(
                    text = "Загружаем лимиты…",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(8.dp),
                )
            }

            usage.plan.equals("Free", ignoreCase = true) -> {
                Text(
                    text = "Лимиты доступны на тарифах Pro, Max и Ultra.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(8.dp),
                )
            }

            else -> UsageContent(usage)
        }
    }
}

@Composable
private fun UsageError(message: String, onRetry: () -> Unit) {
    val colors = LocalClaudenColors.current
    SettingsCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Повторить",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.clay,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}

@Composable
private fun UsageContent(usage: UsageSnapshot) {
    val colors = LocalClaudenColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Лимиты тарифа",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = LocalClaudenFonts.current.serif,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.3).sp,
                ),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Обновляется после каждого ответа",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
        Surface(
            color = colors.control,
            shape = CircleShape,
        ) {
            Text(
                text = usage.planLabel.ifBlank { usage.plan },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }

    SettingsCard {
        Column(modifier = Modifier.padding(18.dp)) {
            UsageRow(
                title = "Текущая сессия",
                subtitle = sessionResetLabel(usage.session.resetsAt),
                metric = usage.session,
            )
            Spacer(modifier = Modifier.height(18.dp))
            UsageDivider()
            Spacer(modifier = Modifier.height(18.dp))
            UsageRow(
                title = "Недельные лимиты",
                subtitle = weeklyResetLabel(usage.weekly.resetsAt),
                metric = usage.weekly,
            )
        }
    }
}

@Composable
private fun UsageRow(
    title: String,
    subtitle: String,
    metric: UsageWindow,
) {
    val colors = LocalClaudenColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percentLabel(metric.percent),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(9.dp))
        UsageBar(metric.percent)
    }
}

@Composable
private fun UsageBar(percent: Double) {
    val colors = LocalClaudenColors.current
    val fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.control),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (percent >= 90) colors.danger else colors.blue),
            )
        }
    }
}

@Composable
private fun UsageDivider() {
    val colors = LocalClaudenColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.border.copy(alpha = 0.5f)),
    )
}

private fun percentLabel(percent: Double): String {
    if (percent <= 0.0) return "0%"
    if (percent < 1.0) return "<1%"
    return "${percent.roundToInt()}%"
}

private fun sessionResetLabel(resetsAt: Long): String {
    val left = resetsAt - System.currentTimeMillis()
    if (left <= 0) return "Сброс скоро"
    val hours = left / 3_600_000
    val minutes = (left % 3_600_000) / 60_000
    return if (hours > 0) "Сброс через $hours ч $minutes мин" else "Сброс через $minutes мин"
}

private fun weeklyResetLabel(resetsAt: Long): String {
    if (resetsAt <= 0) return "Сброс в начале недели"
    val formatter = DateTimeFormatter.ofPattern("EEE в HH:mm", Locale("ru"))
    val text = formatter.format(
        Instant.ofEpochMilli(resetsAt).atZone(ZoneId.systemDefault()),
    )
    return "Сброс ${text.replaceFirstChar { it.uppercase() }}"
}
