@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.example.kmpnativefirst.task.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskProjectConstraints
import com.example.kmpnativefirst.task.TaskSmartView
import com.example.kmpnativefirst.task.data.TaskProjectConflict
import com.example.kmpnativefirst.task.data.TaskProjectItem
import com.example.kmpnativefirst.task.data.TaskSyncState
import kmpnativefirstapptemplate.app.sharedui.generated.resources.Res
import kmpnativefirstapptemplate.app.sharedui.generated.resources.cancel
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete
import kmpnativefirstapptemplate.app.sharedui.generated.resources.edit_project
import kmpnativefirstapptemplate.app.sharedui.generated.resources.keep_this_device
import kmpnativefirstapptemplate.app.sharedui.generated.resources.manage_project
import kmpnativefirstapptemplate.app.sharedui.generated.resources.new_project
import kmpnativefirstapptemplate.app.sharedui.generated.resources.no_project
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_blue
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_color_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_conflicts_waiting
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_conflict_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_conflict_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_deleted
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_green
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_name_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_name_required
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_name_too_long
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_orange
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_purple
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_rose
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_slate
import kmpnativefirstapptemplate.app.sharedui.generated.resources.project_task_count
import kmpnativefirstapptemplate.app.sharedui.generated.resources.projects
import kmpnativefirstapptemplate.app.sharedui.generated.resources.review
import kmpnativefirstapptemplate.app.sharedui.generated.resources.save
import kmpnativefirstapptemplate.app.sharedui.generated.resources.saving
import kmpnativefirstapptemplate.app.sharedui.generated.resources.service_version
import kmpnativefirstapptemplate.app.sharedui.generated.resources.smart_views
import kmpnativefirstapptemplate.app.sharedui.generated.resources.task_project_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.this_device
import kmpnativefirstapptemplate.app.sharedui.generated.resources.use_service
import kmpnativefirstapptemplate.app.sharedui.generated.resources.view_all
import kmpnativefirstapptemplate.app.sharedui.generated.resources.view_completed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.view_inbox
import kmpnativefirstapptemplate.app.sharedui.generated.resources.view_today
import kmpnativefirstapptemplate.app.sharedui.generated.resources.view_upcoming
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TaskNavigationPane(
    state: TaskUiState,
    actions: TaskScreenActions,
    onDestinationSelected: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(Res.string.smart_views),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TaskSmartView.entries.forEach { view ->
                NavigationDrawerItem(
                    label = { Text(stringResource(view.labelResource())) },
                    selected = state.selectedProjectId == null && state.view == view,
                    onClick = {
                        actions.changeView(view)
                        onDestinationSelected()
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.projects),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = actions.createProject,
                    modifier = Modifier.testTag(TaskUiTags.NEW_PROJECT),
                ) {
                    Text("+ ${stringResource(Res.string.new_project)}")
                }
            }

            if (state.projectConflicts.isNotEmpty()) {
                Surface(
                    onClick = {
                        state.projectConflicts.firstOrNull()?.projectId
                            ?.let(actions.showProjectConflict)
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
                                Res.string.project_conflicts_waiting,
                                state.projectConflicts.size,
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(Res.string.review),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = state.projects,
                    key = { it.project.id },
                ) { item ->
                    TaskProjectNavigationItem(
                        item = item,
                        activeTaskCount = state.projectTaskCounts[item.project.id] ?: 0,
                        selected = state.selectedProjectId == item.project.id,
                        onSelect = {
                            actions.changeProject(item.project.id)
                            onDestinationSelected()
                        },
                        onManage = {
                            if (item.syncState == TaskSyncState.CONFLICT) {
                                actions.showProjectConflict(item.project.id)
                            } else {
                                actions.editProject(item.project.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskProjectNavigationItem(
    item: TaskProjectItem,
    activeTaskCount: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationDrawerItem(
            label = {
                Column {
                    Text(
                        item.project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(Res.string.project_task_count, activeTaskCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            selected = selected,
            onClick = onSelect,
            icon = { TaskProjectDot(item.project.color) },
            modifier = Modifier
                .weight(1f)
                .testTag(TaskUiTags.project(item.project.id)),
        )
        TextButton(onClick = onManage) {
            Text(
                stringResource(
                    if (item.syncState == TaskSyncState.CONFLICT) {
                        Res.string.review
                    } else {
                        Res.string.manage_project
                    },
                ),
            )
        }
    }
}

@Composable
internal fun TaskProjectPicker(
    projects: List<TaskProjectItem>,
    selectedProjectId: String?,
    enabled: Boolean,
    onProjectChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull {
        it.project.id == selectedProjectId
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.task_project_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TaskUiTags.EDITOR_PROJECT),
            ) {
                selected?.let {
                    TaskProjectDot(it.project.color)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    selected?.project?.name ?: stringResource(Res.string.no_project),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.no_project)) },
                    onClick = {
                        onProjectChange(null)
                        expanded = false
                    },
                )
                projects.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.project.name) },
                        onClick = {
                            onProjectChange(item.project.id)
                            expanded = false
                        },
                        leadingIcon = {
                            TaskProjectDot(item.project.color)
                        },
                        enabled = item.syncState != TaskSyncState.CONFLICT,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskProjectEditorDialog(
    editor: TaskProjectEditorUiState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onColorChange: (TaskProjectColor) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .testTag(TaskUiTags.PROJECT_EDITOR),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(
                        if (editor.isEditing) {
                            Res.string.edit_project
                        } else {
                            Res.string.new_project
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TaskUiTags.PROJECT_EDITOR_NAME),
                    label = { Text(stringResource(Res.string.project_name_label)) },
                    singleLine = true,
                    isError = editor.showValidationErrors && editor.hasNameError,
                    supportingText = {
                        if (editor.showValidationErrors && editor.hasNameError) {
                            if (editor.name.isBlank()) {
                                Text(stringResource(Res.string.project_name_required))
                            } else {
                                Text(
                                    stringResource(
                                        Res.string.project_name_too_long,
                                        TaskProjectConstraints.MAX_NAME_LENGTH,
                                    ),
                                )
                            }
                        }
                    },
                )
                Text(
                    stringResource(Res.string.project_color_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskProjectColor.entries.forEach { color ->
                        ElevatedFilterChip(
                            selected = editor.color == color,
                            onClick = { onColorChange(color) },
                            label = {
                                Text(stringResource(color.labelResource()))
                            },
                            leadingIcon = { TaskProjectDot(color) },
                        )
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
                        modifier = Modifier.testTag(TaskUiTags.PROJECT_EDITOR_SAVE),
                    ) {
                        Text(
                            stringResource(
                                if (editor.isSaving) {
                                    Res.string.saving
                                } else {
                                    Res.string.save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskProjectConflictDialog(
    conflict: TaskProjectConflict,
    onDismiss: () -> Unit,
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.project_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.project_conflict_body))
                TaskProjectConflictVersion(
                    label = stringResource(Res.string.this_device),
                    project = conflict.local,
                )
                TaskProjectConflictVersion(
                    label = stringResource(Res.string.service_version),
                    project = conflict.remote,
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
private fun TaskProjectConflictVersion(
    label: String,
    project: TaskProject?,
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
            if (project == null) {
                Text(
                    stringResource(Res.string.project_deleted),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskProjectDot(project.color)
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskProjectDot(
    color: TaskProjectColor,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .background(color.displayColor(), MaterialTheme.shapes.extraLarge),
    )
}

@Composable
internal fun TaskProjectColor.labelResource(): StringResource = when (this) {
    TaskProjectColor.BLUE -> Res.string.project_blue
    TaskProjectColor.GREEN -> Res.string.project_green
    TaskProjectColor.ORANGE -> Res.string.project_orange
    TaskProjectColor.PURPLE -> Res.string.project_purple
    TaskProjectColor.ROSE -> Res.string.project_rose
    TaskProjectColor.SLATE -> Res.string.project_slate
}

private fun TaskProjectColor.displayColor(): Color = when (this) {
    TaskProjectColor.BLUE -> Color(0xFF4F73E8)
    TaskProjectColor.GREEN -> Color(0xFF319364)
    TaskProjectColor.ORANGE -> Color(0xFFE57A24)
    TaskProjectColor.PURPLE -> Color(0xFF8057D5)
    TaskProjectColor.ROSE -> Color(0xFFD94F70)
    TaskProjectColor.SLATE -> Color(0xFF64748B)
}

@Composable
private fun TaskSmartView.labelResource(): StringResource = when (this) {
    TaskSmartView.ALL -> Res.string.view_all
    TaskSmartView.INBOX -> Res.string.view_inbox
    TaskSmartView.TODAY -> Res.string.view_today
    TaskSmartView.UPCOMING -> Res.string.view_upcoming
    TaskSmartView.COMPLETED -> Res.string.view_completed
}
