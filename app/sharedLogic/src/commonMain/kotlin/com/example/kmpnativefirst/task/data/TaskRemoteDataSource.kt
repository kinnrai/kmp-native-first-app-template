package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task

internal interface TaskRemoteDataSource {
    suspend fun list(): List<Task>

    suspend fun find(id: String): Task?

    suspend fun create(task: Task): Task

    suspend fun replace(task: Task): Task

    suspend fun delete(
        id: String,
        expectedRevision: Long,
    )

    fun close()
}

internal sealed class TaskRemoteException(
    message: String,
) : RuntimeException(message)

internal class RemoteTaskConflictException(
    val taskId: String,
) : TaskRemoteException("Task '$taskId' conflicts with the remote version.")

internal class RemoteTaskNotFoundException(
    val taskId: String,
) : TaskRemoteException("Task '$taskId' does not exist remotely.")

internal class RemoteTaskRejectedException(
    val statusCode: Int,
    message: String,
) : TaskRemoteException(message)

internal class RemoteTaskServerException(
    val statusCode: Int,
    message: String,
) : TaskRemoteException(message)
