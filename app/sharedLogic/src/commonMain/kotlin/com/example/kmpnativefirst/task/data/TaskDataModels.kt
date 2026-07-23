package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskValidationIssue
import kotlin.time.Instant

data class TaskDraft(
    val title: String,
    val notes: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: Instant? = null,
)

data class TaskEdit(
    val title: String,
    val notes: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: Instant? = null,
    val isCompleted: Boolean = false,
)

fun Task.toEdit(): TaskEdit = TaskEdit(
    title = title,
    notes = notes,
    priority = priority,
    dueAt = dueAt,
    isCompleted = isCompleted,
)

data class TaskItem(
    val task: Task,
    val syncState: TaskSyncState,
)

enum class TaskSyncState {
    SYNCED,
    PENDING,
    CONFLICT,
}

enum class TaskMutationKind {
    CREATE,
    UPDATE,
    DELETE,
}

enum class TaskConflictField {
    CREATION,
    DELETION,
    TITLE,
    NOTES,
    PRIORITY,
    DUE_AT,
    COMPLETION,
}

data class TaskConflict(
    val taskId: String,
    val mutationKind: TaskMutationKind,
    val base: Task?,
    val local: Task?,
    val remote: Task?,
    val conflictingFields: Set<TaskConflictField>,
    val detectedAt: Instant,
)

sealed interface TaskConflictResolution {
    data object UseRemote : TaskConflictResolution

    data object KeepLocal : TaskConflictResolution

    data class Merge(val edit: TaskEdit) : TaskConflictResolution
}

enum class TaskSyncPhase {
    IDLE,
    SYNCING,
    FAILED,
}

data class TaskSyncStatus(
    val phase: TaskSyncPhase = TaskSyncPhase.IDLE,
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSyncedAt: Instant? = null,
    val lastError: TaskSyncFailure? = null,
)

data class TaskSyncFailure(
    val kind: TaskSyncFailureKind,
    val message: String,
)

enum class TaskSyncFailureKind {
    NETWORK,
    SERVER,
    LOCAL,
}

sealed interface TaskSyncResult {
    data class Success(
        val pushedCount: Int,
        val pulledCount: Int,
        val conflictCount: Int,
    ) : TaskSyncResult

    data class Failed(
        val failure: TaskSyncFailure,
    ) : TaskSyncResult
}

class InvalidTaskInputException(
    val issues: List<TaskValidationIssue>,
) : IllegalArgumentException("Task input is invalid.")

class CachedTaskNotFoundException(
    val taskId: String,
) : NoSuchElementException("Task '$taskId' is not available locally.")

class UnresolvedTaskConflictException(
    val taskId: String,
) : IllegalStateException("Task '$taskId' has an unresolved conflict.")

internal data class PendingTaskMutation(
    val operationId: String,
    val taskId: String,
    val kind: TaskMutationKind,
    val base: Task?,
    val desired: Task?,
    val enqueuedAt: Instant,
)
