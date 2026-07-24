package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskLabel

internal interface TaskLabelRemoteDataSource {
    suspend fun listLabels(): List<TaskLabel>

    suspend fun findLabel(id: String): TaskLabel?

    suspend fun createLabel(label: TaskLabel): TaskLabel

    suspend fun replaceLabel(label: TaskLabel): TaskLabel

    suspend fun deleteLabel(
        id: String,
        expectedRevision: Long,
    )

    fun close()
}

internal class RemoteTaskLabelConflictException(
    val labelId: String,
) : TaskRemoteException("Task label '$labelId' conflicts with the remote version.")

internal class RemoteTaskLabelNotFoundException(
    val labelId: String,
) : TaskRemoteException("Task label '$labelId' does not exist remotely.")

internal object EmptyTaskLabelRemoteDataSource : TaskLabelRemoteDataSource {
    override suspend fun listLabels(): List<TaskLabel> = emptyList()

    override suspend fun findLabel(id: String): TaskLabel? = null

    override suspend fun createLabel(label: TaskLabel): TaskLabel =
        throw RemoteTaskLabelNotFoundException(label.id)

    override suspend fun replaceLabel(label: TaskLabel): TaskLabel =
        throw RemoteTaskLabelNotFoundException(label.id)

    override suspend fun deleteLabel(
        id: String,
        expectedRevision: Long,
    ) = throw RemoteTaskLabelNotFoundException(id)

    override fun close() = Unit
}
