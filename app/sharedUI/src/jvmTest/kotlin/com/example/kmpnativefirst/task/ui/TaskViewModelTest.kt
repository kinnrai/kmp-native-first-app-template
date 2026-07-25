package com.example.kmpnativefirst.task.ui

import androidx.lifecycle.viewModelScope
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskConflict
import com.example.kmpnativefirst.task.data.TaskConflictField
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import com.example.kmpnativefirst.task.data.TaskDraft
import com.example.kmpnativefirst.task.data.TaskEdit
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskLabelConflict
import com.example.kmpnativefirst.task.data.TaskLabelConflictResolution
import com.example.kmpnativefirst.task.data.TaskLabelDraft
import com.example.kmpnativefirst.task.data.TaskLabelEdit
import com.example.kmpnativefirst.task.data.TaskLabelItem
import com.example.kmpnativefirst.task.data.TaskMutationKind
import com.example.kmpnativefirst.task.data.TaskProjectConflict
import com.example.kmpnativefirst.task.data.TaskProjectConflictField
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
    fun selectsProjectsAndCreatesTasksInTheCurrentProject() = runTest(dispatcher) {
        val work = projectItem("work", "Work", TaskProjectColor.BLUE)
        val personal = projectItem("personal", "Personal", TaskProjectColor.GREEN)
        val repository = FakeTaskRepository(
            initialTasks = listOf(
                taskItem("work-task", "Prepare launch", projectId = work.project.id),
                taskItem(
                    "personal-task",
                    "Book dinner",
                    projectId = personal.project.id,
                ),
            ),
            initialProjects = listOf(work, personal),
        )
        val viewModel = taskViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.projectTaskCounts["work"])
        viewModel.setProject("work")
        advanceUntilIdle()

        assertEquals("work", viewModel.uiState.value.selectedProjectId)
        assertEquals(
            listOf("work-task"),
            viewModel.uiState.value.tasks.map { it.task.id },
        )

        viewModel.showCreateEditor()
        assertEquals("work", viewModel.uiState.value.editor?.projectId)
        viewModel.setEditorTitle("Publish release")
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals(
            "work",
            repository.tasks.value.first {
                it.task.title == "Publish release"
            }.task.projectId,
        )

        viewModel.setView(TaskSmartView.INBOX)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedProjectId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun validatesCreatesEditsAndDeletesProjects() = runTest(dispatcher) {
        val repository = FakeTaskRepository()
        val viewModel = taskViewModel(repository)
        advanceUntilIdle()

        viewModel.showCreateProjectEditor()
        viewModel.saveProject()
        assertTrue(viewModel.uiState.value.projectEditor?.showValidationErrors == true)

        viewModel.setProjectName("  Product  ")
        viewModel.setProjectColor(TaskProjectColor.PURPLE)
        viewModel.saveProject()
        advanceUntilIdle()

        val created = repository.projects.value.single().project
        assertEquals("Product", created.name)
        assertEquals(TaskProjectColor.PURPLE, created.color)
        assertEquals(created.id, viewModel.uiState.value.selectedProjectId)

        viewModel.showEditProjectEditor(created.id)
        viewModel.setProjectName("Roadmap")
        viewModel.saveProject()
        advanceUntilIdle()
        assertEquals("Roadmap", repository.projects.value.single().project.name)

        viewModel.showCreateEditor()
        viewModel.setEditorTitle("Write launch plan")
        viewModel.saveEditor()
        advanceUntilIdle()
        assertEquals(created.id, repository.tasks.value.single().task.projectId)

        viewModel.requestDeleteProject(created.id)
        assertNotNull(viewModel.uiState.value.projectPendingDeletion)
        viewModel.confirmDeleteProject()
        advanceUntilIdle()

        assertTrue(repository.projects.value.isEmpty())
        assertNull(repository.tasks.value.single().task.projectId)
        assertNull(viewModel.uiState.value.selectedProjectId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun resolvesProjectConflicts() = runTest(dispatcher) {
        val local = project("project", "Local", TaskProjectColor.ORANGE)
        val remote = local.copy(
            name = "Remote",
            color = TaskProjectColor.ROSE,
            revision = 2,
        )
        val repository = FakeTaskRepository(
            initialProjects = listOf(
                TaskProjectItem(local, TaskSyncState.CONFLICT),
            ),
            initialProjectConflicts = listOf(
                TaskProjectConflict(
                    projectId = local.id,
                    mutationKind = TaskMutationKind.UPDATE,
                    base = local.copy(name = "Original"),
                    local = local,
                    remote = remote,
                    conflictingFields = setOf(TaskProjectConflictField.NAME),
                    detectedAt = NOW,
                ),
            ),
        )
        val viewModel = taskViewModel(repository)
        advanceUntilIdle()

        viewModel.showProjectConflict(local.id)
        assertNotNull(viewModel.uiState.value.selectedProjectConflict)
        viewModel.resolveSelectedProjectConflict(
            TaskProjectConflictResolution.UseRemote,
        )
        advanceUntilIdle()

        assertTrue(repository.projectConflicts.value.isEmpty())
        assertEquals("Remote", repository.projects.value.single().project.name)
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
    initialProjects: List<TaskProjectItem> = emptyList(),
    initialProjectConflicts: List<TaskProjectConflict> = emptyList(),
    var syncResult: TaskSyncResult = TaskSyncResult.Success(0, 0, 0),
) : TaskRepository {
    private val mutableTasks = MutableStateFlow(initialTasks)
    override val tasks: StateFlow<List<TaskItem>> = mutableTasks.asStateFlow()

    private val mutableConflicts = MutableStateFlow(initialConflicts)
    override val conflicts: StateFlow<List<TaskConflict>> = mutableConflicts.asStateFlow()

    private val mutableProjects = MutableStateFlow(initialProjects)
    override val projects: StateFlow<List<TaskProjectItem>> =
        mutableProjects.asStateFlow()

    private val mutableProjectConflicts = MutableStateFlow(initialProjectConflicts)
    override val projectConflicts: StateFlow<List<TaskProjectConflict>> =
        mutableProjectConflicts.asStateFlow()
    override val labels = MutableStateFlow<List<TaskLabelItem>>(emptyList())
    override val labelConflicts =
        MutableStateFlow<List<TaskLabelConflict>>(emptyList())

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
            labelIds = draft.labelIds,
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
            labelIds = edit.labelIds ?: current.task.labelIds,
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
                labelIds = current.labelIds,
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

    override suspend fun createProject(draft: TaskProjectDraft): TaskProject {
        val created = project(
            id = "project-${mutableProjects.value.size}",
            name = draft.name.trim(),
            color = draft.color,
        )
        mutableProjects.value += TaskProjectItem(created, TaskSyncState.PENDING)
        return created
    }

    override suspend fun updateProject(
        projectId: String,
        edit: TaskProjectEdit,
    ): TaskProject {
        val current = mutableProjects.value.first {
            it.project.id == projectId
        }
        val updated = current.project.copy(
            name = edit.name.trim(),
            color = edit.color,
            updatedAt = NOW,
        )
        mutableProjects.value = mutableProjects.value.map {
            if (it.project.id == projectId) {
                TaskProjectItem(updated, TaskSyncState.PENDING)
            } else {
                it
            }
        }
        return updated
    }

    override suspend fun deleteProject(projectId: String) {
        mutableProjects.value = mutableProjects.value.filterNot {
            it.project.id == projectId
        }
        mutableTasks.value = mutableTasks.value.map { item ->
            if (item.task.projectId == projectId) {
                item.copy(
                    task = item.task.copy(
                        projectId = null,
                        updatedAt = NOW,
                    ),
                    syncState = TaskSyncState.PENDING,
                )
            } else {
                item
            }
        }
    }

    override suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
    ) {
        val conflict = mutableProjectConflicts.value.first {
            it.projectId == projectId
        }
        val selected = when (resolution) {
            TaskProjectConflictResolution.KeepLocal -> conflict.local
            TaskProjectConflictResolution.UseRemote -> conflict.remote
            is TaskProjectConflictResolution.Merge -> conflict.local?.copy(
                name = resolution.edit.name,
                color = resolution.edit.color,
            )
        }
        mutableProjects.value = mutableProjects.value
            .filterNot { it.project.id == projectId }
            .let { remaining ->
                selected?.let {
                    remaining + TaskProjectItem(it, TaskSyncState.SYNCED)
                } ?: remaining
            }
        mutableProjectConflicts.value = mutableProjectConflicts.value.filterNot {
            it.projectId == projectId
        }
    }

    override suspend fun createLabel(draft: TaskLabelDraft) =
        error("Label operations are outside this task view-model test.")

    override suspend fun updateLabel(
        labelId: String,
        edit: TaskLabelEdit,
    ) = error("Label operations are outside this task view-model test.")

    override suspend fun deleteLabel(labelId: String) =
        error("Label operations are outside this task view-model test.")

    override suspend fun resolveLabelConflict(
        labelId: String,
        resolution: TaskLabelConflictResolution,
    ) = error("Label operations are outside this task view-model test.")

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
    projectId: String? = null,
    dueDate: LocalDate? = null,
    dueAt: Instant? = null,
    isCompleted: Boolean = false,
    syncState: TaskSyncState = TaskSyncState.SYNCED,
): TaskItem = TaskItem(
    task = task(
        id = id,
        title = title,
        projectId = projectId,
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
    labelIds: List<String> = emptyList(),
    priority: TaskPriority = TaskPriority.NONE,
    dueDate: LocalDate? = null,
    dueAt: Instant? = null,
    isCompleted: Boolean = false,
): Task = Task(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    labelIds = labelIds,
    priority = priority,
    dueDate = dueDate,
    dueAt = dueAt,
    isCompleted = isCompleted,
    createdAt = NOW,
    updatedAt = NOW,
    revision = 1,
)

private fun projectItem(
    id: String,
    name: String,
    color: TaskProjectColor,
    syncState: TaskSyncState = TaskSyncState.SYNCED,
): TaskProjectItem = TaskProjectItem(
    project = project(id, name, color),
    syncState = syncState,
)

private fun project(
    id: String,
    name: String,
    color: TaskProjectColor,
): TaskProject = TaskProject(
    id = id,
    name = name,
    color = color,
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
