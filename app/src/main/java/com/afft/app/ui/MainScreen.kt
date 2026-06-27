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
                    maxHeight = 1000
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
                    TextButton(onClick = {
                        afftService.clearLogs()
                    }) {
                        Text("Clear", fontSize = 12.sp)
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
                            "Android Firmware Full Toolkit",
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tentang Pengembang", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
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
                            Text("Version 2.0.2",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Status Binary", fontWeight = FontWeight.Bold,
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
                        Text("Fitur:", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        BulletText("Ekstrak & Repack payload.bin")
                        BulletText("Unpack & Repack super.img (sparse)")
                        BulletText("Ekstrak & Repack filesystem (EROFS/ext4)")
                        BulletText("Unpack & Repack boot images (7 jenis)")
                        BulletText("File Manager & Export ke Downloads")
                    }
                },
                confirmButton = {
                    Button(onClick = { showAboutDialog = false }) {
                        Text("Tutup")
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
