package com.example.kmpnativefirst.task

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TaskLabelContractTest {
    @Test
    fun normalizesAndValidatesLabelNames() {
        assertEquals("Focus", TaskLabelValidator.normalizeName("  Focus  "))
        assertTrue(TaskLabelValidator.validate("Focus", expectedRevision = 1).isEmpty())
        assertEquals(
            listOf(
                TaskLabelValidationIssue(
                    TaskLabelField.NAME,
                    TaskValidationCode.REQUIRED,
                ),
                TaskLabelValidationIssue(
                    TaskLabelField.EXPECTED_REVISION,
                    TaskValidationCode.INVALID,
                ),
            ),
            TaskLabelValidator.validate("  ", expectedRevision = 0),
        )
    }

    @Test
    fun validatesCreateIdentifiersAndNameLength() {
        val issues = TaskLabelValidator.validateCreate(
            id = "not-a-uuid",
            name = "x".repeat(TaskLabelConstraints.MAX_NAME_LENGTH + 1),
        )

        assertEquals(
            listOf(
                TaskLabelValidationIssue(
                    TaskLabelField.ID,
                    TaskValidationCode.INVALID,
                ),
                TaskLabelValidationIssue(
                    TaskLabelField.NAME,
                    TaskValidationCode.TOO_LONG,
                ),
            ),
            issues,
        )
    }

    @Test
    fun serializesStableColorValues() {
        val label = TaskLabel(
            id = "11111111-1111-4111-8111-111111111111",
            name = "Focus",
            color = TaskLabelColor.PURPLE,
            createdAt = Instant.parse("2026-07-23T10:00:00Z"),
            updatedAt = Instant.parse("2026-07-23T10:00:00Z"),
            revision = 1,
        )

        val encoded = Json.encodeToString(TaskLabel.serializer(), label)

        assertTrue(encoded.contains("\"color\":\"purple\""))
        assertEquals(label, Json.decodeFromString(TaskLabel.serializer(), encoded))
    }
}
