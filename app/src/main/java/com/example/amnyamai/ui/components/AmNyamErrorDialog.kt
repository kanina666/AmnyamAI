package com.example.amnyamai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AmNyamErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AmNyamGif(
                    asset = "sad-amnyam.gif",
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ам Ням умер",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            if (message.isNotBlank()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (onRetry != null) {
                Button(
                    onClick = { onRetry(); onDismiss() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Попробовать ещё раз", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Понятно", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (onRetry != null) {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}
