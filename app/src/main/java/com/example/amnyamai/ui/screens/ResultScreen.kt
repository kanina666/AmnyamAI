package com.example.amnyamai.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.amnyamai.data.model.Task
import com.example.amnyamai.ui.theme.AcceptGreen
import com.example.amnyamai.ui.theme.RejectRed
import com.example.amnyamai.ui.viewmodel.ResultUiState
import com.example.amnyamai.ui.viewmodel.ResultViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ResultScreen(meetingId: String, onDone: () -> Unit) {
    val vm: ResultViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(meetingId) { vm.load(meetingId) }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        when (val state = uiState) {
            is ResultUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(64.dp))
                    Spacer(Modifier.height(20.dp))
                    Text("Alice AI анализирует встречу...", style = MaterialTheme.typography.titleMedium)
                    Text("Извлекаем задачи и решения", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ResultUiState.Ready -> ReadyContent(state, vm)
            is ResultUiState.AllDone -> DoneContent(onDone)
            is ResultUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp))
            }
        }
    }
}

@Composable
private fun ReadyContent(state: ResultUiState.Ready, vm: ResultViewModel) {
    val task = state.tasks.getOrNull(state.currentIndex) ?: return
    val total = state.tasks.size
    val current = state.currentIndex + 1
    var showEdit by remember(task.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Progress
        Text(
            "Задача $current из $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "← Отклонить  ·  Принять →",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Summary (only first card)
        if (state.currentIndex == 0 && state.summary.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    state.summary, modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Swipeable card — stack effect: show next card behind
        Box(Modifier.weight(1f), Alignment.Center) {
            // Card behind (next task)
            state.tasks.getOrNull(state.currentIndex + 1)?.let { next ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = 0.93f; scaleY = 0.93f; translationY = 20f },
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Text(next.title, modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }

            // Draggable front card
            SwipeableTaskCard(
                task = task,
                onAccept = { vm.acceptTask(task) },
                onReject = { vm.rejectTask() },
                onEdit = { showEdit = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Manual buttons as fallback
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.rejectTask() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = RejectRed)
            ) { Text("✕  Отклонить") }
            Button(
                onClick = { vm.acceptTask(task) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen)
            ) { Text("✓  Принять") }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showEdit) {
        EditTaskDialog(
            task = task,
            index = state.currentIndex,
            onDismiss = { showEdit = false },
            onSave = { idx, title -> vm.updateTaskTitle(idx, title); showEdit = false }
        )
    }
}

@Composable
private fun SwipeableTaskCard(
    task: Task,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    val offsetX = remember(task.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val screenPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenPx * 0.38f

    val fraction = (offsetX.value / threshold).coerceIn(-1f, 1f)
    val rotation = fraction * 12f
    val acceptAlpha = fraction.coerceAtLeast(0f)
    val rejectAlpha = (-fraction).coerceAtLeast(0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = rotation }
            .pointerInput(task.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX.value > threshold -> {
                                    offsetX.animateTo(screenPx * 1.5f, spring())
                                    onAccept()
                                }
                                offsetX.value < -threshold -> {
                                    offsetX.animateTo(-screenPx * 1.5f, spring())
                                    onReject()
                                }
                                else -> offsetX.animateTo(0f)
                            }
                        }
                    },
                    onHorizontalDrag = { _, drag ->
                        scope.launch { offsetX.snapTo(offsetX.value + drag) }
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box {
            Column(Modifier.padding(24.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👤", fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(task.assignee, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                if (!task.deadline.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 16.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(task.deadline, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onEdit) { Text("✏️ Редактировать") }
            }

            // Accept stamp
            if (acceptAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .border(3.dp, AcceptGreen.copy(alpha = acceptAlpha), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .rotate(-15f)
                ) {
                    Text(
                        "ПРИНЯТЬ ✓",
                        color = AcceptGreen.copy(alpha = acceptAlpha),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
            }

            // Reject stamp
            if (rejectAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .border(3.dp, RejectRed.copy(alpha = rejectAlpha), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .rotate(15f)
                ) {
                    Text(
                        "✕ ПРОПУСТИТЬ",
                        color = RejectRed.copy(alpha = rejectAlpha),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneContent(onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text("🎉", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("Готово!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Принятые задачи добавлены в Google Календарь",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("На главную")
        }
    }
}

@Composable
private fun EditTaskDialog(task: Task, index: Int, onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать задачу") },
        text = {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Название задачи") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(index, title) }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
