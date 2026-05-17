package com.example.amnyamai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

private val BarGreen = Color(0xFF7DD444)

@Composable
fun SoundBar(modifier: Modifier = Modifier) {
    val bars = 28
    val phases = remember { List(bars) { i -> i * (PI.toFloat() / (bars / 2f)) } }

    val transition = rememberInfiniteTransition(label = "soundbar")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "time"
    )

    Canvas(modifier = modifier) {
        val gap = size.width * 0.012f
        val barW = (size.width - gap * (bars - 1)) / bars
        val maxH = size.height
        val minH = maxH * 0.08f

        for (i in 0 until bars) {
            val wave = (sin(time + phases[i]).toFloat() + 1f) / 2f
            val h = minH + (maxH - minH) * wave
            val x = i * (barW + gap)
            val y = (maxH - h) / 2f
            drawRoundRect(
                color = BarGreen,
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
