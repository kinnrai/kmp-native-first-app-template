package com.example.kmpnativefirst.task

import kotlinx.datetime.LocalDate
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
        assertEquals(emptyList(), input.labelIds)
    }

    @Test
    fun normalizesAndValidatesLabelAssignments() {
        val firstId = "11111111-1111-4111-8111-111111111111"
        val secondId = "22222222-2222-4222-8222-222222222222"
        val input = TaskValidator.normalize(
            title = "Plan release",
            notes = null,
            labelIds = listOf(secondId, firstId, secondId),
        )

        assertEquals(listOf(firstId, secondId), input.labelIds)
        assertEquals(
            listOf(
                TaskValidationIssue(
                    TaskField.LABEL_IDS,
                    TaskValidationCode.INVALID,
                ),
            ),
            TaskValidator.validate(
                title = "Plan release",
                notes = null,
                labelIds = listOf("not-a-uuid"),
            ),
        )
        assertEquals(
            listOf(
                TaskValidationIssue(
                    TaskField.LABEL_IDS,
                    TaskValidationCode.TOO_MANY,
                ),
            ),
            TaskValidator.validate(
                title = "Plan release",
                notes = null,
                labelIds = (1..TaskConstraints.MAX_LABELS_PER_TASK + 1).map { index ->
                    "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"
                },
            ),
        )
    }

    @Test
    fun validatesAllInvalidFields() {
        val issues = TaskValidator.validate(
            title = " ",
            notes = "x".repeat(TaskConstraints.MAX_NOTES_LENGTH + 1),
            projectId = "not-a-uuid",
            expectedRevision = 0,
        )

        assertEquals(
            listOf(
                TaskValidationIssue(TaskField.TITLE, TaskValidationCode.REQUIRED),
                TaskValidationIssue(TaskField.NOTES, TaskValidationCode.TOO_LONG),
                TaskValidationIssue(TaskField.PROJECT_ID, TaskValidationCode.INVALID),
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
    fun rejectsAmbiguousDateOnlyAndTimedDeadlines() {
        val issues = TaskValidator.validate(
            title = "Plan release",
            notes = null,
            dueDate = LocalDate(2026, 8, 1),
            dueAt = Instant.parse("2026-08-01T09:00:00Z"),
        )

        assertEquals(
            listOf(
                TaskValidationIssue(TaskField.DUE_DATE, TaskValidationCode.INVALID),
                TaskValidationIssue(TaskField.DUE_AT, TaskValidationCode.INVALID),
            ),
            issues,
        )
    }

    @Test
    fun serializesTaskContractWithStableWireValues() {
        val task = Task(
            id = "task-1",
            title = "Ship the app",
            projectId = "22222222-2222-4222-8222-222222222222",
            labelIds = listOf("33333333-3333-4333-8333-333333333333"),
            priority = TaskPriority.HIGH,
            dueDate = LocalDate(2026, 8, 1),
            reminderAt = Instant.parse("2026-08-01T08:30:00Z"),
            createdAt = Instant.parse("2026-07-23T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
            revision = 1,
        )

        val encoded = Json.encodeToString(Task.serializer(), task)
        val decoded = Json.decodeFromString(Task.serializer(), encoded)

        assertEquals(task, decoded)
        assertEquals(true, encoded.contains("\"projectId\""))
        assertEquals(true, encoded.contains("\"labelIds\""))
        assertEquals(true, encoded.contains("\"priority\":\"high\""))
        assertEquals(true, encoded.contains("\"dueDate\":\"2026-08-01\""))
        assertEquals(true, encoded.contains("\"reminderAt\":\"2026-08-01T08:30:00Z\""))
    }
}
