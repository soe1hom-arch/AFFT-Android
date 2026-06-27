package com.afft.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            // Sidebar drawer dengan Console/Log viewer
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                // Header drawer
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "AFFT Console",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()

                // Menu items
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

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Console Output",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
                )

                // TerminalView/log console dalam drawer
                TerminalView(
                    logs = logs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    maxHeight = 1000
                )

                // Tombol clear logs
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
                        // Clear logs - call a method in AFFTService
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

        // About dialog (professional)
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
                        // About app
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

                        // Developer info
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

                        // Binary status
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

                        // Capabilities
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
        Text("• ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
