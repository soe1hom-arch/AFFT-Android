package com.afft.app.ui
import android.util.Log
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.model.OperationLog
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.Sidebar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarScreen(
    afftService: AFFTService,
    logs: List<OperationLog>,
    isRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf("home") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = { Text("AFFT - Process Monitor", fontFamily = FontFamily.Monospace) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        // Main content with sidebar
        Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Sidebar
            Sidebar(
                selectedSection = selectedSection,
                onSectionSelect = { selectedSection = it },
                operationLogs = logs,
                modifier = Modifier.width(280.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Main content area
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (selectedSection) {
                    "payload" -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Payload Operations", fontFamily = FontFamily.Monospace)
                            Text("""Format: 
                                
Extract payload.bin
                                Unpack super.img  
                                Repack super.img
                                Extract filesystem
                                Repack filesystem
                                Unpack boot images
                                Clean output
                                Wizard mode (auto scan)
                                """.trimIndent(), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                    }
                    "super" -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Super.img Operations", fontFamily = FontFamily.Monospace)
                            Text("""Format: 
                                
Unpack super.img → temp/img/*.img → partition images
                                Repack super.img → from temp/img/*.img → super_repack.img
                                ✓ Unified storage in temp/ (img, contents, repacked)
                                """.trimIndent(), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                    }
                    "filesystem" -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Filesystem Operations", fontFamily = FontFamily.Monospace)
                            Text("""Format: 
                                
Extract filesystem (*.img) → temp/contents/*/
                                Repack filesystem (from temp/contents/*/) → temp/img/*.img
                                ✓ Supports EROFS and ext4
                                ✓ Automatic sparse conversion
                                """.trimIndent(), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                    }
                    "boot" -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Boot Family Operations", fontFamily = FontFamily.Monospace)
                            Text("""Format: 
                                
Unpack boot images:
  • boot.img
  • vendor_boot.img
  • init_boot.img
  • dtbo.img
  • recovery.img
  • vbmeta.img
  • vendor_kernel_boot.img
                                Repack boot images (from extracted contents)
                                ✓ All 7 boot types supported
                                """.trimIndent(), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                    }
                    "process_monitor" -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Process Monitor", fontFamily = FontFamily.Monospace)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Active operations:" , fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (logs.isEmpty()) {
                                    Text("No active processes...", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    logs.takeLast(10).forEach { log ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${if (log.isError) "✗" else if (log.isInfo) "i" else "✓"} ${log.text}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                            Text(formatTimestamp(log.timestamp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                    "logs" -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Operation Logs", fontFamily = FontFamily.Monospace)
                            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                    logs.reversed().take(50).forEach { log ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${if (log.isError) "[ERROR]" else if (log.isInfo) "[INFO]" else "[OK]"} ${log.text}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                            Text(formatTimestamp(log.timestamp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Home", fontFamily = FontFamily.Monospace)
                            Text("Main dashboard and quick actions", fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val seconds = timestamp / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
