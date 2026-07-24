package com.example.kmpnativefirst.task

import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

class TaskService(
    private val repository: TaskRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun list(
        filter: TaskFilter,
        query: String?,
    ): TaskListResponse {
        val allTasks = repository.list()
        val normalizedQuery = query?.trim()?.takeIf(String::isNotEmpty)
        val visibleTasks = allTasks.filter { task ->
            matchesFilter(task, filter) && matchesQuery(task, normalizedQuery)
        }
        return TaskListResponse(
            items = visibleTasks,
            activeCount = allTasks.count { !it.isCompleted },
            completedCount = allTasks.count(Task::isCompleted),
        )
    }

    suspend fun find(id: String): Task = repository.find(id)
        ?: throw TaskNotFoundException(id)

    suspend fun create(request: CreateTaskRequest): Task {
        val issues = TaskValidator.validateCreate(
            id = request.id,
            title = request.title,
            notes = request.notes,
            projectId = request.projectId,
            labelIds = request.labelIds,
            dueDate = request.dueDate,
            dueAt = request.dueAt,
        )
        if (issues.isNotEmpty()) {
            throw TaskValidationException(issues)
        }
        val input = TaskValidator.normalize(
            request.title,
            request.notes,
            request.labelIds,
        )
        val now = clock.now()
        val task = Task(
            id = request.id,
            title = input.title,
            notes = input.notes,
            projectId = request.projectId,
            labelIds = input.labelIds,
            priority = request.priority,
            dueDate = request.dueDate,
            dueAt = request.dueAt,
            reminderAt = request.reminderAt,
            createdAt = now,
            updatedAt = now,
            revision = 1,
        )
        return when (repository.insert(task)) {
            is TaskInsertResult.Inserted -> task
            TaskInsertResult.AlreadyExists -> throw TaskConflictException(task.id)
            TaskInsertResult.InvalidProject -> throw invalidProjectException()
            TaskInsertResult.InvalidLabels -> throw invalidLabelsException()
        }
    }

    suspend fun replace(
        id: String,
        request: ReplaceTaskRequest,
    ): Task {
        validate(
            title = request.title,
            notes = request.notes,
            projectId = request.projectId,
            labelIds = request.labelIds,
            dueDate = request.dueDate,
            dueAt = request.dueAt,
            expectedRevision = request.expectedRevision,
        )
        val current = repository.find(id) ?: throw TaskNotFoundException(id)
        val input = TaskValidator.normalize(
            request.title,
            request.notes,
            request.labelIds,
        )
        val replacement = current.copy(
            title = input.title,
            notes = input.notes,
            projectId = request.projectId,
            labelIds = input.labelIds,
            priority = request.priority,
            dueDate = request.dueDate,
            dueAt = request.dueAt,
            reminderAt = request.reminderAt,
            isCompleted = request.isCompleted,
            updatedAt = clock.now(),
            revision = request.expectedRevision + 1,
        )
        return when (
            val result = repository.replace(
                task = replacement,
                expectedRevision = request.expectedRevision,
            )
        ) {
            is TaskMutationResult.Updated -> result.task
            TaskMutationResult.NotFound -> throw TaskNotFoundException(id)
            TaskMutationResult.Conflict -> throw TaskConflictException(id)
            TaskMutationResult.InvalidProject -> throw invalidProjectException()
            TaskMutationResult.InvalidLabels -> throw invalidLabelsException()
        }
    }

    suspend fun delete(
        id: String,
        expectedRevision: Long,
    ) {
        val issues = TaskValidator.validateRevision(expectedRevision)
        if (issues.isNotEmpty()) {
            throw TaskValidationException(issues)
        }
        when (repository.delete(id, expectedRevision)) {
            TaskDeleteResult.Deleted -> Unit
            TaskDeleteResult.NotFound -> throw TaskNotFoundException(id)
            TaskDeleteResult.Conflict -> throw TaskConflictException(id)
        }
    }

    suspend fun clearCompleted(): Int = repository.deleteCompleted()

    private fun validate(
        title: String,
        notes: String?,
        projectId: String?,
        labelIds: List<String>,
        dueDate: LocalDate?,
        dueAt: Instant?,
        expectedRevision: Long? = null,
    ) {
        val issues = TaskValidator.validate(
            title = title,
            notes = notes,
            projectId = projectId,
            labelIds = labelIds,
            dueDate = dueDate,
            dueAt = dueAt,
            expectedRevision = expectedRevision,
        )
        if (issues.isNotEmpty()) {
            throw TaskValidationException(issues)
        }
    }

    private fun matchesFilter(
        task: Task,
        filter: TaskFilter,
    ): Boolean = when (filter) {
        TaskFilter.ALL -> true
        TaskFilter.ACTIVE -> !task.isCompleted
        TaskFilter.COMPLETED -> task.isCompleted
    }

    private fun matchesQuery(
        task: Task,
        query: String?,
    ): Boolean = query == null ||
        task.title.contains(query, ignoreCase = true) ||
        task.notes?.contains(query, ignoreCase = true) == true
}

private fun invalidProjectException() = TaskValidationException(
    listOf(
        TaskValidationIssue(
            field = TaskField.PROJECT_ID,
            code = TaskValidationCode.INVALID,
        ),
    ),
)

private fun invalidLabelsException() = TaskValidationException(
    listOf(
        TaskValidationIssue(
            field = TaskField.LABEL_IDS,
            code = TaskValidationCode.INVALID,
        ),
    ),
)

class TaskValidationException(
    val issues: List<TaskValidationIssue>,
) : IllegalArgumentException("Task validation failed.")

class TaskNotFoundException(
    val taskId: String,
) : NoSuchElementException("Task '$taskId' was not found.")

class TaskConflictException(
    val taskId: String,
) : IllegalStateException("Task '$taskId' has been modified by another client.")
