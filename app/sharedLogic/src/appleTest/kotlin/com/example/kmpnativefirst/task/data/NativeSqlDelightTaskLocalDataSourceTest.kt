package com.example.kmpnativefirst.task.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.kmpnativefirst.task.data.local.db.TaskDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeSqlDelightTaskLocalDataSourceTest {
    @Test
    fun persistsPendingMutationsInNativeSqlite() = runTest {
        val driver = NativeSqliteDriver(
            schema = TaskDatabase.Schema,
            name = "task-cache-test",
            onConfiguration = { configuration ->
                configuration.copy(inMemory = true)
            },
        )
        val source = SqlDelightTaskLocalDataSource(driver)

        try {
            source.applyLabelCreate(
                label = taskLabel(id = LABEL_ID, revision = 0),
                operationId = "create-native-label",
                enqueuedAt = TEST_INSTANT,
            )
            source.applyCreate(
                task = task(
                    title = "Native persistence",
                    labelIds = listOf(LABEL_ID),
                    revision = 0,
                ),
                operationId = "create-native-task",
                enqueuedAt = TEST_INSTANT,
            )
            source.applyProjectCreate(
                project = taskProject(revision = 0),
                operationId = "create-native-project",
                enqueuedAt = TEST_INSTANT,
            )

            assertEquals(
                TaskSyncState.PENDING,
                source.observeTasks().first().single().syncState,
            )
            assertEquals(
                listOf(LABEL_ID),
                source.observeTasks().first().single().task.labelIds,
            )
            assertEquals(TaskMutationKind.CREATE, source.nextMutation()?.kind)
            assertEquals(
                TaskMutationKind.CREATE,
                source.nextProjectMutation(false)?.kind,
            )
            assertEquals(
                TaskMutationKind.CREATE,
                source.nextLabelMutation(false)?.kind,
            )
        } finally {
            source.close()
        }
    }

    private companion object {
        const val LABEL_ID = "33333333-3333-4333-8333-333333333333"
    }
}
