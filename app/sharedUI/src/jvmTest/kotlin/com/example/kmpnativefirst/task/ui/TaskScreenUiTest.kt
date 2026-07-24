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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskLabel
import com.example.kmpnativefirst.task.TaskLabelColor
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskLabelItem
import com.example.kmpnativefirst.task.data.TaskProjectItem
import com.example.kmpnativefirst.task.data.TaskSyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        runOnIdle {
            assertEquals("Ship the Compose UI", state.editor?.title)
        }
        onNodeWithTag(TaskUiTags.EDITOR_SAVE)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()

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

    @Test
    fun filtersAndAssignsTasksWithLabels() = runComposeUiTest {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                labels = LABELS,
                activeCount = TASKS.size,
            ),
        )
        var savedLabelIds: List<String>? = null

        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { state = it },
                onSave = {
                    savedLabelIds = state.editor?.labelIds
                    state = state.copy(editor = null)
                },
            )
        }

        onNodeWithTag(TaskUiTags.labelFilter("work")).performClick()
        onNodeWithTag(TaskUiTags.task("one")).assertExists()
        onNodeWithTag(TaskUiTags.task("two")).assertDoesNotExist()

        onNodeWithTag(TaskUiTags.NEW_TASK).performClick()
        onNodeWithTag(TaskUiTags.editorLabel("work")).performScrollTo().performClick()
        onNodeWithTag(TaskUiTags.EDITOR_TITLE).performTextInput("Review the release")
        runOnIdle {
            assertEquals("Review the release", state.editor?.title)
            assertEquals(listOf("work"), state.editor?.labelIds)
        }
        onNodeWithTag(TaskUiTags.EDITOR_SAVE).performScrollTo().performClick()

        runOnIdle {
            assertEquals(listOf("work"), savedLabelIds)
        }
    }

    @Test
    fun createsLabelThroughTheManager() = runComposeUiTest {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                labels = LABELS,
                activeCount = TASKS.size,
            ),
        )
        var savedName: String? = null

        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { state = it },
                onLabelSave = {
                    savedName = state.labelEditor?.name
                    state = state.copy(labelEditor = null)
                },
            )
        }

        onNodeWithTag(TaskUiTags.LABELS_BUTTON).performClick()
        onNodeWithTag(TaskUiTags.LABEL_MANAGER).assertExists()
        onNodeWithTag(TaskUiTags.ADD_LABEL).performClick()
        onNodeWithTag(TaskUiTags.LABEL_NAME).performTextInput("Release")
        onNodeWithTag(TaskUiTags.LABEL_SAVE).performClick()

        runOnIdle {
            assertEquals("Release", savedName)
        }
    }

    @Test
    fun keepsLabelWorkflowUsableAtCompactSize() = runSkikoComposeUiTest(
        size = Size(width = 412f, height = 915f),
    ) {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                labels = LABELS,
                activeCount = TASKS.size,
            ),
        )
        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { state = it },
            )
        }

        onNodeWithTag(TaskUiTags.LABELS_BUTTON).performClick()
        onNodeWithTag(TaskUiTags.LABEL_MANAGER).assertIsDisplayed()
        onNodeWithTag(TaskUiTags.ADD_LABEL)
            .assertIsDisplayed()
            .performClick()
        onNodeWithTag(TaskUiTags.LABEL_NAME)
            .assertIsDisplayed()
            .performTextInput("Compact")
        onNodeWithTag(TaskUiTags.LABEL_SAVE)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun opensReminderPickerFromTheTaskEditor() = runSkikoComposeUiTest(
        size = Size(width = 412f, height = 915f),
    ) {
        var state by mutableStateOf(
            TaskUiState(
                isInitializing = false,
                tasks = TASKS,
                activeCount = TASKS.size,
            ),
        )
        var permissionRequested = false

        setContent {
            TestTaskScreen(
                state = state,
                onStateChange = { state = it },
                onReminderPermissionRequest = { permissionRequested = true },
            )
        }

        onNodeWithTag(TaskUiTags.NEW_TASK).performClick()
        onNodeWithTag(TaskUiTags.EDITOR_REMINDER)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        onNodeWithText("Reminder date").assertIsDisplayed()
        assertTrue(permissionRequested)
    }
}

@Composable
private fun TestTaskScreen(
    state: TaskUiState,
    onStateChange: (TaskUiState) -> Unit,
    onSave: () -> Unit = {},
    onSaveProject: () -> Unit = {},
    modifier: Modifier = Modifier,
    onLabelSave: () -> Unit = {},
    onReminderPermissionRequest: () -> Unit = {},
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
                changeLabelFilter = { labelId ->
                    onStateChange(
                        state.copy(
                            selectedLabelId = labelId,
                            tasks = if (labelId == null) {
                                TASKS
                            } else {
                                TASKS.filter { labelId in it.task.labelIds }
                            },
                        ),
                    )
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
                changeEditorLabel = { labelId, selected ->
                    val editor = state.editor
                    onStateChange(
                        state.copy(
                            editor = editor?.copy(
                                labelIds = if (selected) {
                                    (editor.labelIds + labelId).distinct()
                                } else {
                                    editor.labelIds - labelId
                                },
                            ),
                        ),
                    )
                },
                changeEditorReminderAt = { reminderAt ->
                    onStateChange(
                        state.copy(editor = state.editor?.copy(reminderAt = reminderAt)),
                    )
                },
                requestReminderPermission = onReminderPermissionRequest,
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
                showLabelManager = {
                    onStateChange(state.copy(isManagingLabels = true))
                },
                dismissLabelManager = {
                    onStateChange(
                        state.copy(
                            isManagingLabels = false,
                            labelEditor = null,
                        ),
                    )
                },
                createLabel = {
                    onStateChange(state.copy(labelEditor = TaskLabelEditorUiState()))
                },
                dismissLabelEditor = {
                    onStateChange(state.copy(labelEditor = null))
                },
                changeLabelName = {
                    onStateChange(
                        state.copy(labelEditor = state.labelEditor?.copy(name = it)),
                    )
                },
                saveLabel = onLabelSave,
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
            labelIds = listOf("work"),
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

private val LABELS = listOf(
    TaskLabelItem(
        label = TaskLabel(
            id = "work",
            name = "Work",
            color = TaskLabelColor.BLUE,
            createdAt = TEST_NOW,
            updatedAt = TEST_NOW,
            revision = 1,
        ),
        syncState = TaskSyncState.SYNCED,
    ),
)
