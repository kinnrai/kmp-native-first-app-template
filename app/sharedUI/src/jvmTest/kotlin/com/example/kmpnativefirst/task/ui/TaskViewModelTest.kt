package com.example.kmpnativefirst.task.ui

import androidx.lifecycle.viewModelScope
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskConflict
import com.example.kmpnativefirst.task.data.TaskConflictField
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import com.example.kmpnativefirst.task.data.TaskDraft
import com.example.kmpnativefirst.task.data.TaskEdit
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskMutationKind
import com.example.kmpnativefirst.task.data.TaskProjectConflict
import com.example.kmpnativefirst.task.data.TaskProjectConflictResolution
import com.example.kmpnativefirst.task.data.TaskProjectDraft
import com.example.kmpnativefirst.task.data.TaskProjectEdit
import com.example.kmpnativefirst.task.data.TaskProjectItem
import com.example.kmpnativefirst.task.data.TaskRepository
import com.example.kmpnativefirst.task.data.TaskSyncPhase
import com.example.kmpnativefirst.task.data.TaskSyncResult
import com.example.kmpnativefirst.task.data.TaskSyncState
import com.example.kmpnativefirst.task.data.TaskSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initializesSynchronizesAndProjectsSearchAndSmartViews() = runTest(dispatcher) {
        val repository = FakeTaskRepository(
            initialTasks = listOf(
                taskItem("one", "Book the venue"),
                taskItem("two", "Publish notes", isCompleted = true),
                taskItem(
                    "three",
                    "Prepare release notes",
                    dueDate = LocalDate(2026, 7, 24),
                ),
                taskItem(
                    "four",
                    "Plan next release",
                    dueDate = LocalDate(2026, 7, 25),
                ),
            ),
        )
        val viewModel = taskViewModel(repository)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isInitializing)
        assertEquals(1, repository.syncCalls)
        assertEquals(3, viewModel.uiState.value.activeCount)
        assertEquals(1, viewModel.uiState.value.completedCount)
        assertEquals(listOf("three", "four", "one"), viewModel.uiState.value.tasks.map { it.task.id })

        viewModel.setView(TaskSmartView.TODAY)
        viewModel.setSearchQuery("release")
        advanceUntilIdle()

        assertEquals(listOf("three"), viewModel.uiState.value.tasks.map { it.task.id })

        viewModel.setSearchQuery("")
        viewModel.setView(TaskSmartView.UPCOMING)
        advanceUntilIdle()

        assertEquals(listOf("four"), viewModel.uiState.value.tasks.map { it.task.id })
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun editorValidatesCreatesAndUpdatesTasks() = runTest(dispatcher) {
        val repository = FakeTaskRepository()
        val viewModel = taskViewModel(repository)
        advanceUntilIdle()

        viewModel.showCreateEditor()
        viewModel.saveEditor()
        assertTrue(viewModel.uiState.value.editor?.showValidationErrors == true)

        viewModel.setEditorTitle("Ship Android UI")
        viewModel.setEditorNotes("Verify edge-to-edge behavior")
        viewModel.setEditorPriority(TaskPriority.HIGH)
        viewModel.setEditorDueDate(LocalDate(2026, 7, 25))
        viewModel.saveEditor()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editor)
        val created = repository.tasks.value.single().task
        assertEquals("Ship Android UI", created.title)
        assertEquals(TaskPriority.HIGH, created.priority)
        assertEquals(LocalDate(2026, 7, 25), created.dueDate)

        viewModel.showEditEditor(created.id)
        viewModel.setEditorTitle("Ship Compose UI")
        viewModel.setEditorCompleted(true)
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals("Ship Compose UI", repository.tasks.value.single().task.title)
        assertTrue(repository.tasks.value.single().task.isCompleted)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun preservesPreciseDeadlinesUntilTheCalendarDateIsChanged() = runTest(dispatcher) {
        val preciseDeadline = Instant.parse("2026-07-24T09:30:00Z")
        val repository = FakeTaskRepository(
            initialTasks = listOf(
                taskItem(
                    id = "precise",
                    title = "Join the call",
                    dueAt = preciseDeadline,
                ),
            ),
        )
        val viewModel = taskViewModel(repository)
        advanceUntilIdle()

        viewModel.showEditEditor("precise")
        assertEquals(LocalDate(2026, 7, 24), viewModel.uiState.value.editor?.dueDate)
        assertEquals(preciseDeadline, viewModel.uiState.value.editor?.dueAt)

        viewModel.setEditorTitle("Join the planning call")
        viewModel.saveEditor()
        advanceUntilIdle()
        assertEquals(preciseDeadline, repository.tasks.value.single().task.dueAt)
        assertNull(repository.tasks.value.single().task.dueDate)

        viewModel.showEditEditor("precise")
        viewModel.setEditorDueDate(LocalDate(2026, 7, 26))
        viewModel.saveEditor()
        advanceUntilIdle()
        assertNull(repository.tasks.value.single().task.dueAt)
        assertEquals(
            LocalDate(2026, 7, 26),
            repository.tasks.value.single().task.dueDate,
        )
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun confirmsDestructiveActionsAndResolvesConflicts() = runTest(dispatcher) {
        val conflicted = taskItem(
            id = "conflict",
            title = "Choose a title",
            syncState = TaskSyncState.CONFLICT,
        )
        val repository = FakeTaskRepository(
            initialTasks = listOf(
                taskItem("active", "Keep me"),
                taskItem("done", "Remove me", isCompleted = true),
                conflicted,
            ),
            initialConflicts = listOf(
                conflict(
                    local = conflicted.task,
                    remote = conflicted.task.copy(title = "Remote title", revision = 2),
                ),
            ),
        )
        val viewModel = taskViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDelete("active")
        assertNotNull(viewModel.uiState.value.taskPendingDeletion)
        viewModel.cancelDelete()
        assertNull(viewModel.uiState.value.taskPendingDeletion)

        viewModel.requestClearCompleted()
        assertTrue(viewModel.uiState.value.isConfirmingClearCompleted)
        viewModel.confirmClearCompleted()
        advanceUntilIdle()
        assertEquals(listOf("active", "conflict"), repository.tasks.value.map { it.task.id })

        viewModel.showConflict("conflict")
        assertNotNull(viewModel.uiState.value.selectedConflict)
        viewModel.resolveSelectedConflict(TaskConflictResolution.UseRemote)
        advanceUntilIdle()

        assertTrue(repository.conflicts.value.isEmpty())
        assertEquals("Remote title", repository.tasks.value.last().task.title)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun exposesManualSyncResultAndCanRetryInitialization() = runTest(dispatcher) {
        val repository = FakeTaskRepository(
            syncResult = TaskSyncResult.Success(
                pushedCount = 2,
                pulledCount = 3,
                conflictCount = 0,
            ),
        )
        var attempts = 0
        val viewModel = TaskViewModel(
            timeZone = TimeZone.UTC,
            todayProvider = { TODAY },
        ) {
            attempts += 1
            if (attempts == 1) error("database locked")
            repository
        }
        advanceUntilIdle()

        assertEquals("database locked", viewModel.uiState.value.initializationError)
        assertIs<TaskUiNoticeContent.OperationFailed>(
            viewModel.uiState.value.notice?.content,
        )

        viewModel.retryInitialization()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isInitializing)

        viewModel.consumeNotice(requireNotNull(viewModel.uiState.value.notice).id)
        viewModel.synchronize()
        advanceUntilIdle()

        val notice = assertIs<TaskUiNoticeContent.SyncCompleted>(
            viewModel.uiState.value.notice?.content,
        )
        assertEquals(2, notice.pushedCount)
        assertEquals(3, notice.pulledCount)
        viewModel.viewModelScope.cancel()
    }
}

private class FakeTaskRepository(
    initialTasks: List<TaskItem> = emptyList(),
    initialConflicts: List<TaskConflict> = emptyList(),
    var syncResult: TaskSyncResult = TaskSyncResult.Success(0, 0, 0),
) : TaskRepository {
    private val mutableTasks = MutableStateFlow(initialTasks)
    override val tasks: StateFlow<List<TaskItem>> = mutableTasks.asStateFlow()

    private val mutableConflicts = MutableStateFlow(initialConflicts)
    override val conflicts: StateFlow<List<TaskConflict>> = mutableConflicts.asStateFlow()

    override val projects = MutableStateFlow<List<TaskProjectItem>>(emptyList())
    override val projectConflicts =
        MutableStateFlow<List<TaskProjectConflict>>(emptyList())

    private val mutableSyncStatus = MutableStateFlow(TaskSyncStatus())
    override val syncStatus: StateFlow<TaskSyncStatus> = mutableSyncStatus.asStateFlow()

    var syncCalls = 0
        private set

    override suspend fun create(draft: TaskDraft): Task {
        val id = "created-${mutableTasks.value.size}"
        val created = task(
            id = id,
            title = draft.title,
            notes = draft.notes,
            projectId = draft.projectId,
            priority = draft.priority,
            dueDate = draft.dueDate,
            dueAt = draft.dueAt,
        )
        mutableTasks.value += TaskItem(created, TaskSyncState.PENDING)
        return created
    }

    override suspend fun update(taskId: String, edit: TaskEdit): Task {
        val current = mutableTasks.value.first { it.task.id == taskId }
        val updated = current.task.copy(
            title = edit.title,
            notes = edit.notes,
            projectId = edit.projectId,
            priority = edit.priority,
            dueDate = edit.dueDate,
            dueAt = edit.dueAt,
            isCompleted = edit.isCompleted,
            updatedAt = NOW,
        )
        mutableTasks.value = mutableTasks.value.map {
            if (it.task.id == taskId) {
                TaskItem(updated, TaskSyncState.PENDING)
            } else {
                it
            }
        }
        return updated
    }

    override suspend fun toggleCompleted(taskId: String): Task {
        val current = mutableTasks.value.first { it.task.id == taskId }.task
        return update(
            taskId,
            TaskEdit(
                title = current.title,
                notes = current.notes,
                projectId = current.projectId,
                priority = current.priority,
                dueDate = current.dueDate,
                dueAt = current.dueAt,
                isCompleted = !current.isCompleted,
            ),
        )
    }

    override suspend fun delete(taskId: String) {
        mutableTasks.value = mutableTasks.value.filterNot { it.task.id == taskId }
    }

    override suspend fun clearCompleted() {
        mutableTasks.value = mutableTasks.value.filterNot { it.task.isCompleted }
    }

    override suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
    ) {
        val conflict = mutableConflicts.value.first { it.taskId == taskId }
        val selected = when (resolution) {
            TaskConflictResolution.KeepLocal -> conflict.local
            TaskConflictResolution.UseRemote -> conflict.remote
            is TaskConflictResolution.Merge -> conflict.local?.copy(
                title = resolution.edit.title,
                notes = resolution.edit.notes,
                projectId = resolution.edit.projectId,
                priority = resolution.edit.priority,
                dueDate = resolution.edit.dueDate,
                dueAt = resolution.edit.dueAt,
                isCompleted = resolution.edit.isCompleted,
            )
        }
        mutableTasks.value = mutableTasks.value
            .filterNot { it.task.id == taskId }
            .let { remaining ->
                selected?.let {
                    remaining + TaskItem(it, TaskSyncState.SYNCED)
                } ?: remaining
            }
        mutableConflicts.value = mutableConflicts.value.filterNot { it.taskId == taskId }
    }

    override suspend fun createProject(draft: TaskProjectDraft) =
        error("Project operations are outside this task view-model test.")

    override suspend fun updateProject(
        projectId: String,
        edit: TaskProjectEdit,
    ) = error("Project operations are outside this task view-model test.")

    override suspend fun deleteProject(projectId: String) =
        error("Project operations are outside this task view-model test.")

    override suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
    ) = error("Project operations are outside this task view-model test.")

    override suspend fun sync(): TaskSyncResult {
        syncCalls += 1
        mutableSyncStatus.value = TaskSyncStatus(phase = TaskSyncPhase.SYNCING)
        mutableSyncStatus.value = TaskSyncStatus()
        return syncResult
    }

    override fun close() = Unit
}

private val NOW = Instant.parse("2026-07-23T08:00:00Z")
private val TODAY = LocalDate(2026, 7, 24)

private fun taskViewModel(repository: TaskRepository): TaskViewModel = TaskViewModel(
    timeZone = TimeZone.UTC,
    todayProvider = { TODAY },
) {
    repository
}

private fun taskItem(
    id: String,
    title: String,
    dueDate: LocalDate? = null,
    dueAt: Instant? = null,
    isCompleted: Boolean = false,
    syncState: TaskSyncState = TaskSyncState.SYNCED,
): TaskItem = TaskItem(
    task = task(
        id = id,
        title = title,
        dueDate = dueDate,
        dueAt = dueAt,
        isCompleted = isCompleted,
    ),
    syncState = syncState,
)

private fun task(
    id: String,
    title: String,
    notes: String? = null,
    projectId: String? = null,
    priority: TaskPriority = TaskPriority.NONE,
    dueDate: LocalDate? = null,
    dueAt: Instant? = null,
    isCompleted: Boolean = false,
): Task = Task(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    priority = priority,
    dueDate = dueDate,
    dueAt = dueAt,
    isCompleted = isCompleted,
    createdAt = NOW,
    updatedAt = NOW,
    revision = 1,
)

private fun conflict(
    local: Task?,
    remote: Task?,
): TaskConflict = TaskConflict(
    taskId = requireNotNull(local ?: remote).id,
    mutationKind = TaskMutationKind.UPDATE,
    base = local,
    local = local,
    remote = remote,
    conflictingFields = setOf(TaskConflictField.TITLE),
    detectedAt = NOW,
)
