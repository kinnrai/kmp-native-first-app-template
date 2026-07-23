package com.example.kmpnativefirst.task.data

import kotlinx.coroutines.CoroutineDispatcher

internal expect fun taskDatabaseDispatcher(): CoroutineDispatcher
