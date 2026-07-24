package com.example.kmpnativefirst.task

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class SqliteTaskRepository private constructor(
    private val database: Database,
) : TaskRepository, TaskProjectRepository, TaskLabelRepository {
    override suspend fun list(): List<Task> = suspendTransaction(db = database) {
        val rows = TasksTable
            .selectAll()
            .orderBy(TasksTable.updatedAtEpochMillis to SortOrder.DESC)
            .toList()
        val labelIdsByTask = labelIdsByTask(
            rows.map { row -> row[TasksTable.id] },
        )
        rows.map { row ->
            toTask(row, labelIdsByTask[row[TasksTable.id]].orEmpty())
        }
    }

    override suspend fun find(id: String): Task? = suspendTransaction(db = database) {
        val row = TasksTable
            .selectAll()
            .where { TasksTable.id eq id }
            .limit(1)
            .singleOrNull()
            ?: return@suspendTransaction null
        toTask(row, labelIdsForTask(id))
    }

    override suspend fun insert(task: Task): TaskInsertResult = suspendTransaction(db = database) {
        if (!projectExists(task.projectId)) {
            return@suspendTransaction TaskInsertResult.InvalidProject
        }
        if (!labelsExist(task.labelIds)) {
            return@suspendTransaction TaskInsertResult.InvalidLabels
        }
        val insert = TasksTable.insertIgnore { statement ->
            statement[id] = task.id
            statement[title] = task.title
            statement[notes] = task.notes
            statement[projectId] = task.projectId
            statement[priority] = task.priority.name
            statement[dueDate] = task.dueDate?.toString()
            statement[dueAtEpochMillis] = task.dueAt?.toEpochMilliseconds()
            statement[isCompleted] = task.isCompleted
            statement[createdAtEpochMillis] = task.createdAt.toEpochMilliseconds()
            statement[updatedAtEpochMillis] = task.updatedAt.toEpochMilliseconds()
            statement[revision] = task.revision
        }
        if (insert.insertedCount == 1) {
            replaceTaskLabels(task.id, task.labelIds)
            TaskInsertResult.Inserted(task)
        } else {
            TaskInsertResult.AlreadyExists
        }
    }

    override suspend fun replace(
        task: Task,
        expectedRevision: Long,
    ): TaskMutationResult = suspendTransaction(db = database) {
        if (!projectExists(task.projectId)) {
            return@suspendTransaction TaskMutationResult.InvalidProject
        }
        if (!labelsExist(task.labelIds)) {
            return@suspendTransaction TaskMutationResult.InvalidLabels
        }
        val updatedRows = TasksTable.update(
            where = {
                (TasksTable.id eq task.id) and
                    (TasksTable.revision eq expectedRevision)
            },
        ) { statement ->
            statement[title] = task.title
            statement[notes] = task.notes
            statement[projectId] = task.projectId
            statement[priority] = task.priority.name
            statement[dueDate] = task.dueDate?.toString()
            statement[dueAtEpochMillis] = task.dueAt?.toEpochMilliseconds()
            statement[isCompleted] = task.isCompleted
            statement[updatedAtEpochMillis] = task.updatedAt.toEpochMilliseconds()
            statement[revision] = task.revision
        }

        when {
            updatedRows == 1 -> {
                replaceTaskLabels(task.id, task.labelIds)
                TaskMutationResult.Updated(task)
            }
            TasksTable.selectAll().where { TasksTable.id eq task.id }.empty() ->
                TaskMutationResult.NotFound
            else -> TaskMutationResult.Conflict
        }
    }

    override suspend fun delete(
        id: String,
        expectedRevision: Long,
    ): TaskDeleteResult = suspendTransaction(db = database) {
        val deletedRows = TasksTable.deleteWhere {
            (TasksTable.id eq id) and
                (TasksTable.revision eq expectedRevision)
        }
        when {
            deletedRows == 1 -> {
                TaskLabelsTable.deleteWhere { taskId eq id }
                TaskDeleteResult.Deleted
            }
            TasksTable.selectAll().where { TasksTable.id eq id }.empty() ->
                TaskDeleteResult.NotFound
            else -> TaskDeleteResult.Conflict
        }
    }

    override suspend fun deleteCompleted(): Int = suspendTransaction(db = database) {
        val completedTaskIds = TasksTable
            .selectAll()
            .where { TasksTable.isCompleted eq true }
            .map { row -> row[TasksTable.id] }
        if (completedTaskIds.isNotEmpty()) {
            TaskLabelsTable.deleteWhere {
                taskId inList completedTaskIds
            }
        }
        TasksTable.deleteWhere { TasksTable.isCompleted eq true }
    }

    override suspend fun listProjects(): List<TaskProject> =
        suspendTransaction(db = database) {
            TaskProjectsTable
                .selectAll()
                .orderBy(TaskProjectsTable.name to SortOrder.ASC)
                .map(::toTaskProject)
        }

    override suspend fun findProject(id: String): TaskProject? =
        suspendTransaction(db = database) {
            TaskProjectsTable
                .selectAll()
                .where { TaskProjectsTable.id eq id }
                .limit(1)
                .singleOrNull()
                ?.let(::toTaskProject)
        }

    override suspend fun insertProject(
        project: TaskProject,
    ): TaskProjectInsertResult = suspendTransaction(db = database) {
        val insert = TaskProjectsTable.insertIgnore { statement ->
            statement[id] = project.id
            statement[name] = project.name
            statement[color] = project.color.name
            statement[createdAtEpochMillis] = project.createdAt.toEpochMilliseconds()
            statement[updatedAtEpochMillis] = project.updatedAt.toEpochMilliseconds()
            statement[revision] = project.revision
        }
        if (insert.insertedCount == 1) {
            TaskProjectInsertResult.Inserted(project)
        } else {
            TaskProjectInsertResult.AlreadyExists
        }
    }

    override suspend fun replaceProject(
        project: TaskProject,
        expectedRevision: Long,
    ): TaskProjectMutationResult = suspendTransaction(db = database) {
        val updatedRows = TaskProjectsTable.update(
            where = {
                (TaskProjectsTable.id eq project.id) and
                    (TaskProjectsTable.revision eq expectedRevision)
            },
        ) { statement ->
            statement[name] = project.name
            statement[color] = project.color.name
            statement[updatedAtEpochMillis] = project.updatedAt.toEpochMilliseconds()
            statement[revision] = project.revision
        }
        when {
            updatedRows == 1 -> TaskProjectMutationResult.Updated(project)
            TaskProjectsTable
                .selectAll()
                .where { TaskProjectsTable.id eq project.id }
                .empty() -> TaskProjectMutationResult.NotFound
            else -> TaskProjectMutationResult.Conflict
        }
    }

    override suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
        reassignedTasksUpdatedAt: Instant,
    ): TaskProjectDeleteResult = suspendTransaction(db = database) {
        val deletedRows = TaskProjectsTable.deleteWhere {
            (TaskProjectsTable.id eq id) and
                (TaskProjectsTable.revision eq expectedRevision)
        }
        when {
            deletedRows == 1 -> {
                val reassignedTaskCount = TasksTable.update(
                    where = { TasksTable.projectId eq id },
                ) { statement ->
                    statement[projectId] = null
                    statement[updatedAtEpochMillis] =
                        reassignedTasksUpdatedAt.toEpochMilliseconds()
                    statement[revision] = TasksTable.revision + 1
                }
                TaskProjectDeleteResult.Deleted(reassignedTaskCount)
            }
            TaskProjectsTable.selectAll().where { TaskProjectsTable.id eq id }.empty() ->
                TaskProjectDeleteResult.NotFound
            else -> TaskProjectDeleteResult.Conflict
        }
    }

    override suspend fun listLabels(): List<TaskLabel> =
        suspendTransaction(db = database) {
            TaskLabelsDefinitionTable
                .selectAll()
                .orderBy(TaskLabelsDefinitionTable.name to SortOrder.ASC)
                .map(::toTaskLabel)
        }

    override suspend fun findLabel(id: String): TaskLabel? =
        suspendTransaction(db = database) {
            TaskLabelsDefinitionTable
                .selectAll()
                .where { TaskLabelsDefinitionTable.id eq id }
                .limit(1)
                .singleOrNull()
                ?.let(::toTaskLabel)
        }

    override suspend fun insertLabel(
        label: TaskLabel,
    ): TaskLabelInsertResult = suspendTransaction(db = database) {
        val insert = TaskLabelsDefinitionTable.insertIgnore { statement ->
            statement[id] = label.id
            statement[name] = label.name
            statement[color] = label.color.name
            statement[createdAtEpochMillis] = label.createdAt.toEpochMilliseconds()
            statement[updatedAtEpochMillis] = label.updatedAt.toEpochMilliseconds()
            statement[revision] = label.revision
        }
        if (insert.insertedCount == 1) {
            TaskLabelInsertResult.Inserted(label)
        } else {
            TaskLabelInsertResult.AlreadyExists
        }
    }

    override suspend fun replaceLabel(
        label: TaskLabel,
        expectedRevision: Long,
    ): TaskLabelMutationResult = suspendTransaction(db = database) {
        val updatedRows = TaskLabelsDefinitionTable.update(
            where = {
                (TaskLabelsDefinitionTable.id eq label.id) and
                    (TaskLabelsDefinitionTable.revision eq expectedRevision)
            },
        ) { statement ->
            statement[name] = label.name
            statement[color] = label.color.name
            statement[updatedAtEpochMillis] = label.updatedAt.toEpochMilliseconds()
            statement[revision] = label.revision
        }
        when {
            updatedRows == 1 -> TaskLabelMutationResult.Updated(label)
            TaskLabelsDefinitionTable
                .selectAll()
                .where { TaskLabelsDefinitionTable.id eq label.id }
                .empty() -> TaskLabelMutationResult.NotFound
            else -> TaskLabelMutationResult.Conflict
        }
    }

    override suspend fun deleteLabel(
        id: String,
        expectedRevision: Long,
        affectedTasksUpdatedAt: Instant,
    ): TaskLabelDeleteResult = suspendTransaction(db = database) {
        val affectedTaskIds = TaskLabelsTable
            .selectAll()
            .where { TaskLabelsTable.labelId eq id }
            .map { row -> row[TaskLabelsTable.taskId] }
        val deletedRows = TaskLabelsDefinitionTable.deleteWhere {
            (TaskLabelsDefinitionTable.id eq id) and
                (TaskLabelsDefinitionTable.revision eq expectedRevision)
        }
        when {
            deletedRows == 1 -> {
                TaskLabelsTable.deleteWhere { labelId eq id }
                if (affectedTaskIds.isNotEmpty()) {
                    TasksTable.update(
                        where = { TasksTable.id inList affectedTaskIds },
                    ) { statement ->
                        statement[updatedAtEpochMillis] =
                            affectedTasksUpdatedAt.toEpochMilliseconds()
                        statement[revision] = TasksTable.revision + 1
                    }
                }
                TaskLabelDeleteResult.Deleted(affectedTaskIds.size)
            }
            TaskLabelsDefinitionTable
                .selectAll()
                .where { TaskLabelsDefinitionTable.id eq id }
                .empty() -> TaskLabelDeleteResult.NotFound
            else -> TaskLabelDeleteResult.Conflict
        }
    }

    companion object {
        private const val SQLITE_PREFIX = "jdbc:sqlite:"

        fun open(jdbcUrl: String): SqliteTaskRepository {
            require(jdbcUrl.startsWith(SQLITE_PREFIX)) {
                "Task storage requires a SQLite JDBC URL."
            }
            ensureParentDirectory(jdbcUrl)
            val database = Database.connect(
                url = jdbcUrl,
                driver = "org.sqlite.JDBC",
            )
            transaction(database) {
                SchemaUtils.create(
                    TaskProjectsTable,
                    TaskLabelsDefinitionTable,
                    TasksTable,
                    TaskLabelsTable,
                )
                val taskColumns = exec("PRAGMA table_info(tasks)") { result ->
                    buildSet {
                        while (result.next()) {
                            add(result.getString("name"))
                        }
                    }
                }.orEmpty()
                if ("due_date" !in taskColumns) {
                    exec("ALTER TABLE tasks ADD COLUMN due_date VARCHAR(10)")
                }
                if ("project_id" !in taskColumns) {
                    exec("ALTER TABLE tasks ADD COLUMN project_id VARCHAR(36)")
                }
            }
            return SqliteTaskRepository(database)
        }

        private fun ensureParentDirectory(jdbcUrl: String) {
            val databasePath = jdbcUrl
                .removePrefix(SQLITE_PREFIX)
                .substringBefore('?')
            if (
                databasePath.isBlank() ||
                databasePath == ":memory:" ||
                databasePath.startsWith("file:")
            ) {
                return
            }
            Path.of(databasePath).toAbsolutePath().parent?.let(Files::createDirectories)
        }
    }
}

private object TaskProjectsTable : Table("task_projects") {
    val id = varchar("id", length = 36)
    val name = varchar("name", length = TaskProjectConstraints.MAX_NAME_LENGTH)
    val color = varchar("color", length = 16)
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")
    val revision = long("revision")

    override val primaryKey = PrimaryKey(id)
}

private object TaskLabelsDefinitionTable : Table("task_labels") {
    val id = varchar("id", length = 36)
    val name = varchar("name", length = TaskLabelConstraints.MAX_NAME_LENGTH)
    val color = varchar("color", length = 16)
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")
    val revision = long("revision")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, name)
    }
}

private object TasksTable : Table("tasks") {
    val id = varchar("id", length = 36)
    val title = varchar("title", length = TaskConstraints.MAX_TITLE_LENGTH)
    val notes = text("notes").nullable()
    val projectId = varchar("project_id", length = 36).nullable()
    val priority = varchar("priority", length = 16)
    val dueDate = varchar("due_date", length = 10).nullable()
    val dueAtEpochMillis = long("due_at_epoch_millis").nullable()
    val isCompleted = bool("is_completed")
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")
    val revision = long("revision")

    override val primaryKey = PrimaryKey(id)
}

private object TaskLabelsTable : Table("task_label_assignments") {
    val taskId = varchar("task_id", length = 36)
    val labelId = varchar("label_id", length = 36)

    override val primaryKey = PrimaryKey(taskId, labelId)

    init {
        index(isUnique = false, labelId)
    }
}

private fun projectExists(projectId: String?): Boolean =
    projectId == null ||
        TaskProjectsTable
            .selectAll()
            .where { TaskProjectsTable.id eq projectId }
            .limit(1)
            .any()

private fun labelsExist(labelIds: List<String>): Boolean {
    if (labelIds.isEmpty()) {
        return true
    }
    val existingIds = TaskLabelsDefinitionTable
        .selectAll()
        .where { TaskLabelsDefinitionTable.id inList labelIds }
        .mapTo(mutableSetOf()) { row -> row[TaskLabelsDefinitionTable.id] }
    return existingIds.size == labelIds.toSet().size
}

private fun replaceTaskLabels(
    taskId: String,
    labelIds: List<String>,
) {
    TaskLabelsTable.deleteWhere { TaskLabelsTable.taskId eq taskId }
    labelIds.forEach { labelId ->
        TaskLabelsTable.insertIgnore { statement ->
            statement[TaskLabelsTable.taskId] = taskId
            statement[TaskLabelsTable.labelId] = labelId
        }
    }
}

private fun labelIdsForTask(taskId: String): List<String> =
    TaskLabelsTable
        .selectAll()
        .where { TaskLabelsTable.taskId eq taskId }
        .map { row -> row[TaskLabelsTable.labelId] }
        .sorted()

private fun labelIdsByTask(taskIds: List<String>): Map<String, List<String>> {
    if (taskIds.isEmpty()) {
        return emptyMap()
    }
    return TaskLabelsTable
        .selectAll()
        .where { TaskLabelsTable.taskId inList taskIds }
        .groupBy(
            keySelector = { row -> row[TaskLabelsTable.taskId] },
            valueTransform = { row -> row[TaskLabelsTable.labelId] },
        )
        .mapValues { (_, labelIds) -> labelIds.sorted() }
}

private fun toTask(
    row: ResultRow,
    labelIds: List<String>,
): Task = Task(
    id = row[TasksTable.id],
    title = row[TasksTable.title],
    notes = row[TasksTable.notes],
    projectId = row[TasksTable.projectId],
    labelIds = labelIds,
    priority = TaskPriority.valueOf(row[TasksTable.priority]),
    dueDate = row[TasksTable.dueDate]?.let(LocalDate::parse),
    dueAt = row[TasksTable.dueAtEpochMillis]?.let(Instant::fromEpochMilliseconds),
    isCompleted = row[TasksTable.isCompleted],
    createdAt = Instant.fromEpochMilliseconds(row[TasksTable.createdAtEpochMillis]),
    updatedAt = Instant.fromEpochMilliseconds(row[TasksTable.updatedAtEpochMillis]),
    revision = row[TasksTable.revision],
)

private fun toTaskProject(row: ResultRow): TaskProject = TaskProject(
    id = row[TaskProjectsTable.id],
    name = row[TaskProjectsTable.name],
    color = TaskProjectColor.valueOf(row[TaskProjectsTable.color]),
    createdAt = Instant.fromEpochMilliseconds(row[TaskProjectsTable.createdAtEpochMillis]),
    updatedAt = Instant.fromEpochMilliseconds(row[TaskProjectsTable.updatedAtEpochMillis]),
    revision = row[TaskProjectsTable.revision],
)

private fun toTaskLabel(row: ResultRow): TaskLabel = TaskLabel(
    id = row[TaskLabelsDefinitionTable.id],
    name = row[TaskLabelsDefinitionTable.name],
    color = TaskLabelColor.valueOf(row[TaskLabelsDefinitionTable.color]),
    createdAt = Instant.fromEpochMilliseconds(
        row[TaskLabelsDefinitionTable.createdAtEpochMillis],
    ),
    updatedAt = Instant.fromEpochMilliseconds(
        row[TaskLabelsDefinitionTable.updatedAtEpochMillis],
    ),
    revision = row[TaskLabelsDefinitionTable.revision],
)
