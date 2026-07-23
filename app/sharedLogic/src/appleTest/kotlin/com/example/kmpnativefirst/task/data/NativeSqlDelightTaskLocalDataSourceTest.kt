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
            source.applyCreate(
                task = task(title = "Native persistence", revision = 0),
                operationId = "create-native-task",
                enqueuedAt = TEST_INSTANT,
            )

            assertEquals(
                TaskSyncState.PENDING,
                source.observeTasks().first().single().syncState,
            )
            assertEquals(TaskMutationKind.CREATE, source.nextMutation()?.kind)
        } finally {
            source.close()
        }
    }
}
