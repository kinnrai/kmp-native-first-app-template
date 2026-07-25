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
        val project = TaskProject(
            id = "project-1",
            name = "Migrated project",
            createdAt = Instant.parse("2026-07-23T09:00:00Z"),
            updatedAt = Instant.parse("2026-07-23T09:00:00Z"),
            revision = 1,
        )
        assertEquals(
            TaskProjectInsertResult.Inserted(project),
            repository.insertProject(project),
        )
        val original = task().copy(
            projectId = project.id,
            dueDate = dueDate,
            dueAt = null,
            reminderAt = Instant.parse("2026-08-01T08:30:00Z"),
        )

        assertEquals(TaskInsertResult.Inserted(original), repository.insert(original))
        assertEquals(dueDate, repository.find(original.id)?.dueDate)
        assertEquals(project.id, repository.find(original.id)?.projectId)
        assertEquals(original.reminderAt, repository.find(original.id)?.reminderAt)
    }

    @Test
    fun persistsProjectsAndAppliesRevisionChecks() = runBlocking {
        val databaseFile = Files.createTempDirectory("project-repository-test")
            .resolve("tasks.db")
        val jdbcUrl = "jdbc:sqlite:$databaseFile"
        val repository = SqliteTaskRepository.open(jdbcUrl)
        assertEquals(
            TaskInsertResult.InvalidProject,
            repository.insert(
                task(id = "orphan").copy(projectId = "missing-project"),
            ),
        )
        assertNull(repository.find("orphan"))
        val original = TaskProject(
            id = "project-1",
            name = "Personal",
            color = TaskProjectColor.ROSE,
            createdAt = Instant.parse("2026-07-23T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
            revision = 1,
        )
        assertEquals(
            TaskProjectInsertResult.Inserted(original),
            repository.insertProject(original),
        )
        val assignedTask = task().copy(projectId = original.id)
        assertEquals(
            TaskInsertResult.Inserted(assignedTask),
            repository.insert(assignedTask),
        )
        assertEquals(
            TaskMutationResult.InvalidProject,
            repository.replace(
                task = assignedTask.copy(
                    projectId = "missing-project",
                    revision = 2,
                ),
                expectedRevision = 1,
            ),
        )
        assertEquals(original.id, repository.find(assignedTask.id)?.projectId)
        assertEquals(
            TaskProjectInsertResult.AlreadyExists,
            repository.insertProject(original),
        )

        val reopenedRepository = SqliteTaskRepository.open(jdbcUrl)
        assertEquals(original, reopenedRepository.findProject(original.id))
        val replacement = original.copy(
            name = "Home",
            revision = 2,
            updatedAt = Instant.parse("2026-07-23T11:00:00Z"),
        )
        assertEquals(
            TaskProjectMutationResult.Updated(replacement),
            reopenedRepository.replaceProject(replacement, expectedRevision = 1),
        )
        assertEquals(
            TaskProjectMutationResult.Conflict,
            reopenedRepository.replaceProject(
                replacement.copy(name = "Stale"),
                expectedRevision = 1,
            ),
        )
        assertEquals(
            TaskProjectDeleteResult.Deleted(reassignedTaskCount = 1),
            reopenedRepository.deleteProject(
                id = original.id,
                expectedRevision = 2,
                reassignedTasksUpdatedAt = Instant.parse("2026-07-23T12:00:00Z"),
            ),
        )
        val reassignedTask = requireNotNull(reopenedRepository.find(assignedTask.id))
        assertNull(reassignedTask.projectId)
        assertEquals(2, reassignedTask.revision)
        assertEquals(
            Instant.parse("2026-07-23T12:00:00Z"),
            reassignedTask.updatedAt,
        )
    }

    @Test
    fun persistsLabelsAssignmentsAndAppliesRevisionChecks() = runBlocking {
        val databaseFile = Files.createTempDirectory("label-repository-test")
            .resolve("tasks.db")
        val jdbcUrl = "jdbc:sqlite:$databaseFile"
        val repository = SqliteTaskRepository.open(jdbcUrl)
        val original = TaskLabel(
            id = "label-1",
            name = "Focus",
            color = TaskLabelColor.PURPLE,
            createdAt = Instant.parse("2026-07-23T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
            revision = 1,
        )
        assertEquals(
            TaskLabelInsertResult.Inserted(original),
            repository.insertLabel(original),
        )
        assertEquals(
            TaskInsertResult.InvalidLabels,
            repository.insert(
                task(id = "invalid-label").copy(labelIds = listOf("missing-label")),
            ),
        )
        assertNull(repository.find("invalid-label"))

        val assignedTask = task().copy(labelIds = listOf(original.id))
        assertEquals(
            TaskInsertResult.Inserted(assignedTask),
            repository.insert(assignedTask),
        )
        assertEquals(listOf(original.id), repository.find(assignedTask.id)?.labelIds)
        assertEquals(
            TaskMutationResult.InvalidLabels,
            repository.replace(
                task = assignedTask.copy(
                    labelIds = listOf("missing-label"),
                    revision = 2,
                ),
                expectedRevision = 1,
            ),
        )
        assertEquals(listOf(original.id), repository.find(assignedTask.id)?.labelIds)

        val reopenedRepository = SqliteTaskRepository.open(jdbcUrl)
        assertEquals(original, reopenedRepository.findLabel(original.id))
        assertEquals(listOf(original), reopenedRepository.listLabels())
        assertEquals(listOf(original.id), reopenedRepository.find(assignedTask.id)?.labelIds)

        val replacement = original.copy(
            name = "Deep work",
            revision = 2,
            updatedAt = Instant.parse("2026-07-23T11:00:00Z"),
        )
        assertEquals(
            TaskLabelMutationResult.Updated(replacement),
            reopenedRepository.replaceLabel(replacement, expectedRevision = 1),
        )
        assertEquals(
            TaskLabelMutationResult.Conflict,
            reopenedRepository.replaceLabel(
                replacement.copy(name = "Stale"),
                expectedRevision = 1,
            ),
        )
        val deletionTime = Instant.parse("2026-07-23T12:00:00Z")
        assertEquals(
            TaskLabelDeleteResult.Deleted(affectedTaskCount = 1),
            reopenedRepository.deleteLabel(
                id = original.id,
                expectedRevision = replacement.revision,
                affectedTasksUpdatedAt = deletionTime,
            ),
        )
        val detachedTask = requireNotNull(reopenedRepository.find(assignedTask.id))
        assertEquals(emptyList(), detachedTask.labelIds)
        assertEquals(2, detachedTask.revision)
        assertEquals(deletionTime, detachedTask.updatedAt)
    }

    private fun task(
        id: String = "task-1",
        isCompleted: Boolean = false,
    ): Task = Task(
        id = id,
        title = "Persist me",
        priority = TaskPriority.MEDIUM,
        dueAt = Instant.parse("2026-08-01T09:00:00Z"),
        reminderAt = Instant.parse("2026-08-01T08:30:00Z"),
        isCompleted = isCompleted,
        createdAt = Instant.parse("2026-07-23T10:00:00Z"),
        updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
        revision = 1,
    )
}
