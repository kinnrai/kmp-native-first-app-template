@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.kmpnativefirst.task.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kmpnativefirstapptemplate.app.sharedui.generated.resources.Res
import kmpnativefirstapptemplate.app.sharedui.generated.resources.cancel
import kmpnativefirstapptemplate.app.sharedui.generated.resources.reminder
import kmpnativefirstapptemplate.app.sharedui.generated.resources.reminder_date
import kmpnativefirstapptemplate.app.sharedui.generated.resources.reminder_time
import kmpnativefirstapptemplate.app.sharedui.generated.resources.reminder_value
import kmpnativefirstapptemplate.app.sharedui.generated.resources.remove_reminder
import kmpnativefirstapptemplate.app.sharedui.generated.resources.save
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Composable
internal fun TaskReminderEditor(
    editor: TaskEditorUiState,
    onReminderChange: (Instant?) -> Unit,
    onReminderPermissionRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember(editor.taskId, editor.reminderAt) {
        mutableStateOf(false)
    }
    var showTimePicker by remember(editor.taskId, editor.reminderAt) {
        mutableStateOf(false)
    }
    var pendingReminder by remember(editor.taskId, editor.reminderAt) {
        mutableStateOf<LocalDateTime?>(null)
    }
    val timeZone = remember { TimeZone.currentSystemDefault() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                onReminderPermissionRequest()
                pendingReminder = defaultReminderAt(
                    editor = editor,
                    now = Clock.System.now(),
                    timeZone = timeZone,
                ).toLocalDateTime(timeZone)
                showDatePicker = true
            },
            modifier = Modifier.testTag(TaskUiTags.EDITOR_REMINDER),
        ) {
            Text(
                editor.reminderAt?.let {
                    stringResource(
                        Res.string.reminder_value,
                        it.localDateTimeLabel(timeZone),
                    )
                } ?: stringResource(Res.string.reminder),
            )
        }
        if (editor.reminderAt != null) {
            TextButton(onClick = { onReminderChange(null) }) {
                Text(stringResource(Res.string.remove_reminder))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingReminder
                ?.date
                ?.atStartOfDayIn(TimeZone.UTC)
                ?.toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            val selectedDate = Instant.fromEpochMilliseconds(it)
                                .toLocalDateTime(TimeZone.UTC)
                                .date
                            val current = requireNotNull(pendingReminder)
                            pendingReminder = LocalDateTime(selectedDate, current.time)
                            showDatePicker = false
                            showTimePicker = true
                        }
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
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        text = stringResource(Res.string.reminder_date),
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 16.dp,
                        ),
                    )
                },
            )
        }
    }

    if (showTimePicker) {
        val current = requireNotNull(pendingReminder)
        val pickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(Res.string.reminder_time)) },
            text = {
                TimeInput(
                    state = pickerState,
                    modifier = Modifier.testTag(TaskUiTags.EDITOR_REMINDER_TIME),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = pendingReminder
                            ?.date
                            ?.atTime(pickerState.hour, pickerState.minute)
                            ?.toInstant(timeZone)
                        onReminderChange(selected)
                        showTimePicker = false
                    },
                ) {
                    Text(stringResource(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

private fun defaultReminderAt(
    editor: TaskEditorUiState,
    now: Instant,
    timeZone: TimeZone,
): Instant = editor.reminderAt
    ?: editor.dueAt?.takeIf { it > now }
    ?: editor.dueDate
        ?.atTime(9, 0)
        ?.toInstant(timeZone)
        ?.takeIf { it > now }
    ?: (now + 1.hours)

internal fun Instant.localDateTimeLabel(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = toLocalDateTime(timeZone)
    return "${local.date} ${local.hour.twoDigits()}:${local.minute.twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
