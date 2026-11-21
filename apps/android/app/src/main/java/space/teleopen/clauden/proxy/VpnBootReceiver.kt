package space.teleopen.clauden.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

class VpnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        try {
            val store = ProxyStore(context)
            if (!store.autoStart || !store.desiredOn) return
            val node = store.selectedNode() ?: return
            if (!node.supported) return
            if (VpnService.prepare(context) != null) return

            val config = NodeParser.buildXrayConfig(node)
            ClaudenVpnService.start(context, config, node.name)
        } catch (t: Throwable) {
            Log.e("ClaudenVpn", "boot start failed", t)
        }
    }
}
