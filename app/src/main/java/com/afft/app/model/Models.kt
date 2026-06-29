/*
 * Copyright (c) 2026 Wandi (soe1hom-arch). All rights reserved.
 */

package com.afft.app.model

import android.net.Uri

data class OperationResult(
    val ok: Boolean,
    val title: String,
    val message: String,
    val outputPath: String = ""
)

data class FileItem(
    val name: String,
    val uri: Uri,
    val size: Long = 0,
    val isDirectory: Boolean = false
)

data class AppSettings(
    val debugMode: Boolean = false,
    val inputPath: String = "",
    val outputPath: String = ""
)

enum class OperationType {
    EXTRACT_PAYLOAD,
    UNPACK_SUPER,
    REPACK_SUPER,
    EXTRACT_FILESYSTEM,
    REPACK_FILESYSTEM,
    UNPACK_BOOT,
    REPACK_BOOT,
    CLEAN_OUTPUT,
    WIZARD
}

data class OperationLog(
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false,
    val isInfo: Boolean = false
)

enum class BootImageType(val fileName: String, val displayName: String) {
    BOOT("boot.img", "Boot"),
    VENDOR_BOOT("vendor_boot.img", "Vendor Boot"),
    INIT_BOOT("init_boot.img", "Init Boot"),
    DTBO("dtbo.img", "DTBO"),
    RECOVERY("recovery.img", "Recovery"),
    VBMETA("vbmeta.img", "VBMeta"),
    VENDOR_KERNEL_BOOT("vendor_kernel_boot.img", "Vendor Kernel Boot")
}
