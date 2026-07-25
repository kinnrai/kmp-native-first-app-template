package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskLabel
import com.example.kmpnativefirst.task.TaskLabelColor
import com.example.kmpnativefirst.task.TaskLabelValidationIssue
import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class TaskLabelDraft(
    val name: String,
    val color: TaskLabelColor = TaskLabelColor.SLATE,
)

data class TaskLabelEdit(
    val name: String,
    val color: TaskLabelColor = TaskLabelColor.SLATE,
)

fun TaskLabel.toEdit(): TaskLabelEdit = TaskLabelEdit(
    name = name,
    color = color,
)

data class TaskLabelItem(
    val label: TaskLabel,
    val syncState: TaskSyncState,
)

@Serializable
enum class TaskLabelConflictField {
    CREATION,
    DELETION,
    NAME,
    COLOR,
}

@Serializable
data class TaskLabelConflict(
    val labelId: String,
    val mutationKind: TaskMutationKind,
    val base: TaskLabel?,
    val local: TaskLabel?,
    val remote: TaskLabel?,
    val conflictingFields: Set<TaskLabelConflictField>,
    val detectedAt: Instant,
)

sealed interface TaskLabelConflictResolution {
    data object UseRemote : TaskLabelConflictResolution

    data object KeepLocal : TaskLabelConflictResolution

    data class Merge(val edit: TaskLabelEdit) : TaskLabelConflictResolution
}

@Serializable
internal data class PendingTaskLabelMutation(
    val operationId: String,
    val labelId: String,
    val kind: TaskMutationKind,
    val base: TaskLabel?,
    val desired: TaskLabel?,
    val enqueuedAt: Instant,
)

class InvalidTaskLabelInputException(
    val issues: List<TaskLabelValidationIssue>,
) : IllegalArgumentException("Task label input is invalid.")

class CachedTaskLabelNotFoundException(
    labelId: String,
) : NoSuchElementException("Task label '$labelId' was not found in the local cache.")

class UnresolvedTaskLabelConflictException(
    labelId: String,
) : IllegalStateException("Task label '$labelId' has an unresolved synchronization conflict.")

internal class DuplicateCachedTaskLabelException(
    labelId: String,
) : IllegalStateException("Task label '$labelId' already exists locally.")

internal class InvalidCachedTaskLabelStateException(
    labelId: String,
) : IllegalStateException("Task label '$labelId' is in an invalid local state.")
