package com.example.kmpnativefirst.task.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class IndexedDbTaskStateStoreTest {
    @Test
    fun persistsOfflineStateAcrossDatabaseConnections() = runTest {
        val databaseName = "task-store-test-${Uuid.random()}"
        val firstConnection = IndexedDbTaskStateStore.open(databaseName)
        val source = InMemoryTaskLocalDataSource(
            persistState = firstConnection::save,
            closeState = firstConnection::close,
        )
        val created = task(revision = 0)
        val project = taskProject(revision = 0)

        source.applyCreate(created, operationId = "create", enqueuedAt = TEST_INSTANT)
        source.applyProjectCreate(
            project,
            operationId = "create-project",
            enqueuedAt = TEST_INSTANT,
        )
        source.close()

        val secondConnection = IndexedDbTaskStateStore.open(databaseName)
        val restoredState = assertNotNull(secondConnection.load())
        val restored = InMemoryTaskLocalDataSource(
            restoredState = restoredState,
            closeState = secondConnection::close,
        )

        assertEquals(created, restored.findTask(created.id)?.task)
        assertEquals(TaskSyncState.PENDING, restored.findTask(created.id)?.syncState)
        assertEquals("create", restored.nextMutation()?.operationId)
        assertEquals(project, restored.findProject(project.id)?.project)
        assertEquals(
            "create-project",
            restored.nextProjectMutation(false)?.operationId,
        )
        restored.close()
    }
}
