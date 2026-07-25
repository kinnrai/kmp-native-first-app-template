package com.example.kmpnativefirst.task.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmpnativefirst.task.TaskPlanning
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import com.example.kmpnativefirst.task.data.TaskDraft
import com.example.kmpnativefirst.task.data.TaskEdit
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskRepository
import com.example.kmpnativefirst.task.data.TaskSyncResult
import com.example.kmpnativefirst.task.data.TaskSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.todayIn

class TaskViewModel(
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val todayProvider: () -> LocalDate = { Clock.System.todayIn(timeZone) },
    private val repositoryFactory: suspend () -> TaskRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TaskUiState())
    val uiState = mutableUiState.asStateFlow()

    private val listPreferences = MutableStateFlow(TaskListPreferences())
    private var repository: TaskRepository? = null
    private var initializationJob: Job? = null
    private var latestTasks: List<TaskItem> = emptyList()
    private var nextNoticeId = 0L

    init {
        initialize()
    }

    fun retryInitialization() {
        initialize()
    }

    fun setView(view: TaskSmartView) {
        listPreferences.update { it.copy(view = view) }
    }

    fun setSearchQuery(query: String) {
        listPreferences.update { it.copy(searchQuery = query) }
    }

    fun showCreateEditor() {
        mutableUiState.update {
            it.copy(editor = TaskEditorUiState())
        }
    }

    fun showEditEditor(taskId: String) {
        val task = latestTasks.firstOrNull { it.task.id == taskId }?.task ?: return
        mutableUiState.update {
            it.copy(
                editor = TaskEditorUiState(
                    taskId = task.id,
                    projectId = task.projectId,
                    title = task.title,
                    notes = task.notes.orEmpty(),
                    priority = task.priority,
                    dueDate = TaskPlanning.dueDate(task, timeZone),
                    dueAt = task.dueAt,
                    isCompleted = task.isCompleted,
                ),
            )
        }
    }

    fun dismissEditor() {
        if (mutableUiState.value.editor?.isSaving == true) return
        mutableUiState.update { it.copy(editor = null) }
    }

    fun setEditorTitle(title: String) {
        updateEditor {
            copy(title = title)
        }
    }

    fun setEditorNotes(notes: String) {
        updateEditor { copy(notes = notes) }
    }

    fun setEditorPriority(priority: TaskPriority) {
        updateEditor { copy(priority = priority) }
    }

    fun setEditorDueDate(dueDate: LocalDate?) {
        updateEditor {
            copy(
                dueDate = dueDate,
                dueAt = null,
            )
        }
    }

    fun setEditorCompleted(isCompleted: Boolean) {
        updateEditor { copy(isCompleted = isCompleted) }
    }

    fun saveEditor() {
        val editor = mutableUiState.value.editor ?: return
        if (!editor.canSave) {
            updateEditor { copy(showValidationErrors = true) }
            return
        }
        val currentRepository = repository ?: return

        viewModelScope.launch {
            updateEditor { copy(isSaving = true) }
            try {
                if (editor.taskId == null) {
                    currentRepository.create(
                        TaskDraft(
                            title = editor.title,
                            notes = editor.notes,
                            priority = editor.priority,
                            dueDate = editor.dueDate,
                            dueAt = editor.dueAt,
                        ),
                    )
                } else {
                    currentRepository.update(
                        taskId = editor.taskId,
                        edit = TaskEdit(
                            title = editor.title,
                            notes = editor.notes,
                            projectId = editor.projectId,
                            priority = editor.priority,
                            dueDate = editor.dueDate.takeIf { editor.dueAt == null },
                            dueAt = editor.dueAt,
                            isCompleted = editor.isCompleted,
                        ),
                    )
                }
                mutableUiState.update {
                    if (it.editor?.taskId == editor.taskId) {
                        it.copy(editor = null)
                    } else {
                        it
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateEditor { copy(isSaving = false) }
                publishFailure(TaskOperation.SAVE, error)
            }
        }
    }

    fun toggleCompleted(taskId: String) {
        perform(TaskOperation.TOGGLE) {
            toggleCompleted(taskId)
        }
    }

    fun requestDelete(taskId: String) {
        val task = latestTasks.firstOrNull { it.task.id == taskId } ?: return
        mutableUiState.update { it.copy(taskPendingDeletion = task) }
    }

    fun cancelDelete() {
        mutableUiState.update { it.copy(taskPendingDeletion = null) }
    }

    fun confirmDelete() {
        val taskId = mutableUiState.value.taskPendingDeletion?.task?.id ?: return
        mutableUiState.update {
            it.copy(
                taskPendingDeletion = null,
                editor = it.editor?.takeUnless { editor -> editor.taskId == taskId },
            )
        }
        perform(TaskOperation.DELETE) {
            delete(taskId)
        }
    }

    fun requestClearCompleted() {
        if (mutableUiState.value.completedCount == 0) return
        mutableUiState.update { it.copy(isConfirmingClearCompleted = true) }
    }

    fun cancelClearCompleted() {
        mutableUiState.update { it.copy(isConfirmingClearCompleted = false) }
    }

    fun confirmClearCompleted() {
        mutableUiState.update { it.copy(isConfirmingClearCompleted = false) }
        perform(TaskOperation.CLEAR_COMPLETED) {
            clearCompleted()
        }
    }

    fun showConflict(taskId: String) {
        val conflict = mutableUiState.value.conflicts.firstOrNull { it.taskId == taskId }
            ?: return
        mutableUiState.update { it.copy(selectedConflict = conflict) }
    }

    fun dismissConflict() {
        mutableUiState.update { it.copy(selectedConflict = null) }
    }

    fun resolveSelectedConflict(resolution: TaskConflictResolution) {
        val taskId = mutableUiState.value.selectedConflict?.taskId ?: return
        mutableUiState.update { it.copy(selectedConflict = null) }
        perform(TaskOperation.RESOLVE_CONFLICT) {
            resolveConflict(taskId, resolution)
        }
    }

    fun synchronize() {
        synchronize(notifyOnSuccess = true)
    }

    private fun synchronize(notifyOnSuccess: Boolean) {
        val currentRepository = repository ?: return
        viewModelScope.launch {
            try {
                when (val result = currentRepository.sync()) {
                    is TaskSyncResult.Success -> {
                        if (notifyOnSuccess) {
                            publishNotice(
                                TaskUiNoticeContent.SyncCompleted(
                                    pushedCount = result.pushedCount,
                                    pulledCount = result.pulledCount,
                                    conflictCount = result.conflictCount,
                                ),
                            )
                        }
                    }

                    is TaskSyncResult.Failed -> publishNotice(
                        TaskUiNoticeContent.OperationFailed(
                            operation = TaskOperation.SYNC,
                            detail = result.failure.message,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                publishFailure(TaskOperation.SYNC, error)
            }
        }
    }

    fun consumeNotice(id: Long) {
        mutableUiState.update { state ->
            if (state.notice?.id == id) state.copy(notice = null) else state
        }
    }

    override fun onCleared() {
        runCatching { repository?.close() }
        repository = null
    }

    private fun initialize() {
        if (repository != null || initializationJob?.isActive == true) return
        mutableUiState.update {
            it.copy(
                isInitializing = true,
                initializationError = null,
            )
        }
        initializationJob = viewModelScope.launch {
            try {
                val createdRepository = repositoryFactory()
                repository = createdRepository
                mutableUiState.update {
                    it.copy(
                        isInitializing = false,
                        initializationError = null,
                    )
                }
                observeRepository(createdRepository)
                synchronize(notifyOnSuccess = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isInitializing = false,
                        initializationError = error.message,
                    )
                }
                publishFailure(TaskOperation.INITIALIZE, error)
            }
        }
    }

    private fun observeRepository(createdRepository: TaskRepository) {
        viewModelScope.launch {
            combine(
                createdRepository.tasks,
                createdRepository.conflicts,
                createdRepository.syncStatus,
                listPreferences,
            ) { tasks, conflicts, syncStatus, preferences ->
                latestTasks = tasks
                TaskListSnapshot(
                    tasks = tasks.visibleFor(
                        preferences = preferences,
                        today = todayProvider(),
                        timeZone = timeZone,
                    ),
                    view = preferences.view,
                    searchQuery = preferences.searchQuery,
                    activeCount = tasks.count { !it.task.isCompleted },
                    completedCount = tasks.count { it.task.isCompleted },
                    conflicts = conflicts,
                    syncStatus = syncStatus,
                )
            }.collect { snapshot ->
                mutableUiState.update { current ->
                    current.copy(
                        tasks = snapshot.tasks,
                        view = snapshot.view,
                        searchQuery = snapshot.searchQuery,
                        activeCount = snapshot.activeCount,
                        completedCount = snapshot.completedCount,
                        conflicts = snapshot.conflicts,
                        syncStatus = snapshot.syncStatus,
                        selectedConflict = current.selectedConflict
                            ?.let { selected ->
                                snapshot.conflicts.firstOrNull {
                                    it.taskId == selected.taskId
                                }
                            },
                    )
                }
            }
        }
    }

    private fun perform(
        operation: TaskOperation,
        action: suspend TaskRepository.() -> Unit,
    ) {
        val currentRepository = repository ?: return
        viewModelScope.launch {
            try {
                currentRepository.action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                publishFailure(operation, error)
            }
        }
    }

    private fun updateEditor(update: TaskEditorUiState.() -> TaskEditorUiState) {
        mutableUiState.update { state ->
            state.editor?.let { state.copy(editor = it.update()) } ?: state
        }
    }

    private fun publishFailure(
        operation: TaskOperation,
        error: Throwable,
    ) {
        publishNotice(
            TaskUiNoticeContent.OperationFailed(
                operation = operation,
                detail = error.message,
            ),
        )
    }

    private fun publishNotice(content: TaskUiNoticeContent) {
        nextNoticeId += 1
        mutableUiState.update {
            it.copy(notice = TaskUiNotice(nextNoticeId, content))
        }
    }
}

private data class TaskListPreferences(
    val view: TaskSmartView = TaskSmartView.INBOX,
    val searchQuery: String = "",
)

private data class TaskListSnapshot(
    val tasks: List<TaskItem>,
    val view: TaskSmartView,
    val searchQuery: String,
    val activeCount: Int,
    val completedCount: Int,
    val conflicts: List<com.example.kmpnativefirst.task.data.TaskConflict>,
    val syncStatus: TaskSyncStatus,
)

private fun List<TaskItem>.visibleFor(
    preferences: TaskListPreferences,
    today: LocalDate,
    timeZone: TimeZone,
): List<TaskItem> {
    val normalizedQuery = preferences.searchQuery.trim()
    val itemsById = associateBy { it.task.id }
    return TaskPlanning.select(
        tasks = map(TaskItem::task),
        view = preferences.view,
        today = today,
        timeZone = timeZone,
    ).mapNotNull { task ->
        itemsById[task.id]
    }.filter { item ->
        val matchesQuery = normalizedQuery.isEmpty() ||
            item.task.title.contains(normalizedQuery, ignoreCase = true) ||
            item.task.notes?.contains(normalizedQuery, ignoreCase = true) == true
        matchesQuery
    }
}
