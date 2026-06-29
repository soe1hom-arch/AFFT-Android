package com.afft.app.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceFileBrowserDialog(
    title: String,
    currentDir: File,
    onNavigate: (File) -> Unit,
    onFileSelected: (File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            val files = remember(currentDir) {
                currentDir.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedWith(compareBy<File> { it.extension }.thenBy { it.name.lowercase() })
                    ?: emptyList()
            }
            val dirs = remember(currentDir) {
                currentDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
            }

            if (dirs.isEmpty() && files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Folder kosong",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    // Parent directory navigation
                    if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                        item {
                            ListItem(
                                headlineContent = {
                                    Text("../", fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary)
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Folder, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable {
                                    currentDir.parentFile?.let { onNavigate(it) }
                                }
                            )
                            HorizontalDivider()
                        }
                    }

                    // Directories
                    items(dirs) { dir ->
                        ListItem(
                            headlineContent = {
                                Text("${dir.name}/", fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium)
                            },
                            leadingContent = {
                                Icon(Icons.Default.Folder, null,
                                    tint = MaterialTheme.colorScheme.tertiary)
                            },
                            modifier = Modifier.clickable { onNavigate(dir) }
                        )
                        HorizontalDivider()
                    }

                    // Files
                    items(files) { file ->
                        ListItem(
                            headlineContent = {
                                Text(file.name, fontFamily = FontFamily.Monospace,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(formatFileSize(file.length()) + " · " + file.extension.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            leadingContent = {
                                Icon(
                                    when (file.extension.lowercase()) {
                                        "img", "bin" -> Icons.Default.DiscFull
                                        "zip", "gz", "xz" -> Icons.Default.Archive
                                        "txt", "log" -> Icons.Default.TextSnippet
                                        else -> Icons.Default.InsertDriveFile
                                    }, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { onFileSelected(file) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

private fun formatFileSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
    else -> "${"%.2f".format(size.toDouble() / (1024 * 1024 * 1024))} GB"
}

/**
 * Dialog awal untuk memilih sumber file:
 * 1. "Pilih dari Penyimpanan" → System file picker
 * 2. "Pilih dari Folder Kerja" → Workspace browser
 */
@Composable
fun FileSourceSelectorDialog(
    onPickFromStorage: () -> Unit,
    onPickFromWorkspace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Pilih Sumber File",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Button(
                    onClick = {
                        onDismiss()
                        onPickFromStorage()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Storage, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih dari Penyimpanan")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onPickFromWorkspace()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih dari Folder Kerja")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
