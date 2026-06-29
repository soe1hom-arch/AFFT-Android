/*
 * Copyright (c) 2026 Wandi (soe1hom-arch). All rights reserved.
 */

package com.afft.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun SuperScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var repackResult by remember { mutableStateOf<String?>(null) }
    var selectedInputFile by remember { mutableStateOf<File?>(null) }

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

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedInputFile = null
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) selectedFileName = c.getString(nameIdx)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SuperScreen", "Query failed: ${e.message}")
                selectedFileName = it.lastPathSegment
            }
            // Auto-copy picked file to input/ directory dan simpan referensi lokal
            scope.launch {
                selectedInputFile = afftService.copyPickedFileToInput(it)
            }
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
            title = "Pilih super.img",
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
            "Super Image Operations",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Unpack & repack super.img logical partitions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        FilePickerCard(
            title = "Pilih super.img",
            selectedUri = if (selectedInputFile != null) null else selectedUri,
            selectedFileName = selectedFileName,
            onClick = { showSourceSelector = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    selectedInputFile?.let { file ->
                        scope.launch {
                            val result = afftService.unpackSuper(file)
                            if (result.ok) {
                                repackResult = "Unpack selesai. Partisi di temp/img/"
                            }
                        }
                    } ?: selectedUri?.let { uri ->
                        scope.launch {
                            val result = afftService.unpackSuper(uri)
                            if (result.ok) {
                                repackResult = "Unpack selesai. Partisi di temp/img/"
                            }
                        }
                    }
                },
                enabled = (selectedUri != null || selectedInputFile != null) && !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Unarchive, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Unpack")
            }

            Button(
                onClick = {
                    scope.launch {
                        val result = afftService.repackSuper()
                        repackResult = if (result.ok) {
                            "Repack selesai: temp/repacked/super_repack.img"
                        } else {
                            "Repack gagal"
                        }
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowForward, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Repack")
            }
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true)
        }

        repackResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
    }
}
