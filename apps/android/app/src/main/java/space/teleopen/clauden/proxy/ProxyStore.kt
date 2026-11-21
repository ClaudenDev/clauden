package space.teleopen.clauden.proxy

import android.content.Context
import org.json.JSONArray

class ProxyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var desiredOn: Boolean
        get() = prefs.getBoolean(KEY_DESIRED_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_DESIRED_ON, value).apply()

    var autoStarted: Boolean
        get() = prefs.getBoolean(KEY_AUTO_STARTED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_STARTED, value).apply()

    var subscription: String
        get() = prefs.getString(KEY_SUBSCRIPTION, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SUBSCRIPTION, value).apply()

    var title: String
        get() = prefs.getString(KEY_TITLE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TITLE, value).apply()

    var selectedRaw: String
        get() = prefs.getString(KEY_SELECTED, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    var autoNode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_NODE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_NODE, value).apply()

    var builtinNodesRaw: String
        get() = prefs.getString(KEY_BUILTIN_NODES, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BUILTIN_NODES, value).apply()

    var builtinAuto: Boolean
        get() = prefs.getBoolean(KEY_BUILTIN_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_BUILTIN_AUTO, value).apply()

    fun nodes(): List<VpnNode> {
        val raw = prefs.getString(KEY_NODES, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<VpnNode>(array.length())
            for (i in 0 until array.length()) {
                val link = array.optString(i, "")
                if (link.isNotBlank()) NodeParser.parse(link)?.let { result.add(it) }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveNodes(nodes: List<VpnNode>) {
        val array = JSONArray()
        nodes.forEach { array.put(it.raw) }
        prefs.edit().putString(KEY_NODES, array.toString()).apply()
    }

    fun builtinNodes(): List<VpnNode> {
        val raw = prefs.getString(KEY_BUILTIN_NODES, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<VpnNode>(array.length())
            for (i in 0 until array.length()) {
                val link = array.optString(i, "")
                if (link.isNotBlank()) NodeParser.parse(link)?.let { result.add(it) }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveBuiltinNodes(nodes: List<VpnNode>) {
        val array = JSONArray()
        nodes.forEach { array.put(it.raw) }
        prefs.edit().putString(KEY_BUILTIN_NODES, array.toString()).apply()
    }

    fun selectedNode(): VpnNode? {
        val nodes = nodes()
        if (nodes.isEmpty()) return null
        val raw = selectedRaw
        return nodes.firstOrNull { it.raw == raw } ?: nodes.firstOrNull()
    }

    private companion object {
        const val PREFS = "clauden_proxy"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_DESIRED_ON = "desired_on"
        const val KEY_AUTO_STARTED = "auto_started"
        const val KEY_SUBSCRIPTION = "subscription"
        const val KEY_TITLE = "title"
        const val KEY_SELECTED = "selected"
        const val KEY_AUTO_NODE = "auto_node"
        const val KEY_NODES = "nodes"
        const val KEY_BUILTIN_NODES = "builtin_nodes"
        const val KEY_BUILTIN_AUTO = "builtin_auto"
    }
}
