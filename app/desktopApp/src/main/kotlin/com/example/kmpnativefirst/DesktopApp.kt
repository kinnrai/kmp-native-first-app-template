package com.example.kmpnativefirst

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.kmpnativefirst.task.data.createTaskRepository
import com.example.kmpnativefirst.task.ui.TaskApp
import com.example.kmpnativefirst.task.ui.rememberTaskViewModel

@Composable
fun DesktopApp() {
    val repositoryFactory = remember {
        suspend {
            createTaskRepository(
                databasePath = desktopDatabasePath(),
                baseUrl = System.getenv("TASK_API_BASE_URL")
                    ?.takeIf(String::isNotBlank)
                    ?: "http://localhost:8080",
            )
        }
    }
    val viewModel = rememberTaskViewModel(repositoryFactory)

    DesktopTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                        false
                    } else {
                        when (event.key) {
                            Key.N -> {
                                viewModel.showCreateEditor()
                                true
                            }

                            Key.R -> {
                                viewModel.synchronize()
                                true
                            }

                            else -> false
                        }
                    }
                },
            color = MaterialTheme.colorScheme.surface,
        ) {
            TaskApp(viewModel)
        }
    }
}

@Composable
private fun DesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFAEC6FF),
                secondary = Color(0xFFBDC6DC),
                tertiary = Color(0xFFDDBCE0),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF3F5F91),
                secondary = Color(0xFF565F71),
                tertiary = Color(0xFF705575),
            )
        },
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
