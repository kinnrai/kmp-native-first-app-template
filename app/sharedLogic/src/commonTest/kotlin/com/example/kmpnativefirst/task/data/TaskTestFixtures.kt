package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal const val TASK_ID_1 = "00000000-0000-0000-0000-000000000001"
internal const val TASK_ID_2 = "00000000-0000-0000-0000-000000000002"
internal const val TASK_ID_3 = "00000000-0000-0000-0000-000000000003"
internal const val PROJECT_ID_1 = "22222222-2222-4222-8222-222222222222"
internal const val PROJECT_ID_2 = "33333333-3333-4333-8333-333333333333"

internal val TEST_INSTANT = Instant.parse("2026-07-23T00:00:00Z")

internal fun task(
    id: String = TASK_ID_1,
    title: String = "Write tests",
    notes: String? = null,
    projectId: String? = null,
    priority: TaskPriority = TaskPriority.NONE,
    dueDate: LocalDate? = null,
    dueAt: Instant? = null,
    isCompleted: Boolean = false,
    createdAt: Instant = TEST_INSTANT,
    updatedAt: Instant = TEST_INSTANT,
    revision: Long = 1,
): Task = Task(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    priority = priority,
    dueDate = dueDate,
    dueAt = dueAt,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
)

internal fun taskProject(
    id: String = PROJECT_ID_1,
    name: String = "Personal",
    color: TaskProjectColor = TaskProjectColor.BLUE,
    createdAt: Instant = TEST_INSTANT,
    updatedAt: Instant = TEST_INSTANT,
    revision: Long = 1,
): TaskProject = TaskProject(
    id = id,
    name = name,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
)

internal class AdvancingClock(
    start: Instant = TEST_INSTANT,
) : Clock {
    private var current = start

    override fun now(): Instant = current.also {
        current += 1.seconds
    }
}

internal class SequentialIds(
    private val values: ArrayDeque<String> = ArrayDeque(
        listOf(
            TASK_ID_1,
            "10000000-0000-0000-0000-000000000001",
            TASK_ID_2,
            "10000000-0000-0000-0000-000000000002",
            TASK_ID_3,
            "10000000-0000-0000-0000-000000000003",
        ),
    ),
) {
    fun next(): String = values.removeFirst()
}

internal open class FakeTaskRemoteDataSource(
    initialTasks: List<Task> = emptyList(),
) : TaskRemoteDataSource {
    protected val records = initialTasks.associateBy(Task::id).toMutableMap()
    var listCalls = 0
    var createCalls = 0
    var replaceCalls = 0
    var deleteCalls = 0

    override suspend fun list(): List<Task> {
        listCalls += 1
        return records.values.sortedBy(Task::id)
    }

    override suspend fun find(id: String): Task? = records[id]

    override suspend fun create(task: Task): Task {
        createCalls += 1
        if (task.id in records) {
            throw RemoteTaskConflictException(task.id)
        }
        return task.copy(revision = 1).also { records[it.id] = it }
    }

    override suspend fun replace(task: Task): Task {
        replaceCalls += 1
        val current = records[task.id] ?: throw RemoteTaskNotFoundException(task.id)
        if (task.revision != current.revision) {
            throw RemoteTaskConflictException(task.id)
        }
        return task.copy(
            createdAt = current.createdAt,
            revision = current.revision + 1,
        ).also { records[it.id] = it }
    }

    override suspend fun delete(
        id: String,
        expectedRevision: Long,
    ) {
        deleteCalls += 1
        val current = records[id] ?: throw RemoteTaskNotFoundException(id)
        if (current.revision != expectedRevision) {
            throw RemoteTaskConflictException(id)
        }
        records.remove(id)
    }

    override fun close() = Unit
}

internal open class FakeTaskProjectRemoteDataSource(
    initialProjects: List<TaskProject> = emptyList(),
) : TaskProjectRemoteDataSource {
    protected val records = initialProjects.associateBy(TaskProject::id).toMutableMap()
    var listCalls = 0
    var createCalls = 0
    var replaceCalls = 0
    var deleteCalls = 0

    override suspend fun listProjects(): List<TaskProject> {
        listCalls += 1
        return records.values.sortedBy(TaskProject::id)
    }

    override suspend fun findProject(id: String): TaskProject? = records[id]

    override suspend fun createProject(project: TaskProject): TaskProject {
        createCalls += 1
        if (project.id in records) {
            throw RemoteTaskProjectConflictException(project.id)
        }
        return project.copy(revision = 1).also { records[it.id] = it }
    }

    override suspend fun replaceProject(project: TaskProject): TaskProject {
        replaceCalls += 1
        val current = records[project.id]
            ?: throw RemoteTaskProjectNotFoundException(project.id)
        if (project.revision != current.revision) {
            throw RemoteTaskProjectConflictException(project.id)
        }
        return project.copy(
            createdAt = current.createdAt,
            revision = current.revision + 1,
        ).also { records[it.id] = it }
    }

    override suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
    ) {
        deleteCalls += 1
        val current = records[id] ?: throw RemoteTaskProjectNotFoundException(id)
        if (current.revision != expectedRevision) {
            throw RemoteTaskProjectConflictException(id)
        }
        records.remove(id)
    }

    override fun close() = Unit
}
