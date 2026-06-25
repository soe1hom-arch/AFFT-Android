package com.afft.app.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object BinaryManager {

    private const val BIN_DIR = "bin"
    private const val APP_BIN_DIR = "afft_bin"

    private val REQUIRED_BINARIES = listOf(
        "debugfs",
        "extract.erofs",
        "lpmake",
        "lpunpack",
        "magiskboot",
        "make_ext4fs",
        "mkfs.erofs",
        "payload-dumper-go",
        "simg2img"
    )

    private val BINARY_WHITELIST = setOf(
        "debugfs", "extract.erofs", "lpmake", "lpunpack",
        "magiskboot", "make_ext4fs", "mkfs.erofs",
        "payload-dumper-go", "simg2img"
    )

    fun getBinDirectory(context: Context): File {
        val dir = File(context.filesDir, APP_BIN_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun deployBinaries(context: Context): Result<List<String>> {
        val binDir = getBinDirectory(context)
        val deployed = mutableListOf<String>()

        try {
            val assets = context.assets.list(BIN_DIR) ?: emptyArray()

            for (assetName in assets) {
                if (assetName !in BINARY_WHITELIST) {
                    continue
                }

                val targetFile = File(binDir, assetName)
                if (targetFile.exists() && targetFile.canExecute()) {
                    deployed.add(assetName)
                    continue
                }

                try {
                    context.assets.open("$BIN_DIR/$assetName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setExecutable(true, false)
                    targetFile.setReadable(true, false)
                    deployed.add(assetName)
                } catch (e: Exception) {
                    android.util.Log.w("BinaryManager", "Failed to deploy $assetName: ${e.message}")
                }
            }

            if (deployed.isEmpty()) {
                return Result.failure(Exception("No binaries could be deployed"))
            }

            return Result.success(deployed)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getBinaryPath(context: Context, name: String): String? {
        val binDir = getBinDirectory(context)
        val binary = File(binDir, name)
        if (binary.exists() && binary.canExecute()) {
            return binary.absolutePath
        }

        return null
    }

    fun verifyBinaries(context: Context): Map<String, Boolean> {
        val binDir = getBinDirectory(context)
        val results = mutableMapOf<String, Boolean>()

        for (name in REQUIRED_BINARIES) {
            val binary = File(binDir, name)
            results[name] = binary.exists() && binary.canExecute()
        }

        return results
    }

    fun getBinaryDirPath(context: Context): String {
        return getBinDirectory(context).absolutePath
    }
}
