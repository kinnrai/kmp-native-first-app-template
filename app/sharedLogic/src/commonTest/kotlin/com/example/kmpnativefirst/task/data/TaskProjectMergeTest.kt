package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProjectColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TaskProjectMergeTest {
    @Test
    fun mergesIndependentProjectChanges() {
        val base = taskProject(name = "Personal", color = TaskProjectColor.BLUE)
        val local = base.copy(name = "Home")
        val remote = base.copy(color = TaskProjectColor.GREEN, revision = 2)

        val result = assertIs<TaskProjectMergeResult.Merged>(
            TaskProjectMerge.merge(base, local, remote),
        )

        assertEquals("Home", result.project.name)
        assertEquals(TaskProjectColor.GREEN, result.project.color)
        assertEquals(2, result.project.revision)
    }

    @Test
    fun reportsConcurrentChangesToTheSameField() {
        val base = taskProject(name = "Personal")
        val local = base.copy(name = "Home")
        val remote = base.copy(name = "Private", revision = 2)

        val result = assertIs<TaskProjectMergeResult.Conflict>(
            TaskProjectMerge.merge(base, local, remote),
        )

        assertEquals(setOf(TaskProjectConflictField.NAME), result.fields)
    }
}
