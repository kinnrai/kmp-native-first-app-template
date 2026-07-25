package com.example.kmpnativefirst.task

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TaskContractTest {
    @Test
    fun normalizesWhitespaceAndEmptyNotes() {
        val input = TaskValidator.normalize(
            title = "  Plan the release  ",
            notes = "   ",
        )

        assertEquals("Plan the release", input.title)
        assertNull(input.notes)
    }

    @Test
    fun validatesAllInvalidFields() {
        val issues = TaskValidator.validate(
            title = " ",
            notes = "x".repeat(TaskConstraints.MAX_NOTES_LENGTH + 1),
            expectedRevision = 0,
        )

        assertEquals(
            listOf(
                TaskValidationIssue(TaskField.TITLE, TaskValidationCode.REQUIRED),
                TaskValidationIssue(TaskField.NOTES, TaskValidationCode.TOO_LONG),
                TaskValidationIssue(TaskField.EXPECTED_REVISION, TaskValidationCode.INVALID),
            ),
            issues,
        )
    }

    @Test
    fun requiresClientGeneratedUuidForCreate() {
        val issues = TaskValidator.validateCreate(
            id = "not-a-uuid",
            title = "Valid title",
            notes = null,
        )

        assertEquals(
            listOf(TaskValidationIssue(TaskField.ID, TaskValidationCode.INVALID)),
            issues,
        )
    }

    @Test
    fun serializesTaskContractWithStableWireValues() {
        val task = Task(
            id = "task-1",
            title = "Ship the app",
            priority = TaskPriority.HIGH,
            dueAt = Instant.parse("2026-08-01T09:00:00Z"),
            createdAt = Instant.parse("2026-07-23T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
            revision = 1,
        )

        val encoded = Json.encodeToString(Task.serializer(), task)
        val decoded = Json.decodeFromString(Task.serializer(), encoded)

        assertEquals(task, decoded)
        assertEquals(true, encoded.contains("\"priority\":\"high\""))
        assertEquals(true, encoded.contains("\"dueAt\":\"2026-08-01T09:00:00Z\""))
    }
}
