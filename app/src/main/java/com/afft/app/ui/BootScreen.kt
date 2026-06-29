package com.afft.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.model.BootImageType
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.FilePickerCard
import com.afft.app.ui.components.FileSourceSelectorDialog
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.WorkspaceFileBrowserDialog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedBootType by remember { mutableStateOf<BootImageType?>(null) }
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
                        if (nameIdx >= 0) {
                            selectedFileName = c.getString(nameIdx)
                            selectedBootType = BootImageType.entries.find { type ->
                                type.fileName == c.getString(nameIdx)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BootScreen", "Query failed: ${e.message}")
                selectedFileName = it.lastPathSegment
            }
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
            title = "Pilih boot image",
            currentDir = browseDir,
            onNavigate = { dir -> browseDir = dir },
            onFileSelected = { file ->
                selectedInputFile = file
                selectedFileName = file.name
                selectedUri = null
                selectedBootType = BootImageType.entries.find { type ->
                    type.fileName == file.name
                }
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
            "Boot Family Operations",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Unpack & repack boot images (boot, vendor_boot, init_boot, dtbo, recovery, vbmeta)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Pilih tipe boot:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BootImageType.entries.take(4).forEach { type ->
                FilterChip(
                    selected = selectedBootType == type,
                    onClick = { selectedBootType = type },
                    label = { Text(type.displayName) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BootImageType.entries.drop(4).forEach { type ->
                FilterChip(
                    selected = selectedBootType == type,
                    onClick = { selectedBootType = type },
                    label = { Text(type.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FilePickerCard(
            title = "Pilih file boot image",
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
                        selectedBootType?.let { type ->
                            scope.launch {
                                afftService.unpackBoot(file, type.fileName)
                            }
                        }
                    } ?: selectedUri?.let { uri ->
                        selectedBootType?.let { type ->
                            scope.launch {
                                afftService.unpackBoot(uri, type.fileName)
                            }
                        }
                    }
                },
                enabled = (selectedUri != null || selectedInputFile != null) && selectedBootType != null && !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Unarchive, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Unpack")
            }

            Button(
                onClick = {
                    selectedBootType?.let { type ->
                        scope.launch {
                            afftService.repackBoot(type.fileName)
                        }
                    }
                },
                enabled = selectedBootType != null && !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowForward, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Repack")
            }
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true, message = "Processing boot image...")
        }
    }
}
