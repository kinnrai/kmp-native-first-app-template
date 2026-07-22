package com.example.kmpnativefirst.task

import kotlinx.coroutines.runBlocking
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
