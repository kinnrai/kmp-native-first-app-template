@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.kmpnativefirst.task.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kmpnativefirst.task.TaskLabel
import com.example.kmpnativefirst.task.TaskLabelColor
import com.example.kmpnativefirst.task.TaskLabelConstraints
import com.example.kmpnativefirst.task.data.TaskLabelConflict
import com.example.kmpnativefirst.task.data.TaskLabelConflictResolution
import com.example.kmpnativefirst.task.data.TaskLabelItem
import com.example.kmpnativefirst.task.data.TaskSyncState
import kmpnativefirstapptemplate.app.sharedui.generated.resources.Res
import kmpnativefirstapptemplate.app.sharedui.generated.resources.add_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.all_labels
import kmpnativefirstapptemplate.app.sharedui.generated.resources.cancel
import kmpnativefirstapptemplate.app.sharedui.generated.resources.close
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete_label_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete_label_named
import kmpnativefirstapptemplate.app.sharedui.generated.resources.delete_label_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.edit
import kmpnativefirstapptemplate.app.sharedui.generated.resources.edit_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.edit_label_named
import kmpnativefirstapptemplate.app.sharedui.generated.resources.keep_this_device
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color_blue
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color_green
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color_orange
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color_purple
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color_rose
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_color_slate
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_conflict
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_conflict_body
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_conflict_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_conflicts_waiting
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_deleted
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_name
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_name_required
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_name_too_long
import kmpnativefirstapptemplate.app.sharedui.generated.resources.label_pending
import kmpnativefirstapptemplate.app.sharedui.generated.resources.labels_description
import kmpnativefirstapptemplate.app.sharedui.generated.resources.labels_empty
import kmpnativefirstapptemplate.app.sharedui.generated.resources.labels_title
import kmpnativefirstapptemplate.app.sharedui.generated.resources.new_label
import kmpnativefirstapptemplate.app.sharedui.generated.resources.review
import kmpnativefirstapptemplate.app.sharedui.generated.resources.review_label_named
import kmpnativefirstapptemplate.app.sharedui.generated.resources.save
import kmpnativefirstapptemplate.app.sharedui.generated.resources.saving
import kmpnativefirstapptemplate.app.sharedui.generated.resources.service_version
import kmpnativefirstapptemplate.app.sharedui.generated.resources.this_device
import kmpnativefirstapptemplate.app.sharedui.generated.resources.use_service
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TaskLabelFilters(
    labels: List<TaskLabelItem>,
    selectedLabelId: String?,
    onSelectionChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElevatedFilterChip(
            selected = selectedLabelId == null,
            onClick = { onSelectionChange(null) },
            label = { Text(stringResource(Res.string.all_labels)) },
            modifier = Modifier.testTag(TaskUiTags.ALL_LABELS),
        )
        labels.forEach { item ->
            ElevatedFilterChip(
                selected = selectedLabelId == item.label.id,
                onClick = { onSelectionChange(item.label.id) },
                leadingIcon = { TaskLabelDot(item.label.color) },
                label = { Text(item.label.name) },
                modifier = Modifier.testTag(TaskUiTags.labelFilter(item.label.id)),
            )
        }
    }
}

@Composable
internal fun TaskLabelBadges(
    labelIds: List<String>,
    labelsById: Map<String, TaskLabelItem>,
    modifier: Modifier = Modifier,
) {
    val selectedLabels = labelIds.mapNotNull(labelsById::get)
    if (selectedLabels.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        selectedLabels.forEach { item ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskLabelDot(item.label.color)
                    Text(
                        text = item.label.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskLabelSelector(
    labels: List<TaskLabelItem>,
    selectedLabelIds: List<String>,
    onSelectionChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { item ->
            val selected = item.label.id in selectedLabelIds
            ElevatedFilterChip(
                selected = selected,
                onClick = { onSelectionChange(item.label.id, !selected) },
                leadingIcon = { TaskLabelDot(item.label.color) },
                label = { Text(item.label.name) },
                modifier = Modifier.testTag(TaskUiTags.editorLabel(item.label.id)),
            )
        }
    }
}

@Composable
internal fun TaskLabelDialogs(
    state: TaskUiState,
    actions: TaskScreenActions,
) {
    if (state.isManagingLabels) {
        BasicAlertDialog(
            onDismissRequest = actions.dismissLabelManager,
            modifier = Modifier.testTag(TaskUiTags.LABEL_MANAGER),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                state.labelEditor?.let { editor ->
                    TaskLabelEditor(
                        editor = editor,
                        onNameChange = actions.changeLabelName,
                        onColorChange = actions.changeLabelColor,
                        onDismiss = actions.dismissLabelEditor,
                        onSave = actions.saveLabel,
                    )
                } ?: TaskLabelManager(
                    labels = state.labels,
                    conflicts = state.labelConflicts,
                    onCreate = actions.createLabel,
                    onEdit = actions.editLabel,
                    onDelete = actions.requestDeleteLabel,
                    onShowConflict = actions.showLabelConflict,
                    onDismiss = actions.dismissLabelManager,
                )
            }
        }
    }

    state.labelPendingDeletion?.let { item ->
        AlertDialog(
            onDismissRequest = actions.cancelDeleteLabel,
            title = {
                Text(stringResource(Res.string.delete_label_title, item.label.name))
            },
            text = { Text(stringResource(Res.string.delete_label_body)) },
            confirmButton = {
                Button(onClick = actions.confirmDeleteLabel) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.cancelDeleteLabel) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    state.selectedLabelConflict?.let { conflict ->
        TaskLabelConflictDialog(
            conflict = conflict,
            onDismiss = actions.dismissLabelConflict,
            onKeepLocal = {
                actions.resolveLabelConflict(TaskLabelConflictResolution.KeepLocal)
            },
            onUseRemote = {
                actions.resolveLabelConflict(TaskLabelConflictResolution.UseRemote)
            },
        )
    }
}

@Composable
private fun TaskLabelManager(
    labels: List<TaskLabelItem>,
    conflicts: List<TaskLabelConflict>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShowConflict: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(Res.string.labels_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(Res.string.labels_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        }

        if (conflicts.isNotEmpty()) {
            Surface(
                onClick = { onShowConflict(conflicts.first().labelId) },
                modifier = Modifier.fillMaxWidth(),
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
                            Res.string.label_conflicts_waiting,
                            conflicts.size,
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

        if (labels.isEmpty()) {
            Text(
                stringResource(Res.string.labels_empty),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = labels,
                    key = { it.label.id },
                ) { item ->
                    TaskLabelManagerRow(
                        item = item,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onShowConflict = onShowConflict,
                    )
                }
            }
        }

        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onCreate,
                modifier = Modifier.testTag(TaskUiTags.ADD_LABEL),
            ) {
                Text(stringResource(Res.string.add_label))
            }
        }
    }
}

@Composable
private fun TaskLabelManagerRow(
    item: TaskLabelItem,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShowConflict: (String) -> Unit,
) {
    val label = item.label
    val reviewDescription = stringResource(Res.string.review_label_named, label.name)
    val editDescription = stringResource(Res.string.edit_label_named, label.name)
    val deleteDescription = stringResource(Res.string.delete_label_named, label.name)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TaskUiTags.labelRow(label.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskLabelDot(label.color)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                when (item.syncState) {
                    TaskSyncState.SYNCED -> Unit
                    TaskSyncState.PENDING -> Text(
                        stringResource(Res.string.label_pending),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )

                    TaskSyncState.CONFLICT -> Text(
                        stringResource(Res.string.label_conflict),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (item.syncState == TaskSyncState.CONFLICT) {
                TextButton(
                    onClick = { onShowConflict(label.id) },
                    modifier = Modifier.semantics {
                        contentDescription = reviewDescription
                    },
                ) {
                    Text(stringResource(Res.string.review))
                }
            } else {
                TextButton(
                    onClick = { onEdit(label.id) },
                    modifier = Modifier.semantics {
                        contentDescription = editDescription
                    },
                ) {
                    Text(stringResource(Res.string.edit))
                }
                TextButton(
                    onClick = { onDelete(label.id) },
                    modifier = Modifier.semantics {
                        contentDescription = deleteDescription
                    },
                ) {
                    Text(
                        stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskLabelEditor(
    editor: TaskLabelEditorUiState,
    onNameChange: (String) -> Unit,
    onColorChange: (TaskLabelColor) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(
                if (editor.isEditing) Res.string.edit_label else Res.string.new_label,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = editor.name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TaskUiTags.LABEL_NAME),
            label = { Text(stringResource(Res.string.label_name)) },
            singleLine = true,
            isError = editor.showValidationErrors && editor.hasNameError,
            supportingText = {
                if (editor.showValidationErrors && editor.hasNameError) {
                    if (editor.name.isBlank()) {
                        Text(stringResource(Res.string.label_name_required))
                    } else {
                        Text(
                            stringResource(
                                Res.string.label_name_too_long,
                                TaskLabelConstraints.MAX_NAME_LENGTH,
                            ),
                        )
                    }
                }
            },
            enabled = !editor.isSaving,
        )
        Text(
            stringResource(Res.string.label_color),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskLabelColor.entries.forEach { color ->
                ElevatedFilterChip(
                    selected = editor.color == color,
                    onClick = { onColorChange(color) },
                    enabled = !editor.isSaving,
                    leadingIcon = { TaskLabelDot(color) },
                    label = { Text(stringResource(color.labelResource())) },
                    modifier = Modifier.testTag(TaskUiTags.labelColor(color)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                modifier = Modifier.testTag(TaskUiTags.LABEL_SAVE),
            ) {
                Text(
                    stringResource(
                        if (editor.isSaving) Res.string.saving else Res.string.save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TaskLabelConflictDialog(
    conflict: TaskLabelConflict,
    onDismiss: () -> Unit,
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.label_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.label_conflict_body))
                TaskLabelConflictVersion(
                    title = stringResource(Res.string.this_device),
                    label = conflict.local,
                )
                TaskLabelConflictVersion(
                    title = stringResource(Res.string.service_version),
                    label = conflict.remote,
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
private fun TaskLabelConflictVersion(
    title: String,
    label: TaskLabel?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (label == null) {
                Text(
                    stringResource(Res.string.label_deleted),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskLabelDot(label.color)
                    Text(label.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TaskLabelDot(color: TaskLabelColor) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(color.displayColor()),
    )
}

private fun TaskLabelColor.displayColor(): Color = when (this) {
    TaskLabelColor.BLUE -> Color(0xFF1976D2)
    TaskLabelColor.GREEN -> Color(0xFF2E7D32)
    TaskLabelColor.ORANGE -> Color(0xFFEF6C00)
    TaskLabelColor.PURPLE -> Color(0xFF7B1FA2)
    TaskLabelColor.ROSE -> Color(0xFFC2185B)
    TaskLabelColor.SLATE -> Color(0xFF546E7A)
}

private fun TaskLabelColor.labelResource(): StringResource = when (this) {
    TaskLabelColor.BLUE -> Res.string.label_color_blue
    TaskLabelColor.GREEN -> Res.string.label_color_green
    TaskLabelColor.ORANGE -> Res.string.label_color_orange
    TaskLabelColor.PURPLE -> Res.string.label_color_purple
    TaskLabelColor.ROSE -> Res.string.label_color_rose
    TaskLabelColor.SLATE -> Res.string.label_color_slate
}
