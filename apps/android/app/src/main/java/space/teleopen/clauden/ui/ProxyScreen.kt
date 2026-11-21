package space.teleopen.clauden.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.teleopen.clauden.proxy.PrivateDns
import space.teleopen.clauden.proxy.ProxyController
import space.teleopen.clauden.proxy.ProxyUi
import space.teleopen.clauden.proxy.ProxyState
import space.teleopen.clauden.proxy.VpnStatus
import space.teleopen.clauden.proxy.exitCountryLabel

@Composable
fun ProxyScreen(
    state: ProxyState,
    onClose: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    val context = LocalContext.current
    var input by rememberSaveable(state.subscription) { mutableStateOf(state.subscription) }
    var localMessage by remember { mutableStateOf<String?>(null) }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (!ProxyController.connect(context)) {
                localMessage = ProxyController.state.value.error
            }
        } else {
            ProxyController.permissionDenied(context)
        }
    }

    val builtinConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (!ProxyController.connectBuiltin(context)) {
                localMessage = ProxyController.state.value.error
            }
        } else {
            ProxyController.permissionDenied(context)
        }
    }

    fun connectBuiltinWithConsent() {
        localMessage = null
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            builtinConsent.launch(prepare)
        } else if (!ProxyController.connectBuiltin(context)) {
            localMessage = ProxyController.state.value.error
        }
    }

    fun connectWithConsent() {
        localMessage = null
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            consent.launch(prepare)
        } else if (!ProxyController.connect(context)) {
            localMessage = ProxyController.state.value.error
        }
    }

    fun toggle() {
        if (state.connected || state.status == VpnStatus.CONNECTING) {
            ProxyController.disconnect(context)
        } else {
            connectWithConsent()
        }
    }

    fun select(node: space.teleopen.clauden.proxy.VpnNode) {
        localMessage = null
        if (!node.supported) {
            localMessage = if (node.protocol == space.teleopen.clauden.proxy.VpnProtocol.HYSTERIA2) {
                "Hysteria2 пока не поддерживается — выберите другой сервер"
            } else {
                "Этот протокол не поддерживается"
            }
            return
        }
        val wasConnected = state.connected
        ProxyController.select(context, node)
        if (wasConnected) connectWithConsent()
    }

    BackHandler(onBack = onClose)

    Surface(color = colors.bg, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(title = "Прокси", onBack = onClose)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusCard(
                    state = state,
                    onToggle = ::toggle,
                )

                if (state.exitKnown) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Ваш выход в интернет",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${exitCountryLabel(state.exitCountry)} · ${state.exitIp}",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = if (state.exitOutsideRussia) colors.clay else colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state.exitOutsideRussia) {
                                    "claude.ai открывается напрямую: прокси Clauden не нужен. " +
                                        "Свой VPN можно не отключать."
                                } else {
                                    "Выход из России: для claude.ai нужен быстрый канал " +
                                        "или прокси Clauden."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = colors.textTertiary,
                            )
                        }
                    }
                }

                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Быстрый канал без VPN",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Резолвер отдаёт адрес релея для claude.ai: соединение " +
                                "идёт напрямую, VPN и разрешение системы не нужны. " +
                                "Для входа в аккаунт Claude дополнительно включите Private DNS.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = colors.textTertiary,
                        )
                        if (state.relayActive) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Подключено: ${state.relayResolver}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Частный DNS: ${PrivateDns.describe(context)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsActionButton(
                            text = if (state.relayActive) "Отключить быстрый канал" else "Включить быстрый канал",
                            enabled = true,
                            onClick = {
                                localMessage = null
                                if (state.relayActive) {
                                    ProxyController.disableRelay()
                                } else {
                                    ProxyController.enableRelay()
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsActionButton(
                            text = "Как настроить без VPN (инструкция)",
                            enabled = true,
                            onClick = { ProxyUi.guideOpen = true },
                        )
                    }
                }

                if (state.builtinNodes.isNotEmpty()) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Бесплатный прокси Clauden",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Свой туннель до наших серверов: работает там, где " +
                                    "claude.ai недоступен, и не требует подписки или " +
                                    "стороннего VPN.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = colors.textTertiary,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            state.builtinNodes.forEachIndexed { index, node ->
                                NodeRow(
                                    node = node,
                                    selected = state.builtinSelected?.raw == node.raw,
                                    onClick = {
                                        localMessage = null
                                        ProxyController.selectBuiltin(context, node)
                                        if (state.connected) connectWithConsent()
                                    },
                                )
                                if (index != state.builtinNodes.lastIndex) SettingsDivider()
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsActionButton(
                                text = when {
                                    state.connected && state.usingBuiltin -> "Отключить прокси"
                                    state.busy -> "Подключаю..."
                                    else -> "Включить прокси Clauden"
                                },
                                enabled = !state.busy,
                                onClick = {
                                    localMessage = null
                                    if (state.connected && state.usingBuiltin) {
                                        ProxyController.disconnect(context)
                                    } else {
                                        connectBuiltinWithConsent()
                                    }
                                },
                            )
                        }
                    }
                }

                Text(
                    text = "Подписка",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textSecondary,
                )
                SettingsField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "https://... или вставьте vless://, vmess://, trojan://, ss://",
                    height = 96.dp,
                    singleLine = false,
                    textSize = 14,
                )

                SettingsActionButton(
                    text = if (state.loading) "Загружаю..." else "Загрузить серверы",
                    enabled = input.isNotBlank() && !state.loading,
                    onClick = {
                        localMessage = null
                        ProxyController.refreshSubscription(context, input)
                    },
                )

                val message = localMessage ?: state.error
                if (!message.isNullOrBlank()) {
                    MessageCard(message, error = true)
                } else if (!state.notice.isNullOrBlank()) {
                    MessageCard(state.notice, error = false)
                }

                if (state.nodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.title.ifBlank { "Серверы" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = state.nodes.size.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = colors.textTertiary,
                        )
                    }

                    SettingsCard {
                        state.nodes.forEachIndexed { index, node ->
                            NodeRow(
                                node = node,
                                selected = state.selected?.id == node.id,
                                onClick = { select(node) },
                            )
                            if (index != state.nodes.lastIndex) SettingsDivider()
                        }
                    }
                } else {
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Серверов пока нет",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Вставьте ссылку на подписку или список " +
                                    "vless:// / vmess:// / trojan:// / ss:// — " +
                                    "серверы появятся здесь, и можно выбрать любой.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = colors.textTertiary,
                            )
                        }
                    }
                }

                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Автозапуск",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "При открытии приложения и после перезагрузки",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = colors.textTertiary,
                            )
                        }
                        SettingsToggle(
                            checked = state.autoStart,
                            onCheckedChange = { enabled ->
                                ProxyController.setAutoStart(context, enabled)
                                if (enabled && state.configured &&
                                    !state.connected && state.status != VpnStatus.CONNECTING
                                ) {
                                    connectWithConsent()
                                }
                            },
                        )
                    }
                }

                Text(
                    text = "Трафик Clauden и Claude идёт через выбранный сервер. " +
                        "Остальные приложения — через системный VPN. Подписка " +
                        "и ключи хранятся только на телефоне.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(state: ProxyState, onToggle: () -> Unit) {
    val colors = LocalClaudenColors.current
    val tint = when (state.status) {
        VpnStatus.ON -> colors.blue
        VpnStatus.CONNECTING -> colors.clay
        VpnStatus.ERROR -> colors.danger
        VpnStatus.OFF -> colors.textTertiary
    }

    SettingsCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(tint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ClaudenIcons.shield,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.statusLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = state.statusDetail,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.clay,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            SettingsActionButton(
                text = when {
                    state.status == VpnStatus.CONNECTING -> "Отключить"
                    state.connected -> "Отключить"
                    state.needsPermission -> "Разрешить VPN и подключить"
                    state.configured -> "Подключить"
                    else -> "Сначала добавьте подписку"
                },
                enabled = state.configured || state.connected,
                onClick = onToggle,
            )
        }
    }
}

@Composable
private fun NodeRow(
    node: space.teleopen.clauden.proxy.VpnNode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalClaudenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    width = 1.5.dp,
                    color = if (selected) colors.clay else colors.borderStrong,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(colors.clay, CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (node.supported) colors.textPrimary else colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${node.protocolLabel} · ${node.endpoint}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!node.supported) {
            Text(
                text = "не под.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun MessageCard(text: String, error: Boolean) {
    val colors = LocalClaudenColors.current
    Surface(
        color = if (error) colors.dangerSoft else colors.field,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = if (error) colors.danger else colors.textSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

@Composable
fun ProxyShieldButton(
    state: ProxyState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 38,
) {
    val colors = LocalClaudenColors.current
    val tint = when (state.status) {
        VpnStatus.ON -> colors.blue
        VpnStatus.CONNECTING -> colors.clay
        VpnStatus.ERROR -> colors.danger
        VpnStatus.OFF -> colors.textPrimary
    }
    Surface(
        color = colors.bg300.copy(alpha = 0.94f),
        contentColor = tint,
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .size(size.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ClaudenIcons.shield,
                contentDescription = "Прокси",
                tint = tint,
                modifier = Modifier.size((size * 0.52f).dp),
            )
            if (state.connected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(7.dp)
                        .background(colors.blue, CircleShape),
                )
            } else if (state.claudeBlocked) {

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(7.dp)
                        .background(colors.danger, CircleShape),
                )
            }
        }
    }
}

fun proxySettingsSubtitle(state: ProxyState): String = when {
    state.status == VpnStatus.CONNECTING -> "Подключение · ${state.selected?.name.orEmpty()}"
    state.connected -> "Включён · ${state.activeName.ifBlank { state.selected?.name.orEmpty() }}"
    state.status == VpnStatus.ERROR -> "Ошибка · ${state.error.orEmpty()}"
    state.configured -> "Выключен · ${state.selected?.name.orEmpty()}"
    else -> "VPN для Claude и Clauden"
}
