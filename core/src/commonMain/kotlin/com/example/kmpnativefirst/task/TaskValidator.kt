package com.example.kmpnativefirst.task

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

object TaskConstraints {
    const val MAX_TITLE_LENGTH = 120
    const val MAX_NOTES_LENGTH = 2_000
    const val MAX_LABELS_PER_TASK = 20
}

@Serializable
enum class TaskField {
    @SerialName("id")
    ID,

    @SerialName("title")
    TITLE,

    @SerialName("notes")
    NOTES,

    @SerialName("projectId")
    PROJECT_ID,

    @SerialName("labelIds")
    LABEL_IDS,

    @SerialName("dueDate")
    DUE_DATE,

    @SerialName("dueAt")
    DUE_AT,

    @SerialName("expectedRevision")
    EXPECTED_REVISION,
}

@Serializable
enum class TaskValidationCode {
    @SerialName("required")
    REQUIRED,

    @SerialName("too_long")
    TOO_LONG,

    @SerialName("too_many")
    TOO_MANY,

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
    val labelIds: List<String>,
)

object TaskValidator {
    fun normalize(
        title: String,
        notes: String?,
        labelIds: List<String> = emptyList(),
    ): NormalizedTaskInput = NormalizedTaskInput(
        title = title.trim(),
        notes = notes?.trim()?.takeIf(String::isNotEmpty),
        labelIds = labelIds.distinct().sorted(),
    )

    fun validate(
        title: String,
        notes: String?,
        projectId: String? = null,
        labelIds: List<String> = emptyList(),
        dueDate: LocalDate? = null,
        dueAt: Instant? = null,
        expectedRevision: Long? = null,
    ): List<TaskValidationIssue> {
        val input = normalize(title, notes, labelIds)
        return buildList {
            if (input.title.isEmpty()) {
                add(TaskValidationIssue(TaskField.TITLE, TaskValidationCode.REQUIRED))
            } else if (input.title.length > TaskConstraints.MAX_TITLE_LENGTH) {
                add(TaskValidationIssue(TaskField.TITLE, TaskValidationCode.TOO_LONG))
            }

            if (input.notes != null && input.notes.length > TaskConstraints.MAX_NOTES_LENGTH) {
                add(TaskValidationIssue(TaskField.NOTES, TaskValidationCode.TOO_LONG))
            }

            if (projectId != null && Uuid.parseOrNull(projectId) == null) {
                add(TaskValidationIssue(TaskField.PROJECT_ID, TaskValidationCode.INVALID))
            }

            if (input.labelIds.size > TaskConstraints.MAX_LABELS_PER_TASK) {
                add(TaskValidationIssue(TaskField.LABEL_IDS, TaskValidationCode.TOO_MANY))
            } else if (input.labelIds.any { Uuid.parseOrNull(it) == null }) {
                add(TaskValidationIssue(TaskField.LABEL_IDS, TaskValidationCode.INVALID))
            }

            if (dueDate != null && dueAt != null) {
                add(TaskValidationIssue(TaskField.DUE_DATE, TaskValidationCode.INVALID))
                add(TaskValidationIssue(TaskField.DUE_AT, TaskValidationCode.INVALID))
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
        projectId: String? = null,
        labelIds: List<String> = emptyList(),
        dueDate: LocalDate? = null,
        dueAt: Instant? = null,
    ): List<TaskValidationIssue> = buildList {
        if (Uuid.parseOrNull(id) == null) {
            add(TaskValidationIssue(TaskField.ID, TaskValidationCode.INVALID))
        }
        addAll(
            validate(
                title = title,
                notes = notes,
                projectId = projectId,
                labelIds = labelIds,
                dueDate = dueDate,
                dueAt = dueAt,
            ),
        )
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
