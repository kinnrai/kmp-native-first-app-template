package com.example.kmpnativefirst.task

import kotlin.time.Instant

class InMemoryTaskRepository(
    initialTasks: List<Task> = emptyList(),
    initialProjects: List<TaskProject> = emptyList(),
) : TaskRepository, TaskProjectRepository {
    private val tasks = initialTasks.associateByTo(linkedMapOf(), Task::id)
    private val projects = initialProjects.associateByTo(linkedMapOf(), TaskProject::id)

    override suspend fun list(): List<Task> = tasks.values.sortedByDescending(Task::updatedAt)

    override suspend fun find(id: String): Task? = tasks[id]

    override suspend fun insert(task: Task): TaskInsertResult {
        if (task.projectId != null && task.projectId !in projects) {
            return TaskInsertResult.InvalidProject
        }
        if (task.id in tasks) {
            return TaskInsertResult.AlreadyExists
        }
        tasks[task.id] = task
        return TaskInsertResult.Inserted(task)
    }

    override suspend fun replace(
        task: Task,
        expectedRevision: Long,
    ): TaskMutationResult {
        if (task.projectId != null && task.projectId !in projects) {
            return TaskMutationResult.InvalidProject
        }
        val current = tasks[task.id] ?: return TaskMutationResult.NotFound
        if (current.revision != expectedRevision) {
            return TaskMutationResult.Conflict
        }
        tasks[task.id] = task
        return TaskMutationResult.Updated(task)
    }

    override suspend fun delete(
        id: String,
        expectedRevision: Long,
    ): TaskDeleteResult {
        val task = tasks[id] ?: return TaskDeleteResult.NotFound
        if (task.revision != expectedRevision) {
            return TaskDeleteResult.Conflict
        }
        tasks.remove(id)
        return TaskDeleteResult.Deleted
    }

    override suspend fun deleteCompleted(): Int {
        val completedIds = tasks.values.filter(Task::isCompleted).map(Task::id)
        completedIds.forEach(tasks::remove)
        return completedIds.size
    }

    override suspend fun listProjects(): List<TaskProject> =
        projects.values.sortedBy(TaskProject::name)

    override suspend fun findProject(id: String): TaskProject? = projects[id]

    override suspend fun insertProject(project: TaskProject): TaskProjectInsertResult {
        if (project.id in projects) {
            return TaskProjectInsertResult.AlreadyExists
        }
        projects[project.id] = project
        return TaskProjectInsertResult.Inserted(project)
    }

    override suspend fun replaceProject(
        project: TaskProject,
        expectedRevision: Long,
    ): TaskProjectMutationResult {
        val current = projects[project.id] ?: return TaskProjectMutationResult.NotFound
        if (current.revision != expectedRevision) {
            return TaskProjectMutationResult.Conflict
        }
        projects[project.id] = project
        return TaskProjectMutationResult.Updated(project)
    }

    override suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
        reassignedTasksUpdatedAt: Instant,
    ): TaskProjectDeleteResult {
        val project = projects[id] ?: return TaskProjectDeleteResult.NotFound
        if (project.revision != expectedRevision) {
            return TaskProjectDeleteResult.Conflict
        }
        projects.remove(id)
        var reassignedTaskCount = 0
        tasks.replaceAll { _, task ->
            if (task.projectId == id) {
                reassignedTaskCount += 1
                task.copy(
                    projectId = null,
                    updatedAt = reassignedTasksUpdatedAt,
                    revision = task.revision + 1,
                )
            } else {
                task
            }
        }
        return TaskProjectDeleteResult.Deleted(reassignedTaskCount)
    }
}
