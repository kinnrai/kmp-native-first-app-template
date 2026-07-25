package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProjectColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebTaskStoreTest {
    @Test
    fun mapsProjectsAndConflictsToExportedValues() {
        val item = TaskProjectItem(
            project = taskProject(
                name = "Personal",
                color = TaskProjectColor.PURPLE,
            ),
            syncState = TaskSyncState.PENDING,
        ).toWebTaskProjectItem()

        assertEquals("Personal", item.project.name)
        assertEquals("purple", item.project.color)
        assertEquals("pending", item.syncState)

        val conflict = TaskProjectConflict(
            projectId = PROJECT_ID_2,
            mutationKind = TaskMutationKind.UPDATE,
            base = taskProject(id = PROJECT_ID_2, name = "Work"),
            local = taskProject(id = PROJECT_ID_2, name = "Client work"),
            remote = taskProject(id = PROJECT_ID_2, name = "Office"),
            conflictingFields = setOf(TaskProjectConflictField.NAME),
            detectedAt = TEST_INSTANT,
        ).toWebTaskProjectConflict()

        assertEquals(PROJECT_ID_2, conflict.projectId)
        assertEquals("update", conflict.mutationKind)
        assertEquals("Client work", conflict.local?.name)
        assertEquals("Office", conflict.remote?.name)
        assertEquals(listOf("name"), conflict.conflictingFields.toList())
    }

    @Test
    fun mapsProjectEditorValuesAndConflictChoices() {
        assertEquals(
            TaskProjectDraft(
                name = "Personal",
                color = TaskProjectColor.PURPLE,
            ),
            webTaskProjectDraft(name = "Personal", color = "purple"),
        )
        assertEquals(
            TaskProjectEdit(
                name = "Home",
                color = TaskProjectColor.GREEN,
            ),
            webTaskProjectEdit(name = "Home", color = "green"),
        )

        val merge = assertIs<TaskProjectConflictResolution.Merge>(
            webTaskProjectMergeResolution(
                name = "Merged",
                color = "rose",
            ),
        )
        assertEquals("Merged", merge.edit.name)
        assertEquals(TaskProjectColor.ROSE, merge.edit.color)
    }
}
