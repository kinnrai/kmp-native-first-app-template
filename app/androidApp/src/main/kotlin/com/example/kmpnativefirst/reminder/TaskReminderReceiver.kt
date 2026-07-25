package com.example.kmpnativefirst.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.kmpnativefirst.MainActivity
import com.example.kmpnativefirst.R

class TaskReminderReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_ID)
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
            ?: return
        if (!notificationManager.areNotificationsEnabled()) return

        val title = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_TITLE)
            ?: context.getString(R.string.app_name)
        val openTaskIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = AndroidTaskReminderScheduler.reminderUri(taskId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AndroidTaskReminderScheduler.EXTRA_TASK_ID, taskId)
        }
        val openTaskPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, AndroidTaskReminderScheduler.CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val notification = notificationBuilder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_reminder_title))
            .setContentText(title)
            .setStyle(Notification.BigTextStyle().bigText(title))
            .setContentIntent(openTaskPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(taskId.hashCode(), notification)
    }
}
