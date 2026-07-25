package com.example.kmpnativefirst.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

object TaskProjectConstraints {
    const val MAX_NAME_LENGTH = 80
}

@Serializable
enum class TaskProjectField {
    @SerialName("id")
    ID,

    @SerialName("name")
    NAME,

    @SerialName("expectedRevision")
    EXPECTED_REVISION,
}

data class TaskProjectValidationIssue(
    val field: TaskProjectField,
    val code: TaskValidationCode,
)

object TaskProjectValidator {
    fun normalizeName(name: String): String = name.trim()

    fun validateCreate(
        id: String,
        name: String,
    ): List<TaskProjectValidationIssue> = buildList {
        if (Uuid.parseOrNull(id) == null) {
            add(
                TaskProjectValidationIssue(
                    TaskProjectField.ID,
                    TaskValidationCode.INVALID,
                ),
            )
        }
        addAll(validate(name))
    }

    fun validate(
        name: String,
        expectedRevision: Long? = null,
    ): List<TaskProjectValidationIssue> = buildList {
        val normalizedName = normalizeName(name)
        if (normalizedName.isEmpty()) {
            add(
                TaskProjectValidationIssue(
                    TaskProjectField.NAME,
                    TaskValidationCode.REQUIRED,
                ),
            )
        } else if (normalizedName.length > TaskProjectConstraints.MAX_NAME_LENGTH) {
            add(
                TaskProjectValidationIssue(
                    TaskProjectField.NAME,
                    TaskValidationCode.TOO_LONG,
                ),
            )
        }

        if (expectedRevision != null) {
            addAll(validateRevision(expectedRevision))
        }
    }

    fun validateRevision(expectedRevision: Long): List<TaskProjectValidationIssue> =
        if (expectedRevision < 1) {
            listOf(
                TaskProjectValidationIssue(
                    TaskProjectField.EXPECTED_REVISION,
                    TaskValidationCode.INVALID,
                ),
            )
        } else {
            emptyList()
        }
}
