package com.example.kmpnativefirst.task.ui

import com.example.kmpnativefirst.task.TaskFilter
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskConstraints
import com.example.kmpnativefirst.task.data.TaskConflict
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskSyncStatus
import kotlin.time.Instant

data class TaskUiState(
    val isInitializing: Boolean = true,
    val initializationError: String? = null,
    val tasks: List<TaskItem> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val searchQuery: String = "",
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val syncStatus: TaskSyncStatus = TaskSyncStatus(),
    val conflicts: List<TaskConflict> = emptyList(),
    val editor: TaskEditorUiState? = null,
    val taskPendingDeletion: TaskItem? = null,
    val isConfirmingClearCompleted: Boolean = false,
    val selectedConflict: TaskConflict? = null,
    val notice: TaskUiNotice? = null,
)

data class TaskEditorUiState(
    val taskId: String? = null,
    val title: String = "",
    val notes: String = "",
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: Instant? = null,
    val isCompleted: Boolean = false,
    val isSaving: Boolean = false,
    val showValidationErrors: Boolean = false,
) {
    val isEditing: Boolean
        get() = taskId != null

    val hasTitleError: Boolean
        get() = title.isBlank() || title.length > TaskConstraints.MAX_TITLE_LENGTH

    val hasNotesError: Boolean
        get() = notes.length > TaskConstraints.MAX_NOTES_LENGTH

    val canSave: Boolean
        get() = !hasTitleError && !hasNotesError && !isSaving
}

data class TaskUiNotice(
    val id: Long,
    val content: TaskUiNoticeContent,
)

sealed interface TaskUiNoticeContent {
    data class SyncCompleted(
        val pushedCount: Int,
        val pulledCount: Int,
        val conflictCount: Int,
    ) : TaskUiNoticeContent

    data class OperationFailed(
        val operation: TaskOperation,
        val detail: String?,
    ) : TaskUiNoticeContent
}

enum class TaskOperation {
    INITIALIZE,
    SAVE,
    TOGGLE,
    DELETE,
    CLEAR_COMPLETED,
    RESOLVE_CONFLICT,
    SYNC,
}
