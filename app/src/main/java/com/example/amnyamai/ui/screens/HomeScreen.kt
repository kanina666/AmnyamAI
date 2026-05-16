package com.example.amnyamai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.amnyamai.ui.viewmodel.HomeUiState
import com.example.amnyamai.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMeetingCreated: (meetingId: String, code: String) -> Unit,
    onJoinMeeting: () -> Unit,
    onHistory: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val user = vm.currentUser
    var showTitleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is HomeUiState.MeetingCreated) {
            val s = uiState as HomeUiState.MeetingCreated
            onMeetingCreated(s.meetingId, s.code)
            vm.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MeetingAgent", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, "История встреч")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("👋", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            if (user != null) {
                Text(
                    "Привет, ${user.name}!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Что будем делать?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { showTitleDialog = true },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = uiState !is HomeUiState.Loading
            ) {
                if (uiState is HomeUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.MicNone, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Организовать встречу", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onJoinMeeting,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Подключиться по коду", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState is HomeUiState.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    (uiState as HomeUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showTitleDialog) {
        MeetingTitleDialog(
            onDismiss = { showTitleDialog = false },
            onStart = { title -> showTitleDialog = false; vm.createMeeting(title) }
        )
    }
}

@Composable
private fun MeetingTitleDialog(onDismiss: () -> Unit, onStart: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название встречи") },
        text = {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Например: Синк по продукту") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onStart(title.ifBlank { "Встреча" }) }) { Text("Начать запись") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
