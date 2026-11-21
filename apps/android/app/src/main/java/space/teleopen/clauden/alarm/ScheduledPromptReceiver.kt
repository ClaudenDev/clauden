package space.teleopen.clauden.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.runBlocking
import space.teleopen.clauden.MainActivity
import space.teleopen.clauden.data.ApiConfig
import space.teleopen.clauden.data.ChatMessage
import space.teleopen.clauden.data.ChatStore
import space.teleopen.clauden.data.Conversation
import space.teleopen.clauden.data.GatewayApi
import space.teleopen.clauden.data.MemoryStore
import space.teleopen.clauden.data.Prefs
import space.teleopen.clauden.data.Provider
import space.teleopen.clauden.data.ScheduledStore
import space.teleopen.clauden.data.ScheduledTask
import space.teleopen.clauden.data.SettingsStore
import java.util.Calendar
import java.util.UUID

class ScheduledPromptReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val id = intent.getStringExtra(ScheduledScheduler.EXTRA_ID).orEmpty()
        if (id.isBlank()) return
        val service = Intent(context, ScheduledRunService::class.java)
            .putExtra(ScheduledScheduler.EXTRA_ID, id)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        }.onFailure {

            val pending = goAsync()
            Thread {
                runCatching { ScheduledPromptRunner.run(context.applicationContext, id) }
                pending.finish()
            }.start()
        }
    }

    companion object {
        const val ACTION_RUN = "space.teleopen.clauden.SCHEDULED_RUN"
        const val EXTRA_OPEN_CHAT = "clauden_open_chat"
        const val CHANNEL_ID = "clauden_scheduled"
    }
}

object ScheduledPromptRunner {

    fun run(context: Context, id: String) {
        val prefs = Prefs(context)
        val account = prefs.authUserId
        if (account.isBlank()) return
        val store = ScheduledStore(context, account)
        val tasks = store.load()
        val task = tasks.firstOrNull { it.id == id } ?: return
        if (task.runAt > System.currentTimeMillis() + 60_000L) return

        val provider = prefs.provider
        val config = ApiConfig(
            provider = provider,
            baseUrl = prefs.effectiveBaseUrl(provider),
            apiKey = prefs.effectiveKey(provider),
        )
        val model = prefs.model.ifBlank { "claude-sonnet-5-5" }
        val settings = SettingsStore(context).load()
        val memories = MemoryStore(context, account).load().take(30)
        val system = buildString {
            appendLine("Это отложенная задача пользователя. Ответ будет прочитан позже в приложении.")
            appendLine("Начни с короткого итога, затем дай детали. Не задавай уточняющих вопросов,")
            appendLine("если ответа можно избежать: используй разумные допущения и явно их назови.")
            if (settings.nickname.isNotBlank()) appendLine("Пользователя зовут ${settings.nickname}.")
            if (settings.work.isNotBlank()) appendLine("Сфера: ${settings.work}.")
            if (settings.preferenceNote.isNotBlank()) {
                appendLine("Инструкции пользователя: ${settings.preferenceNote.take(2000)}")
            }
            if (settings.memory && memories.isNotEmpty()) {
                appendLine("Долговременная память:")
                memories.forEach { appendLine("- ${it.text}") }
            }
        }
        val text = runCatching {
            GatewayApi().complete(
                config = config,
                model = model,
                prompt = task.prompt,
                system = system,
                maxTokens = 4096,
            )
        }.getOrElse { error ->
            finish(context, store, tasks, task, "error", error.message ?: "Ошибка сети", "")
            return
        }

        val now = System.currentTimeMillis()
        val chatId = UUID.randomUUID().toString()
        val conversation = Conversation(
            id = chatId,
            title = task.title.ifBlank { "Отложенная задача" },
            updatedAt = now,
            messages = listOf(
                ChatMessage(role = "user", content = task.prompt, ts = now),
                ChatMessage(role = "assistant", content = text, ts = now + 1),
            ),
        )
        runCatching {
            val chats = ChatStore(context, account)
            val existing = runBlocking { chats.load() }
            runBlocking { chats.save(listOf(conversation) + existing) }
        }
        finish(context, store, tasks, task, "ok", text.take(280), chatId)
        notify(context, task, text, chatId)
    }

    private fun finish(
        context: Context,
        store: ScheduledStore,
        tasks: List<ScheduledTask>,
        task: ScheduledTask,
        status: String,
        result: String,
        chatId: String,
    ) {
        val now = System.currentTimeMillis()
        val next = ScheduledScheduler.nextRun(task.runAt, task.repeat)
        val updated = task.copy(
            runAt = if (next > 0) next else 0L,
            lastRunAt = now,
            lastStatus = status,
            lastResult = result,
            lastChatId = chatId,
        )
        store.save(tasks.map { if (it.id == task.id) updated else it })
        if (updated.runAt > 0) ScheduledScheduler.schedule(context, updated)
        if (status == "error") notifyError(context, task, result)
    }

    private fun notify(context: Context, task: ScheduledTask, text: String, chatId: String) {
        if (quietNow(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)
        val open = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ScheduledPromptReceiver.EXTRA_OPEN_CHAT, chatId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = text.replace('\n', ' ').trim().take(240)
        manager.notify(
            chatId.hashCode(),
            NotificationCompat.Builder(context, ScheduledPromptReceiver.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Готово: ${task.title}")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    private fun notifyError(context: Context, task: ScheduledTask, error: String) {
        if (quietNow(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)
        manager.notify(
            "err-${task.id}".hashCode(),
            NotificationCompat.Builder(context, ScheduledPromptReceiver.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Задача не выполнена: ${task.title}")
                .setContentText(error.take(200))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun quietNow(context: Context): Boolean {
        val settings = SettingsStore(context).load()
        if (!settings.quietHours) return false
        val calendar = Calendar.getInstance()
        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val day = calendar.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }
        return settings.isQuietNow(minutes, day)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(ScheduledPromptReceiver.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                ScheduledPromptReceiver.CHANNEL_ID,
                "Отложенные задачи",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Ответы на запросы, выполненные по расписанию" },
        )
    }
}
