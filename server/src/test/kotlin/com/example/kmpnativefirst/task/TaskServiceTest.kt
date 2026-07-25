package com.example.kmpnativefirst.task

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class TaskServiceTest {
    private val clock = FixedClock(Instant.parse("2026-07-23T10:00:00Z"))

    @Test
    fun createsNormalizedTaskWithServerOwnedFields() = runBlocking {
        val service = taskService()

        val task = service.create(
            CreateTaskRequest(
                id = TASK_ID,
                title = "  Plan release  ",
                notes = "   ",
                priority = TaskPriority.HIGH,
            ),
        )

        assertEquals(TASK_ID, task.id)
        assertEquals("Plan release", task.title)
        assertNull(task.notes)
        assertEquals(TaskPriority.HIGH, task.priority)
        assertEquals(clock.now(), task.createdAt)
        assertEquals(clock.now(), task.updatedAt)
        assertEquals(1, task.revision)
    }

    @Test
    fun replacesTaskAndRejectsStaleRevision() = runBlocking {
        val service = taskService()
        val created = service.create(CreateTaskRequest(id = TASK_ID, title = "Draft"))
        clock.instant = Instant.parse("2026-07-23T11:00:00Z")

        val updated = service.replace(
            id = created.id,
            request = ReplaceTaskRequest(
                title = "Published",
                isCompleted = true,
                expectedRevision = created.revision,
            ),
        )

        assertEquals("Published", updated.title)
        assertEquals(true, updated.isCompleted)
        assertEquals(2, updated.revision)
        assertEquals(clock.now(), updated.updatedAt)
        assertFailsWith<TaskConflictException> {
            service.replace(
                id = created.id,
                request = ReplaceTaskRequest(
                    title = "Stale update",
                    expectedRevision = created.revision,
                ),
            )
        }
        Unit
    }

    @Test
    fun filtersAndSearchesWhileKeepingGlobalCounts() = runBlocking {
        val repository = InMemoryTaskRepository(
            listOf(
                task(id = "1", title = "Book flights"),
                task(id = "2", title = "Write notes", notes = "Flight details", isCompleted = true),
                task(id = "3", title = "Buy groceries"),
            ),
        )
        val service = TaskService(repository, clock)

        val response = service.list(TaskFilter.COMPLETED, query = "flight")

        assertEquals(listOf("2"), response.items.map(Task::id))
        assertEquals(2, response.activeCount)
        assertEquals(1, response.completedCount)
    }

    @Test
    fun rejectsInvalidInputBeforeWriting() = runBlocking {
        val service = taskService()

        val exception = assertFailsWith<TaskValidationException> {
            service.create(CreateTaskRequest(id = TASK_ID, title = "  "))
        }

        assertEquals(
            listOf(TaskValidationIssue(TaskField.TITLE, TaskValidationCode.REQUIRED)),
            exception.issues,
        )
    }

    @Test
    fun preservesDateOnlyDeadlinesAndRejectsAmbiguousSchedules() = runBlocking {
        val service = taskService()
        val dueDate = LocalDate(2026, 8, 1)

        val created = service.create(
            CreateTaskRequest(
                id = TASK_ID,
                title = "Plan release",
                dueDate = dueDate,
            ),
        )

        assertEquals(dueDate, created.dueDate)
        assertNull(created.dueAt)
        val exception = assertFailsWith<TaskValidationException> {
            service.replace(
                id = created.id,
                request = ReplaceTaskRequest(
                    title = created.title,
                    dueDate = dueDate,
                    dueAt = Instant.parse("2026-08-01T09:00:00Z"),
                    expectedRevision = created.revision,
                ),
            )
        }
        assertEquals(
            listOf(
                TaskValidationIssue(TaskField.DUE_DATE, TaskValidationCode.INVALID),
                TaskValidationIssue(TaskField.DUE_AT, TaskValidationCode.INVALID),
            ),
            exception.issues,
        )
    }

    @Test
    fun rejectsDuplicateClientIdAndStaleDelete() = runBlocking {
        val service = taskService()
        val created = service.create(CreateTaskRequest(id = TASK_ID, title = "Original"))

        assertFailsWith<TaskConflictException> {
            service.create(CreateTaskRequest(id = TASK_ID, title = "Duplicate"))
        }
        assertFailsWith<TaskConflictException> {
            service.delete(created.id, expectedRevision = created.revision + 1)
        }

        service.delete(created.id, expectedRevision = created.revision)
        assertFailsWith<TaskNotFoundException> {
            service.find(created.id)
        }
        Unit
    }

    private fun taskService(): TaskService = TaskService(
        repository = InMemoryTaskRepository(),
        clock = clock,
    )

    private fun task(
        id: String,
        title: String,
        notes: String? = null,
        isCompleted: Boolean = false,
    ): Task = Task(
        id = id,
        title = title,
        notes = notes,
        isCompleted = isCompleted,
        createdAt = clock.now(),
        updatedAt = clock.now(),
        revision = 1,
    )

    private companion object {
        const val TASK_ID = "11111111-1111-4111-8111-111111111111"
    }
}

private class FixedClock(
    var instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
