package com.example.kmpnativefirst.task.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.kmpnativefirst.task.data.local.db.TaskDatabase
import kotlinx.coroutines.withContext

@Throws(Exception::class)
suspend fun createTaskRepository(
    context: Context,
    baseUrl: String,
    databaseName: String = "tasks.db",
): TaskRepository = withContext(taskDatabaseDispatcher()) {
    val driver = AndroidSqliteDriver(
        schema = TaskDatabase.Schema,
        context = context.applicationContext,
        name = databaseName,
    )
    createPersistentTaskRepository(driver, baseUrl)
}
