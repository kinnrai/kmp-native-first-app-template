package com.example.kmpnativefirst.task

interface TaskRepository {
    suspend fun list(): List<Task>

    suspend fun find(id: String): Task?

    suspend fun insert(task: Task): TaskInsertResult

    suspend fun replace(
        task: Task,
        expectedRevision: Long,
    ): TaskMutationResult

    suspend fun delete(
        id: String,
        expectedRevision: Long,
    ): TaskDeleteResult

    suspend fun deleteCompleted(): Int
}

sealed interface TaskInsertResult {
    data class Inserted(val task: Task) : TaskInsertResult

    data object AlreadyExists : TaskInsertResult
}

sealed interface TaskMutationResult {
    data class Updated(val task: Task) : TaskMutationResult

    data object NotFound : TaskMutationResult

    data object Conflict : TaskMutationResult
}

sealed interface TaskDeleteResult {
    data object Deleted : TaskDeleteResult

    data object NotFound : TaskDeleteResult

    data object Conflict : TaskDeleteResult
}
