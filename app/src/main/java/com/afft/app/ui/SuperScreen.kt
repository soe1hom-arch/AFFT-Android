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
import com.afft.app.ui.components.TerminalView
import kotlinx.coroutines.launch

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

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) selectedFileName = c.getString(nameIdx)
                }
            }
        }
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
                        scope.launch {
                            val result = afftService.unpackSuper(uri)
                            if (result.ok) {
                                repackResult = "Unpack selesai. Partisi di temp/img/"
                            }
                        }
                    }
                },
                enabled = selectedUri != null && !isRunning,
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
                            afftService.copyResultToDownload(result.outputPath, "super_repack.img")
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
