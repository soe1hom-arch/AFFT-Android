package com.afft.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.service.AFFTService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentDir by remember { mutableStateOf<File?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var pathHistory by remember { mutableStateOf<List<File>>(emptyList()) }
    var showSize by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCopyDestDialog by remember { mutableStateOf(false) }
    var showMoveDestDialog by remember { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val workDir = afftService.getWorkDir()
    val tempDir = afftService.getTempDir()
    val inputDir = afftService.getInputDir()
    val downloadDir = File("/storage/emulated/0/Download/AFFT")
    val publicDirs = listOf(
        "Downloads" to File("/storage/emulated/0/Download"),
        "Documents" to File("/storage/emulated/0/Documents"),
        "AFFT Export" to downloadDir
    )

    // Show toast
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    fun refreshFiles(dir: File) {
        currentDir = dir
        files = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
        selectedFiles = emptySet()
        selectMode = false
    }

    // File picker for importing from external storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                operationInProgress = true
                val result = afftService.pickAndCopyToInput(uri)
                operationInProgress = false
                if (result != null) {
                    toastMessage = "File diimpor: ${result.name}"
                    refreshFiles(currentDir ?: inputDir)
                } else {
                    toastMessage = "Gagal mengimpor file"
                }
            }
        }
    }

    fun toggleSelectFile(file: File) {
        selectedFiles = if (selectedFiles.contains(file)) {
            selectedFiles - file
        } else {
            selectedFiles + file
        }
        selectMode = selectedFiles.isNotEmpty()
    }

    LaunchedEffect(Unit) {
        refreshFiles(inputDir)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "File Manager",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace
            )
            if (selectMode) {
                TextButton(onClick = {
                    selectedFiles = emptySet()
                    selectMode = false
                }) {
                    Text("Batal", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Path bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pathHistory.isNotEmpty()) {
                IconButton(onClick = {
                    val prev = pathHistory.last()
                    pathHistory = pathHistory.dropLast(1)
                    refreshFiles(prev)
                }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }
            Text(
                text = currentDir?.absolutePath ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Quick location + Import button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {
                    pathHistory = emptyList()
                    refreshFiles(tempDir)
                },
                label = { Text("Temp", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = {
                    pathHistory = emptyList()
                    refreshFiles(workDir)
                },
                label = { Text("Work", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = {
                    pathHistory = emptyList()
                    refreshFiles(inputDir)
                },
                label = { Text("Input", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(14.dp)) }
            )
            if (downloadDir.exists()) {
                AssistChip(
                    onClick = {
                        pathHistory = emptyList()
                        refreshFiles(downloadDir)
                    },
                    label = { Text("DL", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp)) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Import button
            IconButton(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = !operationInProgress
            ) {
                Icon(Icons.Default.FileOpen, "Import", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // File list
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(modifier = Modifier.padding(2.dp)) {
                if (files.isNotEmpty()) {
                    items(files, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            isSelected = selectedFiles.contains(file),
                            selectMode = selectMode,
                            onClick = {
                                if (selectMode) {
                                    toggleSelectFile(file)
                                } else if (file.isDirectory) {
                                    pathHistory = pathHistory + (currentDir ?: tempDir)
                                    refreshFiles(file)
                                }
                            },
                            onLongClick = {
                                toggleSelectFile(file)
                            },
                            showSize = showSize
                        )
                    }
                }
                val minRows = 5
                val currentRows = files.size
                if (currentRows < minRows) {
                    items(minRows - currentRows) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(vertical = 1.dp)
                        )
                    }
                }
            }
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Folder kosong atau tidak ada file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: info
                Text(
                    if (selectMode) "${selectedFiles.size} selected"
                    else "${files.size} item(s)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                // Right: actions
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (selectMode) {
                        // Select All
                        IconButton(onClick = {
                            selectedFiles = files.toSet()
                            selectMode = true
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SelectAll, "Select All", modifier = Modifier.size(18.dp))
                        }
                        // Copy
                        IconButton(
                            onClick = { showCopyDestDialog = true },
                            modifier = Modifier.size(36.dp),
                            enabled = !operationInProgress
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                        }
                        // Move
                        IconButton(
                            onClick = { showMoveDestDialog = true },
                            modifier = Modifier.size(36.dp),
                            enabled = !operationInProgress
                        ) {
                            Icon(Icons.Default.DriveFileMove, "Move", modifier = Modifier.size(18.dp))
                        }
                        // Delete
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(36.dp),
                            enabled = !operationInProgress
                        ) {
                            Icon(Icons.Default.Delete, "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Default actions when no selection
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, "Import", modifier = Modifier.size(18.dp))
                        }
                        TextButton(onClick = { showSize = !showSize }) {
                            Text(if (showSize) "Size" else "Size", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && selectedFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus Permanen?") },
            text = {
                Column {
                    Text(
                        "Tindakan ini akan menghapus file/folder berikut secara permanen " +
                        "dan tidak bisa dikembalikan. Lanjutkan?",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedFiles.take(10).forEach { f ->
                        Text(
                            "• ${f.name}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    if (selectedFiles.size > 10) {
                        Text(
                            "...dan ${selectedFiles.size - 10} lainnya",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            var fail = 0
                            for (f in selectedFiles) {
                                if (afftService.deleteFileWithSafety(f)) ok++
                                else fail++
                            }
                            operationInProgress = false
                            toastMessage = "Dihapus: $ok file, gagal: $fail"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Copy destination dialog
    if (showCopyDestDialog && selectedFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showCopyDestDialog = false },
            title = { Text("Salin ke...") },
            text = {
                Column {
                    Text("Pilih tujuan:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showCopyDestDialog = false
                            scope.launch {
                                operationInProgress = true
                                var ok = 0
                                for (f in selectedFiles) {
                                    if (afftService.copyFileTo(f, downloadDir)) ok++
                                }
                                operationInProgress = false
                                toastMessage = "Disalin ke Downloads: $ok file"
                                refreshFiles(currentDir ?: inputDir)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📁 Downloads/AFFT") }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showCopyDestDialog = false
                            scope.launch {
                                operationInProgress = true
                                var ok = 0
                                for (f in selectedFiles) {
                                    if (afftService.copyFileTo(f, inputDir)) ok++
                                }
                                operationInProgress = false
                                toastMessage = "Disalin ke input/: $ok file"
                                refreshFiles(currentDir ?: inputDir)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📂 Input/ (workspace)") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCopyDestDialog = false }) { Text("Batal") }
            }
        )
    }

    // Move destination dialog
    if (showMoveDestDialog && selectedFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showMoveDestDialog = false },
            title = { Text("Pindah ke...") },
            text = {
                Column {
                    Text("Pilih tujuan:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showMoveDestDialog = false
                            scope.launch {
                                operationInProgress = true
                                var ok = 0
                                for (f in selectedFiles) {
                                    if (afftService.moveFileTo(f, downloadDir)) ok++
                                }
                                operationInProgress = false
                                toastMessage = "Dipindah ke Downloads: $ok file"
                                refreshFiles(currentDir ?: inputDir)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📁 Downloads/AFFT") }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showMoveDestDialog = false
                            scope.launch {
                                operationInProgress = true
                                var ok = 0
                                for (f in selectedFiles) {
                                    if (afftService.moveFileTo(f, inputDir)) ok++
                                }
                                operationInProgress = false
                                toastMessage = "Dipindah ke input/: $ok file"
                                refreshFiles(currentDir ?: inputDir)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📂 Input/ (workspace)") }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Move to Downloads root
                    Button(
                        onClick = {
                            showMoveDestDialog = false
                            scope.launch {
                                operationInProgress = true
                                val dest = File("/storage/emulated/0/Download")
                                var ok = 0
                                for (f in selectedFiles) {
                                    if (afftService.moveFileTo(f, dest)) ok++
                                }
                                operationInProgress = false
                                toastMessage = "Dipindah ke Downloads: $ok file"
                                refreshFiles(currentDir ?: inputDir)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📁 Downloads/ (root)" ) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDestDialog = false }) { Text("Batal") }
            }
        )
    }

    // Processing overlay - fixed card di bawah action bar
    if (operationInProgress) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memproses...", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: File,
    isSelected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showSize: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(vertical = 1.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                file.isDirectory -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox in select mode
            if (selectMode || isSelected) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                when {
                    file.isDirectory -> Icons.Default.Folder
                    file.name.endsWith(".img") -> Icons.Default.SdStorage
                    file.name.endsWith(".bin") -> Icons.Default.Archive
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showSize && !file.isDirectory) {
                    Text(
                        text = formatFileSize(file.length()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
        else -> "${"%.2f".format(size.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}
