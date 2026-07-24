package com.example.kmpnativefirst.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

object TaskLabelConstraints {
    const val MAX_NAME_LENGTH = 60
}

@Serializable
enum class TaskLabelField {
    @SerialName("id")
    ID,

    @SerialName("name")
    NAME,

    @SerialName("expectedRevision")
    EXPECTED_REVISION,
}

data class TaskLabelValidationIssue(
    val field: TaskLabelField,
    val code: TaskValidationCode,
)

object TaskLabelValidator {
    fun normalizeName(name: String): String = name.trim()

    fun validateCreate(
        id: String,
        name: String,
    ): List<TaskLabelValidationIssue> = buildList {
        if (Uuid.parseOrNull(id) == null) {
            add(
                TaskLabelValidationIssue(
                    TaskLabelField.ID,
                    TaskValidationCode.INVALID,
                ),
            )
        }
        addAll(validate(name))
    }

    fun validate(
        name: String,
        expectedRevision: Long? = null,
    ): List<TaskLabelValidationIssue> = buildList {
        val normalizedName = normalizeName(name)
        if (normalizedName.isEmpty()) {
            add(
                TaskLabelValidationIssue(
                    TaskLabelField.NAME,
                    TaskValidationCode.REQUIRED,
                ),
            )
        } else if (normalizedName.length > TaskLabelConstraints.MAX_NAME_LENGTH) {
            add(
                TaskLabelValidationIssue(
                    TaskLabelField.NAME,
                    TaskValidationCode.TOO_LONG,
                ),
            )
        }

        if (expectedRevision != null) {
            addAll(validateRevision(expectedRevision))
        }
    }

    fun validateRevision(expectedRevision: Long): List<TaskLabelValidationIssue> =
        if (expectedRevision < 1) {
            listOf(
                TaskLabelValidationIssue(
                    TaskLabelField.EXPECTED_REVISION,
                    TaskValidationCode.INVALID,
                ),
            )
        } else {
            emptyList()
        }
}
