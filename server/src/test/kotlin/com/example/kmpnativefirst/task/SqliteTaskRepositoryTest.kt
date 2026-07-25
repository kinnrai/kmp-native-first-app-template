package com.example.kmpnativefirst.task

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.sql.DriverManager
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class SqliteTaskRepositoryTest {
    @Test
    fun persistsTasksAndAppliesRevisionChecks() = runBlocking {
        val databaseFile = Files.createTempDirectory("task-repository-test")
            .resolve("tasks.db")
        val jdbcUrl = "jdbc:sqlite:$databaseFile"
        val repository = SqliteTaskRepository.open(jdbcUrl)
        val original = task()
        assertEquals(TaskInsertResult.Inserted(original), repository.insert(original))
        assertEquals(TaskInsertResult.AlreadyExists, repository.insert(original))

        val reopenedRepository = SqliteTaskRepository.open(jdbcUrl)
        assertEquals(original, reopenedRepository.find(original.id))

        val replacement = original.copy(
            title = "Updated title",
            revision = 2,
            updatedAt = Instant.parse("2026-07-23T11:00:00Z"),
        )
        val result = reopenedRepository.replace(replacement, expectedRevision = 1)
        assertEquals(TaskMutationResult.Updated(replacement), result)
        assertEquals(
            TaskMutationResult.Conflict,
            reopenedRepository.replace(replacement.copy(title = "Stale"), expectedRevision = 1),
        )
        assertEquals(
            TaskDeleteResult.Conflict,
            reopenedRepository.delete(original.id, expectedRevision = 1),
        )
        assertEquals(
            TaskDeleteResult.Deleted,
            reopenedRepository.delete(original.id, expectedRevision = 2),
        )
    }

    @Test
    fun deletesOnlyCompletedTasks() = runBlocking {
        val databaseFile = Files.createTempDirectory("task-repository-test")
            .resolve("tasks.db")
        val repository = SqliteTaskRepository.open("jdbc:sqlite:$databaseFile")
        repository.insert(task(id = "active"))
        repository.insert(task(id = "done", isCompleted = true))

        assertEquals(1, repository.deleteCompleted())
        assertNotNull(repository.find("active"))
        assertNull(repository.find("done"))
    }

    @Test
    fun migratesLegacyTaskTableBeforePersistingDateOnlyDeadlines() = runBlocking {
        val databaseFile = Files.createTempDirectory("task-repository-migration-test")
            .resolve("tasks.db")
        val jdbcUrl = "jdbc:sqlite:$databaseFile"
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE tasks (
                        id VARCHAR(36) NOT NULL PRIMARY KEY,
                        title VARCHAR(120) NOT NULL,
                        notes TEXT,
                        priority VARCHAR(16) NOT NULL,
                        due_at_epoch_millis BIGINT,
                        is_completed BOOLEAN NOT NULL,
                        created_at_epoch_millis BIGINT NOT NULL,
                        updated_at_epoch_millis BIGINT NOT NULL,
                        revision BIGINT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val repository = SqliteTaskRepository.open(jdbcUrl)
        val dueDate = LocalDate(2026, 8, 1)
        val original = task().copy(dueDate = dueDate, dueAt = null)

        assertEquals(TaskInsertResult.Inserted(original), repository.insert(original))
        assertEquals(dueDate, repository.find(original.id)?.dueDate)
    }

    private fun task(
        id: String = "task-1",
        isCompleted: Boolean = false,
    ): Task = Task(
        id = id,
        title = "Persist me",
        priority = TaskPriority.MEDIUM,
        dueAt = Instant.parse("2026-08-01T09:00:00Z"),
        isCompleted = isCompleted,
        createdAt = Instant.parse("2026-07-23T10:00:00Z"),
        updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
        revision = 1,
    )
}
