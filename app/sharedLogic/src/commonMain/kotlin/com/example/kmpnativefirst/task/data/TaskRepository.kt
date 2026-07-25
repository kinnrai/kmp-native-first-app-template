package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskLabel
import com.example.kmpnativefirst.task.TaskProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
interface TaskRepository {
    @HiddenFromObjC
    val tasks: Flow<List<TaskItem>>

    @HiddenFromObjC
    val conflicts: Flow<List<TaskConflict>>

    @HiddenFromObjC
    val projects: Flow<List<TaskProjectItem>>

    @HiddenFromObjC
    val projectConflicts: Flow<List<TaskProjectConflict>>

    @HiddenFromObjC
    val labels: Flow<List<TaskLabelItem>>

    @HiddenFromObjC
    val labelConflicts: Flow<List<TaskLabelConflict>>

    @HiddenFromObjC
    val syncStatus: StateFlow<TaskSyncStatus>

    @Throws(Exception::class)
    suspend fun create(draft: TaskDraft): Task

    @Throws(Exception::class)
    suspend fun update(
        taskId: String,
        edit: TaskEdit,
    ): Task

    @Throws(Exception::class)
    suspend fun toggleCompleted(taskId: String): Task

    @Throws(Exception::class)
    suspend fun delete(taskId: String)

    @Throws(Exception::class)
    suspend fun clearCompleted()

    @Throws(Exception::class)
    suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
    )

    @Throws(Exception::class)
    suspend fun createProject(draft: TaskProjectDraft): TaskProject

    @Throws(Exception::class)
    suspend fun updateProject(
        projectId: String,
        edit: TaskProjectEdit,
    ): TaskProject

    @Throws(Exception::class)
    suspend fun deleteProject(projectId: String)

    @Throws(Exception::class)
    suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
    )

    @Throws(Exception::class)
    suspend fun createLabel(draft: TaskLabelDraft): TaskLabel

    @Throws(Exception::class)
    suspend fun updateLabel(
        labelId: String,
        edit: TaskLabelEdit,
    ): TaskLabel

    @Throws(Exception::class)
    suspend fun deleteLabel(labelId: String)

    @Throws(Exception::class)
    suspend fun resolveLabelConflict(
        labelId: String,
        resolution: TaskLabelConflictResolution,
    )

    @Throws(Exception::class)
    suspend fun sync(): TaskSyncResult

    @Throws(Exception::class)
    fun close()
}
