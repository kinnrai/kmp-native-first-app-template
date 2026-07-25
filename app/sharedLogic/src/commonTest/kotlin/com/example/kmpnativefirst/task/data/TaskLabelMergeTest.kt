package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskLabelColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TaskLabelMergeTest {
    @Test
    fun mergesIndependentLabelChanges() {
        val base = taskLabel(name = "Focus", color = TaskLabelColor.SLATE)
        val local = base.copy(name = "Deep work")
        val remote = base.copy(color = TaskLabelColor.PURPLE, revision = 2)

        val result = assertIs<TaskLabelMergeResult.Merged>(
            TaskLabelMerge.merge(base, local, remote),
        )

        assertEquals("Deep work", result.label.name)
        assertEquals(TaskLabelColor.PURPLE, result.label.color)
        assertEquals(2, result.label.revision)
    }

    @Test
    fun reportsConflictingLabelRenames() {
        val base = taskLabel(name = "Focus")
        val local = base.copy(name = "Deep work")
        val remote = base.copy(name = "Concentration", revision = 2)

        val result = assertIs<TaskLabelMergeResult.Conflict>(
            TaskLabelMerge.merge(base, local, remote),
        )

        assertEquals(setOf(TaskLabelConflictField.NAME), result.fields)
    }
}
