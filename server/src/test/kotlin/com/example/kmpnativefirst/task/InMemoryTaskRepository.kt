package com.example.kmpnativefirst.task

class InMemoryTaskRepository(
    initialTasks: List<Task> = emptyList(),
) : TaskRepository {
    private val tasks = initialTasks.associateByTo(linkedMapOf(), Task::id)

    override suspend fun list(): List<Task> = tasks.values.sortedByDescending(Task::updatedAt)

    override suspend fun find(id: String): Task? = tasks[id]

    override suspend fun insert(task: Task): TaskInsertResult {
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
}
