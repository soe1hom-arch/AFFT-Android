package com.afft.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object BinaryManager {

    private const val TAG = "BinaryManager"
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

                    // Set executable permission via Java API
                    val setExecOk = targetFile.setExecutable(true, false)
                    if (!setExecOk) {
                        Log.w(TAG, "setExecutable() returned false for $assetName, trying chmod fallback")
                    }
                    targetFile.setReadable(true, false)

                    // Fallback: use chmod via shell if setExecutable didn't work
                    try {
                        val chmodProcess = Runtime.getRuntime().exec(
                            arrayOf("/system/bin/chmod", "755", targetFile.absolutePath)
                        )
                        chmodProcess.waitFor()
                        if (chmodProcess.exitValue() != 0) {
                            Log.w(TAG, "chmod 755 $assetName exit code: ${chmodProcess.exitValue()}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "chmod fallback failed for $assetName: ${e.message}")
                    }

                    // Verify permissions
                    Log.d(TAG, "$assetName: exists=${targetFile.exists()}" +
                            " execute=${targetFile.canExecute()}" +
                            " read=${targetFile.canRead()}" +
                            " path=${targetFile.absolutePath}")

                    deployed.add(assetName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deploy $assetName: ${e.message}")
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

        val exists = binary.exists()
        val canExec = binary.canExecute()
        val canRead = binary.canRead()

        Log.d(TAG, "getBinaryPath($name): exists=$exists execute=$canExec read=$canRead path=${binary.absolutePath}")

        return if (exists && canExec) binary.absolutePath else null
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
