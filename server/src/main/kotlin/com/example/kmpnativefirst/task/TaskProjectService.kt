package com.example.kmpnativefirst.task

import kotlin.time.Clock

class TaskProjectService(
    private val repository: TaskProjectRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun list(): TaskProjectListResponse = TaskProjectListResponse(
        items = repository.listProjects(),
    )

    suspend fun find(id: String): TaskProject = repository.findProject(id)
        ?: throw TaskProjectNotFoundException(id)

    suspend fun create(request: CreateTaskProjectRequest): TaskProject {
        val issues = TaskProjectValidator.validateCreate(request.id, request.name)
        if (issues.isNotEmpty()) {
            throw TaskProjectValidationException(issues)
        }
        val now = clock.now()
        val project = TaskProject(
            id = request.id,
            name = TaskProjectValidator.normalizeName(request.name),
            color = request.color,
            createdAt = now,
            updatedAt = now,
            revision = 1,
        )
        return when (repository.insertProject(project)) {
            is TaskProjectInsertResult.Inserted -> project
            TaskProjectInsertResult.AlreadyExists ->
                throw TaskProjectConflictException(project.id)
        }
    }

    suspend fun replace(
        id: String,
        request: ReplaceTaskProjectRequest,
    ): TaskProject {
        val issues = TaskProjectValidator.validate(
            name = request.name,
            expectedRevision = request.expectedRevision,
        )
        if (issues.isNotEmpty()) {
            throw TaskProjectValidationException(issues)
        }
        val current = repository.findProject(id)
            ?: throw TaskProjectNotFoundException(id)
        val replacement = current.copy(
            name = TaskProjectValidator.normalizeName(request.name),
            color = request.color,
            updatedAt = clock.now(),
            revision = request.expectedRevision + 1,
        )
        return when (
            val result = repository.replaceProject(
                project = replacement,
                expectedRevision = request.expectedRevision,
            )
        ) {
            is TaskProjectMutationResult.Updated -> result.project
            TaskProjectMutationResult.NotFound -> throw TaskProjectNotFoundException(id)
            TaskProjectMutationResult.Conflict -> throw TaskProjectConflictException(id)
        }
    }

    suspend fun delete(
        id: String,
        expectedRevision: Long,
    ) {
        val issues = TaskProjectValidator.validateRevision(expectedRevision)
        if (issues.isNotEmpty()) {
            throw TaskProjectValidationException(issues)
        }
        when (
            repository.deleteProject(
                id = id,
                expectedRevision = expectedRevision,
                reassignedTasksUpdatedAt = clock.now(),
            )
        ) {
            is TaskProjectDeleteResult.Deleted -> Unit
            TaskProjectDeleteResult.NotFound -> throw TaskProjectNotFoundException(id)
            TaskProjectDeleteResult.Conflict -> throw TaskProjectConflictException(id)
        }
    }
}

class TaskProjectValidationException(
    val issues: List<TaskProjectValidationIssue>,
) : IllegalArgumentException("Task project validation failed.")

class TaskProjectNotFoundException(
    val projectId: String,
) : NoSuchElementException("Task project '$projectId' was not found.")

class TaskProjectConflictException(
    val projectId: String,
) : IllegalStateException("Task project '$projectId' has been modified by another client.")
