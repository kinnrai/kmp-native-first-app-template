package com.example.kmpnativefirst.task.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskSyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class TaskScreenUiTest {
    @Test
    fun createsTaskThroughTheEditor() = runComposeUiTest {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                activeCount = TASKS.size,
            ),
        )
        var savedTitle: String? = null

        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { state = it },
                onSave = {
                    savedTitle = state.editor?.title
                    state = state.copy(editor = null)
                },
            )
        }

        onNodeWithTag(TaskUiTags.task("one")).assertExists()
        onNodeWithTag(TaskUiTags.NEW_TASK).performClick()
        onNodeWithTag(TaskUiTags.EDITOR).assertExists()
        onNodeWithTag(TaskUiTags.EDITOR_TITLE).performTextInput("Ship the Compose UI")
        onNodeWithTag(TaskUiTags.EDITOR_SAVE).performScrollTo().performClick()

        runOnIdle {
            assertEquals("Ship the Compose UI", savedTitle)
        }
        onNodeWithTag(TaskUiTags.EDITOR).assertDoesNotExist()
    }

    @Test
    fun searchNarrowsTheVisibleTaskList() = runComposeUiTest {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                activeCount = TASKS.size,
            ),
        )

        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { updated ->
                    state = updated.copy(
                        tasks = TASKS.filter {
                            it.task.title.contains(
                                updated.searchQuery,
                                ignoreCase = true,
                            )
                        },
                    )
                },
            )
        }

        onNodeWithTag(TaskUiTags.SEARCH).performTextInput("release")

        onNodeWithTag(TaskUiTags.task("one")).assertDoesNotExist()
        onNodeWithTag(TaskUiTags.task("two")).assertExists()
    }
}

@Composable
private fun TestTaskScreen(
    state: TaskUiState,
    onStateChange: (TaskUiState) -> Unit,
    onSave: () -> Unit = {},
) {
    MaterialTheme {
        TaskScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            actions = TaskScreenActions(
                changeSearchQuery = {
                    onStateChange(state.copy(searchQuery = it))
                },
                changeView = {
                    onStateChange(state.copy(view = it))
                },
                createTask = {
                    onStateChange(state.copy(editor = TaskEditorUiState()))
                },
                dismissEditor = {
                    onStateChange(state.copy(editor = null))
                },
                changeEditorTitle = {
                    onStateChange(state.copy(editor = state.editor?.copy(title = it)))
                },
                saveEditor = onSave,
            ),
        )
    }
}

private val TEST_NOW = Instant.parse("2026-07-23T08:00:00Z")

private val TASKS = listOf(
    TaskItem(
        task = Task(
            id = "one",
            title = "Draft the architecture",
            priority = TaskPriority.HIGH,
            createdAt = TEST_NOW,
            updatedAt = TEST_NOW,
            revision = 1,
        ),
        syncState = TaskSyncState.SYNCED,
    ),
    TaskItem(
        task = Task(
            id = "two",
            title = "Prepare release notes",
            createdAt = TEST_NOW,
            updatedAt = TEST_NOW,
            revision = 1,
        ),
        syncState = TaskSyncState.PENDING,
    ),
)
