package com.afft.app.service

import android.content.Context
import android.net.Uri
import com.afft.app.model.OperationResult
import com.afft.app.util.BinaryManager
import com.afft.app.util.ShellExecutor
import com.afft.app.util.SparseImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AFFTService(private val context: Context) {

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    private var debugMode = false

    fun toggleDebug() {
        debugMode = !debugMode
        addLog("Debug mode: ${if (debugMode) "ON" else "OFF"}")
    }

    fun isDebugMode(): Boolean = debugMode

    private fun addLog(text: String) {
        _logs.value = _logs.value + listOf(text)
        android.util.Log.d("AFFTService", text)
    }

    private fun updateProgress(msg: String) {
        _progressMessage.value = msg
        addLog(msg)
    }

    private fun clearLogs() {
        _logs.value = emptyList()
        _progressMessage.value = ""
    }

    fun getWorkDir(): File {
        val dir = File(context.filesDir, "afft_work")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(): File {
        val dir = File(getWorkDir(), "temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInputDir(): File {
        val dir = File(getWorkDir(), "input")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun ensureDirs() {
        getWorkDir()
        getTempDir()
        getInputDir()
        File(getTempDir(), "img").mkdirs()
        File(getTempDir(), "contents").mkdirs()
        File(getTempDir(), "repacked").mkdirs()
        File(getTempDir(), "payload").mkdirs()
        File(getTempDir(), "boot").mkdirs()
        File(getTempDir(), "boot_out").mkdirs()
        File(getTempDir(), "img_src").mkdirs()
        File(getTempDir(), "filesystem_work").mkdirs()
        File(getTempDir(), "logs").mkdirs()
        // Pastikan folder Downloads/AFFT ada
        updateProgress("Menyalin \$destName ke Downloads/AFFT/...")
            val downloadsDir = File("/storage/emulated/0/Download/AFFT")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
    }

    suspend fun copyUriToFile(uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            addLog("[ERROR] Failed to copy file: ${e.message}")
            false
        }
    }

    suspend fun extractPayload(inputUri: Uri): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Menyalin file payload..."
        clearLogs()
        addLog("=== Extract payload.bin ===")

        return try {
            val payloadDumper = BinaryManager.getBinaryPath(context, "payload-dumper-go")
                ?: return OperationResult(false, "Extract Payload", "payload-dumper-go not found")

            val payloadFile = File(getTempDir(), "payload_src.bin")
            if (!copyUriToFile(inputUri, payloadFile)) {
                return OperationResult(false, "Extract Payload", "Failed to copy input file")
            }

            updateProgress("Extracting payload, mohon tunggu...")
            addLog("Running payload-dumper-go...")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(payloadDumper, payloadFile.absolutePath, "-o",
                    File(getTempDir(), "payload").absolutePath),
                workingDir = getTempDir(),
                onProgress = { addLog(it) }
            )

            if (result.exitCode == 0) {
                addLog("[OK] Payload extracted successfully")
                OperationResult(true, "Extract Payload", "Payload extracted successfully",
                    File(getTempDir(), "payload").absolutePath)
            } else {
                addLog("[FAIL] payload-dumper-go exited with code ${result.exitCode}")
                OperationResult(false, "Extract Payload",
                    "payload-dumper-go failed (exit ${result.exitCode})")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Extract Payload", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun unpackSuper(inputUri: Uri): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Menyalin super.img..."
        clearLogs()
        addLog("=== Unpack super.img ===")

        return try {
            val simg2img = BinaryManager.getBinaryPath(context, "simg2img")
            val lpunpack = BinaryManager.getBinaryPath(context, "lpunpack")

            updateProgress("Mengonversi sparse image...")
            if (simg2img == null || lpunpack == null) {
                return OperationResult(false, "Unpack Super",
                    "simg2img or lpunpack not found")
            }

            val superFile = File(getTempDir(), "super_src.img")
            if (!copyUriToFile(inputUri, superFile)) {
                return OperationResult(false, "Unpack Super", "Failed to copy input file")
            }

            val imgDir = File(getTempDir(), "img")
            val workDir = File(getTempDir(), "img_src")

            val isSparse = SparseImage.isSparseImage(superFile)

            if (isSparse) {
                addLog("Sparse image detected, converting to raw...")
                val rawFile = File(workDir, "super_raw.img")
                val convertResult = ShellExecutor.executeWithProgress(
                    command = listOf(simg2img, superFile.absolutePath, rawFile.absolutePath),
                    workingDir = workDir,
                    onProgress = { addLog(it) }
                )
                if (convertResult.exitCode != 0) {
                    return OperationResult(false, "Unpack Super", "simg2img conversion failed")
                }

                updateProgress("Unpacking partisi dengan lpunpack...")
            addLog("Running lpunpack...")
                val unpackResult = ShellExecutor.executeWithProgress(
                    command = listOf(lpunpack, rawFile.absolutePath, workDir.absolutePath),
                    workingDir = workDir,
                    onProgress = { addLog(it) }
                )
                if (unpackResult.exitCode != 0) {
                    return OperationResult(false, "Unpack Super", "lpunpack failed")
                }
            } else {
                addLog("Running lpunpack directly...")
                val unpackResult = ShellExecutor.executeWithProgress(
                    command = listOf(lpunpack, superFile.absolutePath, workDir.absolutePath),
                    workingDir = workDir,
                    onProgress = { addLog(it) }
                )
                if (unpackResult.exitCode != 0) {
                    return OperationResult(false, "Unpack Super", "lpunpack failed")
                }
            }

            workDir.listFiles()?.filter {
                it.isFile && it.extension == "img" &&
                it.name !in setOf("super.img", "super_raw.img") && it.length() > 0
            }?.forEach { img ->
                img.copyTo(File(imgDir, img.name), overwrite = true)
                addLog("  Copied: ${img.name}")
            }

            addLog("[OK] super.img unpacked successfully")
            OperationResult(true, "Unpack Super", "super.img unpacked successfully",
                imgDir.absolutePath)
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Unpack Super", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun repackSuper(): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Mempersiapkan repack super..."
        clearLogs()
        addLog("=== Repack super.img ===")

        return try {
            val lpmake = BinaryManager.getBinaryPath(context, "lpmake")
                ?: return OperationResult(false, "Repack Super", "lpmake not found")

            val imgDir = File(getTempDir(), "img")
            val repackDir = File(getTempDir(), "repacked")

            val partitions = imgDir.listFiles()?.filter {
                it.isFile && it.extension == "img" &&
                it.name !in setOf("super.img", "super_raw.img") && it.length() > 0
            }?.sortedBy { it.name } ?: emptyList()

            if (partitions.isEmpty()) {
                return OperationResult(false, "Repack Super",
                    "No partition images found in temp/img/")
            }

            addLog("Partitions to repack: ${partitions.size}")
            partitions.forEach { addLog("  - ${it.name} (${it.length() / 1024 / 1024} MB)") }

            val outputImg = File(repackDir, "super_repack.img")
            val cmd = mutableListOf(
                lpmake, "--device-size=auto", "--metadata-size=65536",
                "--metadata-slots=3", "--super-name=super", "--sparse"
            )

            val groups = mutableMapOf<String, MutableList<Pair<String, File>>>()
            for (p in partitions) {
                val name = p.nameWithoutExtension
                val group = when {
                    name.endsWith("_a") -> "group_a"
                    name.endsWith("_b") -> "group_b"
                    else -> "default"
                }
                groups.getOrPut(group) { mutableListOf() }.add(name to p)
            }

            for ((groupName, parts) in groups) {
                val groupSize = parts.sumOf { it.second.length() }
                if (groupSize == 0L) continue
                cmd.add("--group=$groupName:$groupSize")
            }

            for ((groupName, parts) in groups) {
                val groupSize = parts.sumOf { it.second.length() }
                if (groupSize == 0L) continue
                for ((name, p) in parts) {
                    val size = p.length()
                    if (size == 0L) continue
                    cmd.add("--partition=$name:readonly:$size:$groupName")
                    cmd.add("--image=$name=${p.absolutePath}")
                }
            }

            cmd.add("--output=${outputImg.absolutePath}")

            updateProgress("Menjalankan lpmake untuk repack...")
            addLog("Running lpmake...")
            val result = ShellExecutor.executeWithProgress(
                command = cmd,
                workingDir = repackDir,
                onProgress = { addLog(it) }
            )

            if (result.exitCode == 0 && outputImg.exists()) {
                val sizeGb = outputImg.length() / (1024.0 * 1024.0 * 1024.0)
                addLog("[OK] super.img repacked successfully (${String.format("%.2f", sizeGb)} GB)")
                OperationResult(true, "Repack Super",
                    "super.img repacked with ${partitions.size} partitions",
                    repackDir.absolutePath)
            } else {
                OperationResult(false, "Repack Super",
                    "lpmake failed (exit ${result.exitCode})")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Repack Super", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun extractFilesystem(inputUri: Uri): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Menyalin filesystem image..."
        clearLogs()
        addLog("=== Extract Filesystem ===")

        return try {
            val extractErofs = BinaryManager.getBinaryPath(context, "extract.erofs")
            val debugfs = BinaryManager.getBinaryPath(context, "debugfs")

            if (extractErofs == null || debugfs == null) {
                return OperationResult(false, "Extract Filesystem",
                    "extract.erofs or debugfs not found")
            }

            val imgFile = File(getTempDir(), "fs_src.img")
            if (!copyUriToFile(inputUri, imgFile)) {
                return OperationResult(false, "Extract Filesystem", "Failed to copy input file")
            }

            val fsType = SparseImage.detectFilesystemType(imgFile)
            val contentsDir = File(getTempDir(), "contents")
            val outputDir = File(contentsDir, imgFile.nameWithoutExtension)

            val isNonFs = setOf(
                "abl", "aop", "bluetooth", "boot", "countrycode",
                "cpucp", "devcfg", "dsp", "dtbo", "featenabler",
                "hyp", "init_boot", "keymaster", "modem",
                "recovery", "tz", "vbmeta", "vendor_boot",
                "vendor_kernel_boot", "uefi", "xbl", "xbl_config"
            ).contains(imgFile.nameWithoutExtension.lowercase())

            if (isNonFs) {
                return OperationResult(false, "Extract Filesystem",
                    "Not a filesystem image (firmware/boot partition)")
            }

            addLog("Detected filesystem: $fsType")
            addLog("Extracting to: ${outputDir.absolutePath}")

            outputDir.mkdirs()

            val isSparse = SparseImage.isSparseImage(imgFile)
            var workingImg = imgFile

            if (isSparse) {
                updateProgress("Mengonversi sparse ke raw ext4...")
                    addLog("Converting sparse to raw...")
                val rawFile = File(getTempDir(), "${imgFile.nameWithoutExtension}_raw.img")
                val simg2img = BinaryManager.getBinaryPath(context, "simg2img")
                if (simg2img != null) {
                    ShellExecutor.executeWithProgress(
                        command = listOf(simg2img, imgFile.absolutePath, rawFile.absolutePath),
                        onProgress = { addLog(it) }
                    )
                    workingImg = rawFile
                }
            }

            when {
                fsType == "erofs" -> {
                    addLog("Running extract.erofs...")
                    val result = ShellExecutor.executeWithProgress(
                        command = listOf(extractErofs, workingImg.absolutePath,
                            outputDir.absolutePath),
                        onProgress = { addLog(it) }
                    )
                    if (result.exitCode != 0) {
                        return OperationResult(false, "Extract Filesystem",
                            "extract.erofs failed (exit ${result.exitCode})")
                    }
                }
                fsType == "ext4" -> {
                    addLog("Running debugfs...")
                    val cmd = "rdump /\nquit\n"
                    val dumpScript = File(getTempDir(), "debugfs_script.txt")
                    dumpScript.writeText(cmd)

                    val result = ShellExecutor.executeWithProgress(
                        command = listOf(debugfs, "-f", dumpScript.absolutePath,
                            "-R", "rdump / ${outputDir.absolutePath}", workingImg.absolutePath),
                        onProgress = { addLog(it) }
                    )
                    if (result.exitCode != 0) {
                        return OperationResult(false, "Extract Filesystem",
                            "debugfs failed (exit ${result.exitCode})")
                    }
                }
                else -> {
                    return OperationResult(false, "Extract Filesystem",
                        "Unknown filesystem type: $fsType")
                }
            }

            addLog("[OK] Filesystem extracted successfully")
            OperationResult(true, "Extract Filesystem",
                "Filesystem extracted successfully", outputDir.absolutePath)
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Extract Filesystem", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun repackFilesystem(dirName: String): OperationResult {
        ensureDirs()
        _isRunning.value = true
        clearLogs()
        addLog("=== Repack Filesystem: $dirName ===")

        return try {
            val contentsDir = File(getTempDir(), "contents")
            val workDir = File(contentsDir, dirName)

            if (!workDir.exists()) {
                return OperationResult(false, "Repack Filesystem",
                    "Directory not found: $dirName")
            }

            val outputDir = File(getTempDir(), "img")
            val outputImg = File(outputDir, "${dirName}.img")

            val isErofs = setOf("product", "system", "system_ext", "vendor",
                "odm", "cust", "my_product", "oplus_product",
                "prism", "optics").contains(dirName.lowercase())

            addLog("Detected filesystem type: ${if (isErofs) "erofs" else "ext4"}")

            if (isErofs) {
                val mkfs = BinaryManager.getBinaryPath(context, "mkfs.erofs")
                    ?: return OperationResult(false, "Repack Filesystem", "mkfs.erofs not found")

                addLog("Running mkfs.erofs...")
                val result = ShellExecutor.executeWithProgress(
                    command = listOf(mkfs, outputImg.absolutePath, workDir.absolutePath),
                    onProgress = { addLog(it) }
                )
                if (result.exitCode != 0) {
                    return OperationResult(false, "Repack Filesystem",
                        "mkfs.erofs failed (exit ${result.exitCode})")
                }
            } else {
                val mkfs = BinaryManager.getBinaryPath(context, "make_ext4fs")
                    ?: return OperationResult(false, "Repack Filesystem", "make_ext4fs not found")

                val totalSize = workDir.walkTopDown().filter { it.isFile }
                    .sumOf { it.length() }
                val refSize = (totalSize * 1.15).toLong() + (1024 * 1024)

                addLog("Total content size: ${totalSize / 1024 / 1024} MB, ref size: ${refSize / 1024 / 1024} MB")
                addLog("Running make_ext4fs...")
                val result = ShellExecutor.executeWithProgress(
                    command = listOf(mkfs, "-s", "-l", refSize.toString(),
                        "-a", dirName, outputImg.absolutePath, workDir.absolutePath),
                    onProgress = { addLog(it) }
                )
                if (result.exitCode != 0) {
                    return OperationResult(false, "Repack Filesystem",
                        "make_ext4fs failed (exit ${result.exitCode})")
                }

                val sparseFile = File(outputDir, "${dirName}.sparse.img")
                if (SparseImage.rawToSparse(outputImg, sparseFile)) {
                    outputImg.delete()
                    sparseFile.renameTo(outputImg)
                    addLog("Converted to sparse image")
                }
            }

            if (outputImg.exists()) {
                val sizeMb = outputImg.length() / (1024.0 * 1024.0)
                addLog("[OK] ${dirName}.img repacked (${String.format("%.1f", sizeMb)} MB)")
                OperationResult(true, "Repack Filesystem",
                    "${dirName}.img repacked successfully", outputImg.absolutePath)
            } else {
                OperationResult(false, "Repack Filesystem",
                    "Output image not found")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Repack Filesystem", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun unpackBoot(inputUri: Uri, bootType: String): OperationResult {
        ensureDirs()
        _isRunning.value = true
        clearLogs()
        addLog("=== Unpack $bootType ===")

        return try {
            val magiskboot = BinaryManager.getBinaryPath(context, "magiskboot")
                ?: return OperationResult(false, "Unpack $bootType", "magiskboot not found")

            val bootFile = File(getTempDir(), "${bootType}")
            if (!copyUriToFile(inputUri, bootFile)) {
                return OperationResult(false, "Unpack $bootType", "Failed to copy input file")
            }

            val bootWorkDir = File(getTempDir(), "boot")
            bootWorkDir.mkdirs()
            val targetFile = File(bootWorkDir, bootType)
            bootFile.copyTo(targetFile, overwrite = true)

            updateProgress("Unpacking boot image dengan magiskboot...")
            addLog("Running magiskboot unpack...")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(magiskboot, "unpack", targetFile.absolutePath),
                workingDir = bootWorkDir,
                onProgress = { addLog(it) }
            )

            if (result.exitCode in listOf(0, 3)) {
                addLog("[OK] $bootType unpacked successfully")
                OperationResult(true, "Unpack $bootType",
                    "$bootType unpacked successfully", bootWorkDir.absolutePath)
            } else {
                OperationResult(false, "Unpack $bootType",
                    "magiskboot failed (exit ${result.exitCode})")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Unpack $bootType", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    suspend fun repackBoot(bootType: String): OperationResult {
        ensureDirs()
        _isRunning.value = true
        clearLogs()
        addLog("=== Repack $bootType ===")

        return try {
            val magiskboot = BinaryManager.getBinaryPath(context, "magiskboot")
                ?: return OperationResult(false, "Repack $bootType", "magiskboot not found")

            val bootWorkDir = File(getTempDir(), "boot")
            val srcImg = File(bootWorkDir, bootType)

            if (!srcImg.exists()) {
                return OperationResult(false, "Repack $bootType",
                    "Source image not found. Unpack first.")
            }

            updateProgress("Repacking boot image dengan magiskboot...")
            addLog("Running magiskboot repack...")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(magiskboot, "repack", srcImg.absolutePath),
                workingDir = bootWorkDir,
                onProgress = { addLog(it) }
            )

            if (result.exitCode == 0) {
                val newImg = File(bootWorkDir, "new-boot.img")
                val bootOutDir = File(getTempDir(), "boot_out")
                bootOutDir.mkdirs()
                val repackName = bootType.replace(".img", "_repack.img")
                val outFile = File(bootOutDir, repackName)

                if (newImg.exists()) {
                    newImg.copyTo(outFile, overwrite = true)
                    newImg.delete()
                }

                addLog("[OK] $repackName repacked successfully")
                OperationResult(true, "Repack $bootType",
                    "$repackName repacked successfully", bootOutDir.absolutePath)
            } else {
                OperationResult(false, "Repack $bootType",
                    "magiskboot failed (exit ${result.exitCode})")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Repack $bootType", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    fun cleanOutput() {
        ensureDirs()
        clearLogs()
        addLog("=== Clean Output ===")

        val dirsToClean = listOf(
            "img", "contents", "repacked", "payload",
            "boot", "boot_out", "img_src", "filesystem_work"
        )

        for (dirName in dirsToClean) {
            val dir = File(getTempDir(), dirName)
            if (dir.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
                addLog("Cleaned: $dirName/")
            }
        }

        addLog("[OK] Output cleaned")
    }

    suspend fun listContentsDirs(): List<String> {
        val contentsDir = File(getTempDir(), "contents")
        if (!contentsDir.exists()) return emptyList()
        return contentsDir.listFiles()?.filter { it.isDirectory }
            ?.map { it.name }?.sorted() ?: emptyList()
    }

    suspend fun listPartitionImages(): List<String> {
        val imgDir = File(getTempDir(), "img")
        if (!imgDir.exists()) return emptyList()
        return imgDir.listFiles()?.filter {
            it.isFile && it.extension == "img" &&
            it.name !in setOf("super.img", "super_raw.img")
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    suspend fun listTempContents(): List<File> {
        val tempDir = getTempDir()
        if (!tempDir.exists()) return emptyList()
        return withContext(Dispatchers.IO) {
            tempDir.listFiles()?.flatMap { dir ->
                if (dir.isDirectory) {
                    listOf(dir) + (dir.listFiles()?.filter { it.isDirectory } ?: emptyList())
                } else {
                    emptyList()
                }
            }?.sortedBy { it.absolutePath } ?: emptyList()
        }
    }

    suspend fun exportAllToDownloads(): OperationResult {
        return withContext(Dispatchers.IO) {
            ensureDirs()
            _isRunning.value = true
            clearLogs()
            addLog("=== Export All to Downloads ===")
            var result: OperationResult = OperationResult(true, "Export All", "")
            try {
                updateProgress("Mengekspor hasil kerja ke Downloads/AFFT/...")
                val downloadsDir = File("/storage/emulated/0/Download/AFFT")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val tempDir = getTempDir()
                if (tempDir.exists()) {
                    val subdirsToCopy = listOf("payload", "img", "repacked", "boot_out", "contents", "logs")
                    var copiedCount = 0
                    for (subdir in subdirsToCopy) {
                        val src = File(tempDir, subdir)
                        if (src.exists() && src.isDirectory) {
                            val dest = File(downloadsDir, subdir)
                            dest.deleteRecursively()
                            src.copyRecursively(dest, overwrite = true)
                            copiedCount++
                            addLog("  Exported: $subdir/")
                        }
                    }
                    if (copiedCount > 0) {
                        addLog("[OK] Hasil kerja diekspor ke Downloads/AFFT/")
                        updateProgress("Ekspor selesai!")
                        result = OperationResult(true, "Export All", "Diekspor ke Downloads/AFFT/", downloadsDir.absolutePath)
                    } else {
                        addLog("[INFO] Tidak ada data untuk diekspor")
                        updateProgress("Tidak ada data untuk diekspor")
                        result = OperationResult(true, "Export All", "Tidak ada data untuk diekspor")
                    }
                } else {
                    result = OperationResult(false, "Export All", "Folder temp belum ada")
                }
            } catch (e: Exception) {
                addLog("[ERROR] Export gagal: ${e.message}")
                result = OperationResult(false, "Export All", e.message ?: "Unknown error")
            } finally {
                _isRunning.value = false
            }
            result
        }
    }

    suspend fun copyResultToDownload(resultPath: String, destName: String): Boolean {
        return try {
            val sourceFile = File(resultPath)
            if (!sourceFile.exists()) return false

            val downloadsDir = File("/storage/emulated/0/Download/AFFT")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val destFile = File(downloadsDir, destName)
            sourceFile.copyTo(destFile, overwrite = true)
            addLog("[OK] Copied to Downloads/AFFT/${destName}")
            true
        } catch (e: Exception) {
            addLog("[ERROR] Copy failed: ${e.message}")
            false
        }
    }
}
