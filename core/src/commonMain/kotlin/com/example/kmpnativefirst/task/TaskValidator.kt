package com.example.kmpnativefirst.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

object TaskConstraints {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_NOTES_LENGTH = 2_000
}

@Serializable
enum class TaskField {
    @SerialName("id")
    ID,

    @SerialName("title")
    TITLE,

    @SerialName("notes")
    NOTES,

    @SerialName("expectedRevision")
    EXPECTED_REVISION,
}

@Serializable
enum class TaskValidationCode {
    @SerialName("required")
    REQUIRED,

    @SerialName("too_long")
    TOO_LONG,

    @SerialName("invalid")
    INVALID,
}

data class TaskValidationIssue(
    val field: TaskField,
    val code: TaskValidationCode,
)

data class NormalizedTaskInput(
    val title: String,
    val notes: String?,
)

object TaskValidator {
    fun normalize(
        title: String,
        notes: String?,
    ): NormalizedTaskInput = NormalizedTaskInput(
        title = title.trim(),
        notes = notes?.trim()?.takeIf(String::isNotEmpty),
    )

    fun validate(
        title: String,
        notes: String?,
        expectedRevision: Long? = null,
    ): List<TaskValidationIssue> {
        val input = normalize(title, notes)
        return buildList {
            if (input.title.isEmpty()) {
                add(TaskValidationIssue(TaskField.TITLE, TaskValidationCode.REQUIRED))
            } else if (input.title.length > TaskConstraints.MAX_TITLE_LENGTH) {
                add(TaskValidationIssue(TaskField.TITLE, TaskValidationCode.TOO_LONG))
            }

            if (input.notes != null && input.notes.length > TaskConstraints.MAX_NOTES_LENGTH) {
                add(TaskValidationIssue(TaskField.NOTES, TaskValidationCode.TOO_LONG))
            }

            if (expectedRevision != null) {
                addAll(validateRevision(expectedRevision))
            }
        }
    }

    fun validateCreate(
        id: String,
        title: String,
        notes: String?,
    ): List<TaskValidationIssue> = buildList {
        if (Uuid.parseOrNull(id) == null) {
            add(TaskValidationIssue(TaskField.ID, TaskValidationCode.INVALID))
        }
        addAll(validate(title, notes))
    }

    fun validateRevision(expectedRevision: Long): List<TaskValidationIssue> =
        if (expectedRevision < 1) {
            listOf(
                TaskValidationIssue(
                    TaskField.EXPECTED_REVISION,
                    TaskValidationCode.INVALID,
                ),
            )
        } else {
            emptyList()
        }
}
