package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
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
        repository.status.value = TaskSyncStatus(pendingCount = 1)
        runCurrent()

        assertEquals("Native task", snapshots.last().tasks.single().task.title)
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
        )
        store.update(
            taskId = TASK_ID_1,
            title = "Ship release",
            notes = null,
            priority = TaskPriority.MEDIUM,
            dueDate = null,
            dueAt = null,
            isCompleted = true,
        )

        assertEquals(
            TaskDraft(
                title = "Plan release",
                notes = "From Swift",
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
        store.close()

        assertTrue(repository.closed)
    }
}

private class RecordingTaskRepository : TaskRepository {
    val taskItems = MutableStateFlow<List<TaskItem>>(emptyList())
    val taskConflicts = MutableStateFlow<List<TaskConflict>>(emptyList())
    val status = MutableStateFlow(TaskSyncStatus())
    var createdDraft: TaskDraft? = null
    var updatedEdit: TaskEdit? = null
    var resolution: TaskConflictResolution? = null
    var closed = false

    override val tasks: Flow<List<TaskItem>> = taskItems
    override val conflicts: Flow<List<TaskConflict>> = taskConflicts
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
