package com.example.kmpnativefirst.task

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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
) : TaskRepository, TaskProjectRepository {
    override suspend fun list(): List<Task> = suspendTransaction(db = database) {
        TasksTable
            .selectAll()
            .orderBy(TasksTable.updatedAtEpochMillis to SortOrder.DESC)
            .map(::toTask)
    }

    override suspend fun find(id: String): Task? = suspendTransaction(db = database) {
        TasksTable
            .selectAll()
            .where { TasksTable.id eq id }
            .limit(1)
            .singleOrNull()
            ?.let(::toTask)
    }

    override suspend fun insert(task: Task): TaskInsertResult = suspendTransaction(db = database) {
        val insert = TasksTable.insertIgnore { statement ->
            statement[id] = task.id
            statement[title] = task.title
            statement[notes] = task.notes
            statement[priority] = task.priority.name
            statement[dueDate] = task.dueDate?.toString()
            statement[dueAtEpochMillis] = task.dueAt?.toEpochMilliseconds()
            statement[isCompleted] = task.isCompleted
            statement[createdAtEpochMillis] = task.createdAt.toEpochMilliseconds()
            statement[updatedAtEpochMillis] = task.updatedAt.toEpochMilliseconds()
            statement[revision] = task.revision
        }
        if (insert.insertedCount == 1) {
            TaskInsertResult.Inserted(task)
        } else {
            TaskInsertResult.AlreadyExists
        }
    }

    override suspend fun replace(
        task: Task,
        expectedRevision: Long,
    ): TaskMutationResult = suspendTransaction(db = database) {
        val updatedRows = TasksTable.update(
            where = {
                (TasksTable.id eq task.id) and
                    (TasksTable.revision eq expectedRevision)
            },
        ) { statement ->
            statement[title] = task.title
            statement[notes] = task.notes
            statement[priority] = task.priority.name
            statement[dueDate] = task.dueDate?.toString()
            statement[dueAtEpochMillis] = task.dueAt?.toEpochMilliseconds()
            statement[isCompleted] = task.isCompleted
            statement[updatedAtEpochMillis] = task.updatedAt.toEpochMilliseconds()
            statement[revision] = task.revision
        }

        when {
            updatedRows == 1 -> TaskMutationResult.Updated(task)
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
            deletedRows == 1 -> TaskDeleteResult.Deleted
            TasksTable.selectAll().where { TasksTable.id eq id }.empty() ->
                TaskDeleteResult.NotFound
            else -> TaskDeleteResult.Conflict
        }
    }

    override suspend fun deleteCompleted(): Int = suspendTransaction(db = database) {
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
    ): TaskProjectDeleteResult = suspendTransaction(db = database) {
        val deletedRows = TaskProjectsTable.deleteWhere {
            (TaskProjectsTable.id eq id) and
                (TaskProjectsTable.revision eq expectedRevision)
        }
        when {
            deletedRows == 1 -> TaskProjectDeleteResult.Deleted
            TaskProjectsTable.selectAll().where { TaskProjectsTable.id eq id }.empty() ->
                TaskProjectDeleteResult.NotFound
            else -> TaskProjectDeleteResult.Conflict
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
                SchemaUtils.create(TaskProjectsTable, TasksTable)
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

private object TasksTable : Table("tasks") {
    val id = varchar("id", length = 36)
    val title = varchar("title", length = TaskConstraints.MAX_TITLE_LENGTH)
    val notes = text("notes").nullable()
    val priority = varchar("priority", length = 16)
    val dueDate = varchar("due_date", length = 10).nullable()
    val dueAtEpochMillis = long("due_at_epoch_millis").nullable()
    val isCompleted = bool("is_completed")
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")
    val revision = long("revision")

    override val primaryKey = PrimaryKey(id)
}

private fun toTask(row: ResultRow): Task = Task(
    id = row[TasksTable.id],
    title = row[TasksTable.title],
    notes = row[TasksTable.notes],
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
