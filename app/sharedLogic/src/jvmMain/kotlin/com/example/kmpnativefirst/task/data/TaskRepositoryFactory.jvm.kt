package com.example.kmpnativefirst.task.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.kmpnativefirst.task.data.local.db.TaskDatabase
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

@Throws(Exception::class)
suspend fun createTaskRepository(
    databasePath: String,
    baseUrl: String,
): TaskRepository = withContext(taskDatabaseDispatcher()) {
    val databaseFile = File(databasePath).absoluteFile
    val parent = databaseFile.parentFile
    check(parent == null || parent.isDirectory || parent.mkdirs()) {
        "The task database directory could not be created."
    }
    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${databaseFile.path}",
        properties = Properties(),
        schema = TaskDatabase.Schema,
    )
    createPersistentTaskRepository(driver, baseUrl)
}
