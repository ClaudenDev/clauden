package space.teleopen.clauden.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import space.teleopen.clauden.data.Prefs
import space.teleopen.clauden.data.ScheduledStore
import space.teleopen.clauden.data.SettingsStore

class ClaudenBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        FocusScheduler.apply(context, SettingsStore(context).load())
        val account = Prefs(context).authUserId
        if (account.isBlank()) return
        ScheduledStore(context, account).load()
            .filter { it.runAt > System.currentTimeMillis() }
            .forEach { ScheduledScheduler.schedule(context, it) }
    }
}
