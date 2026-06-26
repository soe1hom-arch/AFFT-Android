package com.afft.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.model.OperationResult
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.FilePickerCard
import com.afft.app.ui.components.TerminalView
import com.afft.app.ui.theme.*
import com.afft.app.util.BinaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(afftService: AFFTService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf("home") }
    var debugMode by remember { mutableStateOf(false) }
    val logs by afftService.logs.collectAsState()
    val isRunning by afftService.isRunning.collectAsState()
    var binariesReady by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var binaryStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // File picked - handled per screen
        }
    }

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
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedSection) {
                "home" -> HomeScreen(
                    afftService = afftService,
                    binariesReady = binariesReady,
                    logs = logs,
                    isRunning = isRunning
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
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                onClick = { afftService.cleanOutput() },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            ) {
                Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clean", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val workDir = afftService.getTempDir()
                        val zipFile = File(context.cacheDir, "afft_source.zip")
                        try {
                            // copy source to Download
                            Toast.makeText(context, "Menyimpan kode ke Download...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isRunning
            ) {
                Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
