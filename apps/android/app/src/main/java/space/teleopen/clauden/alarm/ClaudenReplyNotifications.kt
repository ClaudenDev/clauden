package space.teleopen.clauden.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import space.teleopen.clauden.MainActivity

object ClaudenReplyNotifications {
    const val CHANNEL_REPLIES = "clauden_replies"
    const val CHANNEL_PROGRESS = "clauden_reply_progress"
    const val PROGRESS_ID = 771001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_REPLIES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REPLIES,
                    "Ответы Clauden",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Когда Clauden закончил ответ, пока приложение свёрнуто"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 35, 80, 35)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    "Clauden отвечает",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Пока ответ готовится в фоне"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
    }

    fun progress(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Clauden отвечает…")
            .setContentText("Можно выйти из приложения — пришлём уведомление, когда ответ будет готов.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun device(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            (title + text).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title.ifBlank { "Clauden" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify((title + text).hashCode(), notification)
        }
    }

    fun reply(context: Context, chatId: String, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val clean = plain(text).take(240).ifBlank { "Ответ готов" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ScheduledPromptReceiver.EXTRA_OPEN_CHAT, chatId)
            data = Uri.parse("clauden://chat/$chatId")
        }
        val pending = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title.ifBlank { "Clauden ответил" })
            .setContentText(clean)
            .setStyle(NotificationCompat.BigTextStyle().bigText(clean))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(chatId.hashCode(), notification)
    }

    fun cancel(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(chatId.hashCode())
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun plain(value: String): String = value
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("[*_`#>]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
