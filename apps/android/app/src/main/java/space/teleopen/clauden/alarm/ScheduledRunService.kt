package space.teleopen.clauden.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScheduledRunService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(ScheduledScheduler.EXTRA_ID).orEmpty()
        startForeground(NOTIFICATION_ID, progressNotification(this))
        Thread {
            runCatching {
                if (id.isNotBlank()) ScheduledPromptRunner.run(applicationContext, id)
            }
            stopSelf(startId)
        }.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun progressNotification(context: Context): android.app.Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Отложенные задачи", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "Выполнение запросов по расписанию" },
                )
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Clauden выполняет запрос")
            .setContentText("Ответ придёт в новый чат и в уведомление")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "clauden_scheduled_run"
        private const val NOTIFICATION_ID = 7311
    }
}
