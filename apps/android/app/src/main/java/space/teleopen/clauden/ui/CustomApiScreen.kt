package space.teleopen.clauden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.ChatUiState
import space.teleopen.clauden.ChatViewModel
import space.teleopen.clauden.data.CustomApi
import space.teleopen.clauden.data.RemoteModel

@Composable
fun CustomApiScreen(
    state: ChatUiState,
    vm: ChatViewModel,
) {
    val colors = LocalClaudenColors.current
    var editing by remember { mutableStateOf<CustomApi?>(null) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var manualModel by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun closeEditor() {
        editing = null
        vm.clearCustomApiTest()
    }

    fun openEditor(api: CustomApi?) {
        editing = api ?: CustomApi()
        name = api?.name.orEmpty()
        baseUrl = api?.baseUrl.orEmpty()
        apiKey = api?.apiKey.orEmpty()
        manualModel = ""
        vm.clearCustomApiTest()
    }

    LaunchedEffect(Unit) { vm.refreshCustomApis() }
    BackHandler(enabled = editing != null || confirmDelete) {
        if (confirmDelete) confirmDelete = false else closeEditor()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground(colors),
    ) {
        SettingsHeader(
            title = when {
                editing == null -> "Свой API"
                editing?.id.isNullOrBlank() -> "Новый API"
                else -> "Изменить API"
            },
            onBack = {
                if (editing != null) closeEditor() else vm.backToSettings()
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (editing == null) {
                Text(
                    text = "Подключите любой OpenAI-совместимый API: OpenRouter, " +
                        "Groq, Qwen, Ollama или свой сервер. Ключ хранится " +
                        "только на телефоне, в зашифрованном виде.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))

                if (state.customApis.isEmpty()) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Пока пусто",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Добавьте API — и его модели появятся в " +
                                    "разделе «Другие модели» при выборе модели.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = colors.textTertiary,
                            )
                        }
                    }
                } else {
                    SettingsCard {
                        state.customApis.forEachIndexed { index, api ->
                            SettingsRow(
                                icon = ClaudenIcons.link,
                                title = api.label,
                                subtitle = "${api.modelCount} моделей · ${api.baseUrl}",
                                trailing = {
                                    if (state.customApiId == api.id) {
                                        Text(
                                            text = "активно",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                            ),
                                            color = colors.clay,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = ClaudenIcons.chevronRight,
                                            contentDescription = null,
                                            tint = colors.textTertiary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                                onClick = { openEditor(api) },
                            )
                            if (index != state.customApis.lastIndex) SettingsDivider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                SettingsActionButton(
                    text = "Добавить API",
                    onClick = { openEditor(null) },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Подходит любой сервис с OpenAI-совместимым " +
                        "/v1/chat/completions и /v1/models. Для Qwen укажите " +
                        "адрес DashScope compatible-mode.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.textTertiary,
                )
            } else {
                Text(
                    text = "Название",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "OpenRouter",
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Base URL",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        vm.clearCustomApiTest()
                    },
                    placeholder = "https://openrouter.ai/api/v1",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "API-ключ",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        vm.clearCustomApiTest()
                    },
                    placeholder = "sk-…",
                    transformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                Spacer(modifier = Modifier.height(18.dp))
                SettingsActionButton(
                    text = if (state.customApiBusy) "Загружаю…" else "Загрузить модели",
                    enabled = !state.customApiBusy && baseUrl.isNotBlank(),
                    onClick = { vm.testCustomApi(baseUrl, apiKey) },
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Модель вручную",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsField(
                    value = manualModel,
                    onValueChange = { manualModel = it },
                    placeholder = "qwen3.8 или id через запятую",
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Если /v1/models у сервиса нет, впишите id модели сюда.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = colors.textTertiary,
                )

                Spacer(modifier = Modifier.height(10.dp))
                when {
                    state.customApiBusy -> Text(
                        text = "Проверяю подключение…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                    )

                    state.customApiError != null -> Text(
                        text = state.customApiError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.dangerSoft,
                    )

                    state.customApiModels.isNotEmpty() -> Text(
                        text = "Подключение OK. Моделей: ${state.customApiModels.size}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textSecondary,
                    )

                    editing?.models?.isNotEmpty() == true -> Text(
                        text = "Сохранено моделей: ${editing?.models?.size ?: 0}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                    )

                    else -> Text(
                        text = "Список моделей пуст. Впишите id вручную выше.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                SettingsActionButton(
                    text = "Сохранить",
                    enabled = !state.customApiBusy && baseUrl.isNotBlank(),
                    onClick = {
                        val manual = manualModel
                            .split(',', '\n')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { RemoteModel(id = it, name = it) }
                        val models = (
                            state.customApiModels.ifEmpty { editing?.models.orEmpty() } + manual
                            ).distinctBy { it.id }
                        vm.saveCustomApi(
                            CustomApi(
                                id = editing?.id.orEmpty(),
                                name = name,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                models = models,
                            ),
                        )
                        closeEditor()
                    },
                )

                if (!editing?.id.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clickable { confirmDelete = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Удалить API",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = colors.dangerSoft,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Модели загружаются с /v1/models. Если сервис вернул " +
                        "пустой список, проверьте Base URL и ключ.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.textTertiary,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (confirmDelete && editing != null) {
        SettingsConfirmDialog(
            title = "Удалить свой API?",
            text = "Ключ и список моделей будут удалены с телефона.",
            confirmText = "Удалить",
            onConfirm = {
                vm.deleteCustomApi(editing?.id.orEmpty())
                confirmDelete = false
                closeEditor()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
