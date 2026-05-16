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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.amnyamai.ui.components.AmNyamErrorDialog
import com.example.amnyamai.ui.components.AmNyamGif
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
            is ResultUiState.Loading -> LoadingContent()
            is ResultUiState.Ready -> SwipeContent(state, vm)
            is ResultUiState.AllDone -> AllDoneContent(state, vm, onDone)
            is ResultUiState.Error -> AmNyamErrorDialog(
                message = state.message,
                onDismiss = onDone,
                onRetry = { vm.reload(meetingId) }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            color = Color(0xFF7DD444)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Ам Ням анализирует встречу...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Извлекаем задачи и решения",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwipeContent(state: ResultUiState.Ready, vm: ResultViewModel) {
    val task = state.tasks.getOrNull(state.currentIndex) ?: return
    var showEdit by remember(task.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "Задача ${state.currentIndex + 1} из ${state.tasks.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "← Отклонить  ·  Принять →",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.currentIndex == 0 && state.summary.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    state.summary,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f), Alignment.Center) {
            state.tasks.getOrNull(state.currentIndex + 1)?.let { next ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = 0.93f; scaleY = 0.93f; translationY = 20f },
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Text(
                        next.title,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f)
                    )
                }
            }
            SwipeableTaskCard(
                task = task,
                onAccept = { vm.acceptTask(task) },
                onReject = { vm.rejectTask() },
                onEdit = { showEdit = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.rejectTask() },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RejectRed),
                shape = RoundedCornerShape(12.dp)
            ) { Text("✕  Отклонить", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { vm.acceptTask(task) },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("✓  Принять", fontWeight = FontWeight.SemiBold) }
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
private fun AllDoneContent(
    state: ResultUiState.AllDone,
    vm: ResultViewModel,
    onDone: () -> Unit
) {
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            AmNyamGif(
                asset = "amnyam_eat.gif",
                modifier = Modifier.size(90.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Встреча\nзавершена!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.summary.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    state.summary,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.acceptedTasks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Принятые задачи (${state.acceptedTasks.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            state.acceptedTasks.forEach { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = AcceptGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!task.deadline.isNullOrBlank()) {
                                Text(
                                    task.deadline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (state.acceptedTasks.isNotEmpty()) {
            Button(
                onClick = { vm.saveToCalendar() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Сохранить в Календаре Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        TextButton(onClick = onDone) { Text("На главную") }
        Spacer(Modifier.height(24.dp))
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
    val acceptAlpha = fraction.coerceAtLeast(0f)
    val rejectAlpha = (-fraction).coerceAtLeast(0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = fraction * 10f }
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
        shape = RoundedCornerShape(16.dp),
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
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👤", fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        task.assignee,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!task.deadline.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 16.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            task.deadline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onEdit) { Text("✏️ Редактировать") }
            }

            if (acceptAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .border(3.dp, AcceptGreen.copy(alpha = acceptAlpha), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .rotate(-15f)
                ) {
                    Text(
                        "ПРИНЯТЬ ✓",
                        color = AcceptGreen.copy(alpha = acceptAlpha),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }
            }

            if (rejectAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .border(3.dp, RejectRed.copy(alpha = rejectAlpha), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .rotate(15f)
                ) {
                    Text(
                        "✕ ПРОПУСТИТЬ",
                        color = RejectRed.copy(alpha = rejectAlpha),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }
            }
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
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(index, title) }, enabled = title.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
