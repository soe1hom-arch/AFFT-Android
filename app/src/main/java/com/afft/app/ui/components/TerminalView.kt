package com.afft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.ui.theme.TerminalBackground
import com.afft.app.ui.theme.TerminalError
import com.afft.app.ui.theme.TerminalInfo
import com.afft.app.ui.theme.TerminalText

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
        Text(
            text = logs.joinToString("\n"),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TerminalText,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun ColoredLogLine(text: String, modifier: Modifier = Modifier) {
    val color = when {
        text.startsWith("[ERROR]") || text.startsWith("[FAIL]") -> TerminalError
        text.startsWith("[INFO]") || text.startsWith("[") -> TerminalInfo
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
