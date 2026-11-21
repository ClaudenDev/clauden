package space.teleopen.clauden.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ClaudenAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "space.teleopen.clauden.alarm.FIRE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val type = intent.getStringExtra(ClaudenAlarmScheduler.EXTRA_TYPE) ?: "timer"
        val message = intent.getStringExtra(ClaudenAlarmScheduler.EXTRA_MESSAGE).orEmpty()
        val hour = intent.getIntExtra(ClaudenAlarmScheduler.EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(ClaudenAlarmScheduler.EXTRA_MINUTE, 0)
        val days = intent.getIntArrayExtra(ClaudenAlarmScheduler.EXTRA_DAYS)
            ?.toList()
            .orEmpty()

        if (type == "alarm") {
            ClaudenAlarmScheduler.rescheduleAlarm(context, hour, minute, days, message)
        }

        val service = Intent(context, ClaudenAlarmService::class.java)
            .putExtra(ClaudenAlarmScheduler.EXTRA_TYPE, type)
            .putExtra(ClaudenAlarmScheduler.EXTRA_MESSAGE, message)

        runCatching {
            ContextCompat.startForegroundService(context, service)
        }.onFailure { error ->
            android.util.Log.e("ClaudenAlarm", "service start failed", error)

            runCatching {
                ClaudenAlarmNotifications.ensureChannel(context)
                val manager = NotificationManagerCompat.from(context)
                if (manager.areNotificationsEnabled()) {
                    manager.notify(
                        ClaudenAlarmNotifications.NOTIFICATION_ID,
                        ClaudenAlarmNotifications.build(context, type, message),
                    )
                }
            }
        }
    }
}
