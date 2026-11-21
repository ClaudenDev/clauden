package space.teleopen.clauden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.ApiKeyInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ApiKeysScreen(state: ChatUiState, vm: ChatViewModel) {
    val colors = LocalClaudenColors.current
    val clipboard = LocalClipboardManager.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf<ApiKeyInfo?>(null) }

    LaunchedEffect(Unit) { vm.loadApiKeys(force = true) }

    DetailScaffold(title = "API-ключ", onBack = vm::backToSettings) {
        when {
            !state.apiKeysCanIssue -> SettingsCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Только по подписке Clauden",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "API-ключ открывается подпиской Pro, Max или Ultra, " +
                            "оформленной в Clauden. Подписка Anthropic или тариф, выданный " +
                            "вручную, ключ не даёт.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = colors.textSecondary,
                    )
                }
            }

            state.apiKeyFresh.isNotBlank() -> SettingsCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Ключ готов",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Полностью показываем один раз — сохраните сейчас.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = colors.bg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.apiKeyFresh,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = colors.textPrimary,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionButton(
                        text = "Скопировать",
                        onClick = { clipboard.setText(AnnotatedString(state.apiKeyFresh)) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = vm::clearFreshApiKey, modifier = Modifier.fillMaxWidth()) {
                        Text("Готово", color = colors.textSecondary, fontSize = 15.sp)
                    }
                }
            }

            else -> {
                val active = state.apiKeys.firstOrNull { !it.locked }
                if (active == null) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Ключа пока нет",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ключ работает с Anthropic-совместимыми клиентами: " +
                                    "Claude Code, Cursor и другими. Он привязан к вашему тарифу " +
                                    "и расходует его лимиты.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            SettingsActionButton(
                                text = if (state.apiKeysBusy) "Выпускаем…" else "Получить ключ",
                                onClick = vm::createApiKey,
                                enabled = !state.apiKeysBusy,
                            )
                        }
                    }
                } else {
                    KeyCard(key = active)
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsActionButton(
                        text = if (state.apiKeysBusy) "Сбрасываем…" else "Сбросить ключ",
                        onClick = { confirmReset = true },
                        enabled = !state.apiKeysBusy,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { confirmRevoke = active },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Отозвать ключ", color = colors.textSecondary, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Сброс выпускает новый ключ и сразу отзывает старый. " +
                            "Внешние клиенты со старым ключом перестанут работать.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = colors.textTertiary,
                    )
                }

                if (state.apiKeysError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.apiKeysError,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = colors.textSecondary,
                    )
                }

                if (state.apiKeyLimit > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "На тарифе ${state.settings.plan} можно держать до " +
                            "${state.apiKeyLimit} ключ(ей).",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDelete(
            title = "Сбросить ключ?",
            text = "Старый ключ сразу перестанет работать. Новый покажем один раз.",
            confirm = "Сбросить",
            onConfirm = {
                confirmReset = false
                vm.resetApiKey()
            },
            onDismiss = { confirmReset = false },
        )
    }
    confirmRevoke?.let { key ->
        ConfirmDelete(
            title = "Отозвать ключ?",
            text = "Ключ ${key.prefix} перестанет работать. Новый можно выпустить в любой момент.",
            confirm = "Отозвать",
            onConfirm = {
                confirmRevoke = null
                vm.revokeApiKey(key.id)
            },
            onDismiss = { confirmRevoke = null },
        )
    }
}

@Composable
private fun KeyCard(key: ApiKeyInfo) {
    val colors = LocalClaudenColors.current
    SettingsCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = key.prefix,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimary,
                )
                Text(
                    text = if (key.locked) "заблокирован" else "активен",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow("Тариф", key.plan)
            InfoRow("Выпущен", dateText(key.createdAt))
            InfoRow("Запросов", key.requests.toString())
            InfoRow("Токенов", "${key.tokensIn} вход / ${key.tokensOut} выход")
            InfoRow("Последний запрос", if (key.lastUsedAt > 0) dateText(key.lastUsedAt) else "ещё не использовался")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun ConfirmDelete(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun dateText(ms: Long): String {
    if (ms <= 0) return "—"
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("ru"))
    return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))
}
