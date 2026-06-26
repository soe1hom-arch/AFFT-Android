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
import com.afft.app.ui.components.TerminalView
import kotlinx.coroutines.launch

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

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
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
        }
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
            selectedUri = selectedUri,
            selectedFileName = selectedFileName,
            onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        selectedBootType?.let { type ->
                            scope.launch {
                                afftService.unpackBoot(uri, type.fileName)
                            }
                        }
                    }
                },
                enabled = selectedUri != null && selectedBootType != null && !isRunning,
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Output:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        TerminalView(
            logs = logs,
            modifier = Modifier.fillMaxWidth(),
            maxHeight = 300
        )
    }
}
