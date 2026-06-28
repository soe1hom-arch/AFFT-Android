package com.afft.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.ui.theme.*

// Warna terminal tambahan
private val AccentGreen = Color(0xFF4CAF50)
private val AccentCyan = Color(0xFF00BCD4)
private val TerminalBg = Color(0xFF0D1117)
private val TerminalBorder = Color(0xFF2D2D2D)

@Composable
fun TerminalView(
    logs: List<String>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 400,
    isRunning: Boolean = false,
    progressPercent: Int = 0,
    currentPartition: String = ""
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        // ── Log area ─────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalBg)
                .verticalScroll(scrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(8.dp)
        ) {
            Column {
                logs.forEach { line ->
                    ColoredLogLine(text = line)
                }
            }
        }

        // ── Progress bar (battery style) — only during payload extraction
        if (isRunning && (progressPercent > 0 || currentPartition.isNotEmpty())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBorder.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    // Label
                    Text(
                        text = "Progress: ${progressPercent}% — ${if (currentPartition.isNotEmpty()) currentPartition else "Initializing..."}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(Color(0xFF0A0A0A), RoundedCornerShape(4.dp))
                    ) {
                        // Filled portion with gradient effect
                        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                            val barWidth = size.width * (progressPercent / 100f)
                            if (barWidth > 0) {
                                drawRoundRect(
                                    color = AccentGreen,
                                    topLeft = Offset.Zero,
                                    size = Size(barWidth, size.height),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                                // Cyan overlay on the right edge for gradient illusion
                                if (barWidth > 10.dp.toPx()) {
                                    drawRoundRect(
                                        color = AccentCyan.copy(alpha = 0.5f),
                                        topLeft = Offset(barWidth - 20.dp.toPx(), 0f),
                                        size = Size(20.dp.toPx(), size.height),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                }
                            }
                        }
                        // Percentage text in the middle
                        Text(
                            text = "${progressPercent}%",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColoredLogLine(text: String, modifier: Modifier = Modifier) {
    val color = when {
        text.startsWith("[ERROR]") || text.startsWith("[FAIL]") -> TerminalError
        text.startsWith("[OK]") -> Green500
        text.startsWith("[WARN]") -> Yellow500
        text.startsWith("[INFO]") || text.startsWith("=== ") -> TerminalInfo
        else -> TerminalText
    }

    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = color,
        modifier = modifier,
        lineHeight = 16.sp
    )
}
