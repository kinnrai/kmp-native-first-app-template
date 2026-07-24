package com.example.kmpnativefirst.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.kmpnativefirst.R
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskSyncState
import com.example.kmpnativefirst.task.reminder.TaskReminderScheduler
import kotlin.time.Clock
import kotlin.time.Instant

class AndroidTaskReminderScheduler(
    context: Context,
) : TaskReminderScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = requireNotNull(
        applicationContext.getSystemService(AlarmManager::class.java),
    )
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun reconcile(tasks: List<TaskItem>) {
        synchronized(this) {
            createNotificationChannel()

            val now = Clock.System.now()
            val scheduledTasks = tasks.remindersToSchedule(now)
            val previousIds = preferences.getStringSet(KEY_SCHEDULED_IDS, emptySet())
                .orEmpty()

            (previousIds - scheduledTasks.keys).forEach(::cancel)
            scheduledTasks.values.forEach(::schedule)

            preferences.edit {
                putStringSet(KEY_SCHEDULED_IDS, scheduledTasks.keys)
            }
        }
    }

    private fun schedule(task: Task) {
        val reminderAt = task.reminderAt ?: return
        val intent = reminderIntent(
            context = applicationContext,
            taskId = task.id,
            title = task.title,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderAt.toEpochMilliseconds(),
            pendingIntent,
        )
    }

    private fun cancel(taskId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            taskId.hashCode(),
            reminderIntent(applicationContext, taskId, title = null),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = applicationContext.getString(
                R.string.notification_channel_description,
            )
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    companion object {
        internal const val CHANNEL_ID = "task-reminders"
        internal const val ACTION_DELIVER = "com.example.kmpnativefirst.action.DELIVER_REMINDER"
        internal const val EXTRA_TASK_ID = "com.example.kmpnativefirst.extra.TASK_ID"
        internal const val EXTRA_TASK_TITLE = "com.example.kmpnativefirst.extra.TASK_TITLE"
        internal const val PREFERENCES_NAME = "task-reminders"
        internal const val KEY_SCHEDULED_IDS = "scheduled-task-ids"

        internal fun reminderUri(taskId: String): Uri =
            "kmpnativefirst://task-reminder/${Uri.encode(taskId)}".toUri()

        internal fun reminderIntent(
            context: Context,
            taskId: String,
            title: String?,
        ): Intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_DELIVER
            data = reminderUri(taskId)
            putExtra(EXTRA_TASK_ID, taskId)
            title?.let { putExtra(EXTRA_TASK_TITLE, it) }
        }
    }
}

internal fun List<TaskItem>.remindersToSchedule(now: Instant): Map<String, Task> =
    asSequence()
        .filter { it.syncState != TaskSyncState.CONFLICT }
        .map(TaskItem::task)
        .filter { task ->
            !task.isCompleted && task.reminderAt?.let { it > now } == true
        }
        .associateBy(Task::id)
