package com.afft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.ui.theme.TerminalBackground

@Composable
fun TerminalView(
    logs: List<String>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 400
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .background(TerminalBackground)
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
}

@Composable
fun ColoredLogLine(text: String, modifier: Modifier = Modifier) {
    val color = when {
        text.startsWith("[ERROR]") || text.startsWith("[FAIL]") -> com.afft.app.ui.theme.TerminalError
        text.startsWith("[OK]") -> com.afft.app.ui.theme.Green500
        text.startsWith("[INFO]") || text.startsWith("=== ") -> com.afft.app.ui.theme.TerminalInfo
        else -> com.afft.app.ui.theme.TerminalText
    }

    androidx.compose.material3.Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = color,
        modifier = modifier,
        lineHeight = 16.sp
    )
}