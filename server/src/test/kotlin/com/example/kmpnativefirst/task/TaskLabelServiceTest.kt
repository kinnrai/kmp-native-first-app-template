package com.example.kmpnativefirst.task

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

class TaskLabelServiceTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-07-23T10:00:00Z")
    }

    @Test
    fun createsNormalizesAndUpdatesLabels() = runTest {
        val service = TaskLabelService(InMemoryTaskRepository(), clock)
        val created = service.create(
            CreateTaskLabelRequest(
                id = LABEL_ID,
                name = "  Focus  ",
                color = TaskLabelColor.PURPLE,
            ),
        )

        assertEquals("Focus", created.name)
        assertEquals(TaskLabelColor.PURPLE, created.color)
        assertEquals(1, created.revision)
        assertEquals(listOf(created), service.list().items)

        val updated = service.replace(
            id = created.id,
            request = ReplaceTaskLabelRequest(
                name = "Deep work",
                color = TaskLabelColor.GREEN,
                expectedRevision = created.revision,
            ),
        )
        assertEquals("Deep work", updated.name)
        assertEquals(2, updated.revision)
    }

    @Test
    fun reportsValidationAndOptimisticConcurrencyFailures() = runTest {
        val service = TaskLabelService(InMemoryTaskRepository(), clock)
        assertFailsWith<TaskLabelValidationException> {
            service.create(CreateTaskLabelRequest(id = "invalid", name = " "))
        }

        val created = service.create(
            CreateTaskLabelRequest(id = LABEL_ID, name = "Focus"),
        )
        service.replace(
            id = created.id,
            request = ReplaceTaskLabelRequest(
                name = "Deep work",
                expectedRevision = created.revision,
            ),
        )
        assertFailsWith<TaskLabelConflictException> {
            service.replace(
                id = created.id,
                request = ReplaceTaskLabelRequest(
                    name = "Stale",
                    expectedRevision = created.revision,
                ),
            )
        }
        assertFailsWith<TaskLabelValidationException> {
            service.delete(created.id, expectedRevision = 0)
        }
    }

    @Test
    fun deletingLabelDetachesItFromAssignedTasks() = runTest {
        val repository = InMemoryTaskRepository()
        val service = TaskLabelService(repository, clock)
        val label = service.create(
            CreateTaskLabelRequest(id = LABEL_ID, name = "Focus"),
        )
        val task = Task(
            id = TASK_ID,
            title = "Plan release",
            labelIds = listOf(label.id),
            createdAt = clock.now(),
            updatedAt = clock.now(),
            revision = 1,
        )
        repository.insert(task)

        service.delete(label.id, expectedRevision = label.revision)

        val detached = requireNotNull(repository.find(task.id))
        assertEquals(emptyList(), detached.labelIds)
        assertEquals(2, detached.revision)
        assertEquals(clock.now(), detached.updatedAt)
    }

    private companion object {
        const val LABEL_ID = "33333333-3333-4333-8333-333333333333"
        const val TASK_ID = "11111111-1111-4111-8111-111111111111"
    }
}
