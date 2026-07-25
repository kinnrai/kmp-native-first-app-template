package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.js.JsExport
import kotlin.time.Instant

/**
 * A React-friendly boundary around the shared offline-first repository.
 *
 * The cached snapshot stays referentially stable until shared state changes,
 * which makes this store safe to consume through React's useSyncExternalStore.
 */
@JsExport
class WebTaskStore(
    baseUrl: String,
    databaseName: String = "kmp-native-first-tasks",
) {
    private val scope: CoroutineScope = MainScope()
    private val listeners = mutableSetOf<(WebTaskSnapshot) -> Unit>()
    private var repository: TaskRepository? = null
    private var currentSnapshot = loadingWebTaskSnapshot()

    init {
        scope.launch {
            try {
                val createdRepository = createWebTaskRepository(
                    baseUrl = baseUrl,
                    databaseName = databaseName,
                )
                repository = createdRepository
                combine(
                    createdRepository.tasks,
                    createdRepository.conflicts,
                    createdRepository.syncStatus,
                ) { tasks, conflicts, syncStatus ->
                    WebTaskSnapshot(
                        isReady = true,
                        tasks = tasks.map(TaskItem::toWebTaskItem).toTypedArray(),
                        conflicts = conflicts.map(TaskConflict::toWebTaskConflict).toTypedArray(),
                        syncPhase = syncStatus.phase.name.lowercase(),
                        pendingCount = syncStatus.pendingCount,
                        conflictCount = syncStatus.conflictCount,
                        lastSyncedAt = syncStatus.lastSyncedAt?.toString(),
                        lastError = syncStatus.lastError?.message,
                        actionError = currentSnapshot.actionError,
                    )
                }.collect(::publish)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                publish(
                    currentSnapshot.withChanges(
                        isReady = false,
                        lastError = error.message ?: "Could not open the browser task store.",
                    ),
                )
            }
        }
    }

    fun subscribe(listener: (WebTaskSnapshot) -> Unit): WebTaskSubscription {
        listeners += listener
        return WebTaskSubscription {
            listeners -= listener
        }
    }

    fun getSnapshot(): WebTaskSnapshot = currentSnapshot

    fun create(
        title: String,
        notes: String?,
        priority: String,
        dueAt: String?,
    ) = runAction {
        create(
            TaskDraft(
                title = title,
                notes = notes,
                priority = priority.toTaskPriority(),
                dueAt = dueAt.toInstantOrNull(),
            ),
        )
    }

    fun update(
        taskId: String,
        title: String,
        notes: String?,
        priority: String,
        dueAt: String?,
        isCompleted: Boolean,
    ) = runAction {
        update(
            taskId = taskId,
            edit = TaskEdit(
                title = title,
                notes = notes,
                priority = priority.toTaskPriority(),
                dueAt = dueAt.toInstantOrNull(),
                isCompleted = isCompleted,
            ),
        )
    }

    fun toggleCompleted(taskId: String) = runAction {
        toggleCompleted(taskId)
    }

    fun delete(taskId: String) = runAction {
        delete(taskId)
    }

    fun clearCompleted() = runAction {
        clearCompleted()
    }

    fun keepLocal(taskId: String) = runAction {
        resolveConflict(taskId, TaskConflictResolution.KeepLocal)
    }

    fun useRemote(taskId: String) = runAction {
        resolveConflict(taskId, TaskConflictResolution.UseRemote)
    }

    fun mergeConflict(
        taskId: String,
        title: String,
        notes: String?,
        priority: String,
        dueAt: String?,
        isCompleted: Boolean,
    ) = runAction {
        resolveConflict(
            taskId = taskId,
            resolution = TaskConflictResolution.Merge(
                TaskEdit(
                    title = title,
                    notes = notes,
                    priority = priority.toTaskPriority(),
                    dueAt = dueAt.toInstantOrNull(),
                    isCompleted = isCompleted,
                ),
            ),
        )
    }

    fun sync() = runAction {
        sync()
    }

    fun clearActionError() {
        if (currentSnapshot.actionError != null) {
            publish(currentSnapshot.withChanges(actionError = null))
        }
    }

    fun close() {
        scope.cancel()
        repository?.close()
        repository = null
        listeners.clear()
    }

    private fun runAction(action: suspend TaskRepository.() -> Unit) {
        val currentRepository = repository
        if (currentRepository == null) {
            publish(
                currentSnapshot.withChanges(
                    actionError = currentSnapshot.lastError ?: "Task data is still loading.",
                ),
            )
            return
        }
        if (currentSnapshot.actionError != null) {
            publish(currentSnapshot.withChanges(actionError = null))
        }
        scope.launch {
            try {
                currentRepository.action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                publish(
                    currentSnapshot.withChanges(
                        actionError = error.toActionMessage(),
                    ),
                )
            }
        }
    }

    private fun publish(snapshot: WebTaskSnapshot) {
        currentSnapshot = snapshot
        listeners.toList().forEach { listener -> listener(snapshot) }
    }
}

@JsExport
class WebTaskSubscription internal constructor(
    private val cancelAction: () -> Unit,
) {
    fun cancel() {
        cancelAction()
    }
}

@JsExport
class WebTaskSnapshot internal constructor(
    val isReady: Boolean,
    val tasks: Array<WebTaskItem>,
    val conflicts: Array<WebTaskConflict>,
    val syncPhase: String,
    val pendingCount: Int,
    val conflictCount: Int,
    val lastSyncedAt: String?,
    val lastError: String?,
    val actionError: String?,
)

@JsExport
class WebTaskItem internal constructor(
    val task: WebTask,
    val syncState: String,
)

@JsExport
class WebTask internal constructor(
    val id: String,
    val title: String,
    val notes: String?,
    val priority: String,
    val dueAt: String?,
    val isCompleted: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val revision: String,
)

@JsExport
class WebTaskConflict internal constructor(
    val taskId: String,
    val mutationKind: String,
    val base: WebTask?,
    val local: WebTask?,
    val remote: WebTask?,
    val conflictingFields: Array<String>,
    val detectedAt: String,
)

private fun loadingWebTaskSnapshot(): WebTaskSnapshot = WebTaskSnapshot(
    isReady = false,
    tasks = emptyArray(),
    conflicts = emptyArray(),
    syncPhase = TaskSyncPhase.IDLE.name.lowercase(),
    pendingCount = 0,
    conflictCount = 0,
    lastSyncedAt = null,
    lastError = null,
    actionError = null,
)

private fun WebTaskSnapshot.withChanges(
    isReady: Boolean = this.isReady,
    tasks: Array<WebTaskItem> = this.tasks,
    conflicts: Array<WebTaskConflict> = this.conflicts,
    syncPhase: String = this.syncPhase,
    pendingCount: Int = this.pendingCount,
    conflictCount: Int = this.conflictCount,
    lastSyncedAt: String? = this.lastSyncedAt,
    lastError: String? = this.lastError,
    actionError: String? = this.actionError,
): WebTaskSnapshot = WebTaskSnapshot(
    isReady = isReady,
    tasks = tasks,
    conflicts = conflicts,
    syncPhase = syncPhase,
    pendingCount = pendingCount,
    conflictCount = conflictCount,
    lastSyncedAt = lastSyncedAt,
    lastError = lastError,
    actionError = actionError,
)

private fun TaskItem.toWebTaskItem(): WebTaskItem = WebTaskItem(
    task = task.toWebTask(),
    syncState = syncState.name.lowercase(),
)

private fun TaskConflict.toWebTaskConflict(): WebTaskConflict = WebTaskConflict(
    taskId = taskId,
    mutationKind = mutationKind.name.lowercase(),
    base = base?.toWebTask(),
    local = local?.toWebTask(),
    remote = remote?.toWebTask(),
    conflictingFields = conflictingFields
        .map { field -> field.name.lowercase() }
        .sorted()
        .toTypedArray(),
    detectedAt = detectedAt.toString(),
)

private fun Task.toWebTask(): WebTask = WebTask(
    id = id,
    title = title,
    notes = notes,
    priority = priority.name.lowercase(),
    dueAt = dueAt?.toString(),
    isCompleted = isCompleted,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    revision = revision.toString(),
)

private fun String.toTaskPriority(): TaskPriority =
    TaskPriority.entries.firstOrNull { priority ->
        priority.name.equals(this, ignoreCase = true)
    } ?: TaskPriority.NONE

private fun String?.toInstantOrNull(): Instant? =
    this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(Instant::parse)

private fun Throwable.toActionMessage(): String = when (this) {
    is InvalidTaskInputException -> "Check the title and notes, then try again."
    is CachedTaskNotFoundException -> "This task is no longer available on this device."
    is UnresolvedTaskConflictException -> "Resolve this task's sync conflict before editing it."
    else -> message ?: "The task could not be updated."
}
