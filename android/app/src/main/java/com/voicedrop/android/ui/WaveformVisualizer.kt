package com.voicedrop.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voicedrop.android.ui.theme.RecordRed
import com.voicedrop.android.ui.theme.VoiceDropPurple
import com.voicedrop.android.ui.theme.VoiceDropPurpleLight
import com.voicedrop.android.ui.theme.WaveformIdle
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    isRecording: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_idle")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_phase"
    )

    // Smooth amplitude transition
    val animatedAmp by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "amp_spring"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val totalSpacing = width / barCount
        val barWidth = totalSpacing * 0.55f
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until barCount) {
            val centerOffset = (i - barCount / 2f) / (barCount / 2f)
            val bellFactor = (1.0f - centerOffset * centerOffset).coerceIn(0.2f, 1.0f)

            val barHeight = if (isRecording) {
                // Modulate based on live amplitude + per-bar frequency simulator
                val barFreq = sin((i * 0.45f) + (animatedAmp * 8f))
                val dynamicFactor = (animatedAmp * 0.8f + (barFreq + 1f) * 0.15f) * bellFactor
                (height * dynamicFactor).coerceIn(4.dp.toPx(), height * 0.95f)
            } else {
                // Gentle idle wave breathing
                val idleWave = (sin(idlePhase + (i * 0.35f)) + 1f) * 0.5f
                (height * (0.12f + idleWave * 0.15f) * bellFactor).coerceIn(4.dp.toPx(), height * 0.4f)
            }

            val x = i * totalSpacing + (totalSpacing - barWidth) / 2f
            val y = (height - barHeight) / 2f

            val brush = if (isRecording) {
                Brush.verticalGradient(
                    colors = listOf(
                        RecordRed,
                        VoiceDropPurpleLight,
                        VoiceDropPurple
                    ),
                    startY = y,
                    endY = y + barHeight
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        WaveformIdle.copy(alpha = 0.8f),
                        WaveformIdle.copy(alpha = 0.4f)
                    ),
                    startY = y,
                    endY = y + barHeight
                )
            }

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
