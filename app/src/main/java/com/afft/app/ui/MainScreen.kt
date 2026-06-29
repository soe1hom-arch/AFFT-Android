/*
 * Copyright (c) 2026 Wandi (soe1hom-arch). All rights reserved.
 */

package com.afft.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import com.afft.app.service.AFFTService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.TerminalView
import com.afft.app.ui.components.ColoredLogLine
import com.afft.app.ui.FileManagerScreen
import com.afft.app.ui.theme.*
import com.afft.app.util.BinaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items


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
    val progressPercent by afftService.progressPercent.collectAsState()
    val currentPartition by afftService.currentPartition.collectAsState()
    var binariesReady by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var aboutEnglish by remember { mutableStateOf(false) }
    var binaryStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scopeDrawer = rememberCoroutineScope()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "AFFT Console",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()

                // Menu items in drawer
                Column(modifier = Modifier.fillMaxWidth()) {
                    DrawerMenuItem(
                        icon = Icons.Default.Home,
                        label = "Home",
                        selected = selectedSection == "home",
                        onClick = {
                            selectedSection = "home"
                            scopeDrawer.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.Archive,
                        label = "Payload",
                        selected = selectedSection == "payload",
                        onClick = {
                            selectedSection = "payload"
                            scopeDrawer.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.SdStorage,
                        label = "Super",
                        selected = selectedSection == "super",
                        onClick = {
                            selectedSection = "super"
                            scopeDrawer.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.Folder,
                        label = "Filesystem",
                        selected = selectedSection == "filesystem",
                        onClick = {
                            selectedSection = "filesystem"
                            scopeDrawer.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.DeveloperBoard,
                        label = "Boot",
                        selected = selectedSection == "boot",
                        onClick = {
                            selectedSection = "boot"
                            scopeDrawer.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.FolderOpen,
                        label = "File Manager",
                        selected = selectedSection == "filemgr",
                        onClick = {
                            selectedSection = "filemgr"
                            scopeDrawer.launch { drawerState.close() }
                        }
                    )
                }

                Divider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Console Output",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
                )

                TerminalView(
                    logs = logs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    maxHeight = 1000,
                    isRunning = isRunning,
                    progressPercent = progressPercent,
                    currentPartition = currentPartition
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${logs.size} lines",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        // Copy log ke clipboard
                        val context = LocalContext.current
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("AFFT Log", logs.joinToString("\n"))
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Log disalin ke clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Log",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Simpan log ke file
                        IconButton(
                            onClick = {
                                try {
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                    val logDir = File(downloadsDir, "AFFT")
                                    logDir.mkdirs()
                                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                    val logFile = File(logDir, "afft_log_$timestamp.txt")
                                    logFile.writeText(logs.joinToString("\n"))
                                    android.widget.Toast.makeText(context, "Log tersimpan: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Gagal simpan log: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = "Save Log",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            afftService.clearLogs()
                        }) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) {
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
                    navigationIcon = {
                        IconButton(onClick = {
                            scopeDrawer.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
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
                        IconButton(onClick = {
                            debugMode = !debugMode
                            afftService.toggleDebug()
                            Toast.makeText(context,
                                if (!debugMode) "Debug mode: OFF" else "Debug mode: ON",
                                Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                Icons.Default.BugReport, "Debug",
                                tint = if (debugMode) TerminalError else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    "logs" -> LogsViewerScreen(
                        afftService = afftService
                    )
                }
            }
        }

        // About dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                icon = {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp))
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AFFT", fontWeight = FontWeight.Bold)
                        Text(
                            if (aboutEnglish) "Android Firmware Full Toolkit" else "Android Firmware Full Toolkit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Toggle EN/ID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                if (aboutEnglish) "EN" else "ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = aboutEnglish,
                                onCheckedChange = { aboutEnglish = it }
                            )
                        }
                        
                        if (aboutEnglish) {
                            Text("About", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "AFFT is an Android firmware modification tool that supports " +
                                "payload.bin, super.img, EROFS/ext4 filesystem, " +
                                "and various boot image types.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Developer", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Tentang Aplikasi", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "AFFT adalah alat modifikasi firmware Android yang " +
                                "mendukung payload.bin, super.img, filesystem EROFS/ext4, " +
                                "dan berbagai jenis boot image.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tentang Pengembang", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("soe1hom-arch (Wandi)", fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kotlin + Jetpack Compose",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Version 2.0.4",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // GitHub repository link
                        Row(
                            modifier = Modifier.clickable {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/soe1hom-arch/AFFT-Android")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Browser tidak tersedia", Toast.LENGTH_SHORT).show()
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                androidx.compose.ui.res.painterResource(com.afft.app.R.drawable.ic_github),
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GitHub: soe1hom-arch/AFFT-Android",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Report issue button
                        Row(
                            modifier = Modifier.clickable {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/soe1hom-arch/AFFT-Android/issues")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Browser tidak tersedia", Toast.LENGTH_SHORT).show()
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (aboutEnglish) "Report Issue" else "Laporkan Masalah",
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (aboutEnglish) "Binary Status" else "Status Binary",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (aboutEnglish) "Features:" else "Fitur:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (aboutEnglish) {
                            BulletText("Extract & Repack payload.bin")
                            BulletText("Unpack & Repack super.img (sparse)")
                            BulletText("Extract & Repack filesystem (EROFS/ext4)")
                            BulletText("Unpack & Repack boot images (7 types)")
                            BulletText("File Manager & Export to Downloads")
                        } else {
                            BulletText("Ekstrak & Repack payload.bin")
                            BulletText("Unpack & Repack super.img (sparse)")
                            BulletText("Ekstrak & Repack filesystem (EROFS/ext4)")
                            BulletText("Unpack & Repack boot images (7 jenis)")
                            BulletText("File Manager & Export ke Downloads")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showAboutDialog = false }) {
                        Text(
                            if (aboutEnglish) "Close" else "Tutup"
                        )
                    }
                }
            )
        }
    }
}

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
                    "AFFT v2.0.2 \u2014 Author: soe1hom-arch / Wandi",
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

        if (isRunning && progressMessage.isNotEmpty()) {
            ProcessingOverlay(
                isRunning = true,
                message = progressMessage
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Export dialog
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

        // Clean dialog
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

        // NOTE: TerminalView telah dipindahkan ke sidebar drawer
    }
}

@Composable
private fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BulletText(text: String) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
    ) {
        Text("\u2022 ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
fun LogsViewerScreen(afftService: AFFTService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedLog by remember { mutableStateOf<File?>(null) }
    var logContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load log files
    LaunchedEffect(Unit) {
        logFiles = afftService.getLogFiles()
        isLoading = false
    }
    
    if (selectedLog != null) {
        // View selected log file
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { 
                    selectedLog = null
                    logContent = ""
                }) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kembali")
                }
                Text(
                    selectedLog?.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${selectedLog?.let { file -> formatFileSizePublic(file.length()) } ?: ""}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // Load content
            LaunchedEffect(selectedLog) {
                selectedLog?.let { file ->
                    logContent = afftService.getLogContent(file)
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    val lines = logContent.lines()
                    items(lines.size) { index ->
                        if (index < lines.size) {
                            ColoredLogLine(
                                text = lines[index],
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = {
                    scope.launch {
                        afftService.saveCurrentLogToDownloads()
                    }
                }) {
                    Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan ke Downloads")
                }
            }
        }
    } else {
        // List log files
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Log Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace
                )
                Row {
                    // Refresh button
                    IconButton(onClick = {
                        isLoading = true
                        scope.launch {
                            logFiles = afftService.getLogFiles()
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    // Clear old logs
                    IconButton(onClick = {
                        scope.launch {
                            afftService.clearOldLogs(20)
                            logFiles = afftService.getLogFiles()
                        }
                    }) {
                        Icon(Icons.Default.CleaningServices, "Clean Old")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Folder: ${afftService.getLogsDir().absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (logFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TextSnippet,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Belum ada log file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Jalankan operasi terlebih dahulu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logFiles.size) { index ->
                        val file = logFiles[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedLog = file
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        formatFileSizePublic(file.length()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSizePublic(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
        else -> "${"%.2f".format(size.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}
