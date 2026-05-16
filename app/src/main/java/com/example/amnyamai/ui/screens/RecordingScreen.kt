package com.example.amnyamai.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.amnyamai.ui.components.AmNyamErrorDialog
import com.example.amnyamai.ui.components.AmNyamGif
import com.example.amnyamai.ui.components.SoundBar
import com.example.amnyamai.ui.viewmodel.RecordingUiState
import com.example.amnyamai.ui.viewmodel.RecordingViewModel

private val RecRed = Color(0xFFE24B4A)

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

    when (val state = uiState) {
        is RecordingUiState.Idle -> IdleLayout(
            onStart = { permLauncher.launch(permissions) }
        )
        is RecordingUiState.Recording -> RecordingLayout(
            state = state,
            code = code,
            onStop = { vm.stopAndUpload() },
            onCopy = { clipboard.setText(AnnotatedString(code)) }
        )
        is RecordingUiState.Uploading -> UploadingLayout()
        is RecordingUiState.Error -> AmNyamErrorDialog(
            message = state.message,
            onDismiss = { vm.reset() },
            onRetry = { vm.stopAndUpload() }
        )
    }
}

// ── Ожидание начала записи ────────────────────────────────────────────────────

@Composable
private fun IdleLayout(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AmNyamGif(
            asset = "anmyam-listen-talk.gif",
            modifier = Modifier.size(260.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Нажми и Ам Ням начнёт слушать",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Начать запись встречи",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Идёт запись ───────────────────────────────────────────────────────────────

@Composable
private fun RecordingLayout(
    state: RecordingUiState.Recording,
    code: String,
    onStop: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        // Таймер + REC-индикатор
        RecIndicator(state.seconds)

        Spacer(Modifier.height(16.dp))

        // Гифка
        AmNyamGif(
            asset = "anmyam-listen-talk.gif",
            modifier = Modifier
                .size(240.dp)
                .weight(1f, fill = false)
        )

        Spacer(Modifier.height(12.dp))

        // Код для участников
        Text(
            "Код встречи: $code",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "нажми чтобы скопировать",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(bottom = 4.dp)
                .run {
                    // кликабельный текст
                    this
                }
        )

        Spacer(Modifier.height(20.dp))

        // Sound bar
        SoundBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Кнопка завершить
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RecRed)
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Завершить встречу",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Анализ ────────────────────────────────────────────────────────────────────

@Composable
private fun UploadingLayout() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AmNyamGif(
            asset = "amnyam_eat.gif",
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = Color(0xFF7DD444)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ам Ням переваривает встречу...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Извлекаем задачи и решения",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── REC-индикатор ─────────────────────────────────────────────────────────────

@Composable
private fun RecIndicator(seconds: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(RecRed)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "REC  ${formatTime(seconds)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = RecRed
        )
    }
}

private fun formatTime(s: Long) =
    if (s >= 3600) "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
