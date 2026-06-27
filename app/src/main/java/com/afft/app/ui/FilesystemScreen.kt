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
import com.afft.app.ui.components.ProcessingOverlay
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.launch
import java.io.File

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilesystemScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedInputFile by remember { mutableStateOf<File?>(null) }
    var availableDirs by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDir by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

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
            try {
                context.contentResolver.query(it, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) selectedFileName = c.getString(nameIdx)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FilesystemScreen", "Query failed: ${e.message}")
                selectedFileName = it.lastPathSegment
            }
            // Auto-copy picked file to input/ directory dan simpan referensi lokal
            scope.launch {
                selectedInputFile = afftService.copyPickedFileToInput(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        availableDirs = afftService.listContentsDirs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Filesystem Operations",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Extract & repack EROFS/ext4 filesystem images",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Extract section
        Text("Extract Filesystem", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        FilePickerCard(
            title = "Pilih filesystem .img",
            selectedUri = selectedUri,
            selectedFileName = selectedFileName,
            onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    scope.launch {
                        val result = afftService.extractFilesystem(file)
                        if (result.ok) {
                            availableDirs = afftService.listContentsDirs()
                        }
                    }
                } ?: selectedUri?.let { uri ->
                    scope.launch {
                        val result = afftService.extractFilesystem(uri)
                        if (result.ok) {
                            availableDirs = afftService.listContentsDirs()
                        }
                    }
                }
            },
            enabled = (selectedUri != null || selectedInputFile != null) && !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Unarchive, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Filesystem")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Repack section
        Text("Repack Filesystem", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (availableDirs.isEmpty()) {
            Text(
                "Belum ada konten untuk direpack. Extract filesystem terlebih dahulu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedDir ?: "Pilih direktori",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableDirs.forEach { dir ->
                        DropdownMenuItem(
                            text = { Text(dir) },
                            onClick = {
                                selectedDir = dir
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    selectedDir?.let { dir ->
                        scope.launch {
                            val result = afftService.repackFilesystem(dir)
                        }
                    }
                },
                enabled = selectedDir != null && !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ArrowForward, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Repack $selectedDir")
            }
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true)
        }
    }
}
