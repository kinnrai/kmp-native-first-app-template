package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProjectColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstTaskRepositoryTest {
    @Test
    fun writesLocallyBeforeAnyNetworkSynchronization() = runTest {
        val remote = FakeTaskRemoteDataSource()
        val ids = SequentialIds()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )

        val created = repository.create(
            TaskDraft(
                title = "  Offline task  ",
                notes = "  available immediately  ",
            ),
        )

        val item = repository.tasks.first().single()
        assertEquals(created, item.task)
        assertEquals("Offline task", item.task.title)
        assertEquals("available immediately", item.task.notes)
        assertEquals(TaskSyncState.PENDING, item.syncState)
        assertEquals(1, repository.syncStatus.value.pendingCount)
        assertEquals(0, remote.createCalls)
    }

    @Test
    fun synchronizesAnOfflineCreateAndStoresTheServerRevision() = runTest {
        val ids = SequentialIds()
        val project = taskProject()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(initialProjects = listOf(project)),
            remote = FakeTaskRemoteDataSource(),
            projectRemote = FakeTaskProjectRemoteDataSource(listOf(project)),
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )
        repository.create(
            TaskDraft(
                title = "Ship it",
                projectId = PROJECT_ID_1,
            ),
        )

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, result.pushedCount)
        val item = repository.tasks.first().single()
        assertEquals(1, item.task.revision)
        assertEquals(PROJECT_ID_1, item.task.projectId)
        assertEquals(TaskSyncState.SYNCED, item.syncState)
        assertEquals(0, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun followsACreateWithAnUpdateWhenTheCreateEndpointCannotRepresentAnOfflineEdit() = runTest {
        val remote = object : FakeTaskRemoteDataSource() {
            override suspend fun create(task: com.example.kmpnativefirst.task.Task):
                com.example.kmpnativefirst.task.Task {
                createCalls += 1
                return task.copy(
                    isCompleted = false,
                    revision = 1,
                ).also { records[it.id] = it }
            }
        }
        val ids = SequentialIds()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )
        val created = repository.create(TaskDraft(title = "Complete offline"))
        repository.toggleCompleted(created.id)

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(2, result.pushedCount)
        assertEquals(1, remote.createCalls)
        assertEquals(1, remote.replaceCalls)
        assertTrue(repository.tasks.first().single().task.isCompleted)
        assertEquals(2, repository.tasks.first().single().task.revision)
    }

    @Test
    fun compensatesOnTheServerWhenAnOfflineCreateIsDeletedInFlight() = runTest {
        val createStarted = CompletableDeferred<Unit>()
        val allowCreateToFinish = CompletableDeferred<Unit>()
        val remote = object : FakeTaskRemoteDataSource() {
            override suspend fun create(task: com.example.kmpnativefirst.task.Task):
                com.example.kmpnativefirst.task.Task {
                createStarted.complete(Unit)
                allowCreateToFinish.await()
                return super.create(task)
            }
        }
        val ids = SequentialIds()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )
        val created = repository.create(TaskDraft(title = "Transient"))
        val synchronization = async { repository.sync() }
        createStarted.await()

        repository.delete(created.id)
        allowCreateToFinish.complete(Unit)

        val result = assertIs<TaskSyncResult.Success>(synchronization.await())
        assertEquals(2, result.pushedCount)
        assertEquals(1, remote.createCalls)
        assertEquals(1, remote.deleteCalls)
        assertTrue(repository.tasks.first().isEmpty())
    }

    @Test
    fun rebasesAnEditMadeWhileCreateIsInFlight() = runTest {
        val createStarted = CompletableDeferred<Unit>()
        val allowCreateToFinish = CompletableDeferred<Unit>()
        val remote = object : FakeTaskRemoteDataSource() {
            override suspend fun create(task: com.example.kmpnativefirst.task.Task):
                com.example.kmpnativefirst.task.Task {
                createStarted.complete(Unit)
                allowCreateToFinish.await()
                return super.create(task)
            }
        }
        val ids = SequentialIds()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )
        val created = repository.create(TaskDraft(title = "First"))
        val synchronization = async { repository.sync() }
        createStarted.await()

        repository.update(created.id, created.toEdit().copy(title = "Second"))
        allowCreateToFinish.complete(Unit)

        val result = assertIs<TaskSyncResult.Success>(synchronization.await())
        assertEquals(2, result.pushedCount)
        assertEquals("Second", repository.tasks.first().single().task.title)
        assertEquals(2, repository.tasks.first().single().task.revision)
        assertEquals(TaskSyncState.SYNCED, repository.tasks.first().single().syncState)
    }

    @Test
    fun treatsAMatchingCreateConflictAsAnIdempotentRetry() = runTest {
        val localTask = task(revision = 0)
        val local = InMemoryTaskLocalDataSource()
        local.applyCreate(localTask, "create", TEST_INSTANT)
        val repository = OfflineFirstTaskRepository(
            local = local,
            remote = FakeTaskRemoteDataSource(
                listOf(localTask.copy(revision = 1)),
            ),
            clock = AdvancingClock(),
        )

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, result.pushedCount)
        assertEquals(TaskSyncState.SYNCED, repository.tasks.first().single().syncState)
        assertEquals(1, repository.tasks.first().single().task.revision)
        assertTrue(repository.conflicts.first().isEmpty())
    }

    @Test
    fun exposesAUuidCollisionInsteadOfOverwritingAnUnrelatedRemoteTask() = runTest {
        val localTask = task(title = "Local", revision = 0)
        val local = InMemoryTaskLocalDataSource()
        local.applyCreate(localTask, "create", TEST_INSTANT)
        val repository = OfflineFirstTaskRepository(
            local = local,
            remote = FakeTaskRemoteDataSource(
                listOf(task(title = "Unrelated remote", revision = 4)),
            ),
            clock = AdvancingClock(),
        )

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, result.conflictCount)
        assertEquals(
            setOf(TaskConflictField.CREATION),
            repository.conflicts.first().single().conflictingFields,
        )
        assertEquals(TaskSyncState.CONFLICT, repository.tasks.first().single().syncState)
    }

    @Test
    fun leavesTheOutboxIntactWhenTheNetworkFails() = runTest {
        val remote = object : FakeTaskRemoteDataSource() {
            override suspend fun create(task: com.example.kmpnativefirst.task.Task) =
                throw IllegalStateException("offline")
        }
        val ids = SequentialIds()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )
        repository.create(TaskDraft(title = "Retry later"))

        val result = assertIs<TaskSyncResult.Failed>(repository.sync())

        assertEquals(TaskSyncFailureKind.NETWORK, result.failure.kind)
        assertEquals(TaskSyncPhase.FAILED, repository.syncStatus.value.phase)
        assertEquals(1, repository.syncStatus.value.pendingCount)
        assertEquals(TaskSyncState.PENDING, repository.tasks.first().single().syncState)
    }

    @Test
    fun automaticallyMergesIndependentConcurrentChanges() = runTest {
        val base = task()
        val remoteVersion = base.copy(
            priority = TaskPriority.HIGH,
            revision = 2,
        )
        val remote = FakeTaskRemoteDataSource(listOf(remoteVersion))
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(base)),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = { "operation" },
        )
        repository.update(
            base.id,
            base.toEdit().copy(title = "Local title"),
        )

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, result.pushedCount)
        assertEquals(2, remote.replaceCalls)
        val synchronized = repository.tasks.first().single()
        assertEquals("Local title", synchronized.task.title)
        assertEquals(TaskPriority.HIGH, synchronized.task.priority)
        assertEquals(3, synchronized.task.revision)
        assertEquals(TaskSyncState.SYNCED, synchronized.syncState)
    }

    @Test
    fun recordsSameFieldConflictsAndCanKeepTheLocalVersion() = runTest {
        val base = task(title = "Original")
        val remote = FakeTaskRemoteDataSource(
            listOf(base.copy(title = "Remote", revision = 2)),
        )
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(base)),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = { "operation" },
        )
        repository.update(
            base.id,
            base.toEdit().copy(title = "Local"),
        )

        val firstSync = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, firstSync.conflictCount)
        val conflict = repository.conflicts.first().single()
        assertEquals(setOf(TaskConflictField.TITLE), conflict.conflictingFields)
        assertEquals(TaskSyncState.CONFLICT, repository.tasks.first().single().syncState)

        repository.resolveConflict(base.id, TaskConflictResolution.KeepLocal)
        assertTrue(repository.conflicts.first().isEmpty())
        assertEquals(TaskSyncState.PENDING, repository.tasks.first().single().syncState)

        val secondSync = assertIs<TaskSyncResult.Success>(repository.sync())
        assertEquals(1, secondSync.pushedCount)
        assertEquals("Local", repository.tasks.first().single().task.title)
        assertEquals(TaskSyncState.SYNCED, repository.tasks.first().single().syncState)
    }

    @Test
    fun resolvesAConflictWithAnExplicitlyMergedVersion() = runTest {
        val base = task(title = "Original", notes = "Base")
        val remote = FakeTaskRemoteDataSource(
            listOf(base.copy(title = "Remote", notes = "Remote note", revision = 2)),
        )
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(base)),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = { "operation" },
        )
        repository.update(
            base.id,
            base.toEdit().copy(title = "Local"),
        )
        repository.sync()

        repository.resolveConflict(
            base.id,
            TaskConflictResolution.Merge(
                TaskEdit(
                    title = "Combined",
                    notes = "Remote note",
                ),
            ),
        )
        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, result.pushedCount)
        assertTrue(repository.conflicts.first().isEmpty())
        val synchronized = repository.tasks.first().single()
        assertEquals("Combined", synchronized.task.title)
        assertEquals("Remote note", synchronized.task.notes)
        assertEquals(3, synchronized.task.revision)
        assertEquals(TaskSyncState.SYNCED, synchronized.syncState)
    }

    @Test
    fun restoresTheRemoteVersionWhenAConflictingDeleteIsRejected() = runTest {
        val base = task()
        val changedRemotely = base.copy(
            title = "Updated elsewhere",
            revision = 2,
        )
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(base)),
            remote = FakeTaskRemoteDataSource(listOf(changedRemotely)),
            clock = AdvancingClock(),
            idGenerator = { "operation" },
        )
        repository.delete(base.id)

        val firstSync = assertIs<TaskSyncResult.Success>(repository.sync())
        assertEquals(1, firstSync.conflictCount)
        assertEquals(
            setOf(TaskConflictField.DELETION),
            repository.conflicts.first().single().conflictingFields,
        )

        repository.resolveConflict(base.id, TaskConflictResolution.UseRemote)

        assertTrue(repository.conflicts.first().isEmpty())
        assertEquals(0, repository.syncStatus.value.pendingCount)
        val restored = repository.tasks.first().single()
        assertEquals("Updated elsewhere", restored.task.title)
        assertEquals(2, restored.task.revision)
        assertEquals(TaskSyncState.SYNCED, restored.syncState)
    }

    @Test
    fun treatsAnAlreadyDeletedRemoteTaskAsACompletedDelete() = runTest {
        val base = task()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(base)),
            remote = FakeTaskRemoteDataSource(),
            clock = AdvancingClock(),
            idGenerator = { "delete-operation" },
        )
        repository.delete(base.id)

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, result.pushedCount)
        assertTrue(repository.tasks.first().isEmpty())
        assertEquals(0, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun clearsCompletedTasksThroughRevisionAwareDeletes() = runTest {
        val completed = task(id = TASK_ID_1, isCompleted = true)
        val active = task(id = TASK_ID_2, title = "Active")
        val remote = FakeTaskRemoteDataSource(listOf(completed, active))
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(completed, active)),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = { "delete-operation" },
        )

        repository.clearCompleted()

        assertEquals(listOf(TASK_ID_2), repository.tasks.first().map { it.task.id })
        assertEquals(1, repository.syncStatus.value.pendingCount)
        val result = assertIs<TaskSyncResult.Success>(repository.sync())
        assertEquals(1, result.pushedCount)
        assertEquals(1, remote.deleteCalls)
        assertEquals(listOf(TASK_ID_2), repository.tasks.first().map { it.task.id })
    }

    @Test
    fun leavesConflictedCompletedTasksForExplicitResolutionWhenClearing() = runTest {
        val conflicted = task(id = TASK_ID_1, title = "Base", isCompleted = true)
        val clearable = task(id = TASK_ID_2, title = "Clearable", isCompleted = true)
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(listOf(conflicted, clearable)),
            remote = FakeTaskRemoteDataSource(
                listOf(
                    conflicted.copy(title = "Remote", revision = 2),
                    clearable,
                ),
            ),
            clock = AdvancingClock(),
            idGenerator = { "operation" },
        )
        repository.update(
            conflicted.id,
            conflicted.toEdit().copy(title = "Local"),
        )
        repository.sync()

        repository.clearCompleted()

        val remaining = repository.tasks.first().single()
        assertEquals(TASK_ID_1, remaining.task.id)
        assertEquals(TaskSyncState.CONFLICT, remaining.syncState)
        assertEquals(1, repository.conflicts.first().size)
        assertEquals(1, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun keepsInvalidInputOutOfTheLocalDatabase() = runTest {
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = FakeTaskRemoteDataSource(),
        )

        assertFailsWith<InvalidTaskInputException> {
            repository.create(TaskDraft(title = "   "))
        }

        assertTrue(repository.tasks.first().isEmpty())
        assertEquals(0, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun rethrowsCancellationAndReturnsToIdleWithoutDroppingWork() = runTest {
        val remote = object : FakeTaskRemoteDataSource() {
            override suspend fun create(task: com.example.kmpnativefirst.task.Task) =
                throw CancellationException("cancelled")
        }
        val ids = SequentialIds()
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
            clock = AdvancingClock(),
            idGenerator = ids::next,
        )
        repository.create(TaskDraft(title = "Keep me"))

        assertFailsWith<CancellationException> {
            repository.sync()
        }

        assertEquals(TaskSyncPhase.IDLE, repository.syncStatus.value.phase)
        assertEquals(1, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun serializesConcurrentSynchronizationRequests() = runTest {
        val remote = object : FakeTaskRemoteDataSource() {
            var activeCalls = 0
            var maximumActiveCalls = 0

            override suspend fun list(): List<com.example.kmpnativefirst.task.Task> {
                activeCalls += 1
                maximumActiveCalls = maxOf(maximumActiveCalls, activeCalls)
                delay(100)
                activeCalls -= 1
                return super.list()
            }
        }
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = remote,
        )

        val first = async { repository.sync() }
        val second = async { repository.sync() }
        first.await()
        second.await()

        assertEquals(1, remote.maximumActiveCalls)
        assertEquals(2, remote.listCalls)
        assertFalse(repository.syncStatus.value.phase == TaskSyncPhase.SYNCING)
        assertNull(repository.syncStatus.value.lastError)
    }

    @Test
    fun synchronizesProjectsBeforeTasksThatReferenceThem() = runTest {
        var projectAvailable = false
        val projectRemote = object : FakeTaskProjectRemoteDataSource() {
            override suspend fun createProject(
                project: com.example.kmpnativefirst.task.TaskProject,
            ): com.example.kmpnativefirst.task.TaskProject =
                super.createProject(project).also { projectAvailable = true }
        }
        val taskRemote = object : FakeTaskRemoteDataSource() {
            override suspend fun create(
                task: com.example.kmpnativefirst.task.Task,
            ): com.example.kmpnativefirst.task.Task {
                assertTrue(projectAvailable)
                return super.create(task)
            }
        }
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = taskRemote,
            projectRemote = projectRemote,
            clock = AdvancingClock(),
            idGenerator = SequentialIds(
                ArrayDeque(
                    listOf(
                        PROJECT_ID_1,
                        "10000000-0000-0000-0000-000000000001",
                        TASK_ID_1,
                        "10000000-0000-0000-0000-000000000002",
                    ),
                ),
            )::next,
        )
        val project = repository.createProject(
            TaskProjectDraft("  Work  ", TaskProjectColor.PURPLE),
        )
        repository.create(
            TaskDraft(title = "Ship", projectId = project.id),
        )

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(2, result.pushedCount)
        assertEquals("Work", repository.projects.first().single().project.name)
        assertEquals(TaskSyncState.SYNCED, repository.projects.first().single().syncState)
        assertEquals(project.id, repository.tasks.first().single().task.projectId)
        assertEquals(0, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun deletesAProjectOnlyAfterUnassigningItsTasks() = runTest {
        val project = taskProject()
        val assigned = task(projectId = project.id)
        val taskRemote = FakeTaskRemoteDataSource(listOf(assigned))
        val projectRemote = FakeTaskProjectRemoteDataSource(listOf(project))
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(
                initialTasks = listOf(assigned),
                initialProjects = listOf(project),
            ),
            remote = taskRemote,
            projectRemote = projectRemote,
            clock = AdvancingClock(),
            idGenerator = SequentialIds()::next,
        )

        repository.deleteProject(project.id)

        assertNull(repository.tasks.first().single().task.projectId)
        assertTrue(repository.projects.first().isEmpty())
        assertEquals(2, repository.syncStatus.value.pendingCount)

        val result = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(2, result.pushedCount)
        assertNull(taskRemote.find(assigned.id)?.projectId)
        assertNull(projectRemote.findProject(project.id))
        assertEquals(0, repository.syncStatus.value.pendingCount)
    }

    @Test
    fun recordsAndResolvesConcurrentProjectEdits() = runTest {
        val base = taskProject(name = "Personal")
        val remoteProject = base.copy(name = "Private", revision = 2)
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(initialProjects = listOf(base)),
            remote = FakeTaskRemoteDataSource(),
            projectRemote = FakeTaskProjectRemoteDataSource(listOf(remoteProject)),
            clock = AdvancingClock(),
            idGenerator = { "10000000-0000-0000-0000-000000000001" },
        )
        repository.updateProject(
            base.id,
            base.toEdit().copy(name = "Home"),
        )

        val first = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, first.conflictCount)
        assertEquals(
            setOf(TaskProjectConflictField.NAME),
            repository.projectConflicts.first().single().conflictingFields,
        )
        assertEquals(
            TaskSyncState.CONFLICT,
            repository.projects.first().single().syncState,
        )

        repository.resolveProjectConflict(
            base.id,
            TaskProjectConflictResolution.KeepLocal,
        )
        val second = assertIs<TaskSyncResult.Success>(repository.sync())

        assertEquals(1, second.pushedCount)
        assertEquals("Home", repository.projects.first().single().project.name)
        assertEquals(
            TaskSyncState.SYNCED,
            repository.projects.first().single().syncState,
        )
    }

    @Test
    fun rejectsAssignmentToAProjectThatIsNotCached() = runTest {
        val repository = OfflineFirstTaskRepository(
            local = InMemoryTaskLocalDataSource(),
            remote = FakeTaskRemoteDataSource(),
        )

        assertFailsWith<CachedTaskProjectNotFoundException> {
            repository.create(
                TaskDraft(title = "Orphan", projectId = PROJECT_ID_1),
            )
        }

        assertTrue(repository.tasks.first().isEmpty())
    }

}
