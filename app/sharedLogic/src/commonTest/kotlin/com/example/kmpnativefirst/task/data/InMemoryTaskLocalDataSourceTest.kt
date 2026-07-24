package com.example.kmpnativefirst.task.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTaskLocalDataSourceTest {
    @Test
    fun coalescesCreateAndEditsIntoOneCreateMutation() = runTest {
        val source = InMemoryTaskLocalDataSource()
        val created = task(revision = 0)

        source.applyCreate(created, operationId = "create", enqueuedAt = TEST_INSTANT)
        source.applyUpdate(
            created.copy(title = "Edited offline"),
            operationId = "edit",
            enqueuedAt = TEST_INSTANT,
        )

        val mutation = requireNotNull(source.nextMutation())
        assertEquals(TaskMutationKind.CREATE, mutation.kind)
        assertNull(mutation.base)
        assertEquals("Edited offline", mutation.desired?.title)
        assertEquals(1, source.pendingCount())
        assertEquals(TaskSyncState.PENDING, source.findTask(created.id)?.syncState)
    }

    @Test
    fun removesOfflineCreateWhenDeletedBeforeSync() = runTest {
        val source = InMemoryTaskLocalDataSource()
        val created = task(revision = 0)

        source.applyCreate(created, operationId = "create", enqueuedAt = TEST_INSTANT)
        source.applyDelete(created.id, operationId = "delete", enqueuedAt = TEST_INSTANT)

        assertNull(source.findTask(created.id))
        assertNull(source.nextMutation())
    }

    @Test
    fun rebasesAnEditMadeWhileAnOlderMutationWasInFlight() = runTest {
        val source = InMemoryTaskLocalDataSource(listOf(task()))
        source.applyUpdate(
            task(title = "First edit"),
            operationId = "first",
            enqueuedAt = TEST_INSTANT,
        )
        val inFlight = requireNotNull(source.nextMutation())
        source.applyUpdate(
            task(title = "Second edit"),
            operationId = "second",
            enqueuedAt = TEST_INSTANT,
        )

        source.acknowledgeMutation(
            mutation = inFlight,
            remoteTask = task(title = "First edit", revision = 2),
        )

        val rebased = requireNotNull(source.nextMutation())
        assertEquals("second", rebased.operationId)
        assertEquals(2, rebased.base?.revision)
        assertEquals(2, rebased.desired?.revision)
        assertEquals("Second edit", rebased.desired?.title)
    }

    @Test
    fun remoteSnapshotPreservesPendingTasksAndRemovesMissingSyncedTasks() = runTest {
        val first = task(id = TASK_ID_1)
        val second = task(id = TASK_ID_2)
        val source = InMemoryTaskLocalDataSource(listOf(first, second))
        source.applyUpdate(
            first.copy(title = "Pending"),
            operationId = "edit",
            enqueuedAt = TEST_INSTANT,
        )

        source.replaceRemoteSnapshot(
            listOf(task(id = TASK_ID_3, title = "Remote")),
        )

        val items = source.observeTasks().first()
        assertEquals(setOf(TASK_ID_1, TASK_ID_3), items.map { it.task.id }.toSet())
        assertEquals(TaskSyncState.PENDING, source.findTask(TASK_ID_1)?.syncState)
        assertNull(source.findTask(TASK_ID_2))
    }

    @Test
    fun keepsConflictWhenMergeResolutionHasNoSourceTask() = runTest {
        val source = InMemoryTaskLocalDataSource(listOf(task()))
        source.applyUpdate(
            task(title = "Local"),
            operationId = "edit",
            enqueuedAt = TEST_INSTANT,
        )
        val mutation = requireNotNull(source.nextMutation())
        source.recordConflict(
            mutation,
            TaskConflict(
                taskId = TASK_ID_1,
                mutationKind = TaskMutationKind.UPDATE,
                base = null,
                local = null,
                remote = null,
                conflictingFields = setOf(TaskConflictField.DELETION),
                detectedAt = TEST_INSTANT,
            ),
        )

        assertFailsWith<CachedTaskNotFoundException> {
            source.resolveConflict(
                TASK_ID_1,
                TaskConflictResolution.Merge(TaskEdit(title = "Recovered")),
                operationId = "resolve",
                enqueuedAt = TEST_INSTANT,
            )
        }

        assertEquals(1, source.conflictCount())
        assertTrue(source.observeConflicts().first().isNotEmpty())
    }

    @Test
    fun restoresPersistedTasksAndPendingMutations() = runTest {
        var savedState: TaskLocalState? = null
        val source = InMemoryTaskLocalDataSource(
            persistState = { savedState = it },
        )
        val created = task(revision = 0)

        source.applyCreate(created, operationId = "create", enqueuedAt = TEST_INSTANT)
        val restored = InMemoryTaskLocalDataSource(
            restoredState = requireNotNull(savedState),
        )

        assertEquals(created, restored.findTask(created.id)?.task)
        assertEquals(TaskSyncState.PENDING, restored.findTask(created.id)?.syncState)
        assertEquals("create", restored.nextMutation()?.operationId)
    }

    @Test
    fun rollsBackMutationWhenPersistenceFails() = runTest {
        val source = InMemoryTaskLocalDataSource(
            persistState = { error("Storage unavailable") },
        )
        val created = task(revision = 0)

        assertFailsWith<IllegalStateException> {
            source.applyCreate(created, operationId = "create", enqueuedAt = TEST_INSTANT)
        }

        assertNull(source.findTask(created.id))
        assertEquals(0, source.pendingCount())
        assertTrue(source.observeTasks().first().isEmpty())
    }

    @Test
    fun coalescesProjectCreationAndEditsIntoOneMutation() = runTest {
        val source = InMemoryTaskLocalDataSource()
        val project = taskProject(revision = 0)

        source.applyProjectCreate(project, "create-project", TEST_INSTANT)
        source.applyProjectUpdate(
            project.copy(name = "Edited offline"),
            "edit-project",
            TEST_INSTANT,
        )

        val mutation = requireNotNull(
            source.nextProjectMutation(deletionsOnly = false),
        )
        assertEquals(TaskMutationKind.CREATE, mutation.kind)
        assertNull(mutation.base)
        assertEquals("Edited offline", mutation.desired?.name)
        assertEquals(1, source.pendingProjectCount())
    }

    @Test
    fun deletingAProjectAtomicallyUnassignsItsTasks() = runTest {
        val project = taskProject()
        val assigned = task(projectId = project.id)
        val source = InMemoryTaskLocalDataSource(
            initialTasks = listOf(assigned),
            initialProjects = listOf(project),
        )

        source.applyProjectDelete(
            projectId = project.id,
            operationId = "delete-project",
            taskOperationId = { "unassign-task" },
            enqueuedAt = TEST_INSTANT,
        )

        assertNull(source.findProject(project.id))
        assertNull(source.findTask(assigned.id)?.task?.projectId)
        assertEquals(TaskMutationKind.UPDATE, source.nextMutation()?.kind)
        assertEquals(
            TaskMutationKind.DELETE,
            source.nextProjectMutation(deletionsOnly = true)?.kind,
        )
    }

    @Test
    fun restoresPersistedProjectsAndProjectMutations() = runTest {
        var savedState: TaskLocalState? = null
        val source = InMemoryTaskLocalDataSource(
            persistState = { savedState = it },
        )
        val project = taskProject(revision = 0)

        source.applyProjectCreate(project, "create-project", TEST_INSTANT)
        val restored = InMemoryTaskLocalDataSource(
            restoredState = requireNotNull(savedState),
        )

        assertEquals(project, restored.findProject(project.id)?.project)
        assertEquals(
            TaskSyncState.PENDING,
            restored.findProject(project.id)?.syncState,
        )
        assertEquals(
            "create-project",
            restored.nextProjectMutation(deletionsOnly = false)?.operationId,
        )
    }
}
