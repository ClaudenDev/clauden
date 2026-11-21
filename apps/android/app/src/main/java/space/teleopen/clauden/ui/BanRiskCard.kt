package space.teleopen.clauden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.data.AuthMode
import space.teleopen.clauden.data.BanRisk

@Composable
internal fun BanRiskSection(
    state: ChatUiState,
    onCheck: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val active = state.authMode == AuthMode.Claude
    val paid = !state.settings.plan.isBlank() && !state.settings.plan.equals("Free", true)
    val claudenProtected = paid && !active
    val linked = state.claudeLinked
    if (!linked && !claudenProtected) return

    NewSectionLabel("Безопасность")
    SettingsCard {
        if (claudenProtected) {
            ClaudenProtectedBlock(linked = linked)
            if (linked) {
                SettingsDivider()
                BanRiskContent(
                    state = state,
                    onCheck = onCheck,
                    onClear = onClear,
                    compactHeader = true,
                )
            }
        } else {
            BanRiskContent(
                state = state,
                onCheck = onCheck,
                onClear = onClear,
                compactHeader = false,
            )
        }
    }
}

@Composable
private fun ClaudenProtectedBlock(
    linked: Boolean,
) {
    val colors = LocalClaudenColors.current
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ClaudenIcons.shield,
                contentDescription = null,
                tint = riskGreen(colors),
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Вы подключены через Clauden",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Риск блокировки за запросы через Clauden: 0",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = riskGreen(colors),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ответы готовит шлюз Clauden. Ваша сессия Claude не используется " +
                "для чатов, поэтому запросы из Clauden не ведут к блокировке аккаунта.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (linked) {
                "Пока включён режим Clauden. Приложение может обновлять тариф Claude, " +
                    "но чаты в Clauden не проходят через Anthropic. При переключении на " +
                    "Claude запросы пойдут напрямую — тогда проверьте IP."
            } else {
                "Сессия Claude не привязана — ваш аккаунт Anthropic вообще не участвует."
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun BanRiskContent(
    state: ChatUiState,
    onCheck: () -> Unit,
    onClear: () -> Unit,
    compactHeader: Boolean,
) {
    val colors = LocalClaudenColors.current
    val risk = state.banRisk
    if (!compactHeader) {
        SettingsRow(
            icon = if (risk == null) ClaudenIcons.shield else ClaudenIcons.globe,
            title = if (risk == null) "Проверить риск бана" else "Риск блокировки: ${risk.levelLabel}",
            subtitle = if (risk == null) {
                "Какой IP видит Claude и насколько он чистый"
            } else {
                "Проверено по IP ${risk.ip}"
            },
            onClick = onCheck,
        )
    } else {
        SettingsRow(
            icon = ClaudenIcons.globe,
            title = "Проверить IP для режима Claude",
            subtitle = "Когда переключитесь на свой аккаунт Anthropic",
            onClick = onCheck,
        )
    }

    if (!state.banRiskBusy && risk == null && state.banRiskError == null) return

    SettingsDivider()
    Column(modifier = Modifier.padding(16.dp)) {
        when {
            state.banRiskBusy -> {
                Text(
                    text = "Смотрю, какой IP видит claude.ai…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = colors.clay,
                    trackColor = colors.control,
                )
            }

            state.banRiskError != null -> {
                Text(
                    text = state.banRiskError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.dangerSoft,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Повторить",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.clay,
                    modifier = Modifier.clickable(onClick = onCheck),
                )
            }

            else -> {
                risk?.let { result ->
                    RiskSummary(result)
                    Spacer(modifier = Modifier.height(14.dp))
                    InfoLine("IP", result.ip)
                    InfoLine(
                        "Где",
                        listOf(result.city, result.region, result.country)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")
                            .ifBlank { "неизвестно" },
                    )
                    InfoLine("Провайдер", result.provider.ifBlank { "неизвестен" })
                    InfoLine("Тип", result.kindLabel.ifBlank { "неизвестен" })
                    if (result.asn.isNotBlank()) {
                        InfoLine(
                            "ASN",
                            listOf(result.asn, result.asOrganization)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                        )
                    }
                    val flags = riskFlags(result)
                    if (flags.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = flags,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = riskColor(result.level, colors),
                        )
                    }
                    if (result.tips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        result.tips.forEach { tip ->
                            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = colors.textTertiary,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                    ),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            text = "Проверить снова",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.clay,
                            modifier = Modifier.clickable(onClick = onCheck),
                        )
                        Text(
                            text = "Скрыть",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.textTertiary,
                            modifier = Modifier.clickable(onClick = onClear),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskSummary(risk: BanRisk) {
    val colors = LocalClaudenColors.current
    val color = riskColor(risk.level, colors)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .background(color.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = risk.score.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = color,
                )
                Text(
                    text = "из 100",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = risk.levelLabel.ifBlank { "Результат проверки" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = color,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = risk.summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.textTertiary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun riskFlags(risk: BanRisk): String {
    val flags = mutableListOf<String>()
    if (risk.restricted != null) flags += "Регион недоступен"
    if (risk.flags.tor) flags += "Tor"
    if (risk.flags.hosting) flags += "Дата-центр"
    if (risk.flags.proxy) flags += "Прокси"
    if (risk.flags.vpn) flags += "VPN"
    if (risk.flags.crawler) flags += "Бот"
    if (risk.flags.abuser) flags += "История злоупотреблений"
    if (risk.flags.mobile) flags += "Мобильный"
    return flags.joinToString(" · ")
}

private fun riskGreen(colors: ClaudenColors): Color =
    if (colors.isDark) Color(0xFF6FCF97) else Color(0xFF2E7D4F)

private fun riskColor(level: String, colors: ClaudenColors): Color = when (level) {
    "low" -> riskGreen(colors)
    "medium" -> colors.clay
    "elevated", "high", "blocked" -> colors.danger
    else -> colors.textSecondary
}
