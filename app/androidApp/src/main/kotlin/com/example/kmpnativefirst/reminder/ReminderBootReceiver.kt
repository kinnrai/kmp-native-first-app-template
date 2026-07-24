package com.example.kmpnativefirst.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kmpnativefirst.BuildConfig
import com.example.kmpnativefirst.task.data.TaskRepository
import com.example.kmpnativefirst.task.data.createTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var repository: TaskRepository? = null
            try {
                repository = createTaskRepository(
                    context = context.applicationContext,
                    baseUrl = BuildConfig.TASK_API_BASE_URL,
                )
                val tasks = repository.tasks.first()
                AndroidTaskReminderScheduler(context).reconcile(tasks)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to restore task reminders", error)
            } finally {
                runCatching { repository?.close() }
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ReminderBootReceiver"
    }
}
