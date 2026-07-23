package com.example.kmpnativefirst

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.kmpnativefirst.task.data.createTaskRepository
import com.example.kmpnativefirst.task.ui.TaskApp
import com.example.kmpnativefirst.task.ui.rememberTaskViewModel

@Composable
fun AndroidApp() {
    val applicationContext = LocalContext.current.applicationContext
    val repositoryFactory = remember(applicationContext) {
        suspend {
            createTaskRepository(
                context = applicationContext,
                baseUrl = BuildConfig.TASK_API_BASE_URL,
            )
        }
    }
    val viewModel = rememberTaskViewModel(repositoryFactory)

    AndroidTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            TaskApp(viewModel)
        }
    }
}
