package space.teleopen.clauden.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import space.teleopen.clauden.data.ChatAction
import java.util.Calendar

object ClaudenAlarmScheduler {

    const val EXTRA_TYPE = "type"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_HOUR = "hour"
    const val EXTRA_MINUTE = "minute"
    const val EXTRA_DAYS = "days"
    const val EXTRA_REQUEST = "request"

    fun scheduleTimer(context: Context, action: ChatAction): ChatAction {
        val seconds = action.seconds ?: return fail(action, "Не указана длительность таймера")
        if (seconds <= 0) return fail(action, "Неверная длительность таймера")
        if (!notificationsAllowed(context)) {
            return fail(action, "Разрешите уведомления в настройках, иначе таймер не разбудит.")
        }
        val trigger = System.currentTimeMillis() + seconds * 1000L
        val request = stableRequest(action, trigger)
        val pending = pendingIntent(
            context = context,
            type = "timer",
            message = action.message.ifBlank { "Таймер" },
            hour = 0,
            minute = 0,
            days = emptyList(),
            request = request,
        )
        setExact(context, trigger, pending, isAlarm = false)
        return action.copy(
            status = "done",
            detail = "Таймер поставлен на " + duration(seconds),
        )
    }

    fun scheduleAlarm(context: Context, action: ChatAction): ChatAction {
        val hour = action.hour ?: return fail(action, "Не указано время будильника")
        val minute = action.minute ?: 0
        if (hour !in 0..23 || minute !in 0..59) {
            return fail(action, "Неверное время будильника")
        }
        if (!notificationsAllowed(context)) {
            return fail(action, "Разрешите уведомления в настройках, иначе будильник не разбудит.")
        }
        val days = action.days
        val trigger = nextTrigger(hour, minute, days, System.currentTimeMillis())
        val request = stableRequest(action, hour * 100L + minute)
        val pending = pendingIntent(
            context = context,
            type = "alarm",
            message = action.message.ifBlank { "Будильник" },
            hour = hour,
            minute = minute,
            days = days,
            request = request,
        )
        setExact(context, trigger, pending, isAlarm = true)
        return action.copy(
            status = "done",
            detail = "Будильник поставлен на " + String.format("%02d:%02d", hour, minute),
        )
    }

    fun rescheduleAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        days: List<Int>,
        message: String,
    ) {
        if (days.isEmpty()) return
        val trigger = nextTrigger(hour, minute, days, System.currentTimeMillis())
        val request = (hour * 100 + minute).coerceAtLeast(1)
        val pending = pendingIntent(
            context = context,
            type = "alarm",
            message = message,
            hour = hour,
            minute = minute,
            days = days,
            request = request,
        )
        setExact(context, trigger, pending, isAlarm = true)
    }

    fun nextTrigger(hour: Int, minute: Int, days: List<Int>, from: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (days.isEmpty()) {
            if (calendar.timeInMillis <= from) calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }
        repeat(8) {
            if (calendar.timeInMillis > from && days.contains(weekDay(calendar))) {
                return calendar.timeInMillis
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun weekDay(calendar: Calendar): Int = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    private fun pendingIntent(
        context: Context,
        type: String,
        message: String,
        hour: Int,
        minute: Int,
        days: List<Int>,
        request: Int,
    ): PendingIntent {
        val intent = Intent(context, ClaudenAlarmReceiver::class.java)
            .setAction(ClaudenAlarmReceiver.ACTION_FIRE)
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_HOUR, hour)
            .putExtra(EXTRA_MINUTE, minute)
            .putExtra(EXTRA_DAYS, days.toIntArray())
            .putExtra(EXTRA_REQUEST, request)
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setExact(context: Context, trigger: Long, pending: PendingIntent, isAlarm: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (isAlarm) {
            runCatching {
                val show = PendingIntent.getActivity(
                    context,
                    pending.hashCode(),
                    Intent(context, AlarmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pending)
                return
            }
        }
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun stableRequest(action: ChatAction, fallback: Long): Int {
        val seed = action.id.ifBlank { fallback.toString() }
        return (seed.hashCode() and 0x7fffffff).coerceAtLeast(1)
    }

    private fun duration(seconds: Long): String = when {
        seconds >= 3600 && seconds % 3600 == 0L -> "${seconds / 3600} ч"
        seconds >= 60 && seconds % 60 == 0L -> "${seconds / 60} мин"
        seconds >= 60 -> "${seconds / 60} мин ${seconds % 60} с"
        else -> "$seconds с"
    }

    private fun fail(action: ChatAction, detail: String): ChatAction =
        action.copy(status = "failed", detail = detail)
}
