package com.example.kmpnativefirst

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.kmpnativefirst.reminder.AndroidTaskReminderScheduler
import com.example.kmpnativefirst.task.data.createTaskRepository
import com.example.kmpnativefirst.task.ui.TaskApp
import com.example.kmpnativefirst.task.ui.rememberTaskViewModel

@Composable
fun AndroidApp(
    reminderTaskId: String? = null,
    onReminderTaskConsumed: () -> Unit = {},
) {
    val applicationContext = LocalContext.current.applicationContext
    val repositoryFactory = remember(applicationContext) {
        suspend {
            createTaskRepository(
                context = applicationContext,
                baseUrl = BuildConfig.TASK_API_BASE_URL,
            )
        }
    }
    val reminderScheduler = remember(applicationContext) {
        AndroidTaskReminderScheduler(applicationContext)
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestReminderPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val viewModel = rememberTaskViewModel(
        repositoryFactory = repositoryFactory,
        reminderScheduler = reminderScheduler,
    )
    LaunchedEffect(reminderTaskId) {
        reminderTaskId?.let {
            viewModel.showTaskFromReminder(it)
            onReminderTaskConsumed()
        }
    }

    AndroidTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            TaskApp(
                viewModel = viewModel,
                requestReminderPermission = requestReminderPermission,
            )
        }
    }
}
