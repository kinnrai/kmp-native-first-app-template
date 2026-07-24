package com.example.kmpnativefirst.task.data

@Throws(Exception::class)
suspend fun createAppleTaskStore(
    baseUrl: String,
    databaseName: String = "tasks.db",
): AppleTaskStore = AppleTaskStore(
    createTaskRepository(
        baseUrl = baseUrl,
        databaseName = databaseName,
    ),
)
