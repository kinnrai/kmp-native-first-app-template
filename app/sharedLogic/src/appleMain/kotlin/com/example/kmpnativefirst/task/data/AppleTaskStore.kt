package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * A small callback-based boundary for Swift.
 *
 * The shared repository keeps Flow as its Kotlin-first API. This façade avoids
 * leaking coroutine collection into Swift while preserving the repository as
 * the single source of truth for both Apple apps.
 */
class AppleTaskStore internal constructor(
    private val repository: TaskRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun observe(
        listener: (AppleTaskSnapshot) -> Unit,
    ): AppleTaskObservation {
        val job = scope.launch {
            combine(
                repository.tasks,
                repository.conflicts,
                repository.syncStatus,
                ::AppleTaskSnapshot,
            ).collect(listener)
        }
        return AppleTaskObservation(job)
    }

    @Throws(Exception::class)
    suspend fun create(
        title: String,
        notes: String?,
        priority: TaskPriority,
        dueAt: Instant?,
    ): Task = repository.create(
        TaskDraft(
            title = title,
            notes = notes,
            priority = priority,
            dueAt = dueAt,
        ),
    )

    @Throws(Exception::class)
    suspend fun update(
        taskId: String,
        title: String,
        notes: String?,
        priority: TaskPriority,
        dueAt: Instant?,
        isCompleted: Boolean,
    ): Task = repository.update(
        taskId = taskId,
        edit = TaskEdit(
            title = title,
            notes = notes,
            priority = priority,
            dueAt = dueAt,
            isCompleted = isCompleted,
        ),
    )

    @Throws(Exception::class)
    suspend fun toggleCompleted(taskId: String): Task =
        repository.toggleCompleted(taskId)

    @Throws(Exception::class)
    suspend fun delete(taskId: String) {
        repository.delete(taskId)
    }

    @Throws(Exception::class)
    suspend fun clearCompleted() {
        repository.clearCompleted()
    }

    @Throws(Exception::class)
    suspend fun keepLocal(taskId: String) {
        repository.resolveConflict(taskId, TaskConflictResolution.KeepLocal)
    }

    @Throws(Exception::class)
    suspend fun useRemote(taskId: String) {
        repository.resolveConflict(taskId, TaskConflictResolution.UseRemote)
    }

    @Throws(Exception::class)
    suspend fun mergeConflict(
        taskId: String,
        title: String,
        notes: String?,
        priority: TaskPriority,
        dueAt: Instant?,
        isCompleted: Boolean,
    ) {
        repository.resolveConflict(
            taskId = taskId,
            resolution = TaskConflictResolution.Merge(
                TaskEdit(
                    title = title,
                    notes = notes,
                    priority = priority,
                    dueAt = dueAt,
                    isCompleted = isCompleted,
                ),
            ),
        )
    }

    @Throws(Exception::class)
    suspend fun sync(): TaskSyncResult = repository.sync()

    @Throws(Exception::class)
    fun close() {
        scope.cancel()
        repository.close()
    }
}

data class AppleTaskSnapshot(
    val tasks: List<TaskItem>,
    val conflicts: List<TaskConflict>,
    val syncStatus: TaskSyncStatus,
)

class AppleTaskObservation internal constructor(
    private val job: Job,
) {
    fun cancel() {
        job.cancel()
    }
}
