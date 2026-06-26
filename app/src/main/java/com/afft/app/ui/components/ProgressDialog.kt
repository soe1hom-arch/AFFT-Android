package com.afft.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingOverlay(
    isRunning: Boolean,
    message: String = "Processing...",
    modifier: Modifier = Modifier
) {
    if (isRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "spinner")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )
        val arcProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = 600),
                repeatMode = RepeatMode.Restart
            ),
            label = "arcLen"
        )

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Custom Canvas spinner (avoids Material3 CircularProgressIndicator internal keyframes crash)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp)
                ) {
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val sweepAngle = 60f + arcProgress * 240f
                        drawArc(
                            color = MaterialTheme.colorScheme.primary,
                            startAngle = angle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Use a fixed progress value to avoid indeterminate animation internal keyframes crash
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
