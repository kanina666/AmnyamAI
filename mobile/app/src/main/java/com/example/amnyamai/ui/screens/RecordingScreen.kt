package com.example.amnyamai.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.amnyamai.ui.viewmodel.RecordingUiState
import com.example.amnyamai.ui.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(meetingId: String, code: String, onDone: (String) -> Unit) {
    val vm: RecordingViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val navigateTo by vm.navigateToResult.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(navigateTo) { navigateTo?.let { onDone(it) } }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) vm.start(meetingId)
    }

    LaunchedEffect(Unit) { permLauncher.launch(permissions) }

    Scaffold(topBar = { TopAppBar(title = { Text("Запись встречи") }) }) { padding ->
        when (val state = uiState) {
            is RecordingUiState.Recording -> RecordingContent(
                state = state, code = code,
                onStop = { vm.stopAndUpload() },
                onCopyCode = { clipboard.setText(AnnotatedString(code)) }
            )
            is RecordingUiState.Uploading -> Box(
                Modifier.fillMaxSize().padding(padding), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Загружаем запись...", style = MaterialTheme.typography.titleMedium)
                    Text("Alice AI анализирует встречу", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is RecordingUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding), Alignment.Center
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            else -> {}
        }
    }
}

@Composable
private fun RecordingContent(
    state: RecordingUiState.Recording,
    code: String,
    onStop: () -> Unit,
    onCopyCode: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        0.85f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Meeting code card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = onCopyCode
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Код для участников", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    code, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Нажмите чтобы скопировать", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.6f))
            }
        }

        Spacer(Modifier.weight(1f))

        // Pulsing dot
        Box(
            modifier = Modifier.size(100.dp).scale(scale)
                .clip(CircleShape).background(Color(0xFFE53935)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White))
        }

        Spacer(Modifier.height(16.dp))

        Text(
            formatTime(state.seconds),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text("Идёт запись", color = Color(0xFFE53935), style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))

        // Participants
        if (state.participants.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Участники (${state.participants.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    state.participants.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
        ) {
            Box(Modifier.size(18.dp).background(Color.White, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text("Завершить встречу", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatTime(s: Long) =
    if (s >= 3600) "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
