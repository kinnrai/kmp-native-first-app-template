package com.example.kmpnativefirst.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskProjectContractTest {
    @Test
    fun normalizesAndValidatesProjectNames() {
        assertEquals("Personal", TaskProjectValidator.normalizeName("  Personal  "))
        assertTrue(TaskProjectValidator.validate("Personal", expectedRevision = 1).isEmpty())
        assertEquals(
            listOf(
                TaskProjectValidationIssue(
                    TaskProjectField.NAME,
                    TaskValidationCode.REQUIRED,
                ),
                TaskProjectValidationIssue(
                    TaskProjectField.EXPECTED_REVISION,
                    TaskValidationCode.INVALID,
                ),
            ),
            TaskProjectValidator.validate("  ", expectedRevision = 0),
        )
    }

    @Test
    fun validatesCreateIdentifiersAndNameLength() {
        val issues = TaskProjectValidator.validateCreate(
            id = "not-a-uuid",
            name = "x".repeat(TaskProjectConstraints.MAX_NAME_LENGTH + 1),
        )

        assertEquals(
            listOf(
                TaskProjectValidationIssue(
                    TaskProjectField.ID,
                    TaskValidationCode.INVALID,
                ),
                TaskProjectValidationIssue(
                    TaskProjectField.NAME,
                    TaskValidationCode.TOO_LONG,
                ),
            ),
            issues,
        )
    }
}
