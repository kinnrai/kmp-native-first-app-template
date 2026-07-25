package com.example.kmpnativefirst.task.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.kmpnativefirst.task.data.local.db.TaskDatabase
import kotlinx.coroutines.withContext

@Throws(Exception::class)
suspend fun createTaskRepository(
    baseUrl: String,
    databaseName: String = "tasks.db",
): TaskRepository = withContext(taskDatabaseDispatcher()) {
    val driver = NativeSqliteDriver(TaskDatabase.Schema, databaseName)
    createPersistentTaskRepository(driver, baseUrl)
}
