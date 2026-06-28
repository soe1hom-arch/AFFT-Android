package com.afft.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.FilePickerCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

// Warna terminal
private val TerminalBg = Color(0xFF0D1117)
private val TerminalBorder = Color(0xFF2D2D2D)
private val TerminalInfo = Color(0xFF44BBFF)
private val TerminalOk = Color(0xFF00FF00)
private val TerminalError = Color(0xFFFF4444)
private val TerminalWarning = Color(0xFFFFAA00)
private val TerminalPrompt = Color(0xFF888888)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentCyan = Color(0xFF00BCD4)

@Composable
fun PayloadScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    DisposableEffect(Unit) {
        onDispose { scope.cancel() }
    }
    
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedInputFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val progressPercent by afftService.progressPercent.collectAsState()
    val currentPartition by afftService.currentPartition.collectAsState()

    // Auto-detect file dari input/
    LaunchedEffect(Unit) {
        val latestFile = afftService.getLatestInputFile()
        if (latestFile != null) {
            selectedInputFile = latestFile
            selectedFileName = latestFile.name
            selectedUri = null
        }
    }
    
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            errorMessage = null
            try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            selectedFileName = c.getString(nameIdx)
                        }
                    }
                } ?: run {
                    selectedFileName = it.lastPathSegment ?: "Unknown file"
                }
            } catch (e: Exception) {
                selectedFileName = it.lastPathSegment ?: "Unknown file"
            }
            scope.launch {
                selectedInputFile = afftService.copyPickedFileToInput(it)
            }
        } ?: run {
            errorMessage = "Tidak ada file dipilih"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            "Extract payload.bin",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Extract OTA firmware payload.bin files",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // File picker
        FilePickerCard(
            title = "Pilih payload.bin",
            selectedUri = selectedUri,
            selectedFileName = selectedFileName,
            onClick = {
                errorMessage = null
                filePicker.launch(arrayOf("application/octet-stream", "*/*"))
            }
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tombol Extract
        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    scope.launch {
                        errorMessage = null
                        val result = afftService.extractPayload(file)
                        if (!result.ok) {
                            errorMessage = result.message
                        }
                    }
                } ?: selectedUri?.let { uri ->
                    scope.launch {
                        errorMessage = null
                        val result = afftService.extractPayload(uri)
                        if (!result.ok) {
                            errorMessage = result.message
                        }
                    }
                }
            },
            enabled = (selectedUri != null || selectedInputFile != null) && !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ArrowForward, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract payload.bin")
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(12.dp))
            // Terminal-style view
            TerminalView(
                logs = logs,
                progressPercent = progressPercent,
                currentPartition = currentPartition
            )
        }
    }
}

@Composable
private fun TerminalView(
    logs: List<String>,
    progressPercent: Int,
    currentPartition: String
) {
    val scrollState = rememberScrollState()
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(TerminalBg, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Title bar ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBorder, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Window buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF4444), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFAA00), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), RoundedCornerShape(4.dp)))
                }
                // Title
                Text(
                    text = "AFFT — Terminal",
                    color = TerminalPrompt,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ── Log content ──────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Command line
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = TerminalPrompt)) { append("$ ") }
                        withStyle(SpanStyle(color = AccentGreen)) { append("./afft") }
                        append(" --extract payload.bin")
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TerminalPrompt
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Log entries (tampilkan hanya yang relevan, filter progress bar noise)
                val displayLogs = logs
                    .filterNot { it.startsWith("\u001b") || it.contains(Regex("^\\[\\d+A\\[J$")) }
                    .takeLast(30)

                displayLogs.forEach { line ->
                    TerminalLogLine(line)
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Current partition info
                if (currentPartition.isNotEmpty()) {
                    TerminalLogLine("[OK] Extracting partition: $currentPartition")
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // ── Progress bar (battery style) ─────────────────
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
private fun TerminalLogLine(text: String) {
    val color = when {
        text.startsWith("[ERROR]") || text.startsWith("[FAIL]") -> TerminalError
        text.startsWith("[OK]") -> TerminalOk
        text.startsWith("[WARN]") -> TerminalWarning
        text.startsWith("[INFO]") || text.startsWith("[DEBUG]") -> TerminalInfo
        else -> Color(0xFFCCCCCC)
    }
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
}
