package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProject

internal interface TaskProjectRemoteDataSource {
    suspend fun listProjects(): List<TaskProject>

    suspend fun findProject(id: String): TaskProject?

    suspend fun createProject(project: TaskProject): TaskProject

    suspend fun replaceProject(project: TaskProject): TaskProject

    suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
    )

    fun close()
}
internal class RemoteTaskProjectConflictException(
    val projectId: String,
) : TaskRemoteException("Task project '$projectId' conflicts with the remote version.")

internal class RemoteTaskProjectNotFoundException(
    val projectId: String,
) : TaskRemoteException("Task project '$projectId' does not exist remotely.")

internal object EmptyTaskProjectRemoteDataSource : TaskProjectRemoteDataSource {
    override suspend fun listProjects(): List<TaskProject> = emptyList()

    override suspend fun findProject(id: String): TaskProject? = null

    override suspend fun createProject(project: TaskProject): TaskProject =
        throw RemoteTaskProjectNotFoundException(project.id)

    override suspend fun replaceProject(project: TaskProject): TaskProject =
        throw RemoteTaskProjectNotFoundException(project.id)

    override suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
    ) = throw RemoteTaskProjectNotFoundException(id)

    override fun close() = Unit
}
