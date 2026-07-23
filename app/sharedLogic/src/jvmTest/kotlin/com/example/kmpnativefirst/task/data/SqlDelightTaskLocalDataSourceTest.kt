package com.example.kmpnativefirst.task.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.kmpnativefirst.task.data.local.db.TaskDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightTaskLocalDataSourceTest {
    @Test
    fun repositoryFactoryRestoresPendingStatusFromDisk() = runTest {
        val path = Files.createTempFile("task-repository-", ".db")
        Files.delete(path)
        try {
            val first = createTaskRepository(
                databasePath = path.toString(),
                baseUrl = "http://127.0.0.1:8080",
            )
            first.create(TaskDraft(title = "Resume later"))
            first.close()

            val reopened = createTaskRepository(
                databasePath = path.toString(),
                baseUrl = "http://127.0.0.1:8080",
            )
            try {
                assertEquals(1, reopened.syncStatus.value.pendingCount)
                assertEquals("Resume later", reopened.tasks.first().single().task.title)
            } finally {
                reopened.close()
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(Path.of("$path-shm"))
            Files.deleteIfExists(Path.of("$path-wal"))
        }
    }

    @Test
    fun persistsCachedTasksAndTheOutboxAcrossReopen() = runTest {
        val path = Files.createTempFile("task-cache-", ".db")
        Files.delete(path)
        try {
            open(path).useSource { source ->
                source.applyCreate(
                    task = task(title = "Persist me", revision = 0),
                    operationId = "operation",
                    enqueuedAt = TEST_INSTANT,
                )
            }

            open(path).useSource { reopened ->
                val item = reopened.observeTasks().first().single()
                assertEquals("Persist me", item.task.title)
                assertEquals(TaskSyncState.PENDING, item.syncState)
                assertEquals(TaskMutationKind.CREATE, reopened.nextMutation()?.kind)
                assertEquals(1, reopened.pendingCount())
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(Path.of("$path-shm"))
            Files.deleteIfExists(Path.of("$path-wal"))
        }
    }

    @Test
    fun appliesTheSameConflictAndResolutionStateMachineToPersistentData() = runTest {
        val path = Files.createTempFile("task-cache-", ".db")
        Files.delete(path)
        try {
            open(path).useSource { source ->
                val base = task()
                source.replaceRemoteSnapshot(listOf(base))
                source.applyUpdate(
                    task = base.copy(title = "Local"),
                    operationId = "edit",
                    enqueuedAt = TEST_INSTANT,
                )
                val mutation = requireNotNull(source.nextMutation())
                val remote = base.copy(title = "Remote", revision = 2)
                source.recordConflict(
                    mutation,
                    TaskConflict(
                        taskId = base.id,
                        mutationKind = TaskMutationKind.UPDATE,
                        base = base,
                        local = mutation.desired,
                        remote = remote,
                        conflictingFields = setOf(TaskConflictField.TITLE),
                        detectedAt = TEST_INSTANT,
                    ),
                )

                source.resolveConflict(
                    taskId = base.id,
                    resolution = TaskConflictResolution.KeepLocal,
                    operationId = "resolve",
                    enqueuedAt = TEST_INSTANT,
                )

                val resolved = requireNotNull(source.nextMutation())
                assertEquals(TaskMutationKind.UPDATE, resolved.kind)
                assertEquals(2, resolved.base?.revision)
                assertEquals(2, resolved.desired?.revision)
                assertEquals("Local", resolved.desired?.title)
                assertEquals(0, source.conflictCount())

                source.acknowledgeMutation(
                    resolved,
                    remote.copy(title = "Local", revision = 3),
                )
                assertEquals(TaskSyncState.SYNCED, source.findTask(base.id)?.syncState)
                assertTrue(source.observeConflicts().first().isEmpty())
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(Path.of("$path-shm"))
            Files.deleteIfExists(Path.of("$path-wal"))
        }
    }

    private fun open(path: Path): SqlDelightTaskLocalDataSource {
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$path",
            properties = Properties(),
            schema = TaskDatabase.Schema,
        )
        return SqlDelightTaskLocalDataSource(driver)
    }

    private suspend inline fun <T> SqlDelightTaskLocalDataSource.useSource(
        block: suspend (SqlDelightTaskLocalDataSource) -> T,
    ): T = try {
        block(this)
    } finally {
        close()
    }
}
