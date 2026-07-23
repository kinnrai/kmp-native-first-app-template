@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.example.kmpnativefirst.task.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskConstraints
import com.example.kmpnativefirst.task.TaskFilter
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.data.TaskConflict
import com.example.kmpnativefirst.task.data.TaskConflictResolution
import com.example.kmpnativefirst.task.data.TaskItem
import com.example.kmpnativefirst.task.data.TaskRepository
import com.example.kmpnativefirst.task.data.TaskSyncPhase
import com.example.kmpnativefirst.task.data.TaskSyncState
import kmpnativefirstapptemplate.app.sharedui.generated.resources.Res
import kmpnativefirstapptemplate.app.sharedui.generated.resources.cancel
import kmpnativefirstapptemplate.app.sharedui.generated.resources.clear_completed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.clear_completed_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.clear_completed_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.conflict_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.conflict_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.conflicts_waiting
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete_task_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete_task_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.due_date
import kmpnativefirstapptemplate.app.sharedui.generated.resources.due_value
import kmpnativefirstapptemplate.app.sharedui.generated.resources.edit_task
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_active_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_active_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_all_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_all_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_completed_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_completed_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_search_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.empty_search_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.filter_active
import kmpnativefirstapptemplate.app.sharedui.generated.resources.filter_all
import kmpnativefirstapptemplate.app.sharedui.generated.resources.filter_completed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.initialization_failed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.keep_this_device
import kmpnativefirstapptemplate.app.sharedui.generated.resources.mark_completed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.new_task
import kmpnativefirstapptemplate.app.sharedui.generated.resources.operation_failed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.operation_failed_without_detail
import kmpnativefirstapptemplate.app.sharedui.generated.resources.priority_high
import kmpnativefirstapptemplate.app.sharedui.generated.resources.priority_low
import kmpnativefirstapptemplate.app.sharedui.generated.resources.priority_medium
import kmpnativefirstapptemplate.app.sharedui.generated.resources.priority_none
import kmpnativefirstapptemplate.app.sharedui.generated.resources.remove_due_date
import kmpnativefirstapptemplate.app.sharedui.generated.resources.retry
import kmpnativefirstapptemplate.app.sharedui.generated.resources.review
import kmpnativefirstapptemplate.app.sharedui.generated.resources.save
import kmpnativefirstapptemplate.app.sharedui.generated.resources.saving
import kmpnativefirstapptemplate.app.sharedui.generated.resources.search_tasks
import kmpnativefirstapptemplate.app.sharedui.generated.resources.service_version
import kmpnativefirstapptemplate.app.sharedui.generated.resources.sync
import kmpnativefirstapptemplate.app.sharedui.generated.resources.sync_complete
import kmpnativefirstapptemplate.app.sharedui.generated.resources.sync_complete_with_conflicts
import kmpnativefirstapptemplate.app.sharedui.generated.resources.sync_failed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.sync_pending
import kmpnativefirstapptemplate.app.sharedui.generated.resources.syncing
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_conflict
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_deleted
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_notes_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_notes_too_long
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_pending
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_priority_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_title_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_title_required
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_title_too_long
import kmpnativefirstapptemplate.app.sharedui.generated.resources.tasks_summary
import kmpnativefirstapptemplate.app.sharedui.generated.resources.tasks_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.this_device
import kmpnativefirstapptemplate.app.sharedui.generated.resources.use_service
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun rememberTaskViewModel(
    repositoryFactory: suspend () -> TaskRepository,
): TaskViewModel = viewModel {
    TaskViewModel(repositoryFactory)
}

@Composable
fun TaskApp(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeMessage = notice?.content?.message()
    val actions = remember(viewModel) {
        TaskScreenActions(
            retryInitialization = viewModel::retryInitialization,
            changeSearchQuery = viewModel::setSearchQuery,
            changeFilter = viewModel::setFilter,
            createTask = viewModel::showCreateEditor,
            editTask = viewModel::showEditEditor,
            toggleCompleted = viewModel::toggleCompleted,
            requestDelete = viewModel::requestDelete,
            cancelDelete = viewModel::cancelDelete,
            confirmDelete = viewModel::confirmDelete,
            requestClearCompleted = viewModel::requestClearCompleted,
            cancelClearCompleted = viewModel::cancelClearCompleted,
            confirmClearCompleted = viewModel::confirmClearCompleted,
            synchronize = viewModel::synchronize,
            showConflict = viewModel::showConflict,
            dismissConflict = viewModel::dismissConflict,
            resolveConflict = viewModel::resolveSelectedConflict,
            dismissEditor = viewModel::dismissEditor,
            changeEditorTitle = viewModel::setEditorTitle,
            changeEditorNotes = viewModel::setEditorNotes,
            changeEditorPriority = viewModel::setEditorPriority,
            changeEditorDueAt = viewModel::setEditorDueAt,
            changeEditorCompleted = viewModel::setEditorCompleted,
            saveEditor = viewModel::saveEditor,
        )
    }

    LaunchedEffect(notice?.id) {
        if (notice != null && noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            viewModel.consumeNotice(notice.id)
        }
    }

    TaskScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = actions,
        modifier = modifier,
    )
}

@Composable
internal fun TaskScreen(
    state: TaskUiState,
    snackbarHostState: SnackbarHostState,
    actions: TaskScreenActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(TaskUiTags.ROOT),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(Res.string.tasks_title))
                },
                subtitle = {
                    Text(
                        stringResource(
                            Res.string.tasks_summary,
                            state.activeCount,
                            state.completedCount,
                        ),
                    )
                },
                actions = {
                    FilledTonalButton(
                        onClick = actions.synchronize,
                        enabled = !state.isInitializing &&
                            state.syncStatus.phase != TaskSyncPhase.SYNCING,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        AnimatedContent(
                            targetState = state.syncStatus.phase == TaskSyncPhase.SYNCING,
                        ) { syncingNow ->
                            if (syncingNow) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ContainedLoadingIndicator(Modifier.size(20.dp))
                                    Text(stringResource(Res.string.syncing))
                                }
                            } else {
                                Text(stringResource(Res.string.sync))
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.createTask,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag(TaskUiTags.NEW_TASK),
            ) {
                Text("+  ${stringResource(Res.string.new_task)}")
            }
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding),
        ) {
            val showEditorPane = maxWidth >= 980.dp && state.editor != null
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TaskListPane(
                    state = state,
                    onRetryInitialization = actions.retryInitialization,
                    onSearchQueryChange = actions.changeSearchQuery,
                    onFilterChange = actions.changeFilter,
                    onEditTask = actions.editTask,
                    onToggleCompleted = actions.toggleCompleted,
                    onRequestDelete = actions.requestDelete,
                    onRequestClearCompleted = actions.requestClearCompleted,
                    onShowConflict = actions.showConflict,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = if (showEditorPane) 900.dp else 1040.dp),
                )

                AnimatedVisibility(showEditorPane) {
                    state.editor?.let { editor ->
                        Surface(
                            modifier = Modifier
                                .width(420.dp)
                                .fillMaxHeight()
                                .padding(start = 8.dp, end = 16.dp, bottom = 16.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            TaskEditor(
                                editor = editor,
                                onDismiss = actions.dismissEditor,
                                onTitleChange = actions.changeEditorTitle,
                                onNotesChange = actions.changeEditorNotes,
                                onPriorityChange = actions.changeEditorPriority,
                                onDueAtChange = actions.changeEditorDueAt,
                                onCompletedChange = actions.changeEditorCompleted,
                                onSave = actions.saveEditor,
                                onDelete = {
                                    editor.taskId?.let(actions.requestDelete)
                                },
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }

            if (state.editor != null && !showEditorPane) {
                TaskEditorDialog(
                    editor = state.editor,
                    onDismiss = actions.dismissEditor,
                    onTitleChange = actions.changeEditorTitle,
                    onNotesChange = actions.changeEditorNotes,
                    onPriorityChange = actions.changeEditorPriority,
                    onDueAtChange = actions.changeEditorDueAt,
                    onCompletedChange = actions.changeEditorCompleted,
                    onSave = actions.saveEditor,
                    onDelete = {
                        state.editor.taskId?.let(actions.requestDelete)
                    },
                )
            }
        }
    }

    state.taskPendingDeletion?.let { pending ->
        AlertDialog(
            onDismissRequest = actions.cancelDelete,
            title = {
                Text(stringResource(Res.string.delete_task_title, pending.task.title))
            },
            text = { Text(stringResource(Res.string.delete_task_body)) },
            confirmButton = {
                Button(onClick = actions.confirmDelete) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.cancelDelete) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (state.isConfirmingClearCompleted) {
        AlertDialog(
            onDismissRequest = actions.cancelClearCompleted,
            title = { Text(stringResource(Res.string.clear_completed_title)) },
            text = { Text(stringResource(Res.string.clear_completed_body)) },
            confirmButton = {
                Button(onClick = actions.confirmClearCompleted) {
                    Text(stringResource(Res.string.clear_completed))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.cancelClearCompleted) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    state.selectedConflict?.let { conflict ->
        ConflictDialog(
            conflict = conflict,
            onDismiss = actions.dismissConflict,
            onKeepLocal = {
                actions.resolveConflict(TaskConflictResolution.KeepLocal)
            },
            onUseRemote = {
                actions.resolveConflict(TaskConflictResolution.UseRemote)
            },
        )
    }
}

@Composable
private fun TaskListPane(
    state: TaskUiState,
    onRetryInitialization: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
    onEditTask: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestClearCompleted: () -> Unit,
    onShowConflict: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TaskUiTags.SEARCH),
            placeholder = { Text(stringResource(Res.string.search_tasks)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            shape = MaterialTheme.shapes.extraLarge,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskFilter.entries.forEach { filter ->
                ElevatedFilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    TaskFilter.ALL -> Res.string.filter_all
                                    TaskFilter.ACTIVE -> Res.string.filter_active
                                    TaskFilter.COMPLETED -> Res.string.filter_completed
                                },
                            ),
                        )
                    },
                )
            }
            if (state.completedCount > 0) {
                TextButton(onClick = onRequestClearCompleted) {
                    Text(stringResource(Res.string.clear_completed))
                }
            }
        }

        SyncSummary(
            state = state,
            onShowConflict = onShowConflict,
        )

        when {
            state.isInitializing -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator(Modifier.size(64.dp))
                }
            }

            state.initializationError != null -> {
                InitializationError(onRetryInitialization)
            }

            state.tasks.isEmpty() -> {
                EmptyTasks(
                    filter = state.filter,
                    hasSearchQuery = state.searchQuery.isNotBlank(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        ListItemDefaults.SegmentedGap,
                    ),
                ) {
                    itemsIndexed(
                        items = state.tasks,
                        key = { _, item -> item.task.id },
                    ) { index, item ->
                        TaskRow(
                            item = item,
                            index = index,
                            count = state.tasks.size,
                            onToggleCompleted = onToggleCompleted,
                            onEdit = onEditTask,
                            onDelete = onRequestDelete,
                            onShowConflict = onShowConflict,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSummary(
    state: TaskUiState,
    onShowConflict: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        AnimatedVisibility(state.syncStatus.phase == TaskSyncPhase.FAILED) {
            StatusSurface(
                text = stringResource(Res.string.sync_failed),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        AnimatedVisibility(state.syncStatus.pendingCount > 0) {
            StatusSurface(
                text = stringResource(
                    Res.string.sync_pending,
                    state.syncStatus.pendingCount,
                ),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        AnimatedVisibility(state.conflicts.isNotEmpty()) {
            Surface(
                onClick = {
                    state.conflicts.firstOrNull()?.taskId?.let(onShowConflict)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            Res.string.conflicts_waiting,
                            state.conflicts.size,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(Res.string.review),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSurface(
    text: String,
    color: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = contentColor,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TaskRow(
    item: TaskItem,
    index: Int,
    count: Int,
    onToggleCompleted: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShowConflict: (String) -> Unit,
) {
    val task = item.task
    SegmentedListItem(
        onClick = {
            if (item.syncState == TaskSyncState.CONFLICT) {
                onShowConflict(task.id)
            } else {
                onEdit(task.id)
            }
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.testTag(TaskUiTags.task(task.id)),
        content = {
            Text(
                text = task.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                modifier = Modifier.alpha(if (task.isCompleted) 0.68f else 1f),
            )
        },
        supportingContent = {
            TaskSupportingText(item)
        },
        leadingContent = {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleCompleted(task.id) },
                enabled = item.syncState != TaskSyncState.CONFLICT,
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    contentDescription = task.title
                },
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                TaskSyncLabel(item.syncState)
                TextButton(
                    onClick = { onDelete(task.id) },
                    enabled = item.syncState != TaskSyncState.CONFLICT,
                ) {
                    Text(stringResource(Res.string.delete))
                }
            }
        },
    )
}

@Composable
private fun TaskSupportingText(item: TaskItem) {
    val task = item.task
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        task.notes?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (task.priority != TaskPriority.NONE) {
                Text(
                    stringResource(task.priority.labelResource()),
                    color = task.priority.labelColor(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            task.dueAt?.let {
                Text(
                    stringResource(Res.string.due_value, it.dateLabel()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun TaskSyncLabel(syncState: TaskSyncState) {
    when (syncState) {
        TaskSyncState.SYNCED -> Unit
        TaskSyncState.PENDING -> {
            Text(
                stringResource(Res.string.task_pending),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        TaskSyncState.CONFLICT -> {
            Text(
                stringResource(Res.string.task_conflict),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun InitializationError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.initialization_failed),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(Res.string.retry))
            }
        }
    }
}

@Composable
private fun EmptyTasks(
    filter: TaskFilter,
    hasSearchQuery: Boolean,
) {
    val title: StringResource
    val body: StringResource
    if (hasSearchQuery) {
        title = Res.string.empty_search_title
        body = Res.string.empty_search_body
    } else {
        when (filter) {
            TaskFilter.ALL -> {
                title = Res.string.empty_all_title
                body = Res.string.empty_all_body
            }

            TaskFilter.ACTIVE -> {
                title = Res.string.empty_active_title
                body = Res.string.empty_active_body
            }

            TaskFilter.COMPLETED -> {
                title = Res.string.empty_completed_title
                body = Res.string.empty_completed_body
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskEditorDialog(
    editor: TaskEditorUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueAtChange: (Instant?) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            TaskEditor(
                editor = editor,
                onDismiss = onDismiss,
                onTitleChange = onTitleChange,
                onNotesChange = onNotesChange,
                onPriorityChange = onPriorityChange,
                onDueAtChange = onDueAtChange,
                onCompletedChange = onCompletedChange,
                onSave = onSave,
                onDelete = onDelete,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun TaskEditor(
    editor: TaskEditorUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onDueAtChange: (Instant?) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember(editor.taskId, editor.dueAt) {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag(TaskUiTags.EDITOR),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(
                if (editor.isEditing) Res.string.edit_task else Res.string.new_task,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = editor.title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TaskUiTags.EDITOR_TITLE),
            label = { Text(stringResource(Res.string.task_title_label)) },
            singleLine = true,
            isError = editor.showValidationErrors && editor.hasTitleError,
            supportingText = {
                if (editor.showValidationErrors && editor.hasTitleError) {
                    if (editor.title.isBlank()) {
                        Text(stringResource(Res.string.task_title_required))
                    } else {
                        Text(
                            stringResource(
                                Res.string.task_title_too_long,
                                TaskConstraints.MAX_TITLE_LENGTH,
                            ),
                        )
                    }
                }
            },
        )

        OutlinedTextField(
            value = editor.notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.task_notes_label)) },
            minLines = 3,
            maxLines = 6,
            isError = editor.showValidationErrors && editor.hasNotesError,
            supportingText = {
                if (editor.showValidationErrors && editor.hasNotesError) {
                    Text(
                        stringResource(
                            Res.string.task_notes_too_long,
                            TaskConstraints.MAX_NOTES_LENGTH,
                        ),
                    )
                }
            },
        )

        Text(
            stringResource(Res.string.task_priority_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskPriority.entries.forEach { priority ->
                ElevatedFilterChip(
                    selected = editor.priority == priority,
                    onClick = { onPriorityChange(priority) },
                    label = {
                        Text(stringResource(priority.labelResource()))
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(
                    editor.dueAt?.let {
                        stringResource(Res.string.due_value, it.dateLabel())
                    } ?: stringResource(Res.string.due_date),
                )
            }
            if (editor.dueAt != null) {
                TextButton(onClick = { onDueAtChange(null) }) {
                    Text(stringResource(Res.string.remove_due_date))
                }
            }
        }

        if (editor.isEditing) {
            Surface(
                onClick = { onCompletedChange(!editor.isCompleted) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = editor.isCompleted,
                        onCheckedChange = onCompletedChange,
                    )
                    Text(stringResource(Res.string.mark_completed))
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editor.isEditing) {
                TextButton(
                    onClick = onDelete,
                    enabled = !editor.isSaving,
                ) {
                    Text(
                        stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onDismiss,
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(Res.string.cancel))
            }
            Button(
                onClick = onSave,
                enabled = !editor.isSaving,
                modifier = Modifier.testTag(TaskUiTags.EDITOR_SAVE),
            ) {
                Text(
                    stringResource(
                        if (editor.isSaving) Res.string.saving else Res.string.save,
                    ),
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = editor.dueAt?.toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onDueAtChange(Instant.fromEpochMilliseconds(it))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ConflictDialog(
    conflict: TaskConflict,
    onDismiss: () -> Unit,
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.conflict_body))
                ConflictVersion(
                    label = stringResource(Res.string.this_device),
                    task = conflict.local,
                )
                ConflictVersion(
                    label = stringResource(Res.string.service_version),
                    task = conflict.remote,
                )
            }
        },
        confirmButton = {
            Button(onClick = onKeepLocal) {
                Text(stringResource(Res.string.keep_this_device))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onUseRemote) {
                    Text(stringResource(Res.string.use_service))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun ConflictVersion(
    label: String,
    task: Task?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (task == null) {
                Text(
                    stringResource(Res.string.task_deleted),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                task.notes?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun TaskUiNoticeContent.message(): String = when (this) {
    is TaskUiNoticeContent.SyncCompleted -> {
        if (conflictCount == 0) {
            stringResource(
                Res.string.sync_complete,
                pushedCount,
                pulledCount,
            )
        } else {
            stringResource(
                Res.string.sync_complete_with_conflicts,
                conflictCount,
            )
        }
    }

    is TaskUiNoticeContent.OperationFailed -> detail
        ?.takeIf(String::isNotBlank)
        ?.let { stringResource(Res.string.operation_failed, it) }
        ?: stringResource(Res.string.operation_failed_without_detail)
}

@Composable
private fun TaskPriority.labelResource(): StringResource = when (this) {
    TaskPriority.NONE -> Res.string.priority_none
    TaskPriority.LOW -> Res.string.priority_low
    TaskPriority.MEDIUM -> Res.string.priority_medium
    TaskPriority.HIGH -> Res.string.priority_high
}

@Composable
private fun TaskPriority.labelColor(): Color = when (this) {
    TaskPriority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
}

private fun Instant.dateLabel(): String = toString().substringBefore('T')

internal object TaskUiTags {
    const val ROOT = "tasks-root"
    const val SEARCH = "tasks-search"
    const val NEW_TASK = "tasks-new"
    const val EDITOR = "task-editor"
    const val EDITOR_TITLE = "task-editor-title"
    const val EDITOR_SAVE = "task-editor-save"

    fun task(id: String): String = "task-$id"
}

@Preview
@Composable
private fun TaskScreenPreview() {
    val now = Instant.parse("2026-07-23T08:00:00Z")
    MaterialTheme {
        TaskScreen(
            state = TaskUiState(
                isInitializing = false,
                tasks = listOf(
                    TaskItem(
                        task = Task(
                            id = "one",
                            title = "Review the platform release checklist",
                            notes = "Verify Android, Desktop, iOS, macOS and Web.",
                            priority = TaskPriority.HIGH,
                            dueAt = Instant.parse("2026-07-25T00:00:00Z"),
                            createdAt = now,
                            updatedAt = now,
                            revision = 1,
                        ),
                        syncState = TaskSyncState.PENDING,
                    ),
                    TaskItem(
                        task = Task(
                            id = "two",
                            title = "Document the offline workflow",
                            isCompleted = true,
                            createdAt = now,
                            updatedAt = now,
                            revision = 1,
                        ),
                        syncState = TaskSyncState.SYNCED,
                    ),
                ),
                activeCount = 1,
                completedCount = 1,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            actions = TaskScreenActions(),
        )
    }
}
