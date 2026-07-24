package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskPriority
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TaskMergeTest {
    @Test
    fun mergesIndependentLocalAndRemoteChanges() {
        val base = task()
        val local = base.copy(title = "Local title")
        val remote = base.copy(
            priority = TaskPriority.HIGH,
            revision = 2,
        )

        val result = assertIs<TaskMergeResult.Merged>(
            TaskMerge.merge(base, local, remote),
        )

        assertEquals("Local title", result.task.title)
        assertEquals(TaskPriority.HIGH, result.task.priority)
        assertEquals(2, result.task.revision)
    }

    @Test
    fun reportsOnlyFieldsChangedDifferentlyOnBothSides() {
        val base = task(title = "Original", notes = "Base")
        val local = base.copy(title = "Local", notes = "Shared")
        val remote = base.copy(title = "Remote", notes = "Shared", revision = 2)

        val result = assertIs<TaskMergeResult.Conflict>(
            TaskMerge.merge(base, local, remote),
        )

        assertEquals(setOf(TaskConflictField.TITLE), result.fields)
    }

    @Test
    fun mergesDateOnlyDeadlinesIndependentlyFromOtherFields() {
        val base = task()
        val local = base.copy(dueDate = LocalDate(2026, 8, 1))
        val remote = base.copy(priority = TaskPriority.HIGH, revision = 2)

        val result = assertIs<TaskMergeResult.Merged>(
            TaskMerge.merge(base, local, remote),
        )

        assertEquals(LocalDate(2026, 8, 1), result.task.dueDate)
        assertEquals(TaskPriority.HIGH, result.task.priority)
    }

    @Test
    fun reportsConflictingProjectMoves() {
        val base = task(projectId = PROJECT_ID_1)
        val local = base.copy(projectId = PROJECT_ID_2)
        val remote = base.copy(projectId = PROJECT_ID_3, revision = 2)

        val result = assertIs<TaskMergeResult.Conflict>(
            TaskMerge.merge(base, local, remote),
        )

        assertEquals(setOf(TaskConflictField.PROJECT), result.fields)
    }

    private companion object {
        const val PROJECT_ID_1 = "10000000-0000-4000-8000-000000000001"
        const val PROJECT_ID_2 = "10000000-0000-4000-8000-000000000002"
        const val PROJECT_ID_3 = "10000000-0000-4000-8000-000000000003"
    }
}
