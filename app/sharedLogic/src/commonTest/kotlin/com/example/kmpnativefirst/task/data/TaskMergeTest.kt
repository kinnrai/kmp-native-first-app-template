package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskPriority
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
}
