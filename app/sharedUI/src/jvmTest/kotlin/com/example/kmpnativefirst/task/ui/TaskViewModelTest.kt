package com.example.kmpnativefirst.task.ui

import androidx.lifecycle.viewModelScope
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskFilter
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.data.TaskConflict
import com.example.kmpnativefirst.task.data.TaskConflictField
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import com.example.kmpnativefirst.task.data.TaskDraft
import com.example.kmpnativefirst.task.data.TaskEdit
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskMutationKind
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
    fun initializesSynchronizesAndProjectsSearchAndFilters() = runTest(dispatcher) {
        val repository = FakeTaskRepository(
            initialTasks = listOf(
                taskItem("one", "Book the venue"),
                taskItem("two", "Publish notes", isCompleted = true),
                taskItem("three", "Prepare release notes"),
            ),
        )
        val viewModel = TaskViewModel { repository }

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isInitializing)
        assertEquals(1, repository.syncCalls)
        assertEquals(2, viewModel.uiState.value.activeCount)
        assertEquals(1, viewModel.uiState.value.completedCount)

        viewModel.setFilter(TaskFilter.ACTIVE)
        viewModel.setSearchQuery("release")
        advanceUntilIdle()

        assertEquals(listOf("three"), viewModel.uiState.value.tasks.map { it.task.id })
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun editorValidatesCreatesAndUpdatesTasks() = runTest(dispatcher) {
        val repository = FakeTaskRepository()
        val viewModel = TaskViewModel { repository }
        advanceUntilIdle()

        viewModel.showCreateEditor()
        viewModel.saveEditor()
        assertTrue(viewModel.uiState.value.editor?.showValidationErrors == true)

        viewModel.setEditorTitle("Ship Android UI")
        viewModel.setEditorNotes("Verify edge-to-edge behavior")
        viewModel.setEditorPriority(TaskPriority.HIGH)
        viewModel.saveEditor()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editor)
        val created = repository.tasks.value.single().task
        assertEquals("Ship Android UI", created.title)
        assertEquals(TaskPriority.HIGH, created.priority)

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
        val viewModel = TaskViewModel { repository }
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
        val viewModel = TaskViewModel {
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
            priority = draft.priority,
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
            priority = edit.priority,
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
                priority = current.priority,
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
                priority = resolution.edit.priority,
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

    override suspend fun sync(): TaskSyncResult {
        syncCalls += 1
        mutableSyncStatus.value = TaskSyncStatus(phase = TaskSyncPhase.SYNCING)
        mutableSyncStatus.value = TaskSyncStatus()
        return syncResult
    }

    override fun close() = Unit
}

private val NOW = Instant.parse("2026-07-23T08:00:00Z")

private fun taskItem(
    id: String,
    title: String,
    isCompleted: Boolean = false,
    syncState: TaskSyncState = TaskSyncState.SYNCED,
): TaskItem = TaskItem(
    task = task(id, title, isCompleted = isCompleted),
    syncState = syncState,
)

private fun task(
    id: String,
    title: String,
    notes: String? = null,
    priority: TaskPriority = TaskPriority.NONE,
    dueAt: Instant? = null,
    isCompleted: Boolean = false,
): Task = Task(
    id = id,
    title = title,
    notes = notes,
    priority = priority,
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
