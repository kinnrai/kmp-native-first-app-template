package com.example.kmpnativefirst.task.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun taskDatabaseDispatcher(): CoroutineDispatcher = Dispatchers.Default
