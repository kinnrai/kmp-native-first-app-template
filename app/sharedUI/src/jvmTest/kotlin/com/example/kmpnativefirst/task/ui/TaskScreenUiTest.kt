package com.example.kmpnativefirst.task.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskProjectItem
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

    @Test
    fun createsAndSelectsAProject() = runComposeUiTest {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                activeCount = TASKS.size,
            ),
        )
        var savedProjectName: String? = null

        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { state = it },
                onSaveProject = {
                    savedProjectName = state.projectEditor?.name
                    val project = PROJECT.copy(name = requireNotNull(savedProjectName))
                    state = state.copy(
                        projects = listOf(
                            TaskProjectItem(project, TaskSyncState.PENDING),
                        ),
                        selectedProjectId = project.id,
                        projectEditor = null,
                    )
                },
            )
        }

        onNodeWithTag(TaskUiTags.NEW_PROJECT).performClick()
        onNodeWithTag(TaskUiTags.PROJECT_EDITOR).assertExists()
        onNodeWithTag(TaskUiTags.PROJECT_EDITOR_NAME).performTextInput("Product")
        onNodeWithTag(TaskUiTags.PROJECT_EDITOR_SAVE).performClick()

        runOnIdle {
            assertEquals("Product", savedProjectName)
            assertEquals(PROJECT.id, state.selectedProjectId)
        }
        onNodeWithTag(TaskUiTags.project(PROJECT.id)).assertExists()
    }

    @Test
    fun opensProjectNavigationInACompactWindow() = runComposeUiTest {
        val state = TaskUiState(
            isInitializing = false,
            projects = listOf(
                TaskProjectItem(PROJECT, TaskSyncState.SYNCED),
            ),
        )

        setContent {
            Box(Modifier.size(width = 700.dp, height = 600.dp)) {
                TestTaskScreen(
                    state = state,
                    onStateChange = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithText("Browse")
            .assertIsDisplayed()
            .performClick()
        onNodeWithTag(TaskUiTags.NEW_PROJECT).assertIsDisplayed()
        onNodeWithTag(TaskUiTags.project(PROJECT.id)).assertIsDisplayed()
    }
}

@Composable
private fun TestTaskScreen(
    state: TaskUiState,
    onStateChange: (TaskUiState) -> Unit,
    onSave: () -> Unit = {},
    onSaveProject: () -> Unit = {},
    modifier: Modifier = Modifier,
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
                changeProject = {
                    onStateChange(state.copy(selectedProjectId = it))
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
                createProject = {
                    onStateChange(
                        state.copy(projectEditor = TaskProjectEditorUiState()),
                    )
                },
                dismissProjectEditor = {
                    onStateChange(state.copy(projectEditor = null))
                },
                changeProjectName = {
                    onStateChange(
                        state.copy(
                            projectEditor = state.projectEditor?.copy(name = it),
                        ),
                    )
                },
                saveProject = onSaveProject,
            ),
            modifier = modifier,
        )
    }
}

private val TEST_NOW = Instant.parse("2026-07-23T08:00:00Z")

private val PROJECT = TaskProject(
    id = "product",
    name = "Product",
    color = TaskProjectColor.PURPLE,
    createdAt = TEST_NOW,
    updatedAt = TEST_NOW,
    revision = 1,
)

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
