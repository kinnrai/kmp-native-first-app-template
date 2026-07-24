package com.example.kmpnativefirst.task

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TaskPlanningTest {
    private val today = LocalDate(2026, 7, 24)
    private val utcPlusEight = UtcOffset(hours = 8).asTimeZone()

    @Test
    fun keepsDateOnlyDeadlinesIndependentOfTimeZones() {
        val task = task(dueDate = today)

        assertEquals(today, TaskPlanning.dueDate(task, TimeZone.UTC))
        assertEquals(today, TaskPlanning.dueDate(task, utcPlusEight))
    }

    @Test
    fun interpretsTimedDeadlinesInTheSelectedTimeZone() {
        val task = task(dueAt = Instant.parse("2026-07-23T17:00:00Z"))

        assertEquals(LocalDate(2026, 7, 23), TaskPlanning.dueDate(task, TimeZone.UTC))
        assertEquals(today, TaskPlanning.dueDate(task, utcPlusEight))
    }

    @Test
    fun todayIncludesOverdueTasksButUpcomingDoesNot() {
        val overdue = task(id = "overdue", dueDate = LocalDate(2026, 7, 23))
        val dueToday = task(id = "today", dueDate = today)
        val upcoming = task(id = "upcoming", dueDate = LocalDate(2026, 7, 25))

        assertTrue(TaskPlanning.matches(overdue, TaskSmartView.TODAY, today, utcPlusEight))
        assertTrue(TaskPlanning.matches(dueToday, TaskSmartView.TODAY, today, utcPlusEight))
        assertFalse(TaskPlanning.matches(upcoming, TaskSmartView.TODAY, today, utcPlusEight))
        assertTrue(TaskPlanning.matches(upcoming, TaskSmartView.UPCOMING, today, utcPlusEight))
    }

    @Test
    fun inboxContainsOnlyUnscheduledActiveTasks() {
        assertTrue(
            TaskPlanning.matches(task(), TaskSmartView.INBOX, today, utcPlusEight),
        )
        assertFalse(
            TaskPlanning.matches(
                task(dueDate = today),
                TaskSmartView.INBOX,
                today,
                utcPlusEight,
            ),
        )
        assertFalse(
            TaskPlanning.matches(
                task(isCompleted = true),
                TaskSmartView.INBOX,
                today,
                utcPlusEight,
            ),
        )
    }

    @Test
    fun selectionUsesDeadlineThenPriorityForStableFocusOrdering() {
        val tasks = listOf(
            task(id = "later", dueDate = LocalDate(2026, 7, 26)),
            task(
                id = "medium",
                priority = TaskPriority.MEDIUM,
                dueDate = today,
            ),
            task(
                id = "high",
                priority = TaskPriority.HIGH,
                dueDate = today,
            ),
        )

        assertEquals(
            listOf("high", "medium", "later"),
            TaskPlanning.select(tasks, TaskSmartView.ALL, today, utcPlusEight).map(Task::id),
        )
    }

    private fun task(
        id: String = "task",
        priority: TaskPriority = TaskPriority.NONE,
        dueDate: LocalDate? = null,
        dueAt: Instant? = null,
        isCompleted: Boolean = false,
    ): Task = Task(
        id = id,
        title = id,
        priority = priority,
        dueDate = dueDate,
        dueAt = dueAt,
        isCompleted = isCompleted,
        createdAt = Instant.parse("2026-07-23T09:00:00Z"),
        updatedAt = Instant.parse("2026-07-23T09:00:00Z"),
        revision = 1,
    )
}
