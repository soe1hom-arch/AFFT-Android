/*
 * Copyright (c) 2026 Wandi (soe1hom-arch). All rights reserved.
 */

package com.afft.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.model.OperationLog

@Composable
fun Sidebar(
    selectedSection: String,
    onSectionSelect: (String) -> Unit,
    operationLogs: List<OperationLog>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Navigation Menu
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "AFFT Operations",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp, 8.dp)
            )

            val menuItems = listOf(
                Triple("home", Icons.Default.Home, "Home"),
                Triple("payload", Icons.Default.Archive, "Payload"),
                Triple("super", Icons.Default.SdStorage, "Super"),
                Triple("filesystem", Icons.Default.Folder, "Filesystem"),
                Triple("boot", Icons.Default.DeveloperBoard, "Boot"),
                Triple("process_monitor", Icons.Default.List, "Process Monitor"),
                Triple("logs", Icons.Default.TextSnippet, "Logs"),
            )

            for ((section, icon, label) in menuItems) {
                SidebarMenuItem(
                    icon = icon,
                    label = label,
                    selected = selectedSection == section,
                    onClick = { onSectionSelect(section) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Running Processes Monitor
        if (selectedSection == "process_monitor") {
            ProcessMonitorSection(operationLogs)
        }

        // Operation Logs
        if (selectedSection == "logs") {
            LogsSection(operationLogs)
        }
    }
}

@Composable
fun SidebarMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp, 12.dp)
            .background(
                if (selected) Color(0xFF4CAF50) else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ProcessMonitorSection(operationLogs: List<OperationLog>) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            "Running Operations",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        )

        if (operationLogs.isEmpty()) {
            Text(
                "No operations running...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            operationLogs.forEach { log ->
                ProcessLogCard(log)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ProcessLogCard(log: OperationLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isError) Color(0xFFFFEBEE)
                else if (log.isInfo) Color(0xFFE3F2FD)
                else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    log.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (log.isError) Color(0xFFC62828)
                        else if (log.isInfo) Color(0xFF1565C0)
                        else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LogsSection(operationLogs: List<OperationLog>) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            "Operation Logs",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        )

        if (operationLogs.isEmpty()) {
            Text(
                "No logs available...",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    operationLogs.reversed().take(50).forEach { log ->
                        LogEntry(log)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntry(log: OperationLog) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            if (log.isError) "[✗]"
            else if (log.isInfo) "[i]"
            else "[ ]",
            fontFamily = FontFamily.Monospace,
            color = if (log.isError) Color(0xFFC62828)
                else if (log.isInfo) Color(0xFF1565C0)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Text(
            log.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (log.isError) Color(0xFFC62828)
                else if (log.isInfo) Color(0xFF1565C0)
                else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatTimestamp(log.timestamp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
