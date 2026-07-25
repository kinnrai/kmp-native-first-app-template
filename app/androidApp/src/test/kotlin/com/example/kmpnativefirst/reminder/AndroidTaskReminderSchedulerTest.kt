package com.example.kmpnativefirst.reminder

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskSyncState
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class AndroidTaskReminderSchedulerTest {
    @Test
    fun schedulesOnlyFutureActiveNonConflictingReminders() {
        val now = Instant.parse("2026-07-25T08:00:00Z")
        val tasks = listOf(
            taskItem("future", reminderAt = Instant.parse("2026-07-25T09:00:00Z")),
            taskItem("past", reminderAt = Instant.parse("2026-07-25T07:00:00Z")),
            taskItem(
                "completed",
                reminderAt = Instant.parse("2026-07-25T09:00:00Z"),
                isCompleted = true,
            ),
            taskItem(
                "conflict",
                reminderAt = Instant.parse("2026-07-25T09:00:00Z"),
                syncState = TaskSyncState.CONFLICT,
            ),
            taskItem("without-reminder"),
        )

        assertEquals(setOf("future"), tasks.remindersToSchedule(now).keys)
    }
}

private fun taskItem(
    id: String,
    reminderAt: Instant? = null,
    isCompleted: Boolean = false,
    syncState: TaskSyncState = TaskSyncState.SYNCED,
): TaskItem = TaskItem(
    task = Task(
        id = id,
        title = id,
        reminderAt = reminderAt,
        isCompleted = isCompleted,
        createdAt = Instant.parse("2026-07-25T06:00:00Z"),
        updatedAt = Instant.parse("2026-07-25T06:00:00Z"),
        revision = 1,
    ),
    syncState = syncState,
)
