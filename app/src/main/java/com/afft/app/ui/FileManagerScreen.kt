package com.afft.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.TerminalView
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileManagerScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean
) {
    val scope = rememberCoroutineScope()
    var currentDir by remember { mutableStateOf<File?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var pathHistory by remember { mutableStateOf<List<File>>(emptyList()) }
    var showSize by remember { mutableStateOf(false) }

    val workDir = afftService.getWorkDir()
    val tempDir = afftService.getTempDir()
    val inputDir = afftService.getInputDir()
    val downloadDir = File("/storage/emulated/0/Download/AFFT")

    fun refreshFiles(dir: File) {
        currentDir = dir
        files = dir.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshFiles(inputDir)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "File Manager",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Browse extracted files and folders",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Path bar & quick shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back button
            if (pathHistory.isNotEmpty()) {
                IconButton(onClick = {
                    val prev = pathHistory.last()
                    pathHistory = pathHistory.dropLast(1)
                    refreshFiles(prev)
                }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }

            // Current path
            Text(
                text = currentDir?.absolutePath ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick location buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AssistChip(
                onClick = {
                    pathHistory = emptyList()
                    refreshFiles(inputDir)
                },
                label = { Text("Temp", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = {
                    pathHistory = emptyList()
                    refreshFiles(workDir)
                },
                label = { Text("Work", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = {
                    pathHistory = emptyList()
                    refreshFiles(inputDir)
                },
                label = { Text("Input", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(16.dp)) }
            )
            if (downloadDir.exists()) {
                AssistChip(
                    onClick = {
                        pathHistory = emptyList()
                        refreshFiles(downloadDir)
                    },
                    label = { Text("Download", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // File list
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 400.dp)
        ) {
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Folder kosong atau tidak ada file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(4.dp)
                ) {
                    items(files) { file ->
                        FileRow(
                            file = file,
                            isCurrentDir = currentDir?.absolutePath == tempDir.absolutePath,
                            onClick = {
                                if (file.isDirectory) {
                                    pathHistory = pathHistory + (currentDir ?: tempDir)
                                    refreshFiles(file)
                                }
                            },
                            showSize = showSize
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${files.size} item(s) | ${currentDir?.absolutePath ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { showSize = !showSize }) {
                Text(if (showSize) "Hide Size" else "Show Size", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Output:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        TerminalView(
            logs = logs,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxHeight = 150
        )
    }
}

@Composable
fun FileRow(
    file: File,
    isCurrentDir: Boolean,
    onClick: () -> Unit,
    showSize: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (file.isDirectory)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    file.isDirectory -> Icons.Default.Folder
                    file.name.endsWith(".img") -> Icons.Default.SdStorage
                    file.name.endsWith(".bin") -> Icons.Default.Archive
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (file.isDirectory)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                if (showSize && !file.isDirectory) {
                    Text(
                        text = formatFileSize(file.length()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
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
