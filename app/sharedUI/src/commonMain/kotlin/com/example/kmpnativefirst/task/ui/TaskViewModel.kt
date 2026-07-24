package com.example.kmpnativefirst.task.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmpnativefirst.task.TaskPlanning
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import com.example.kmpnativefirst.task.data.TaskDraft
import com.example.kmpnativefirst.task.data.TaskEdit
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskProjectConflict
import com.example.kmpnativefirst.task.data.TaskProjectConflictResolution
import com.example.kmpnativefirst.task.data.TaskProjectDraft
import com.example.kmpnativefirst.task.data.TaskProjectEdit
import com.example.kmpnativefirst.task.data.TaskProjectItem
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
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

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
    private var latestProjects: List<TaskProjectItem> = emptyList()
    private var nextNoticeId = 0L

    init {
        initialize()
    }

    fun retryInitialization() {
        initialize()
    }

    fun setView(view: TaskSmartView) {
        listPreferences.update {
            it.copy(
                view = view,
                selectedProjectId = null,
            )
        }
    }

    fun setProject(projectId: String) {
        if (latestProjects.none { it.project.id == projectId }) return
        listPreferences.update { it.copy(selectedProjectId = projectId) }
    }

    fun setSearchQuery(query: String) {
        listPreferences.update { it.copy(searchQuery = query) }
    }

    fun showCreateEditor() {
        val selectedProjectId = listPreferences.value.selectedProjectId
            ?.takeIf { selected ->
                latestProjects.any { it.project.id == selected }
            }
        mutableUiState.update {
            it.copy(
                editor = TaskEditorUiState(
                    projectId = selectedProjectId,
                ),
            )
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

    fun setEditorProject(projectId: String?) {
        if (projectId != null && latestProjects.none { it.project.id == projectId }) {
            return
        }
        updateEditor { copy(projectId = projectId) }
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
                            projectId = editor.projectId,
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

    fun showCreateProjectEditor() {
        mutableUiState.update {
            it.copy(projectEditor = TaskProjectEditorUiState())
        }
    }

    fun showEditProjectEditor(projectId: String) {
        val project = latestProjects.firstOrNull {
            it.project.id == projectId
        }?.project ?: return
        mutableUiState.update {
            it.copy(
                projectEditor = TaskProjectEditorUiState(
                    projectId = project.id,
                    name = project.name,
                    color = project.color,
                ),
            )
        }
    }

    fun dismissProjectEditor() {
        if (mutableUiState.value.projectEditor?.isSaving == true) return
        mutableUiState.update { it.copy(projectEditor = null) }
    }

    fun setProjectName(name: String) {
        updateProjectEditor { copy(name = name) }
    }

    fun setProjectColor(color: TaskProjectColor) {
        updateProjectEditor { copy(color = color) }
    }

    fun saveProject() {
        val editor = mutableUiState.value.projectEditor ?: return
        if (!editor.canSave) {
            updateProjectEditor { copy(showValidationErrors = true) }
            return
        }
        val currentRepository = repository ?: return

        viewModelScope.launch {
            updateProjectEditor { copy(isSaving = true) }
            try {
                val project = if (editor.projectId == null) {
                    currentRepository.createProject(
                        TaskProjectDraft(
                            name = editor.name,
                            color = editor.color,
                        ),
                    )
                } else {
                    currentRepository.updateProject(
                        projectId = editor.projectId,
                        edit = TaskProjectEdit(
                            name = editor.name,
                            color = editor.color,
                        ),
                    )
                }
                if (editor.projectId == null) {
                    listPreferences.update {
                        it.copy(selectedProjectId = project.id)
                    }
                }
                mutableUiState.update {
                    if (it.projectEditor?.projectId == editor.projectId) {
                        it.copy(projectEditor = null)
                    } else {
                        it
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                updateProjectEditor { copy(isSaving = false) }
                publishFailure(TaskOperation.SAVE_PROJECT, error)
            }
        }
    }

    fun requestDeleteProject(projectId: String) {
        val project = latestProjects.firstOrNull {
            it.project.id == projectId
        } ?: return
        mutableUiState.update { it.copy(projectPendingDeletion = project) }
    }

    fun cancelDeleteProject() {
        mutableUiState.update { it.copy(projectPendingDeletion = null) }
    }

    fun confirmDeleteProject() {
        val projectId = mutableUiState.value.projectPendingDeletion
            ?.project
            ?.id
            ?: return
        mutableUiState.update {
            it.copy(
                projectPendingDeletion = null,
                projectEditor = it.projectEditor?.takeUnless { editor ->
                    editor.projectId == projectId
                },
            )
        }
        if (listPreferences.value.selectedProjectId == projectId) {
            listPreferences.update { it.copy(selectedProjectId = null) }
        }
        perform(TaskOperation.DELETE_PROJECT) {
            deleteProject(projectId)
        }
    }

    fun showProjectConflict(projectId: String) {
        val conflict = mutableUiState.value.projectConflicts.firstOrNull {
            it.projectId == projectId
        } ?: return
        mutableUiState.update { it.copy(selectedProjectConflict = conflict) }
    }

    fun dismissProjectConflict() {
        mutableUiState.update { it.copy(selectedProjectConflict = null) }
    }

    fun resolveSelectedProjectConflict(
        resolution: TaskProjectConflictResolution,
    ) {
        val projectId = mutableUiState.value.selectedProjectConflict
            ?.projectId
            ?: return
        mutableUiState.update { it.copy(selectedProjectConflict = null) }
        perform(TaskOperation.RESOLVE_PROJECT_CONFLICT) {
            resolveProjectConflict(projectId, resolution)
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
                createdRepository.projects,
                createdRepository.projectConflicts,
                createdRepository.syncStatus,
            ) { tasks, conflicts, projects, projectConflicts, syncStatus ->
                TaskRepositorySnapshot(
                    tasks = tasks,
                    conflicts = conflicts,
                    projects = projects,
                    projectConflicts = projectConflicts,
                    syncStatus = syncStatus,
                )
            }.combine(listPreferences) { repositorySnapshot, preferences ->
                val tasks = repositorySnapshot.tasks
                val projects = repositorySnapshot.projects
                latestTasks = tasks
                latestProjects = projects
                val selectedProjectId = preferences.selectedProjectId
                    ?.takeIf { selected ->
                        projects.any { it.project.id == selected }
                    }
                val countScope = selectedProjectId?.let { projectId ->
                    tasks.filter { it.task.projectId == projectId }
                } ?: tasks
                TaskListSnapshot(
                    tasks = tasks.visibleFor(
                        preferences = preferences,
                        selectedProjectId = selectedProjectId,
                        today = todayProvider(),
                        timeZone = timeZone,
                    ),
                    view = preferences.view,
                    selectedProjectId = selectedProjectId,
                    searchQuery = preferences.searchQuery,
                    activeCount = countScope.count { !it.task.isCompleted },
                    completedCount = countScope.count { it.task.isCompleted },
                    projects = projects,
                    projectTaskCounts = tasks
                        .asSequence()
                        .filterNot { it.task.isCompleted }
                        .mapNotNull { it.task.projectId }
                        .groupingBy { it }
                        .eachCount(),
                    conflicts = repositorySnapshot.conflicts,
                    projectConflicts = repositorySnapshot.projectConflicts,
                    syncStatus = repositorySnapshot.syncStatus,
                )
            }.collect { snapshot ->
                mutableUiState.update { current ->
                    val projectIds = snapshot.projects
                        .mapTo(mutableSetOf()) { it.project.id }
                    current.copy(
                        tasks = snapshot.tasks,
                        view = snapshot.view,
                        selectedProjectId = snapshot.selectedProjectId,
                        searchQuery = snapshot.searchQuery,
                        activeCount = snapshot.activeCount,
                        completedCount = snapshot.completedCount,
                        projects = snapshot.projects,
                        projectTaskCounts = snapshot.projectTaskCounts,
                        conflicts = snapshot.conflicts,
                        projectConflicts = snapshot.projectConflicts,
                        syncStatus = snapshot.syncStatus,
                        editor = current.editor?.let { editor ->
                            if (
                                editor.projectId == null ||
                                editor.projectId in projectIds
                            ) {
                                editor
                            } else {
                                editor.copy(projectId = null)
                            }
                        },
                        projectEditor = current.projectEditor?.takeIf { editor ->
                            editor.projectId == null || editor.projectId in projectIds
                        },
                        selectedConflict = current.selectedConflict
                            ?.let { selected ->
                                snapshot.conflicts.firstOrNull {
                                    it.taskId == selected.taskId
                                }
                            },
                        selectedProjectConflict = current.selectedProjectConflict
                            ?.let { selected ->
                                snapshot.projectConflicts.firstOrNull {
                                    it.projectId == selected.projectId
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

    private fun updateProjectEditor(
        update: TaskProjectEditorUiState.() -> TaskProjectEditorUiState,
    ) {
        mutableUiState.update { state ->
            state.projectEditor?.let {
                state.copy(projectEditor = it.update())
            } ?: state
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
    val selectedProjectId: String? = null,
    val searchQuery: String = "",
)

private data class TaskRepositorySnapshot(
    val tasks: List<TaskItem>,
    val conflicts: List<com.example.kmpnativefirst.task.data.TaskConflict>,
    val projects: List<TaskProjectItem>,
    val projectConflicts: List<TaskProjectConflict>,
    val syncStatus: TaskSyncStatus,
)

private data class TaskListSnapshot(
    val tasks: List<TaskItem>,
    val view: TaskSmartView,
    val selectedProjectId: String?,
    val searchQuery: String,
    val activeCount: Int,
    val completedCount: Int,
    val projects: List<TaskProjectItem>,
    val projectTaskCounts: Map<String, Int>,
    val conflicts: List<com.example.kmpnativefirst.task.data.TaskConflict>,
    val projectConflicts: List<TaskProjectConflict>,
    val syncStatus: TaskSyncStatus,
)

private fun List<TaskItem>.visibleFor(
    preferences: TaskListPreferences,
    selectedProjectId: String?,
    today: LocalDate,
    timeZone: TimeZone,
): List<TaskItem> {
    val normalizedQuery = preferences.searchQuery.trim()
    val itemsById = associateBy { it.task.id }
    val selectedTasks = if (selectedProjectId == null) {
        TaskPlanning.select(
            tasks = map(TaskItem::task),
            view = preferences.view,
            today = today,
            timeZone = timeZone,
        )
    } else {
        map(TaskItem::task).filter { it.projectId == selectedProjectId }
    }
    return selectedTasks.mapNotNull { task ->
        itemsById[task.id]
    }.filter { item ->
        val matchesQuery = normalizedQuery.isEmpty() ||
            item.task.title.contains(normalizedQuery, ignoreCase = true) ||
            item.task.notes?.contains(normalizedQuery, ignoreCase = true) == true
        matchesQuery
    }
}
