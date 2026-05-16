package com.example.amnyamai.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.amnyamai.ui.components.AmNyam
import com.example.amnyamai.ui.components.AmNyamErrorDialog
import com.example.amnyamai.ui.viewmodel.RecordingUiState
import com.example.amnyamai.ui.viewmodel.RecordingViewModel
import com.example.amnyamai.ui.viewmodel.TranscriptLine

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
        is RecordingUiState.Recording -> ActiveLayout(
            state   = state,
            code    = code,
            onStop  = { vm.stopAndUpload() },
            onCopy  = { clipboard.setText(AnnotatedString(code)) }
        )
        is RecordingUiState.Uploading -> UploadingLayout()
        is RecordingUiState.Error -> AmNyamErrorDialog(
            message = state.message,
            onDismiss = { vm.reset() },
            onRetry = { vm.stopAndUpload() }
        )
    }
}

// ── Экран ожидания (до начала записи) ────────────────────────────────────────

@Composable
private fun IdleLayout(onStart: () -> Unit) {
    // Глаза смотрят чуть вниз — на кнопку
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AmNyam(
            modifier   = Modifier.size(220.dp),
            eyeLookY   = 0.7f,
            mouthOpenFraction = 0f
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Нажми и Ам Ням начнёт слушать",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(64.dp),
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

// ── Активная запись ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveLayout(
    state: RecordingUiState.Recording,
    code: String,
    onStop: () -> Unit,
    onCopy: () -> Unit
) {
    // Рот открывается
    val mouthOpen by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "mouth"
    )

    // Прыжки вверх-вниз
    val jumpTransition = rememberInfiniteTransition(label = "jump")
    val jumpOffset by jumpTransition.animateFloat(
        initialValue = 0f,
        targetValue  = -36f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jump"
    )

    // Глаза смотрят чуть влево-вправо во время записи
    val eyeTransition = rememberInfiniteTransition(label = "eye_rec")
    val eyeLookX by eyeTransition.animateFloat(
        initialValue = -0.5f,
        targetValue  =  0.5f,
        animationSpec = infiniteRepeatable(
            tween(1800), RepeatMode.Reverse
        ),
        label = "eyeX_rec"
    )

    Scaffold(
        topBar = { RecStatusBar(state.seconds) },
        bottomBar = {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RecRed)
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(Color.White, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Завершить встречу",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Ам Ням прыгает
            item {
                AmNyam(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer { translationY = jumpOffset },
                    eyeLookX = eyeLookX,
                    mouthOpenFraction = mouthOpen
                )
            }

            // Таймер
            item {
                Text(
                    formatTime(state.seconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Идёт запись",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RecRed,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Код для участников
            item {
                Card(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Код для участников",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            code,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Нажмите чтобы скопировать",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Блок «Уже поймал»
            if (state.transcriptLines.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Уже поймал",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("🎯", fontSize = 18.sp)
                    }
                }
                items(state.transcriptLines.reversed()) { line ->
                    CaughtCard(line)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Анализ после записи ───────────────────────────────────────────────────────

@Composable
private fun UploadingLayout() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AmNyam(
                modifier = Modifier.size(180.dp),
                mouthOpenFraction = 0.6f
            )
            Spacer(Modifier.height(20.dp))
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
                color = Color(0xFF4A6330)
            )
        }
    }
}

// ── REC-статусбар ─────────────────────────────────────────────────────────────

@Composable
private fun RecStatusBar(seconds: Long) {
    val pulse = rememberInfiniteTransition(label = "rec_dot")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "dot"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RecRed)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "REC  ${formatTime(seconds)}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ── Карточка транскрипта ──────────────────────────────────────────────────────

@Composable
private fun CaughtCard(line: TranscriptLine) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                line.speaker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTime(s: Long) =
    if (s >= 3600) "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
