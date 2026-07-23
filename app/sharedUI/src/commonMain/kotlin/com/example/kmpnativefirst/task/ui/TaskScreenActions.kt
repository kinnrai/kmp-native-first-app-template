package com.example.kmpnativefirst.task.ui

import androidx.compose.runtime.Stable
import com.example.kmpnativefirst.task.TaskFilter
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import kotlin.time.Instant

@Stable
internal class TaskScreenActions(
    val retryInitialization: () -> Unit = {},
    val changeSearchQuery: (String) -> Unit = {},
    val changeFilter: (TaskFilter) -> Unit = {},
    val createTask: () -> Unit = {},
    val editTask: (String) -> Unit = {},
    val toggleCompleted: (String) -> Unit = {},
    val requestDelete: (String) -> Unit = {},
    val cancelDelete: () -> Unit = {},
    val confirmDelete: () -> Unit = {},
    val requestClearCompleted: () -> Unit = {},
    val cancelClearCompleted: () -> Unit = {},
    val confirmClearCompleted: () -> Unit = {},
    val synchronize: () -> Unit = {},
    val showConflict: (String) -> Unit = {},
    val dismissConflict: () -> Unit = {},
    val resolveConflict: (TaskConflictResolution) -> Unit = {},
    val dismissEditor: () -> Unit = {},
    val changeEditorTitle: (String) -> Unit = {},
    val changeEditorNotes: (String) -> Unit = {},
    val changeEditorPriority: (TaskPriority) -> Unit = {},
    val changeEditorDueAt: (Instant?) -> Unit = {},
    val changeEditorCompleted: (Boolean) -> Unit = {},
    val saveEditor: () -> Unit = {},
)
