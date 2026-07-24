package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskSmartView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppleTaskStoreTest {
    @Test
    fun emitsOneSnapshotForTheSwiftBoundary() = runTest {
        val repository = RecordingTaskRepository()
        val store = AppleTaskStore(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val snapshots = mutableListOf<AppleTaskSnapshot>()

        val observation = store.observe(snapshots::add)
        runCurrent()
        repository.taskItems.value = listOf(
            TaskItem(task(title = "Native task"), TaskSyncState.PENDING),
        )
        repository.projectItems.value = listOf(
            TaskProjectItem(
                project = taskProject(name = "Personal"),
                syncState = TaskSyncState.PENDING,
            ),
        )
        repository.status.value = TaskSyncStatus(pendingCount = 1)
        runCurrent()

        assertEquals("Native task", snapshots.last().tasks.single().task.title)
        assertEquals("Personal", snapshots.last().projects.single().project.name)
        assertEquals(1, snapshots.last().syncStatus.pendingCount)

        observation.cancel()
        repository.taskItems.value = emptyList()
        runCurrent()
        assertTrue(snapshots.last().tasks.isNotEmpty())
    }

    @Test
    fun mapsPrimitiveEditorValuesToRepositoryModels() = runTest {
        val repository = RecordingTaskRepository()
        val store = AppleTaskStore(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        store.create(
            title = "Plan release",
            notes = "From Swift",
            priority = TaskPriority.HIGH,
            dueDate = LocalDate(2026, 7, 24),
            dueAt = null,
            projectId = TASK_ID_2,
        )
        store.update(
            taskId = TASK_ID_1,
            title = "Ship release",
            notes = null,
            priority = TaskPriority.MEDIUM,
            dueDate = null,
            dueAt = null,
            isCompleted = true,
            projectId = TASK_ID_2,
        )

        assertEquals(
            TaskDraft(
                title = "Plan release",
                notes = "From Swift",
                projectId = TASK_ID_2,
                priority = TaskPriority.HIGH,
                dueDate = LocalDate(2026, 7, 24),
                dueAt = null,
            ),
            repository.createdDraft,
        )
        assertEquals(
            TaskEdit(
                title = "Ship release",
                notes = null,
                projectId = TASK_ID_2,
                priority = TaskPriority.MEDIUM,
                dueDate = null,
                dueAt = null,
                isCompleted = true,
            ),
            repository.updatedEdit,
        )
    }

    @Test
    fun projectsSmartViewsWithSharedPlanningRules() = runTest {
        val repository = RecordingTaskRepository()
        val store = AppleTaskStore(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val observation = store.observe {}
        repository.taskItems.value = listOf(
            TaskItem(
                task(title = "Inbox"),
                TaskSyncState.SYNCED,
            ),
            TaskItem(
                task(
                    id = TASK_ID_1,
                    title = "Today",
                    dueDate = LocalDate(2026, 7, 24),
                ),
                TaskSyncState.PENDING,
            ),
        )
        runCurrent()

        assertEquals(
            listOf("Today"),
            store.plannedTasks(
                view = TaskSmartView.TODAY,
                todayYear = 2026,
                todayMonth = 7,
                todayDay = 24,
                timeZoneId = "Asia/Shanghai",
            ).map { it.task.title },
        )
        observation.cancel()
    }

    @Test
    fun forwardsConflictChoicesAndClosesRepository() = runTest {
        val repository = RecordingTaskRepository()
        val store = AppleTaskStore(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        store.keepLocal(TASK_ID_1)
        assertTrue(repository.resolution is TaskConflictResolution.KeepLocal)
        store.useRemote(TASK_ID_1)
        assertTrue(repository.resolution is TaskConflictResolution.UseRemote)
        store.mergeConflict(
            taskId = TASK_ID_1,
            title = "Merged",
            notes = null,
            priority = TaskPriority.NONE,
            dueDate = null,
            dueAt = null,
            isCompleted = false,
            projectId = TASK_ID_2,
        )
        val merge = assertIs<TaskConflictResolution.Merge>(repository.resolution)
        assertEquals(TASK_ID_2, merge.edit.projectId)
        store.close()

        assertTrue(repository.closed)
    }

    @Test
    fun mapsProjectEditorValuesAndConflictChoices() = runTest {
        val repository = RecordingTaskRepository()
        val store = AppleTaskStore(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        store.createProject(
            name = "Personal",
            color = TaskProjectColor.PURPLE,
        )
        assertEquals(
            TaskProjectDraft(
                name = "Personal",
                color = TaskProjectColor.PURPLE,
            ),
            repository.createdProjectDraft,
        )

        store.updateProject(
            projectId = TASK_ID_1,
            name = "Home",
            color = TaskProjectColor.GREEN,
        )
        assertEquals(
            TaskProjectEdit(
                name = "Home",
                color = TaskProjectColor.GREEN,
            ),
            repository.updatedProjectEdit,
        )

        store.deleteProject(TASK_ID_1)
        assertEquals(TASK_ID_1, repository.deletedProjectId)

        store.keepLocalProject(TASK_ID_1)
        assertTrue(
            repository.projectResolution is
                TaskProjectConflictResolution.KeepLocal,
        )
        store.useRemoteProject(TASK_ID_1)
        assertTrue(
            repository.projectResolution is
                TaskProjectConflictResolution.UseRemote,
        )
        store.mergeProjectConflict(
            projectId = TASK_ID_1,
            name = "Merged",
            color = TaskProjectColor.ROSE,
        )
        val merge = assertIs<TaskProjectConflictResolution.Merge>(
            repository.projectResolution,
        )
        assertEquals("Merged", merge.edit.name)
        assertEquals(TaskProjectColor.ROSE, merge.edit.color)
    }
}

private class RecordingTaskRepository : TaskRepository {
    val taskItems = MutableStateFlow<List<TaskItem>>(emptyList())
    val taskConflicts = MutableStateFlow<List<TaskConflict>>(emptyList())
    val projectItems = MutableStateFlow<List<TaskProjectItem>>(emptyList())
    val projectConflictItems =
        MutableStateFlow<List<TaskProjectConflict>>(emptyList())
    val status = MutableStateFlow(TaskSyncStatus())
    var createdDraft: TaskDraft? = null
    var updatedEdit: TaskEdit? = null
    var resolution: TaskConflictResolution? = null
    var createdProjectDraft: TaskProjectDraft? = null
    var updatedProjectEdit: TaskProjectEdit? = null
    var deletedProjectId: String? = null
    var projectResolution: TaskProjectConflictResolution? = null
    var closed = false

    override val tasks: Flow<List<TaskItem>> = taskItems
    override val conflicts: Flow<List<TaskConflict>> = taskConflicts
    override val projects: Flow<List<TaskProjectItem>> = projectItems
    override val projectConflicts: Flow<List<TaskProjectConflict>> =
        projectConflictItems
    override val labels = MutableStateFlow<List<TaskLabelItem>>(emptyList())
    override val labelConflicts =
        MutableStateFlow<List<TaskLabelConflict>>(emptyList())
    override val syncStatus: StateFlow<TaskSyncStatus> = status

    override suspend fun create(draft: TaskDraft): Task {
        createdDraft = draft
        return task()
    }

    override suspend fun update(
        taskId: String,
        edit: TaskEdit,
    ): Task {
        updatedEdit = edit
        return task(id = taskId)
    }

    override suspend fun toggleCompleted(taskId: String): Task =
        task(id = taskId, isCompleted = true)

    override suspend fun delete(taskId: String) = Unit

    override suspend fun clearCompleted() = Unit

    override suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
    ) {
        this.resolution = resolution
    }

    override suspend fun createProject(draft: TaskProjectDraft) =
        taskProject().also {
            createdProjectDraft = draft
        }

    override suspend fun updateProject(
        projectId: String,
        edit: TaskProjectEdit,
    ) = taskProject(id = projectId).also {
        updatedProjectEdit = edit
    }

    override suspend fun deleteProject(projectId: String) {
        deletedProjectId = projectId
    }

    override suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
    ) {
        projectResolution = resolution
    }

    override suspend fun createLabel(draft: TaskLabelDraft) =
        error("Label operations are outside this Apple task-store test.")

    override suspend fun updateLabel(
        labelId: String,
        edit: TaskLabelEdit,
    ) = error("Label operations are outside this Apple task-store test.")

    override suspend fun deleteLabel(labelId: String) =
        error("Label operations are outside this Apple task-store test.")

    override suspend fun resolveLabelConflict(
        labelId: String,
        resolution: TaskLabelConflictResolution,
    ) = error("Label operations are outside this Apple task-store test.")

    override suspend fun sync(): TaskSyncResult =
        TaskSyncResult.Success(
            pushedCount = 0,
            pulledCount = 0,
            conflictCount = 0,
        )

    override fun close() {
        closed = true
    }
}
