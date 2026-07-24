package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskValidationIssue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class TaskDraft(
    val title: String,
    val notes: String? = null,
    val projectId: String? = null,
    val labelIds: List<String> = emptyList(),
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: LocalDate? = null,
    val dueAt: Instant? = null,
    val reminderAt: Instant? = null,
)

data class TaskEdit(
    val title: String,
    val notes: String? = null,
    val projectId: String? = null,
    val labelIds: List<String>? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: LocalDate? = null,
    val dueAt: Instant? = null,
    val reminderAt: Instant? = null,
    val isCompleted: Boolean = false,
)

fun Task.toEdit(): TaskEdit = TaskEdit(
    title = title,
    notes = notes,
    projectId = projectId,
    labelIds = labelIds,
    priority = priority,
    dueDate = dueDate,
    dueAt = dueAt,
    reminderAt = reminderAt,
    isCompleted = isCompleted,
)

data class TaskItem(
    val task: Task,
    val syncState: TaskSyncState,
)

@Serializable
enum class TaskSyncState {
    SYNCED,
    PENDING,
    CONFLICT,
}

@Serializable
enum class TaskMutationKind {
    CREATE,
    UPDATE,
    DELETE,
}

@Serializable
enum class TaskConflictField {
    CREATION,
    DELETION,
    TITLE,
    NOTES,
    PROJECT,
    PRIORITY,
    DUE_DATE,
    DUE_AT,
    COMPLETION,
}

@Serializable
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

@Serializable
internal data class PendingTaskMutation(
    val operationId: String,
    val taskId: String,
    val kind: TaskMutationKind,
    val base: Task?,
    val desired: Task?,
    val enqueuedAt: Instant,
)
