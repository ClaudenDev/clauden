package space.teleopen.clauden.proxy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VpnProtocol(val label: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    SOCKS("SOCKS5"),
    HYSTERIA2("Hysteria2"),
    UNKNOWN("Неизвестный"),
}

data class VpnNode(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val protocol: VpnProtocol,
    val raw: String,
    val params: Map<String, String> = emptyMap(),
) {
    val supported: Boolean
        get() = protocol != VpnProtocol.HYSTERIA2 && protocol != VpnProtocol.UNKNOWN

    val endpoint: String
        get() = "$address:$port"

    val protocolLabel: String
        get() = protocol.label
}

enum class VpnStatus { OFF, CONNECTING, ON, ERROR }

data class ProxyState(
    val nodes: List<VpnNode> = emptyList(),
    val selected: VpnNode? = null,

    val builtinNodes: List<VpnNode> = emptyList(),
    val builtinSelected: VpnNode? = null,

    val claudeReachable: Boolean? = null,

    val exitIp: String = "",

    val exitCountry: String = "",

    val relayActive: Boolean = false,
    val relayResolver: String = "",
    val subscription: String = "",
    val title: String = "",
    val autoStart: Boolean = true,
    val status: VpnStatus = VpnStatus.OFF,
    val activeName: String = "",
    val error: String? = null,
    val notice: String? = null,
    val loading: Boolean = false,
    val needsPermission: Boolean = false,
) {
    val configured: Boolean get() = selected != null
    val connected: Boolean get() = status == VpnStatus.ON
    val busy: Boolean get() = status == VpnStatus.CONNECTING || loading

    val usingBuiltin: Boolean
        get() = builtinSelected != null && builtinSelected.raw == selected?.raw

    val claudeBlocked: Boolean
        get() = claudeReachable == false && !connected

    val exitKnown: Boolean get() = exitIp.isNotBlank()

    val exitOutsideRussia: Boolean get() = exitCountry.isNotBlank() && !exitCountry.equals("RU", true)

    val needsBypass: Boolean get() = claudeBlocked || (exitKnown && !exitOutsideRussia)

    val statusLabel: String
        get() = when (status) {
            VpnStatus.ON -> "Подключено"
            VpnStatus.CONNECTING -> "Подключение"
            VpnStatus.ERROR -> "Ошибка"
            VpnStatus.OFF -> "Выключено"
        }

    val statusDetail: String
        get() = when {
            status == VpnStatus.ON -> activeName.ifBlank { selected?.name.orEmpty() }
            status == VpnStatus.CONNECTING -> selected?.name.orEmpty()
            status == VpnStatus.ERROR -> error.orEmpty()
            selected != null -> "Сервер: ${selected.name}"
            else -> "Добавьте подписку, чтобы включить"
        }
}

fun exitCountryLabel(code: String): String = when (code.trim().uppercase()) {
    "RU" -> "Россия"
    "BY" -> "Беларусь"
    "KZ" -> "Казахстан"
    "UA" -> "Украина"
    "DE" -> "Германия"
    "NL" -> "Нидерланды"
    "US" -> "США"
    "GB" -> "Великобритания"
    "FR" -> "Франция"
    "PL" -> "Польша"
    "TR" -> "Турция"
    "GE" -> "Грузия"
    "AM" -> "Армения"
    "AE" -> "ОАЭ"
    "TH" -> "Таиланд"
    "VN" -> "Вьетнам"
    "ID" -> "Индонезия"
    "IN" -> "Индия"
    "CA" -> "Канада"
    "FI" -> "Финляндия"
    "SE" -> "Швеция"
    "EE" -> "Эстония"
    "LV" -> "Латвия"
    "LT" -> "Литва"
    "MD" -> "Молдова"
    "RS" -> "Сербия"
    "BG" -> "Болгария"
    "CZ" -> "Чехия"
    "AT" -> "Австрия"
    "CH" -> "Швейцария"
    "ES" -> "Испания"
    "IT" -> "Италия"
    "PT" -> "Португалия"
    "SG" -> "Сингапур"
    "JP" -> "Япония"
    "KR" -> "Южная Корея"
    "HK" -> "Гонконг"
    "CN" -> "Китай"
    "IL" -> "Израиль"
    "" -> "неизвестно"
    else -> code.uppercase()
}

object ProxyUi {

    var open by mutableStateOf(false)

    var guideOpen by mutableStateOf(false)
}
