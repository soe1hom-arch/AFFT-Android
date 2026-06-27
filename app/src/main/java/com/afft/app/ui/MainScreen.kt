package com.afft.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.TerminalView
import com.afft.app.ui.FileManagerScreen
import com.afft.app.ui.theme.*
import com.afft.app.util.BinaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(afftService: AFFTService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf("home") }
    var debugMode by remember { mutableStateOf(false) }
    val logs by afftService.logs.collectAsState()
    val isRunning by afftService.isRunning.collectAsState()
    val progressMessage by afftService.progressMessage.collectAsState()
    var binariesReady by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var binaryStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            BinaryManager.deployBinaries(context)
        }
        binariesReady = result.isSuccess
        binaryStatus = BinaryManager.verifyBinaries(context)
        if (!binariesReady) {
            Toast.makeText(context,
                "Failed to deploy binaries: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AFFT v2.0.2",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (debugMode) {
                        Text(
                            "DEBUG",
                            color = TerminalError,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { debugMode = !debugMode; afftService.toggleDebug() }) {
                        Icon(Icons.Default.BugReport, "Debug")
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedSection == "home",
                    onClick = { selectedSection = "home" },
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedSection == "payload",
                    onClick = { selectedSection = "payload" },
                    icon = { Icon(Icons.Default.Archive, "Payload") },
                    label = { Text("Payload") }
                )
                NavigationBarItem(
                    selected = selectedSection == "super",
                    onClick = { selectedSection = "super" },
                    icon = { Icon(Icons.Default.SdStorage, "Super") },
                    label = { Text("Super") }
                )
                NavigationBarItem(
                    selected = selectedSection == "filesystem",
                    onClick = { selectedSection = "filesystem" },
                    icon = { Icon(Icons.Default.Folder, "FS") },
                    label = { Text("FS") }
                )
                NavigationBarItem(
                    selected = selectedSection == "boot",
                    onClick = { selectedSection = "boot" },
                    icon = { Icon(Icons.Default.DeveloperBoard, "Boot") },
                    label = { Text("Boot") }
                )
                NavigationBarItem(
                    selected = selectedSection == "filemgr",
                    onClick = { selectedSection = "filemgr" },
                    icon = { Icon(Icons.Default.FolderOpen, "Files") },
                    label = { Text("Files") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedSection) {
                "home" -> HomeScreen(
                    afftService = afftService,
                    binariesReady = binariesReady,
                    logs = logs,
                    isRunning = isRunning,
                    progressMessage = progressMessage
                )
                "payload" -> PayloadScreen(
                    afftService = afftService,
                    logs = logs,
                    isRunning = isRunning
                )
                "super" -> SuperScreen(
                    afftService = afftService,
                    logs = logs,
                    isRunning = isRunning
                )
                "filesystem" -> FilesystemScreen(
                    afftService = afftService,
                    logs = logs,
                    isRunning = isRunning
                )
                "boot" -> BootScreen(
                    afftService = afftService,
                    logs = logs,
                    isRunning = isRunning
                )
                "filemgr" -> FileManagerScreen(
                    afftService = afftService,
                    logs = logs,
                    isRunning = isRunning
                )
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About AFFT") },
            text = {
                Column {
                    Text("Android Firmware Full Toolkit v2.0.2")
                    Text("Author: soe1hom-arch / Wandi", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A complete tool for modifying Android firmware. " +
                        "Supports payload.bin, super.img, EROFS/ext4 filesystem, " +
                        "and boot images.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Binaries status:", fontWeight = FontWeight.Bold)
                    binaryStatus.forEach { (name, ok) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Text(
                                if (ok) "OK" else "MISSING",
                                color = if (ok) TerminalText else TerminalError,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    afftService: AFFTService,
    binariesReady: Boolean,
    logs: List<String>,
    isRunning: Boolean,
    progressMessage: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCleanConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportOptions by remember { mutableStateOf(
        mapOf(
            "payload" to true,
            "img" to true,
            "repacked" to true,
            "boot_out" to true,
            "contents" to true,
            "input" to false
        )
    ) }
    var cleanOptions by remember { mutableStateOf(
        mapOf(
            "img" to true,
            "contents" to true,
            "repacked" to true,
            "payload" to true,
            "boot" to true,
            "boot_out" to true,
            "img_src" to true,
            "filesystem_work" to true,
            "logs" to true
        )
    ) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ANDROID FIRMWARE FULL TOOLKIT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "AFFT v2.0.2 — Author: soe1hom-arch / Wandi",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Binary status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (binariesReady) TerminalBackground
                    else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (binariesReady) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (binariesReady) TerminalText else TerminalError,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (binariesReady) "All binaries deployed successfully"
                    else "Binary deployment failed",
                    color = if (binariesReady) TerminalText else TerminalError,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showCleanConfirmDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            ) {
                Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clean", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            ) {
                Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export All", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress indicator
        if (isRunning && progressMessage.isNotEmpty()) {
            ProcessingOverlay(
                isRunning = true,
                message = progressMessage
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Export dialog with folder selection
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export ke Downloads/AFFT") },
                text = {
                    Column {
                        Text("Pilih folder yang akan diekspor:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        exportOptions.forEach { (folder, selected) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        exportOptions = exportOptions + (folder to !selected)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        exportOptions = exportOptions + (folder to checked)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folder, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showExportDialog = false
                        scope.launch {
                            val selectedFolders = exportOptions.filter { it.value }.keys.toList()
                            if (selectedFolders.isEmpty()) {
                                Toast.makeText(context, "Pilih minimal satu folder", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            Toast.makeText(context, "Mengekspor ${selectedFolders.size} folder...", Toast.LENGTH_SHORT).show()
                            afftService.exportSelectedToDownloads(selectedFolders)
                        }
                    }) {
                        Text("Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Clean selection dialog
        if (showCleanConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCleanConfirmDialog = false },
                icon = {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                },
                title = { Text("Pilih Folder untuk Dibersihkan") },
                text = {
                    Column {
                        Text("Centang folder yang ingin dihapus:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "File di input/ dan Downloads/AFFT/ TIDAK akan terhapus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        cleanOptions.forEach { (folder, selected) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cleanOptions = cleanOptions + (folder to !selected)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        cleanOptions = cleanOptions + (folder to checked)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folder, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCleanConfirmDialog = false
                            scope.launch {
                                val selectedFolders = cleanOptions.filter { it.value }.keys.toList()
                                if (selectedFolders.isEmpty()) {
                                    Toast.makeText(context, "Pilih minimal satu folder", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                Toast.makeText(context, "Membersihkan ${selectedFolders.size} folder...", Toast.LENGTH_SHORT).show()
                                afftService.cleanSelected(selectedFolders)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Folder Terpilih")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanConfirmDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Terminal output
        Text(
            "Output Console",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        TerminalView(
            logs = logs,
            modifier = Modifier.fillMaxWidth(),
            maxHeight = 300
        )
    }
}
