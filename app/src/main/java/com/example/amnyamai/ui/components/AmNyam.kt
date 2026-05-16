package com.example.amnyamai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

private val BodyGreen    = Color(0xFF7DD444)
private val OutlineGreen = Color(0xFF2D6E00)

/**
 * Ам Ням — рисуется полностью через Canvas, без внешних ресурсов.
 *
 * @param eyeLookX       -1f = влево, 0f = центр, 1f = вправо
 * @param eyeLookY       -1f = вверх,  0f = центр, 1f = вниз
 * @param isBlinking     true = глаза закрыты
 * @param mouthOpenFraction  0f = улыбка, 1f = широко открыт
 *
 * Для анимации тела (покачивание, прыжок) применяй graphicsLayer { translationX/Y }
 * снаружи как модификатор — это эффективнее и не перерисовывает Canvas.
 */
@Composable
fun AmNyam(
    modifier: Modifier = Modifier,
    eyeLookX: Float = 0f,
    eyeLookY: Float = 0f,
    isBlinking: Boolean = false,
    mouthOpenFraction: Float = 0f
) {
    Canvas(modifier = modifier) {
        val r  = minOf(size.width, size.height) * 0.40f
        val cx = size.width / 2f
        val cy = size.height * 0.53f   // чуть ниже центра — место для антенны

        drawAntenna(cx, cy, r)
        drawBody(cx, cy, r)
        drawEyes(cx, cy, r, eyeLookX, eyeLookY, isBlinking)
        drawMouth(cx, cy, r, mouthOpenFraction)
    }
}

// ── Антенна ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawAntenna(cx: Float, cy: Float, r: Float) {
    val tipX = cx - r * 0.15f
    val tipY = cy - r * 1.36f
    drawLine(
        color = OutlineGreen,
        start = Offset(cx + r * 0.07f, cy - r * 0.95f),
        end   = Offset(tipX, tipY),
        strokeWidth = r * 0.07f,
        cap = StrokeCap.Round
    )
    drawCircle(color = OutlineGreen, radius = r * 0.14f, center = Offset(tipX, tipY))
    drawCircle(color = BodyGreen,    radius = r * 0.09f, center = Offset(tipX, tipY))
}

// ── Тело ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBody(cx: Float, cy: Float, r: Float) {
    drawCircle(color = BodyGreen, radius = r, center = Offset(cx, cy))
    drawCircle(
        color = OutlineGreen,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.048f)
    )
}

// ── Глаза ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawEyes(
    cx: Float, cy: Float, r: Float,
    lookX: Float, lookY: Float,
    isBlinking: Boolean
) {
    val eyeR   = r * 0.215f
    val pupilR = r * 0.118f
    val eyeY   = cy - r * 0.10f

    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.275f

        // Белок
        drawCircle(color = Color.White, radius = eyeR, center = Offset(ex, eyeY))
        drawCircle(
            color = OutlineGreen, radius = eyeR,
            center = Offset(ex, eyeY),
            style = Stroke(width = r * 0.025f)
        )

        if (isBlinking) {
            // Моргание — горизонтальная линия
            drawLine(
                color = OutlineGreen,
                start = Offset(ex - eyeR * 0.78f, eyeY),
                end   = Offset(ex + eyeR * 0.78f, eyeY),
                strokeWidth = r * 0.055f,
                cap = StrokeCap.Round
            )
        } else {
            val px = ex + lookX * eyeR * 0.40f
            val py = eyeY + lookY * eyeR * 0.35f
            // Зрачок
            drawCircle(color = Color.Black, radius = pupilR, center = Offset(px, py))
            // Блик
            drawCircle(
                color = Color.White,
                radius = pupilR * 0.33f,
                center = Offset(px - pupilR * 0.28f, py - pupilR * 0.28f)
            )
        }
    }
}

// ── Рот ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawMouth(cx: Float, cy: Float, r: Float, openFraction: Float) {
    if (openFraction < 0.05f) {
        // Закрытый — улыбка дугой
        val path = Path().apply {
            moveTo(cx - r * 0.27f, cy + r * 0.24f)
            quadraticTo(cx, cy + r * 0.47f, cx + r * 0.27f, cy + r * 0.24f)
        }
        drawPath(path, OutlineGreen, style = Stroke(width = r * 0.068f, cap = StrokeCap.Round))
    } else {
        // Открытый — овал с зубами
        val mW  = r * 0.56f
        val mH  = r * 0.40f * openFraction
        val mCy = cy + r * 0.32f
        val left = cx - mW / 2f
        val top  = mCy - mH / 2f

        // Тёмное нутро
        drawOval(
            color = OutlineGreen,
            topLeft = Offset(left, top),
            size = Size(mW, mH)
        )

        // Зубы — появляются при openFraction > 0.35
        if (openFraction > 0.35f) {
            val alpha   = ((openFraction - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val count   = 4
            val toothW  = mW / count
            val toothH  = minOf(mH * 0.43f, r * 0.115f)
            for (i in 0 until count) {
                val tx = left + i * toothW + r * 0.005f
                drawRect(
                    color = Color.White.copy(alpha = alpha),
                    topLeft = Offset(tx, top + r * 0.008f),
                    size = Size(toothW - r * 0.018f, toothH)
                )
            }
        }

        // Обводка рта
        drawOval(
            color = OutlineGreen,
            topLeft = Offset(left, top),
            size = Size(mW, mH),
            style = Stroke(width = r * 0.038f)
        )
    }
}
