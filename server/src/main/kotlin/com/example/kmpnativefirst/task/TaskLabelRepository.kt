package com.example.kmpnativefirst.task

import kotlin.time.Instant

interface TaskLabelRepository {
    suspend fun listLabels(): List<TaskLabel>

    suspend fun findLabel(id: String): TaskLabel?

    suspend fun insertLabel(label: TaskLabel): TaskLabelInsertResult

    suspend fun replaceLabel(
        label: TaskLabel,
        expectedRevision: Long,
    ): TaskLabelMutationResult

    suspend fun deleteLabel(
        id: String,
        expectedRevision: Long,
        affectedTasksUpdatedAt: Instant,
    ): TaskLabelDeleteResult
}

sealed interface TaskLabelInsertResult {
    data class Inserted(val label: TaskLabel) : TaskLabelInsertResult

    data object AlreadyExists : TaskLabelInsertResult
}

sealed interface TaskLabelMutationResult {
    data class Updated(val label: TaskLabel) : TaskLabelMutationResult

    data object NotFound : TaskLabelMutationResult

    data object Conflict : TaskLabelMutationResult
}

sealed interface TaskLabelDeleteResult {
    data class Deleted(
        val affectedTaskCount: Int,
    ) : TaskLabelDeleteResult

    data object NotFound : TaskLabelDeleteResult

    data object Conflict : TaskLabelDeleteResult
}
