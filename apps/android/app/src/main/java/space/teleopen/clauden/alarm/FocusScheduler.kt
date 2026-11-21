package space.teleopen.clauden.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import space.teleopen.clauden.data.AppSettings
import space.teleopen.clauden.data.ScheduledTask

object FocusScheduler {

    const val ACTION_BREAK = "space.teleopen.clauden.BREAK"
    const val ACTION_SNOOZE = "space.teleopen.clauden.BREAK_SNOOZE"
    private const val REQUEST = 7101

    fun apply(context: Context, settings: AppSettings) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, BreakReminderReceiver::class.java).setAction(ACTION_BREAK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pending)
        if (!settings.breakReminders || settings.breakInterval <= 0) return
        val interval = settings.breakInterval * 60_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pending,
            )
        } else {
            manager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pending,
            )
        }
    }

    fun snooze(context: Context, minutes: Int = 15) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST + 1,
            Intent(context, BreakReminderReceiver::class.java).setAction(ACTION_BREAK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(REQUEST, REQUEST + 1).forEach { request ->
            manager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    request,
                    Intent(context, BreakReminderReceiver::class.java).setAction(ACTION_BREAK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }
}

object ScheduledScheduler {

    const val EXTRA_ID = "scheduled_id"

    fun schedule(context: Context, task: ScheduledTask) {
        if (task.runAt <= System.currentTimeMillis()) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, task.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.runAt, pending)
            }.onFailure {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.runAt, pending)
            }
        } else {
            manager.setExact(AlarmManager.RTC_WAKEUP, task.runAt, pending)
        }
    }

    fun cancel(context: Context, id: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, id))
    }

    fun nextRun(current: Long, repeat: String): Long {
        val step = when (repeat) {
            "daily" -> 24 * 60 * 60 * 1000L
            "weekly" -> 7 * 24 * 60 * 60 * 1000L
            else -> return 0L
        }
        var next = current + step
        val now = System.currentTimeMillis()
        while (next <= now) next += step
        return next
    }

    private fun pendingIntent(context: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ScheduledPromptReceiver::class.java)
                .setAction(ScheduledPromptReceiver.ACTION_RUN)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
