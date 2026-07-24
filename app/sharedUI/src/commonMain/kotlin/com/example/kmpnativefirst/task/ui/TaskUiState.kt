package com.example.kmpnativefirst.task.ui

import com.example.kmpnativefirst.task.TaskConstraints
import com.example.kmpnativefirst.task.TaskLabelColor
import com.example.kmpnativefirst.task.TaskLabelConstraints
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskProjectConstraints
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskConflict
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskLabelConflict
import com.example.kmpnativefirst.task.data.TaskLabelItem
import com.example.kmpnativefirst.task.data.TaskProjectConflict
import com.example.kmpnativefirst.task.data.TaskProjectItem
import com.example.kmpnativefirst.task.data.TaskSyncStatus
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class TaskUiState(
    val isInitializing: Boolean = true,
    val initializationError: String? = null,
    val tasks: List<TaskItem> = emptyList(),
    val labels: List<TaskLabelItem> = emptyList(),
    val view: TaskSmartView = TaskSmartView.INBOX,
    val selectedProjectId: String? = null,
    val searchQuery: String = "",
    val selectedLabelId: String? = null,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val projects: List<TaskProjectItem> = emptyList(),
    val projectTaskCounts: Map<String, Int> = emptyMap(),
    val syncStatus: TaskSyncStatus = TaskSyncStatus(),
    val conflicts: List<TaskConflict> = emptyList(),
    val projectConflicts: List<TaskProjectConflict> = emptyList(),
    val labelConflicts: List<TaskLabelConflict> = emptyList(),
    val editor: TaskEditorUiState? = null,
    val projectEditor: TaskProjectEditorUiState? = null,
    val isManagingLabels: Boolean = false,
    val labelEditor: TaskLabelEditorUiState? = null,
    val taskPendingDeletion: TaskItem? = null,
    val projectPendingDeletion: TaskProjectItem? = null,
    val labelPendingDeletion: TaskLabelItem? = null,
    val isConfirmingClearCompleted: Boolean = false,
    val selectedConflict: TaskConflict? = null,
    val selectedProjectConflict: TaskProjectConflict? = null,
    val selectedLabelConflict: TaskLabelConflict? = null,
    val notice: TaskUiNotice? = null,
) {
    val selectedProject: TaskProject?
        get() = projects.firstOrNull {
            it.project.id == selectedProjectId
        }?.project
}

data class TaskEditorUiState(
    val taskId: String? = null,
    val projectId: String? = null,
    val labelIds: List<String> = emptyList(),
    val title: String = "",
    val notes: String = "",
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: LocalDate? = null,
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

data class TaskProjectEditorUiState(
    val projectId: String? = null,
    val name: String = "",
    val color: TaskProjectColor = TaskProjectColor.BLUE,
    val isSaving: Boolean = false,
    val showValidationErrors: Boolean = false,
) {
    val isEditing: Boolean
        get() = projectId != null

    val hasNameError: Boolean
        get() = name.isBlank() ||
            name.trim().length > TaskProjectConstraints.MAX_NAME_LENGTH

    val canSave: Boolean
        get() = !hasNameError && !isSaving
}

data class TaskLabelEditorUiState(
    val labelId: String? = null,
    val name: String = "",
    val color: TaskLabelColor = TaskLabelColor.SLATE,
    val isSaving: Boolean = false,
    val showValidationErrors: Boolean = false,
) {
    val isEditing: Boolean
        get() = labelId != null

    val hasNameError: Boolean
        get() {
            val normalizedName = name.trim()
            return normalizedName.isEmpty() ||
                normalizedName.length > TaskLabelConstraints.MAX_NAME_LENGTH
        }

    val canSave: Boolean
        get() = !hasNameError && !isSaving
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
    SAVE_PROJECT,
    DELETE_PROJECT,
    RESOLVE_PROJECT_CONFLICT,
    SAVE_LABEL,
    DELETE_LABEL,
    RESOLVE_LABEL_CONFLICT,
    SYNC,
}
