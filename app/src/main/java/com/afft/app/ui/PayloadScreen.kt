/*
 * Copyright (c) 2026 Wandi (soe1hom-arch). All rights reserved.
 */

package com.afft.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.FilePickerCard
import com.afft.app.ui.components.FileSourceSelectorDialog
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.WorkspaceFileBrowserDialog
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PayloadScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedInputFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val progressPercent by afftService.progressPercent.collectAsState()
    val currentPartition by afftService.currentPartition.collectAsState()

    // Dialogs state
    var showSourceSelector by remember { mutableStateOf(false) }
    var showWorkspaceBrowser by remember { mutableStateOf(false) }
    var browseDir by remember { mutableStateOf(afftService.getInputDir()) }

    // Auto-detect file dari input/ saat screen dimuat (untuk menghindari copy ulang)
    LaunchedEffect(Unit) {
        val latestFile = afftService.getLatestInputFile()
        if (latestFile != null) {
            selectedInputFile = latestFile
            selectedFileName = latestFile.name
            selectedUri = null
        }
    }

    // System file picker (dari penyimpanan perangkat)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedInputFile = null
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
                android.util.Log.w("PayloadScreen", "File query error: ${e.message}")
            }
            // Auto-copy picked file to input/ directory dan simpan referensi lokal
            scope.launch {
                try {
                    selectedInputFile = afftService.copyPickedFileToInput(it)
                } catch (_: CancellationException) { }
            }
        } ?: run {
            errorMessage = "Tidak ada file dipilih"
        }
    }

    // ── Source Selector Dialog ──
    if (showSourceSelector) {
        FileSourceSelectorDialog(
            onPickFromStorage = {
                filePicker.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onPickFromWorkspace = {
                browseDir = afftService.getInputDir()
                showWorkspaceBrowser = true
            },
            onDismiss = { showSourceSelector = false }
        )
    }

    // ── Workspace Browser Dialog ──
    if (showWorkspaceBrowser) {
        WorkspaceFileBrowserDialog(
            title = "Pilih payload.bin",
            currentDir = browseDir,
            onNavigate = { dir -> browseDir = dir },
            onFileSelected = { file ->
                selectedInputFile = file
                selectedFileName = file.name
                selectedUri = null
                showWorkspaceBrowser = false
            },
            onDismiss = { showWorkspaceBrowser = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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

        FilePickerCard(
            title = "Pilih payload.bin",
            selectedUri = if (selectedInputFile != null) null else selectedUri,
            selectedFileName = selectedFileName,
            onClick = {
                errorMessage = null
                showSourceSelector = true
            }
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    scope.launch {
                        try {
                            errorMessage = null
                            val result = afftService.extractPayload(file)
                            if (!result.ok) {
                                errorMessage = result.message
                            }
                        } catch (_: CancellationException) { }
                    }
                } ?: selectedUri?.let { uri ->
                    scope.launch {
                        try {
                            errorMessage = null
                            val result = afftService.extractPayload(uri)
                            if (!result.ok) {
                                errorMessage = result.message
                            }
                        } catch (_: CancellationException) { }
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
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(
                isRunning = true,
                message = if (currentPartition.isNotEmpty()) "Extracting: $currentPartition" else "Extracting payload.bin...",
                progressPercent = if (progressPercent > 0) progressPercent.toFloat() else null
            )
        }
    }
}
