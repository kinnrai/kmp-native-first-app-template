package com.example.kmpnativefirst.task.reminder

import com.example.kmpnativefirst.task.data.TaskItem

/**
 * Platform-owned scheduling boundary for task reminders.
 *
 * The shared task repository remains the source of truth. Platforms decide how
 * reminders are delivered and whether they can survive the process being
 * closed or suspended.
 */
fun interface TaskReminderScheduler {
    fun reconcile(tasks: List<TaskItem>)
}
