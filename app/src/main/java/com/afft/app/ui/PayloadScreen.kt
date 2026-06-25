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
fun PayloadScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    // Fallback: use last path segment
                    selectedFileName = it.lastPathSegment ?: "Unknown file"
                }
            } catch (e: Exception) {
                selectedFileName = it.lastPathSegment ?: "Unknown file"
                android.util.Log.w("PayloadScreen", "File query error: ${e.message}")
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

        Button(
            onClick = {
                selectedUri?.let { uri ->
                    scope.launch {
                        errorMessage = null
                        val result = afftService.extractPayload(uri)
                        if (!result.ok) {
                            errorMessage = result.message
                        }
                    }
                }
            },
            enabled = selectedUri != null && !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ArrowForward, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract payload.bin")
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true, message = "Extracting payload.bin...")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Output:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        TerminalView(
            logs = logs,
            modifier = Modifier.fillMaxWidth(),
            maxHeight = 350
        )
    }
}
