package com.example.kmpnativefirst.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class TaskProjectServiceTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-07-23T10:00:00Z")
    }

    @Test
    fun createsNormalizesAndUpdatesProjects() = runTest {
        val service = TaskProjectService(InMemoryTaskRepository(), clock)
        val created = service.create(
            CreateTaskProjectRequest(
                id = PROJECT_ID,
                name = "  Personal  ",
                color = TaskProjectColor.PURPLE,
            ),
        )

        assertEquals("Personal", created.name)
        assertEquals(TaskProjectColor.PURPLE, created.color)
        assertEquals(1, created.revision)
        assertEquals(listOf(created), service.list().items)

        val updated = service.replace(
            id = created.id,
            request = ReplaceTaskProjectRequest(
                name = "Home",
                color = TaskProjectColor.GREEN,
                expectedRevision = created.revision,
            ),
        )
        assertEquals("Home", updated.name)
        assertEquals(2, updated.revision)
    }

    @Test
    fun reportsValidationAndOptimisticConcurrencyFailures() = runTest {
        val service = TaskProjectService(InMemoryTaskRepository(), clock)
        assertFailsWith<TaskProjectValidationException> {
            service.create(CreateTaskProjectRequest(id = "invalid", name = " "))
        }

        val created = service.create(
            CreateTaskProjectRequest(id = PROJECT_ID, name = "Personal"),
        )
        service.replace(
            id = created.id,
            request = ReplaceTaskProjectRequest(
                name = "Home",
                expectedRevision = created.revision,
            ),
        )
        assertFailsWith<TaskProjectConflictException> {
            service.replace(
                id = created.id,
                request = ReplaceTaskProjectRequest(
                    name = "Stale",
                    expectedRevision = created.revision,
                ),
            )
        }
        assertFailsWith<TaskProjectValidationException> {
            service.delete(created.id, expectedRevision = 0)
        }
    }

    private companion object {
        const val PROJECT_ID = "22222222-2222-4222-8222-222222222222"
    }
}
