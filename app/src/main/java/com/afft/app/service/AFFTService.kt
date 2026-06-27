package com.afft.app.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
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
import java.io.FileOutputStream
import java.io.RandomAccessFile

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
        addLog("[INFO] Debug mode: ${if (debugMode) "ON" else "OFF"}")
    }

    fun isDebugMode(): Boolean = debugMode

    private fun addLog(text: String) {
        _logs.value = _logs.value + listOf(text)
        android.util.Log.d("AFFTService", text)
    }

    private fun updateProgress(msg: String) {
        _progressMessage.value = msg
        if (debugMode) addLog("[INFO] $msg")
        android.util.Log.d("AFFTService", msg)
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
    }

    /**
     * Copy a file picked via SAF (content URI) to the input/ directory.
     * This makes it visible in the File Manager.
     */
    suspend fun copyPickedFileToInput(uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val inputDir = getInputDir()
                val fileName = resolveFileName(uri) ?: "imported_${System.currentTimeMillis()}"
                val destFile = File(inputDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                addLog("[OK] File disalin ke input/: $fileName")
                updateProgress("File disalin: $fileName")
                destFile
            } catch (e: Exception) {
                addLog("[ERROR] Gagal menyalin file ke input/: ${e.message}")
                null
            }
        }
    }

    /**
     * Resolve a display name from a content URI.
     */
    private fun resolveFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) c.getString(nameIdx) else null
                } else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    suspend fun copyUriToFile(uri: Uri, destFile: File): Boolean {
        return try {
            if (debugMode) addLog("[INFO] Copying $uri -> ${destFile.absolutePath}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (debugMode) addLog("[OK] Copy selesai: ${destFile.name}")
            true
        } catch (e: Exception) {
            addLog("[ERROR] Failed to copy file: ${e.message}")
            false
        }
    }

    suspend fun extractPayload(inputUri: Uri): OperationResult {
        ensureDirs()
        val payloadFile = File(getTempDir(), "payload_src.bin")
        if (!copyUriToFile(inputUri, payloadFile)) {
            return OperationResult(false, "Extract Payload", "Failed to copy input file")
        }
        return extractPayload(payloadFile)
    }

    suspend fun extractPayload(inputFile: File): OperationResult {
        ensureDirs()
        _isRunning.value = true
        clearLogs()
        addLog("=== Extract payload.bin ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        return try {
            val payloadDumper = BinaryManager.getBinaryPath(context, "payload-dumper-go")
                ?: return OperationResult(false, "Extract Payload", "payload-dumper-go not found")

            updateProgress("Extracting payload, mohon tunggu...")
            addLog("Running payload-dumper-go...")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(payloadDumper, inputFile.absolutePath, "-o",
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
        val superFile = File(getTempDir(), "super_src.img")
        if (!copyUriToFile(inputUri, superFile)) {
            return OperationResult(false, "Unpack Super", "Failed to copy input file")
        }
        return unpackSuper(superFile)
    }

    suspend fun unpackSuper(inputFile: File): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Unpacking super.img..."
        clearLogs()
        addLog("=== Unpack super.img ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        return try {
            val lpunpack = BinaryManager.getBinaryPath(context, "lpunpack")
                ?: return OperationResult(false, "Unpack Super", "lpunpack not found")

            updateProgress("Unpacking super.img...")
            val imgDir = File(getTempDir(), "img")
            addLog("Running lpunpack...")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(lpunpack, "--slot=0", inputFile.absolutePath, imgDir.absolutePath),
                workingDir = getTempDir(),
                onProgress = { addLog(it) }
            )

            if (result.exitCode == 0) {
                addLog("[OK] Super unpacked successfully")
                OperationResult(true, "Unpack Super", "Super unpacked to temp/img/", imgDir.absolutePath)
            } else {
                addLog("[INFO] Mencoba tanpa --slot...")
                val result2 = ShellExecutor.executeWithProgress(
                    command = listOf(lpunpack, inputFile.absolutePath, imgDir.absolutePath),
                    workingDir = getTempDir(),
                    onProgress = { addLog(it) }
                )
                if (result2.exitCode == 0) {
                    addLog("[OK] Super unpacked successfully")
                    OperationResult(true, "Unpack Super", "Super unpacked to temp/img/", imgDir.absolutePath)
                } else {
                    addLog("[FAIL] lpunpack failed (exit ${result2.exitCode})")
                    OperationResult(false, "Unpack Super", "lpunpack failed (exit ${result2.exitCode})")
                }
            }
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
        _progressMessage.value = "Repacking super.img..."
        clearLogs()
        addLog("=== Repack super.img ===")

        return try {
            val lpmake = BinaryManager.getBinaryPath(context, "lpmake")
                ?: return OperationResult(false, "Repack Super", "lpmake not found")

            val imgDir = File(getTempDir(), "img")
            if (!imgDir.exists() || imgDir.listFiles().isNullOrEmpty()) {
                return OperationResult(false, "Repack Super", "No images in temp/img/")
            }

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()

            addLog("Finding partition images...")
            val images = imgDir.listFiles()?.filter {
                it.isFile && it.name.endsWith(".img") &&
                it.name !in setOf("super.img", "super_raw.img")
            } ?: emptyList()

            if (images.isEmpty()) {
                return OperationResult(false, "Repack Super", "No partition images found in temp/img/")
            }

            // Build lpmake command
            val cmd = mutableListOf(lpmake, "--device-size=0x800000000",
                "--metadata-size=65536", "--super-name=super",
                "--metadata-slots=2")

            for (img in images) {
                val partitionName = img.nameWithoutExtension
                cmd.add("--partition=$partitionName:readonly:${img.length()}:0")
            }

            for (img in images) {
                val partitionName = img.nameWithoutExtension
                cmd.add("--image=$partitionName=${img.absolutePath}")
            }

            val outputFile = File(repackedDir, "super_repack.img")
            cmd.add("--output=${outputFile.absolutePath}")

            updateProgress("Menjalankan lpmake...")
            addLog("Running: lpmake ...")
            if (debugMode) addLog("[DEBUG] ${cmd.joinToString(" ")}")

            val result = ShellExecutor.executeWithProgress(
                command = cmd,
                workingDir = getTempDir(),
                onProgress = { addLog(it) }
            )

            if (result.exitCode == 0 && outputFile.exists()) {
                addLog("[OK] super_repack.img created: ${outputFile.length()} bytes")
                // Auto-copy to Downloads/AFFT
                copyResultToDownload(outputFile.absolutePath, "super_repack.img")
                OperationResult(true, "Repack Super", "Repack selesai",
                    outputFile.absolutePath)
            } else {
                addLog("[FAIL] lpmake failed (exit ${result.exitCode})")
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
        val fsFile = File(getTempDir(), "filesystem_src.img")
        if (!copyUriToFile(inputUri, fsFile)) {
            return OperationResult(false, "Extract Filesystem", "Failed to copy input file")
        }
        return extractFilesystem(fsFile)
    }

    suspend fun extractFilesystem(inputFile: File): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Extracting filesystem..."
        clearLogs()
        addLog("=== Extract Filesystem ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")
        addLog("[INFO] File size: ${inputFile.length()} bytes")

        val contentsDir = File(getTempDir(), "contents")
        val name = inputFile.nameWithoutExtension
        val outDir = File(contentsDir, name)
        outDir.mkdirs()

        return try {
            // Check if image is sparse Android format, convert with simg2img if needed
            updateProgress("Memeriksa format gambar...")
            val simg2imgBin = BinaryManager.getBinaryPath(context, "simg2img")
            var workingFile = inputFile

            if (simg2imgBin != null && isSparseImage(inputFile)) {
                updateProgress("Mengkonversi sparse ke raw image...")
                addLog("[INFO] Deteksi gambar sparse Android, mengkonversi ke raw...")
                val rawFile = File(getTempDir(), "${name}_raw.img")
                val convertResult = ShellExecutor.executeWithProgress(
                    command = listOf(simg2imgBin, inputFile.absolutePath, rawFile.absolutePath),
                    workingDir = getTempDir(),
                    onProgress = { addLog(it) }
                )
                if (convertResult.exitCode == 0 && rawFile.exists()) {
                    addLog("[OK] Konversi sparse->raw berhasil: ${rawFile.length()} bytes")
                    workingFile = rawFile
                } else {
                    addLog("[WARN] Konversi sparse gagal, menggunakan file asli")
                    convertResult.errorOutput.forEach { addLog("[ERROR] $it") }
                }
            } else if (simg2imgBin == null) {
                addLog("[INFO] simg2img tidak tersedia, gunakan file langsung")
            }

            // Detect filesystem type on the (possibly converted) raw file
            updateProgress("Menganalisis filesystem...")
            addLog("Mengidentifikasi tipe filesystem...")
            
            // Debug: check first bytes and EROFS magic
            if (debugMode) {
                try {
                    val debugBytes = ByteArray(16)
                    java.io.RandomAccessFile(workingFile, "r").use { it.readFully(debugBytes) }
                    val hexStr = debugBytes.joinToString(" ") { String.format("%02x", it.toInt() and 0xFF) }
                    addLog("[DEBUG] 16 bytes pertama: $hexStr")
                    
                    val erofsBytes = ByteArray(4)
                    java.io.RandomAccessFile(workingFile, "r").use { 
                        it.seek(0x400)
                        it.readFully(erofsBytes) 
                    }
                    val erofsHex = erofsBytes.joinToString(" ") { String.format("%02x", it.toInt() and 0xFF) }
                    addLog("[DEBUG] EROFS magic di 0x400: $erofsHex")
                } catch (e: Exception) {
                    addLog("[DEBUG] Gagal baca header: ${e.message}")
                }
            }
            
            val fsType = detectFilesystemType(workingFile)
            addLog("[INFO] Terdeteksi filesystem: $fsType")

            // === EROFS: fast path ===
            if (fsType == "erofs") {
                val extractTool = BinaryManager.getBinaryPath(context, "extract.erofs")
                if (extractTool != null) {
                    updateProgress("Mengekstrak EROFS filesystem...")
                    addLog("Menjalankan: extract.erofs ${workingFile.name} -> $name/")
                    val result = ShellExecutor.executeWithProgress(
                        command = listOf(extractTool, "-i", workingFile.absolutePath, "-x", "-o", outDir.absolutePath, "-f"),
                        workingDir = getTempDir(),
                        onProgress = { addLog(it) }
                    )
                    if (result.exitCode == 0) {
                        val fileCount = outDir.walkTopDown().count() - 1
                        addLog("[OK] EROFS filesystem terekstrak ke $name/ ($fileCount item)")
                        updateProgress("Ekstrak EROFS selesai! $fileCount item")
                        return OperationResult(true, "Extract Filesystem",
                            "EROFS extracted ($fileCount items)", outDir.absolutePath)
                    }
                    addLog("[FAIL] extract.erofs gagal (exit ${result.exitCode})")
                    result.errorOutput.forEach { addLog("[ERROR] $it") }
                } else {
                    addLog("[INFO] extract.erofs tidak tersedia")
                }
                addLog("[INFO] Mencoba debugfs sebagai fallback...")
            }

            // === GZIP: decompress and retry ===
            if (fsType == "gzip") {
                addLog("[INFO] File terkompresi gzip, mencoba dekompresi...")
                val decompressed = File(getTempDir(), "${name}_decompressed.img")
                val gunzipRes = ShellExecutor.executeWithProgress(
                    command = listOf("sh", "-c", "gzip -d -k -c '${workingFile.absolutePath}' > '${decompressed.absolutePath}'"),
                    workingDir = getTempDir(),
                    onProgress = { addLog(it) }
                )
                if (gunzipRes.exitCode == 0 && decompressed.exists()) {
                    addLog("[OK] Dekompresi berhasil, mendeteksi ulang...")
                    return extractFilesystem(decompressed)
                }
                addLog("[FAIL] Gagal dekompresi gzip, lanjut ke debugfs...")
            }

            // === UNKNOWN: try extract.erofs as fallback ===
            if (fsType == "unknown") {
                addLog("[INFO] Tipe filesystem tidak terdeteksi, mencoba EROFS...")
                val fallbackTool = BinaryManager.getBinaryPath(context, "extract.erofs")
                if (fallbackTool != null) {
                    val erofsResult = ShellExecutor.executeWithProgress(
                        command = listOf(fallbackTool, "-i", workingFile.absolutePath, "-x", "-o", outDir.absolutePath, "-f"),
                        workingDir = getTempDir(),
                        onProgress = { addLog(it) }
                    )
                    if (erofsResult.exitCode == 0) {
                        val fileCount = outDir.walkTopDown().count() - 1
                        addLog("[OK] EROFS filesystem terekstrak ke $name/ ($fileCount item)")
                        updateProgress("Ekstrak EROFS selesai! $fileCount item")
                        return OperationResult(true, "Extract Filesystem",
                            "EROFS extracted ($fileCount items)", outDir.absolutePath)
                    }
                    addLog("[INFO] extract.erofs gagal, mencoba debugfs...")
                } else {
                    addLog("[INFO] extract.erofs tidak tersedia, langsung ke debugfs...")
                }
            }

            // === DEBUGFS: universal fallback (ext4/unknown/erofs-fallback) ===
            val debugfsBin = BinaryManager.getBinaryPath(context, "debugfs")
                ?: return OperationResult(false, "Extract Filesystem", "debugfs not found (binary tidak ada)")

            updateProgress("Mengekstrak filesystem dengan debugfs...")
            addLog("Menjalankan: debugfs -R 'rdump /' ${workingFile.name} -> $name/")

            val debugfsResult = ShellExecutor.executeWithProgress(
                command = listOf(debugfsBin, "-R", "rdump / ${outDir.absolutePath}", workingFile.absolutePath),
                workingDir = getTempDir(),
                onProgress = { addLog(it) }
            )

            if (debugfsResult.exitCode == 0) {
                val fileCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
                addLog("[OK] Filesystem terekstrak ke $name/ ($fileCount item)")
                updateProgress("Ekstrak selesai! $fileCount item")
                OperationResult(true, "Extract Filesystem",
                    "Filesystem extracted ($fileCount items)", outDir.absolutePath)
            } else {
                addLog("[FAIL] debugfs rdump gagal (exit ${debugfsResult.exitCode})")
                debugfsResult.errorOutput.forEach { addLog("[ERROR] $it") }
                
                // Check if error is "Bad magic" - try EROFS as fallback
                val badMagicError = debugfsResult.errorOutput.any { 
                    it.contains("Bad magic number") || it.contains("Filesystem not open")
                }
                if (badMagicError) {
                    addLog("[INFO] Bad magic number! File mungkin EROFS, coba extract.erofs...")
                    val erofsTool = BinaryManager.getBinaryPath(context, "extract.erofs")
                    if (erofsTool != null) {
                        val erofsResult = ShellExecutor.executeWithProgress(
                            command = listOf(erofsTool, "-i", workingFile.absolutePath, "-x", "-o", outDir.absolutePath, "-f"),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) }
                        )
                        if (erofsResult.exitCode == 0) {
                            val fileCount = outDir.walkTopDown().count() - 1
                            addLog("[OK] EROFS filesystem terekstrak! ($fileCount item)")
                            updateProgress("Ekstrak EROFS selesai! $fileCount item")
                            return OperationResult(true, "Extract Filesystem",
                                "EROFS extracted ($fileCount items)", outDir.absolutePath)
                        }
                        addLog("[INFO] extract.erofs juga gagal")
                    }
                }
                
                if (workingFile != inputFile) {
                    addLog("[INFO] Mencoba debugfs dengan file raw...")
                    val rawResult = ShellExecutor.executeWithProgress(
                        command = listOf(debugfsBin, "-R", "rdump / ${outDir.absolutePath}", workingFile.absolutePath),
                        workingDir = getTempDir(),
                        onProgress = { addLog(it) }
                    )
                    if (rawResult.exitCode == 0) {
                        val fileCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
                        addLog("[OK] Filesystem terekstrak dari file raw! ($fileCount item)")
                        updateProgress("Ekstrak selesai! $fileCount item")
                        OperationResult(true, "Extract Filesystem",
                            "Filesystem extracted via raw ($fileCount items)", outDir.absolutePath)
                    } else {
                        addLog("[FAIL] debugfs pada raw juga gagal")
                        rawResult.errorOutput.forEach { addLog("[ERROR] $it") }
                        OperationResult(false, "Extract Filesystem",
                            "debugfs failed (exit ${debugfsResult.exitCode})")
                    }
                } else {
                    addLog("[INFO] debugfs rdump gagal. Mencoba ls untuk verifikasi...")
                    val lsResult = ShellExecutor.executeWithProgress(
                        command = listOf(debugfsBin, "-R", "ls -l /", workingFile.absolutePath),
                        workingDir = getTempDir(),
                        onProgress = { addLog(it) }
                    )
                    if (lsResult.exitCode == 0) {
                        addLog("[INFO] debugfs ls berhasil! Mencoba rdump dengan path absolut...")
                        val rdumpResult = ShellExecutor.executeWithProgress(
                            command = listOf(debugfsBin, "-R", "rdump / ${outDir.absolutePath}", workingFile.absolutePath),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) }
                        )
                        if (rdumpResult.exitCode == 0) {
                            addLog("[OK] Ekstrak berhasil dengan rdump!")
                            OperationResult(true, "Extract Filesystem",
                                "Filesystem extracted", outDir.absolutePath)
                        } else {
                            addLog("[FAIL] Semua metode debugfs gagal")
                            OperationResult(false, "Extract Filesystem",
                                "debugfs failed after all attempts")
                        }
                    } else {
                        addLog("[FAIL] debugfs tidak dapat membaca image")
                        OperationResult(false, "Extract Filesystem",
                            "debugfs cannot read this image")
                    }
                }
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            e.printStackTrace()
            OperationResult(false, "Extract Filesystem", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    /**
     * Check if file is an Android sparse image (magic: 0xED26FF3A).
     */
    private fun isSparseImage(file: File): Boolean {
        return try {
            val magic = ByteArray(4)
            RandomAccessFile(file, "r").use { it.readFully(magic) }
            magic[0] == 0x3A.toByte() && magic[1] == 0xFF.toByte() &&
            magic[2] == 0x26.toByte() && magic[3] == 0xED.toByte()
        } catch (e: Exception) {
            false
        }
    }

    private fun detectFilesystemType(file: File): String {
        // Delegate to SparseImage for core erofs/ext4/f2fs detection
        val detected = SparseImage.detectFilesystemType(file)
        return when (detected) {
            "unknown" -> {
                if (isGzipFile(file)) "gzip" else detected
            }
            else -> detected
        }
    }

    private fun isGzipFile(file: File): Boolean {
        return try {
            val magic = ByteArray(2)
            RandomAccessFile(file, "r").use { it.readFully(magic) }
            magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun repackFilesystem(dirName: String): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Repacking filesystem..."
        clearLogs()
        addLog("=== Repack Filesystem ===")

        return try {
            val contentsDir = File(getTempDir(), "contents")
            val srcDir = File(contentsDir, dirName)
            if (!srcDir.exists()) {
                return OperationResult(false, "Repack Filesystem",
                    "Directory not found: $dirName")
            }

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()
            val outputFile = File(repackedDir, "${dirName}_repack.img")

            // Try make_ext4fs first (for ext4), fallback to mkfs.erofs (for erofs)
            val makeExt4 = BinaryManager.getBinaryPath(context, "make_ext4fs")
            val mkfsErofs = BinaryManager.getBinaryPath(context, "mkfs.erofs")

            updateProgress("Repacking filesystem...")

            if (makeExt4 != null) {
                addLog("Running: make_ext4fs -s ${dirName}_repack.img $dirName/")
                // Estimate partition size (1.25x source dir size, rounded up)
                val sizeBytes = calculateDirSize(srcDir)
                val partitionSize = ((sizeBytes * 1.25).toLong() + 4095) / 4096 * 4096
                val result = ShellExecutor.executeWithProgress(
                    command = listOf(makeExt4, "-s", "-l", partitionSize.toString(),
                        outputFile.absolutePath, srcDir.absolutePath),
                    workingDir = getTempDir(),
                    onProgress = { addLog(it) }
                )

                if (result.exitCode == 0 && outputFile.exists()) {
                    addLog("[OK] Repacked ext4: ${outputFile.name} (${outputFile.length()} bytes)")
                    copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                    OperationResult(true, "Repack Filesystem", "Repack ext4 selesai",
                        outputFile.absolutePath)
                } else {
                    addLog("[FAIL] make_ext4fs failed (exit ${result.exitCode})")
                    if (mkfsErofs != null) {
                        addLog("[INFO] Mencoba mkfs.erofs...")
                        val result2 = ShellExecutor.executeWithProgress(
                            command = listOf(mkfsErofs, outputFile.absolutePath, srcDir.absolutePath),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) }
                        )
                        if (result2.exitCode == 0 && outputFile.exists()) {
                            addLog("[OK] Repacked EROFS: ${outputFile.name}")
                            copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                            OperationResult(true, "Repack Filesystem", "Repack EROFS selesai",
                                outputFile.absolutePath)
                        } else {
                            addLog("[FAIL] mkfs.erofs also failed (exit ${result2.exitCode})")
                            OperationResult(false, "Repack Filesystem",
                                "make_ext4fs & mkfs.erofs both failed")
                        }
                    } else {
                        OperationResult(false, "Repack Filesystem",
                            "make_ext4fs failed, mkfs.erofs not available")
                    }
                }
            } else if (mkfsErofs != null) {
                addLog("Running: mkfs.erofs ${dirName}_repack.img $dirName/")
                val result = ShellExecutor.executeWithProgress(
                    command = listOf(mkfsErofs, outputFile.absolutePath, srcDir.absolutePath),
                    workingDir = getTempDir(),
                    onProgress = { addLog(it) }
                )
                if (result.exitCode == 0 && outputFile.exists()) {
                    addLog("[OK] Repacked EROFS: ${outputFile.name}")
                    copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                    OperationResult(true, "Repack Filesystem", "Repack EROFS selesai",
                        outputFile.absolutePath)
                } else {
                    addLog("[FAIL] mkfs.erofs failed (exit ${result.exitCode})")
                    OperationResult(false, "Repack Filesystem",
                        "mkfs.erofs failed (exit ${result.exitCode})")
                }
            } else {
                addLog("[FAIL] Tidak ada binary repack (make_ext4fs, mkfs.erofs)")
                OperationResult(false, "Repack Filesystem",
                    "Tidak ada binary repack yang tersedia")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Repack Filesystem", e.message ?: "Unknown error")
        } finally {
            _isRunning.value = false
        }
    }

    private fun calculateDirSize(dir: File): Long {
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            16777216L // 16MB default
        }
    }

    suspend fun unpackBoot(inputUri: Uri, bootType: String): OperationResult {
        ensureDirs()
        val bootFile = File(getTempDir(), "boot/${bootType}")
        if (!copyUriToFile(inputUri, bootFile)) {
            return OperationResult(false, "Unpack Boot", "Failed to copy input file")
        }
        return unpackBoot(bootFile, bootType)
    }

    suspend fun unpackBoot(inputFile: File, bootType: String): OperationResult {
        ensureDirs()
        _isRunning.value = true
        _progressMessage.value = "Unpacking $bootType..."
        clearLogs()
        addLog("=== Unpack $bootType ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        return try {
            val magisk = BinaryManager.getBinaryPath(context, "magiskboot")
                ?: return OperationResult(false, "Unpack Boot", "magiskboot not found (binary tidak ada)")

            val outDir = File(getTempDir(), "boot_out/${bootType}_out")
            outDir.mkdirs()

            // Copy boot image to outDir (magiskboot unpack works in current directory)
            val bootCopy = File(outDir, bootType)
            inputFile.copyTo(bootCopy, overwrite = true)

            updateProgress("Unpacking $bootType...")
            addLog("Running: magiskboot unpack ${bootType} (in ${outDir.name}/)")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(magisk, "unpack", bootCopy.absolutePath),
                workingDir = outDir,
                onProgress = { addLog(it) }
            )

            if (result.exitCode == 0) {
                addLog("[OK] $bootType unpacked to $outDir")
                // List extracted files
                val files = outDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
                files.forEach { addLog("  - $it") }
                OperationResult(true, "Unpack $bootType", "Boot unpacked",
                    outDir.absolutePath)
            } else {
                addLog("[FAIL] magiskboot unpack failed (exit ${result.exitCode})")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
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
        _progressMessage.value = "Repacking $bootType..."
        clearLogs()
        addLog("=== Repack $bootType ===")

        return try {
            val magisk = BinaryManager.getBinaryPath(context, "magiskboot")
                ?: return OperationResult(false, "Repack Boot", "magiskboot not found (binary tidak ada)")

            val outDir = File(getTempDir(), "boot_out/${bootType}_out")
            if (!outDir.exists()) {
                return OperationResult(false, "Repack $bootType",
                    "No unpacked boot found at boot_out/${bootType}_out/")
            }

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()
            val outputFile = File(repackedDir, bootType)

            // Copy the original boot image back if it exists
            val bootCopy = File(outDir, bootType)
            if (!bootCopy.exists()) {
                addLog("[FAIL] Original boot image not found in $outDir")
                return OperationResult(false, "Repack $bootType",
                    "Original boot image not found, re-extract first")
            }

            updateProgress("Repacking $bootType...")
            addLog("Running: magiskboot repack ${bootType}")
            addLog("[INFO] Working dir: ${outDir.absolutePath}")
            addLog("[INFO] Output: ${outputFile.absolutePath}")

            // magiskboot repack creates new-boot.img in the working directory
            val result = ShellExecutor.executeWithProgress(
                command = listOf(magisk, "repack", bootCopy.absolutePath),
                workingDir = outDir,
                onProgress = { addLog(it) }
            )

            // Check for new-boot.img in outDir
            val newBoot = File(outDir, "new-boot.img")
            if (result.exitCode == 0 && newBoot.exists()) {
                newBoot.copyTo(outputFile, overwrite = true)
                addLog("[OK] ${bootType} repacked: ${outputFile.absolutePath}")
                copyResultToDownload(outputFile.absolutePath, bootType)
                OperationResult(true, "Repack $bootType",
                    "Repack selesai: $bootType", outputFile.absolutePath)
            } else {
                addLog("[FAIL] magiskboot repack failed (exit ${result.exitCode})")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
                OperationResult(false, "Repack $bootType",
                    "magiskboot repack failed (exit ${result.exitCode})")
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
            _isRunning.value = true
            clearLogs()
            addLog("=== Export All to Downloads ===")
            var result: OperationResult = OperationResult(true, "Export All", "")
            try {
                updateProgress("Mengekspor hasil kerja ke Downloads/AFFT/...")
                val tempDir = getTempDir()
                if (!tempDir.exists()) {
                    return@withContext OperationResult(false, "Export All", "Folder temp belum ada")
                }

                val subdirsToCopy = listOf("payload", "img", "repacked", "boot_out", "contents", "logs")
                var copiedCount = 0
                for (subdir in subdirsToCopy) {
                    val src = File(tempDir, subdir)
                    if (src.exists() && src.isDirectory) {
                        val files = src.listFiles()
                        if (!files.isNullOrEmpty()) {
                            if (exportDirectoryToDownloads(src, subdir)) {
                                copiedCount++
                            }
                        } else {
                            addLog("  [INFO] $subdir/ is empty, skipping")
                        }
                    } else {
                        addLog("  [INFO] $subdir/ does not exist, skipping")
                    }
                }

                // Also export input/ if not empty
                val inputDir = getInputDir()
                if (inputDir.exists()) {
                    val inputFiles = inputDir.listFiles()
                    if (!inputFiles.isNullOrEmpty()) {
                        if (exportDirectoryToDownloads(inputDir, "input")) {
                            copiedCount++
                        }
                    }
                }

                if (copiedCount > 0) {
                    addLog("[OK] Hasil kerja diekspor ke Downloads/AFFT/ ($copiedCount folders)")
                    updateProgress("Ekspor selesai! $copiedCount folder(s) exported")
                    result = OperationResult(true, "Export All",
                        "Diekspor ke Downloads/AFFT/ ($copiedCount folders)",
                        "/storage/emulated/0/Download/AFFT")
                } else {
                    addLog("[INFO] Tidak ada data untuk diekspor")
                    updateProgress("Tidak ada data untuk diekspor")
                    result = OperationResult(true, "Export All", "Tidak ada data untuk diekspor")
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

    suspend fun exportSelectedToDownloads(selectedFolders: List<String>): OperationResult {
        return withContext(Dispatchers.IO) {
            _isRunning.value = true
            clearLogs()
            addLog("=== Export Selected to Downloads ===")
            var result: OperationResult = OperationResult(true, "Export Selected", "")
            try {
                updateProgress("Mengekspor folder terpilih ke Downloads/AFFT/...")
                val tempDir = getTempDir()
                if (!tempDir.exists()) {
                    return@withContext OperationResult(false, "Export Selected", "Folder temp belum ada")
                }
                
                var copiedCount = 0
                for (subdir in selectedFolders) {
                    when (subdir) {
                        "input" -> {
                            val inputDir = getInputDir()
                            if (inputDir.exists()) {
                                val inputFiles = inputDir.listFiles()
                                if (!inputFiles.isNullOrEmpty()) {
                                    if (exportDirectoryToDownloads(inputDir, "input")) {
                                        copiedCount++
                                    }
                                } else {
                                    addLog("  [INFO] input/ is empty, skipping")
                                }
                            }
                        }
                        else -> {
                            val src = File(tempDir, subdir)
                            if (src.exists() && src.isDirectory) {
                                val files = src.listFiles()
                                if (!files.isNullOrEmpty()) {
                                    if (exportDirectoryToDownloads(src, subdir)) {
                                        copiedCount++
                                    }
                                } else {
                                    addLog("  [INFO] $subdir/ is empty, skipping")
                                }
                            } else {
                                addLog("  [INFO] $subdir/ does not exist, skipping")
                            }
                        }
                    }
                }
                
                if (copiedCount > 0) {
                    addLog("[OK] $copiedCount folder(s) diekspor ke Downloads/AFFT/")
                    updateProgress("Ekspor selesai! $copiedCount folder(s) exported")
                    result = OperationResult(true, "Export Selected",
                        "Diekspor ke Downloads/AFFT/ ($copiedCount folders)",
                        "/storage/emulated/0/Download/AFFT")
                } else {
                    addLog("[INFO] Tidak ada data untuk diekspor")
                    updateProgress("Tidak ada data untuk diekspor")
                    result = OperationResult(true, "Export Selected", "Tidak ada data untuk diekspor")
                }
            } catch (e: Exception) {
                addLog("[ERROR] Export gagal: ${e.message}")
                result = OperationResult(false, "Export Selected", e.message ?: "Unknown error")
            } finally {
                _isRunning.value = false
            }
            result
        }
    }

    private fun saveFileToDownloadsMediaStore(sourceFile: File, relativePath: String, fileName: String): Boolean {
        // MediaStore.Downloads requires Android 10+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            addLog("[INFO] MediaStore tidak tersedia (Android < 10)")
            return false
        }
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/AFFT/$relativePath")
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { input -> input.copyTo(outputStream) }
            }
            addLog("[OK] Disimpan via MediaStore: Download/AFFT/$relativePath/$fileName")
            true
        } catch (e: Exception) {
            addLog("[ERROR] MediaStore gagal: ${e.message}")
            false
        }
    }

    private suspend fun exportDirectoryToDownloads(srcDir: File, subdirName: String): Boolean {
        return try {
            val downloadsDir = File("/storage/emulated/0/Download/AFFT")
            // Try direct path first
            try {
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val dest = File(downloadsDir, subdirName)
                if (dest.exists()) dest.deleteRecursively()
                srcDir.copyRecursively(dest, overwrite = true)
                if (dest.exists() && dest.listFiles()?.isNotEmpty() == true) {
                    addLog("  Exported: $subdirName/")
                    return true
                }
            } catch (e: Exception) {
                if (debugMode) addLog("[DEBUG] Direct export gagal: ${e.message}, fallback MediaStore")
            }
            // Fallback: MediaStore
            addLog("  [INFO] Mencoba via MediaStore untuk $subdirName...")
            var successCount = 0
            val files = srcDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (file.isFile) {
                    if (saveFileToDownloadsMediaStore(file, subdirName, file.name)) successCount++
                } else if (file.isDirectory) {
                    val nestedFiles = file.listFiles() ?: emptyArray()
                    for (nf in nestedFiles) {
                        if (saveFileToDownloadsMediaStore(nf, "${subdirName}/${file.name}", nf.name)) successCount++
                    }
                }
            }
            if (successCount > 0) {
                addLog("  Exported (MediaStore): $subdirName/ ($successCount files)")
                true
            } else {
                addLog("  [ERROR] Gagal mengekspor $subdirName/")
                false
            }
        } catch (e: Exception) {
            addLog("  [ERROR] Export $subdirName gagal: ${e.message}")
            false
        }
    }

    suspend fun copyResultToDownload(resultPath: String, destName: String): Boolean {
        return try {
            val sourceFile = File(resultPath)
            if (!sourceFile.exists()) {
                if (debugMode) addLog("[DEBUG] Source file not found: $resultPath")
                return false
            }

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

    /**
     * List files in the input/ directory.
     */
    suspend fun listInputFiles(): List<File> {
        val inputDir = getInputDir()
        if (!inputDir.exists()) return emptyList()
        return withContext(Dispatchers.IO) {
            inputDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        }
    }

    /**
     * Get the most recently modified file from input/ directory.
     * Used to auto-select files after app restart.
     */
    suspend fun getLatestInputFile(): File? {
        val files = listInputFiles()
        return withContext(Dispatchers.IO) {
            files.maxByOrNull { it.lastModified() }
        }
    }
}
