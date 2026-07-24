package com.example.kmpnativefirst

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kmpnativefirst.reminder.AndroidTaskReminderScheduler

class MainActivity : ComponentActivity() {
    private var reminderTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reminderTaskId = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_ID)

        setContent {
            AndroidApp(
                reminderTaskId = reminderTaskId,
                onReminderTaskConsumed = { reminderTaskId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderTaskId = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_TASK_ID)
    }
}
