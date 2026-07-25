package com.example.kmpnativefirst.task

import kotlin.time.Instant

interface TaskProjectRepository {
    suspend fun listProjects(): List<TaskProject>

    suspend fun findProject(id: String): TaskProject?

    suspend fun insertProject(project: TaskProject): TaskProjectInsertResult

    suspend fun replaceProject(
        project: TaskProject,
        expectedRevision: Long,
    ): TaskProjectMutationResult

    suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
        reassignedTasksUpdatedAt: Instant,
    ): TaskProjectDeleteResult
}

sealed interface TaskProjectInsertResult {
    data class Inserted(val project: TaskProject) : TaskProjectInsertResult

    data object AlreadyExists : TaskProjectInsertResult
}

sealed interface TaskProjectMutationResult {
    data class Updated(val project: TaskProject) : TaskProjectMutationResult

    data object NotFound : TaskProjectMutationResult

    data object Conflict : TaskProjectMutationResult
}

sealed interface TaskProjectDeleteResult {
    data class Deleted(
        val reassignedTaskCount: Int,
    ) : TaskProjectDeleteResult

    data object NotFound : TaskProjectDeleteResult

    data object Conflict : TaskProjectDeleteResult
}
