package com.example.kmpnativefirst.task

import kotlin.time.Clock

class TaskLabelService(
    private val repository: TaskLabelRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun list(): TaskLabelListResponse = TaskLabelListResponse(
        items = repository.listLabels(),
    )

    suspend fun find(id: String): TaskLabel = repository.findLabel(id)
        ?: throw TaskLabelNotFoundException(id)

    suspend fun create(request: CreateTaskLabelRequest): TaskLabel {
        val issues = TaskLabelValidator.validateCreate(request.id, request.name)
        if (issues.isNotEmpty()) {
            throw TaskLabelValidationException(issues)
        }
        val now = clock.now()
        val label = TaskLabel(
            id = request.id,
            name = TaskLabelValidator.normalizeName(request.name),
            color = request.color,
            createdAt = now,
            updatedAt = now,
            revision = 1,
        )
        return when (repository.insertLabel(label)) {
            is TaskLabelInsertResult.Inserted -> label
            TaskLabelInsertResult.AlreadyExists -> throw TaskLabelConflictException(label.id)
        }
    }

    suspend fun replace(
        id: String,
        request: ReplaceTaskLabelRequest,
    ): TaskLabel {
        val issues = TaskLabelValidator.validate(
            name = request.name,
            expectedRevision = request.expectedRevision,
        )
        if (issues.isNotEmpty()) {
            throw TaskLabelValidationException(issues)
        }
        val current = repository.findLabel(id)
            ?: throw TaskLabelNotFoundException(id)
        val replacement = current.copy(
            name = TaskLabelValidator.normalizeName(request.name),
            color = request.color,
            updatedAt = clock.now(),
            revision = request.expectedRevision + 1,
        )
        return when (
            val result = repository.replaceLabel(
                label = replacement,
                expectedRevision = request.expectedRevision,
            )
        ) {
            is TaskLabelMutationResult.Updated -> result.label
            TaskLabelMutationResult.NotFound -> throw TaskLabelNotFoundException(id)
            TaskLabelMutationResult.Conflict -> throw TaskLabelConflictException(id)
        }
    }

    suspend fun delete(
        id: String,
        expectedRevision: Long,
    ) {
        val issues = TaskLabelValidator.validateRevision(expectedRevision)
        if (issues.isNotEmpty()) {
            throw TaskLabelValidationException(issues)
        }
        when (
            repository.deleteLabel(
                id = id,
                expectedRevision = expectedRevision,
                affectedTasksUpdatedAt = clock.now(),
            )
        ) {
            is TaskLabelDeleteResult.Deleted -> Unit
            TaskLabelDeleteResult.NotFound -> throw TaskLabelNotFoundException(id)
            TaskLabelDeleteResult.Conflict -> throw TaskLabelConflictException(id)
        }
    }
}

class TaskLabelValidationException(
    val issues: List<TaskLabelValidationIssue>,
) : IllegalArgumentException("Task label validation failed.")

class TaskLabelNotFoundException(
    val labelId: String,
) : NoSuchElementException("Task label '$labelId' was not found.")

class TaskLabelConflictException(
    val labelId: String,
) : IllegalStateException("Task label '$labelId' has been modified by another client.")
