package com.afft.app.service

import android.content.Context
import android.net.Uri
import android.os.Build
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
import kotlin.text.Regex
import java.io.File
import kotlin.text.RegexOutputStream
import java.io.RandomAccessFile
import com.afft.app.service.AFFTExtractService

class AFFTService(private val context: Context) {

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Buffer mutable untuk in-memory logs dengan batas aman (hindari O(n²) copy)
    private val logBuffer = mutableListOf<String>()
    private val maxInMemoryLogs = 1500

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

    // Foreground service untuk mencegah proses anak di-freeze oleh sistem
    private var foregroundActive = false

    private fun ensureForegroundRunning() {
        if (!foregroundActive) {
            foregroundActive = true
            try {
                AFFTExtractService.start(context)
            } catch (_: Exception) {}
        }
    }

    private fun ensureForegroundStopped() {
        if (foregroundActive) {
            foregroundActive = false
            try {
                AFFTExtractService.stop(context)
            } catch (_: Exception) {}
        }
    }


    private var _currentLogFile: File? = null
    val currentLogFile: File? get() = _currentLogFile

    init {
        // Inisialisasi log file saat service dibuat
        try { initLogFile() } catch (_: Exception) {}
    }

    private fun initLogFile() {
        try {
            val logsDir = File(getTempDir(), "logs")
            logsDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(java.util.Date())
            _currentLogFile = File(logsDir, "log_${timestamp}.txt")
            _currentLogFile?.writeText("=== AFFT Log Session: $timestamp ===\n")
            android.util.Log.d("AFFTService", "[LOG] Log file: ${_currentLogFile?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("AFFTService", "Gagal init log file: ${e.message}")
        }
    }
    // Filter progress bar lines (noise reduction)
    private fun isProgressBarLine(text: String): Boolean {
        // Lewati line yang hanya progress bar dari payload-dumper-go
        // Contoh: "system (821 MB) [========>       ] 45%"
        return text.matches(Regex(".*\\[= >]+\\].*\\d+%"))
    }

    // Throttle mechanism: only update StateFlow every 300ms
    private var lastStateUpdate = 0L
    private val stateUpdateInterval = 300L

    private fun addLog(text: String) {
        // Filter progress bar lines
        if (isProgressBarLine(text)) {
            // Still write to log file, but skip StateFlow update
            val logFile = _currentLogFile
            if (logFile != null) {
                try {
                    logFile.appendText("$text\n")
                } catch (e: Exception) {}
            }
            return
        }

        // Efficient O(1) add ke mutable buffer (hindari O(n) copy listOf)
        logBuffer.add(text)
        // Trim buffer jika melebihi batas (hapus paling lama)
        if (logBuffer.size > maxInMemoryLogs + 200) {
            val excess = logBuffer.size - maxInMemoryLogs
            repeat(excess) { logBuffer.removeAt(0) }
        }
        
        // Throttle StateFlow update (max every 300ms)
        val now = System.currentTimeMillis()
        if (now - lastStateUpdate >= stateUpdateInterval) {
            // Snapshot untuk state
            _logs.value = logBuffer.toList()
            lastStateUpdate = now
        }
        
        android.util.Log.d("AFFTService", text)
        // Write to log file
        val logFile = _currentLogFile
        if (logFile != null) {
            try {
                logFile.appendText("$text\n")
            } catch (e: Exception) {
                android.util.Log.w("AFFTService", "Gagal tulis log file: ${e.message}")
            }
        }
    }

    private fun updateProgress(msg: String) {
        _progressMessage.value = msg
        if (debugMode) addLog("[INFO] $msg")
        android.util.Log.d("AFFTService", msg)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        logBuffer.clear()
        _progressMessage.value = ""
        initLogFile()
    }



    suspend fun getLogFiles(): List<File> {
        val logsDir = File(getTempDir(), "logs")
        if (!logsDir.exists()) return emptyList()
        return withContext(Dispatchers.IO) {
            logsDir.listFiles()?.filter { it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    suspend fun getLogContent(logFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                logFile.readText()
            } catch (e: Exception) {
                "Gagal membaca log: ${e.message}"
            }
        }
    }

    fun getLogsDir(): File = File(getTempDir(), "logs")

    /**
     * Simpan semua log saat ini ke file di Downloads/AFFT/logs/
     */
    suspend fun saveCurrentLogToDownloads(): File? {
        val logs = _logs.value
        if (logs.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                val downloadDir = File("/storage/emulated/0/Download/AFFT/logs")
                downloadDir.mkdirs()
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val logFile = File(downloadDir, "afft_log_$timestamp.txt")
                logFile.writeText(logs.joinToString("\n"))
                addLog("[OK] Log tersimpan: ${logFile.absolutePath}")
                logFile
            } catch (e: Exception) {
                addLog("[ERROR] Gagal simpan log: ${e.message}")
                null
            }
        }
    }

    /**
     * Hapus log file lama, sisakan hanya [maxFiles] terbaru
     */
    suspend fun clearOldLogs(maxFiles: Int = 20) {
        withContext(Dispatchers.IO) {
            try {
                val logsDir = File(getTempDir(), "logs")
                if (!logsDir.exists()) return@withContext
                val files = logsDir.listFiles()
                    ?.filter { it.name.endsWith(".txt") }
                    ?.sortedByDescending { it.lastModified() } ?: return@withContext
                if (files.size <= maxFiles) return@withContext
                files.drop(maxFiles).forEach { file ->
                    try { file.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun getFreeSpace(path: String): Long {
        return try {
            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()
            dir.freeSpace
        } catch (e: Exception) {
            -1L
        }
    }

    fun checkStorageSpace(fileSize: Long, destPath: String): Boolean {
        val free = getFreeSpace(destPath)
        if (free <= 0) return true // can't check, allow
        if (fileSize > free) {
            addLog("[ERROR] Ruang penyimpanan tidak cukup! Butuh ${formatFileSizePublic(fileSize)}, tersedia ${formatFileSizePublic(free)}")
            return false
        }
        return true
    }

    suspend fun deleteFileWithSafety(file: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val workDir = getWorkDir()
                val canonWork = workDir.canonicalPath
                val canonFile = file.canonicalPath
                val downloadAFFT = File("/storage/emulated/0/Download/AFFT")
                val canonDl = downloadAFFT.canonicalPath

                // Izinkan hapus di workDir ATAU di Downloads/AFFT
                val allowed = canonFile.startsWith(canonWork + File.separator) ||
                              canonFile.startsWith(canonDl + File.separator)
                if (!allowed) {
                    addLog("[ERROR] Safety abort: ${file.name} di luar work dir & Downloads/AFFT!")
                    return@withContext false
                }

                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }

                if (file.exists()) {
                    addLog("[ERROR] Gagal menghapus: ${file.name} (masih ada)")
                    false
                } else {
                    addLog("[OK] Dihapus: ${file.name}")
                    true
                }
            } catch (e: Exception) {
                addLog("[ERROR] Gagal menghapus ${file.name}: ${e.message}")
                false
            }
        }
    }

    suspend fun copyFileTo(src: File, destDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Validasi sumber
                if (!src.exists()) {
                    addLog("[ERROR] Sumber tidak ditemukan: ${src.absolutePath}")
                    return@withContext false
                }
                if (!src.canRead()) {
                    addLog("[ERROR] Tidak bisa membaca: ${src.absolutePath} (izin?)")
                    return@withContext false
                }

                val size = if (src.isDirectory) src.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                           else src.length()
                addLog("[INFO] Ukuran: ${formatFileSizePublic(size)}")

                if (!checkStorageSpace(size, destDir.absolutePath)) {
                    return@withContext false
                }
                if (!destDir.exists()) destDir.mkdirs()
                if (!destDir.exists()) {
                    addLog("[ERROR] Gagal membuat folder tujuan: ${destDir.absolutePath}")
                    return@withContext false
                }

                val dest = resolveDestFile(src, destDir)
                addLog("[INFO] Menyalin: ${src.name} → ${dest.parent}")

                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = false)
                } else {
                    // Gunakan stream untuk memastikan file benar-benar tersalin
                    src.inputStream().use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Verifikasi hasil
                if (dest.exists() && (dest.isDirectory || dest.length() == src.length())) {
                    addLog("[OK] Disalin: ${src.name} → ${destDir.name}/")
                    true
                } else {
                    val destSize = if (dest.exists()) formatFileSizePublic(dest.length()) else "0"
                    addLog("[ERROR] Hasil copy tidak valid! Dest size: $destSize")
                    false
                }
            } catch (e: Exception) {
                addLog("[ERROR] Gagal menyalin ${src.name}: ${e.message}")
                false
            }
        }
    }

    suspend fun moveFileTo(src: File, destDir: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!src.exists()) {
                    addLog("[ERROR] Sumber tidak ditemukan: ${src.absolutePath}")
                    return@withContext false
                }
                val size = if (src.isDirectory) src.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                           else src.length()
                if (!checkStorageSpace(size, destDir.absolutePath)) {
                    return@withContext false
                }
                if (!destDir.exists()) destDir.mkdirs()
                val dest = resolveDestFile(src, destDir)
                addLog("[INFO] Memindah: ${src.name} → ${dest.parent}")

                var moved = src.renameTo(dest)
                if (!moved) {
                    addLog("[INFO] rename gagal, fallback copy+delete...")
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = true)
                        src.deleteRecursively()
                    } else {
                        src.inputStream().use { input ->
                            dest.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        src.delete()
                    }
                    moved = true
                }

                if (dest.exists()) {
                    addLog("[OK] Dipindah: ${src.name} → ${destDir.name}/")
                    true
                } else {
                    addLog("[ERROR] Gagal memindah: ${src.name}")
                    false
                }
            } catch (e: Exception) {
                addLog("[ERROR] Gagal memindah ${src.name}: ${e.message}")
                false
            }
        }
    }

    private fun resolveDestFile(src: File, destDir: File): File {
        var dest = File(destDir, src.name)
        var counter = 1
        while (dest.exists()) {
            val name = src.nameWithoutExtension
            val ext = src.extension
            val newName = if (ext.isNotEmpty()) "${name}_$counter.$ext" else "${name}_$counter"
            dest = File(destDir, newName)
            counter++
        }
        return dest
    }

    suspend fun pickAndCopyToInput(uri: Uri): File? {
        return copyPickedFileToInput(uri)
    }

    private fun formatFileSizePublic(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(size.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
    fun getWorkDir(): File {
        val baseDir = context.getExternalFilesDir(null)
        if (baseDir == null) {
            // Fallback ke internal jika external storage tidak tersedia
            val dir = File(context.filesDir, "afft_work")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
        val dir = File(baseDir, "afft_work")
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
        File(getTempDir(), "Payload").mkdirs()
        File(getTempDir(), "boot").mkdirs()
        File(getTempDir(), "boot_out").mkdirs()
        File(getTempDir(), "img_src").mkdirs()
        File(getTempDir(), "filesystem_work").mkdirs()
        File(getTempDir(), "logs").mkdirs()
        initLogFile()
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
        val originalName = resolveFileName(inputUri) ?: "payload_src.bin"
        val payloadFile = File(getTempDir(), originalName)
        if (!copyUriToFile(inputUri, payloadFile)) {
            return OperationResult(false, "Extract Payload", "Failed to copy input file")
        }
        addLog("[INFO] File sumber: $originalName (${payloadFile.length()} bytes)")
        return extractPayload(payloadFile)
    }

    suspend fun extractPayload(inputFile: File): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        _isRunning.value = true
        clearLogs()
        addLog("=== Extract payload.bin ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        return try {
            val payloadDumper = BinaryManager.getBinaryPath(context, "payload-dumper-go")
                ?: return OperationResult(false, "Extract Payload", "payload-dumper-go not found")

            updateProgress("Extracting payload, mohon tunggu...")
            addLog("Running payload-dumper-go...")
            
            // Deteksi mode eksekusi: cek ELF program headers untuk PT_INTERP
            val isStaticBinary = try {
                val elfFile = java.io.RandomAccessFile(payloadDumper, "r")
                val magic = elfFile.readInt()
                if (magic.toLong() != 0x464c457fL) { // ELF magic (ELF)
                    elfFile.close()
                    false
                } else {
                    val eiClass = elfFile.readByte()  // EI_CLASS at offset 4
                    elfFile.skipBytes(11) // skip to e_phoff (offset 0x1C for 32-bit, 0x20 for 64-bit)
                    val is64Bit = eiClass.toInt() == 2
                    if (is64Bit) {
                        elfFile.skipBytes(3) // adjust alignment
                    }
                    val ePhoff = if (is64Bit) elfFile.readLong() else elfFile.readInt().toLong()
                    val ePhentsize = if (is64Bit) elfFile.readShort() else elfFile.readShort()
                    val ePhnum = if (is64Bit) elfFile.readShort() else elfFile.readShort()
                    
                    // Scan program headers for PT_INTERP (type = 3)
                    var hasInterp = false
                    elfFile.seek(ePhoff)
                    for (i in 0 until ePhnum) {
                        val pType = elfFile.readInt()
                        if (pType == 3) {
                            hasInterp = true
                            break
                        }
                        // Skip to next program header
                        elfFile.skipBytes(ePhentsize.toInt() - 4)
                    }
                    elfFile.close()
                    !hasInterp
                }
            } catch (e: Exception) { false }
            addLog("[INFO] Mode: ${if (isStaticBinary) "static binary" else "dynamic binary (linker64)"}")
            
            // Set LD_LIBRARY_PATH untuk memastikan liblzma.so.5 ditemukan
            // (dibutuhkan oleh payload-dumper-go yang dynamic link via CGO)
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val binDir = BinaryManager.getBinDirectory(context).absolutePath
            val ldLibraryPath = "$nativeLibDir:$binDir"
            addLog("[INFO] LD_LIBRARY_PATH=$ldLibraryPath")
            
            val cores = Runtime.getRuntime().availableProcessors()
            val concurrency = maxOf(2, cores - 2)
            addLog("[INFO] CPU cores: $cores, concurrency: -c $concurrency")
            val result = ShellExecutor.executeWithProgress(
                command = listOf(payloadDumper, inputFile.absolutePath, "-o",
                    File(getTempDir(), "Payload").absolutePath, "-c", concurrency.toString()),
                workingDir = getTempDir(),
                envVars = mapOf("LD_LIBRARY_PATH" to ldLibraryPath),
                onProgress = { addLog(it) },
                timeoutMillis = 1800000L
            )

            if (result.isTimeout) {
                addLog("[TIMEOUT] Extract Payload: proses tidak selesai dalam 30 menit, dibatalkan")
                addLog("[TIMEOUT] Perangkat mungkin kekurangan RAM atau sistem membekukan proses")
                return OperationResult(false, "Extract Payload", "Timeout: proses tidak selesai dalam 30 menit")
            } else if (result.exitCode == 0) {
                addLog("[OK] Payload extracted successfully")
                OperationResult(true, "Extract Payload", "Payload extracted successfully",
                    File(getTempDir(), "Payload").absolutePath)
            } else {
                addLog("[FAIL] payload-dumper-go exited with code ${result.exitCode}")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
                
                // Cek apakah error karena missing library
                val linkError = result.errorOutput.any {
                    it.contains("CANNOT LINK", ignoreCase = true) ||
                    it.contains("library.*not found".toRegex())
                }
                if (linkError) {
                    addLog("[INFO] Mencoba fallback: deploy binary dari assets + LD_LIBRARY_PATH...")
                    addLog("[INFO] Pastikan payload-dumper-go adalah static binary (CGO_ENABLED=0)")
                    addLog("[INFO] atau bundle liblzma.so.5 di nativeLibraryDir")
                    
                    // Coba deploy dan jalankan dari filesDir dengan LD_LIBRARY_PATH
                    val fallbackResult = runPayloadDumperFallback(inputFile)
                    if (fallbackResult != null) return fallbackResult
                }
                
                OperationResult(false, "Extract Payload",
                    "payload-dumper-go failed (exit ${result.exitCode})")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            OperationResult(false, "Extract Payload", e.message ?: "Unknown error")
        } finally {
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    /**
     * Fallback: deploy payload-dumper-go dari assets ke filesDir dan jalankan
     * dengan LD_LIBRARY_PATH yang sesuai.
     */
    private suspend fun runPayloadDumperFallback(inputFile: File): OperationResult? {
        return try {
            val binDir = BinaryManager.getBinDirectory(context)
            val localBinary = File(binDir, "payload-dumper-go")
            
            // Deploy payload-dumper-go dari assets jika perlu
            if (!localBinary.exists() || !localBinary.canExecute()) {
                try {
                    context.assets.open("bin/payload-dumper-go").use { input ->
                        localBinary.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    localBinary.setExecutable(true, false)
                    localBinary.setReadable(true, false)
                    addLog("[INFO] Deployed payload-dumper-go dari assets ke ${localBinary.absolutePath}")
                } catch (e: Exception) {
                    addLog("[WARN] Gagal deploy dari assets: ${e.message}")
                    return null
                }
            }
            
            // Juga deploy liblzma.so.5 jika ada di assets/bin/
            val libLzmaFile = File(binDir, "liblzma.so.5")
            if (!libLzmaFile.exists()) {
                try {
                    context.assets.open("bin/liblzma.so.5").use { input ->
                        libLzmaFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    libLzmaFile.setReadable(true, false)
                    addLog("[INFO] Deployed liblzma.so.5 dari assets ke ${libLzmaFile.absolutePath}")
                } catch (e: Exception) {
                    addLog("[WARN] liblzma.so.5 tidak tersedia di assets: ${e.message}")
                }
            }
            
            if (!localBinary.exists()) {
                addLog("[WARN] Binary tidak tersedia di assets maupun filesDir")
                return null
            }
            
            // Jalankan dengan LD_LIBRARY_PATH yang mencakup binDir (tempat liblzma.so.5)
            val ldPath = "${context.applicationInfo.nativeLibraryDir}:${binDir.absolutePath}"
            addLog("[INFO] Fallback: menjalankan dari ${localBinary.absolutePath}")
            addLog("[INFO] LD_LIBRARY_PATH=$ldPath")
            
            val envVars = mapOf("LD_LIBRARY_PATH" to ldPath)
            // Gunakan ShellExecutor.executeBinary() yang memiliki fallback:
            // direct -> linker64 -> sh -c (untuk mengatasi SELinux noexec)
            val fallbackResult = ShellExecutor.executeBinary(
                binaryPath = localBinary.absolutePath,
                args = listOf(inputFile.absolutePath, "-o",
                    File(getTempDir(), "Payload").absolutePath, "-c", concurrency.toString()),
                workingDir = getTempDir(),
                envVars = envVars,
                onOutput = { addLog(it) },
                timeoutMillis = 1800000L
            )
            
            if (fallbackResult.isTimeout) {
                addLog("[TIMEOUT] Fallback: proses tidak selesai dalam 30 menit, dibatalkan")
                return OperationResult(false, "Extract Payload", "Timeout: fallback juga tidak selesai")
            } else if (fallbackResult.exitCode == 0) {
                addLog("[OK] Payload extracted (fallback)")
                return OperationResult(true, "Extract Payload", "Payload extracted (fallback)",
                    File(getTempDir(), "Payload").absolutePath)
            }
            
            addLog("[FAIL] Fallback juga gagal (exit ${fallbackResult.exitCode})")
            fallbackResult.errorOutput.forEach { addLog("[ERROR] $it") }
            null
        } catch (e: Exception) {
            addLog("[WARN] Fallback error: ${e.message}")
            null
        }
    }

    suspend fun unpackSuper(inputUri: Uri): OperationResult {
        ensureDirs()
        val originalName = resolveFileName(inputUri) ?: "super_src.img"
        val superFile = File(getTempDir(), originalName)
        if (!copyUriToFile(inputUri, superFile)) {
            return OperationResult(false, "Unpack Super", "Failed to copy input file")
        }
        addLog("[INFO] File sumber: $originalName (${superFile.length()} bytes)")
        return unpackSuper(superFile)
    }

    suspend fun unpackSuper(inputFile: File): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
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
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    suspend fun repackSuper(): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
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
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    suspend fun extractFilesystem(inputUri: Uri): OperationResult {
        ensureDirs()
        // Get original filename from URI
        val originalName = resolveFileName(inputUri) ?: "filesystem_src.img"
        val fsFile = File(getTempDir(), originalName)
        if (!copyUriToFile(inputUri, fsFile)) {
            return OperationResult(false, "Extract Filesystem", "Failed to copy input file")
        }
        addLog("[INFO] File sumber: $originalName (${fsFile.length()} bytes)")
        return extractFilesystem(fsFile)
    }

    suspend fun extractFilesystem(inputFile: File): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
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
            // Check if image is sparse Android format, convert to raw if needed
            updateProgress("Memeriksa format gambar...")
            var workingFile = inputFile

            if (isSparseImage(inputFile)) {
                updateProgress("Mengkonversi sparse ke raw image (pure Kotlin)...")
                addLog("[INFO] Deteksi gambar sparse Android, mengkonversi ke raw...")
                val rawFile = File(getTempDir(), "${name}_raw.img")
                val convertOk = SparseImage.sparseToRaw(inputFile, rawFile)
                if (convertOk && rawFile.exists() && rawFile.length() > 0) {
                    addLog("[OK] Konversi sparse->raw berhasil: ${rawFile.length()} bytes")
                    workingFile = rawFile
                } else {
                    addLog("[WARN] Konversi sparse->raw gagal, menggunakan file asli")
                    addLog("[WARN] File mungkin bukan sparse image yang valid, lanjut dengan file asli")
                }
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
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
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
        ensureForegroundRunning()
        _isRunning.value = true
        _progressMessage.value = "Repacking filesystem..."
        clearLogs()
        addLog("=== Repack Filesystem ===")

        return try {
            val contentsDir = File(getTempDir(), "contents")
            val srcDir = File(contentsDir, dirName)
            if (!srcDir.exists()) {
                return OperationResult(
                    false, "Repack Filesystem",
                    "Directory not found: $dirName"
                )
            }

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()
            val outputFile = File(repackedDir, "${dirName}_repack.img")
            if (outputFile.exists()) outputFile.delete()

            // Cari binary dan file_contexts
            val makeExt4 = BinaryManager.getBinaryPath(context, "make_ext4fs")
            val mkfsErofs = BinaryManager.getBinaryPath(context, "mkfs.erofs")
            val fileContextsPath = findFileContexts(contentsDir, dirName)

            updateProgress("Repacking filesystem...")

            // Prioritaskan mkfs.erofs untuk Android modern (EROFS)
            // Fallback ke make_ext4fs untuk Android lama (ext4)
            if (mkfsErofs != null) {
                addLog("[INFO] Repack dengan mkfs.erofs -z lz4hc,9 -C 4096 ${dirName}_repack.img $dirName/")
                var erofsOk = repackErofs(
                    mkfsErofs, outputFile, srcDir, fileContextsPath, "lz4hc,9"
                )
                if (erofsOk) {
                    addLog("[OK] Repacked EROFS: ${outputFile.name} (${outputFile.length()} bytes)")
                    copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                    OperationResult(true, "Repack Filesystem", "Repack EROFS selesai",
                        outputFile.absolutePath)
                } else {
                    addLog("[INFO] lz4hc gagal, mencoba tanpa kompresi...")
                    if (outputFile.exists()) outputFile.delete()
                    erofsOk = repackErofs(
                        mkfsErofs, outputFile, srcDir, fileContextsPath, "none"
                    )
                    if (erofsOk) {
                        addLog("[OK] Repacked EROFS (uncompressed): ${outputFile.name}")
                        copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                        OperationResult(true, "Repack Filesystem",
                            "Repack EROFS (uncompressed) selesai", outputFile.absolutePath)
                    } else {
                        addLog("[FAIL] mkfs.erofs lz4hc dan none gagal, fallback ke make_ext4fs...")
                        if (outputFile.exists()) outputFile.delete()
                        // Fallback ke make_ext4fs
                        if (makeExt4 != null) {
                            val fallbackOk = repackExt4(makeExt4, outputFile, srcDir)
                            if (fallbackOk) {
                                copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                                OperationResult(true, "Repack Filesystem",
                                    "Repack ext4 (fallback) selesai", outputFile.absolutePath)
                            } else {
                                OperationResult(false, "Repack Filesystem",
                                    "mkfs.erofs & make_ext4fs all failed")
                            }
                        } else {
                            OperationResult(false, "Repack Filesystem",
                                "mkfs.erofs failed, make_ext4fs not available")
                        }
                    }
                }
                        } else if (makeExt4 != null) {
                addLog("Running: make_ext4fs -s ${dirName}_repack.img $dirName/")
                val repackOk = repackExt4(makeExt4, outputFile, srcDir)
                if (repackOk) {
                    addLog("[OK] Repacked ext4: ${outputFile.name} (${outputFile.length()} bytes)")
                    copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                    OperationResult(true, "Repack Filesystem", "Repack ext4 selesai",
                        outputFile.absolutePath)
                } else {
                    addLog("[FAIL] make_ext4fs gagal dan tidak ada mkfs.erofs sebagai fallback")
                    OperationResult(false, "Repack Filesystem",
                        "make_ext4fs failed, mkfs.erofs not available")
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
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    private fun calculateDirSize(dir: File): Long {
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            16777216L // 16MB default
        }
    }

    /**
     * Helper untuk menjalankan mkfs.erofs dengan parameter kompresi spesifik.
     * Mengikuti standar Google:
     *   -z lz4hc,9 -C 4096 untuk kompresi tinggi (standar Android modern)
     *   -z none untuk uncompressed EROFS
     *   --file-contexts=<path> untuk menyertakan konteks keamanan
     */
    private suspend fun repackErofs(
        mkfsErofs: String,
        outputFile: File,
        srcDir: File,
        fileContextsPath: String?,
        compression: String
    ): Boolean {
        if (outputFile.exists()) outputFile.delete()

        val cmdArgs = mutableListOf(
            mkfsErofs, "-z", compression, "-C", "4096"
        )
        if (fileContextsPath != null) {
            cmdArgs.add("--file-contexts=$fileContextsPath")
        }
        cmdArgs.add(outputFile.absolutePath)
        cmdArgs.add(srcDir.absolutePath)

        val compressionLabel = when (compression) {
            "lz4hc,9" -> "lz4hc level 9"
            "none" -> "tanpa kompresi"
            else -> compression
        }
        addLog("[INFO] Menjalankan mkfs.erofs ($compressionLabel) dengan flags: -z $compression -C 4096${if (fileContextsPath != null) " --file-contexts=$fileContextsPath" else ""}")

        val result = ShellExecutor.executeWithProgress(
            command = cmdArgs,
            workingDir = getTempDir(),
            onProgress = { addLog(it) }
        )

        if (result.exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
            addLog("[OK] mkfs.erofs ($compressionLabel) berhasil: ${outputFile.length()} bytes")
            return true
        }
        addLog("[FAIL] mkfs.erofs ($compressionLabel) gagal (exit ${result.exitCode})")
        result.errorOutput.forEach { addLog("[ERROR] $it") }
        return false
    }

    /**
     * Mencari file file_contexts di direktori hasil ekstraksi.
     * file_contexts diperlukan untuk menjaga hak akses (SELinux contexts)
     * saat me-repack dan mem-flash ke perangkat.
     */

    /**
     * Helper untuk menjalankan make_ext4fs dengan parameter yang benar.
     * Digunakan sebagai fallback ketika mkfs.erofs tidak tersedia.
     */
    private suspend fun repackExt4(
        makeExt4: String,
        outputFile: File,
        srcDir: File
    ): Boolean {
        if (outputFile.exists()) outputFile.delete()

        val sizeBytes = calculateDirSize(srcDir)
        val partitionSize = ((sizeBytes * 1.25).toLong() + 4095) / 4096 * 4096
        addLog("[INFO] Menjalankan make_ext4fs -s -l $partitionSize ${outputFile.name} ${srcDir.name}/")

        val result = ShellExecutor.executeWithProgress(
            command = listOf(
                makeExt4, "-s", "-l", partitionSize.toString(),
                outputFile.absolutePath, srcDir.absolutePath
            ),
            workingDir = getTempDir(),
            onProgress = { addLog(it) }
        )

        if (result.exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
            addLog("[OK] make_ext4fs berhasil: ${outputFile.length()} bytes")
            return true
        }
        addLog("[FAIL] make_ext4fs gagal (exit ${result.exitCode})")
        result.errorOutput.forEach { addLog("[ERROR] $it") }
        return false
    }
    private fun findFileContexts(contentsDir: File, dirName: String): String? {
        val candidates = listOf(
            File(contentsDir, "file_contexts"),
            File(contentsDir, "config/file_contexts"),
            File(contentsDir.parentFile, "file_contexts"),
            File(contentsDir, "${dirName}/file_contexts")
        )
        for (f in candidates) {
            if (f.exists() && f.isFile) {
                addLog("[INFO] Ditemukan file_contexts: ${f.absolutePath}")
                return f.absolutePath
            }
        }
        addLog("[INFO] file_contexts tidak ditemukan, repo tanpa konteks keamanan")
        return null
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
        ensureForegroundRunning()
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
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    suspend fun repackBoot(bootType: String): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
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
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    fun cleanOutput() {
        val defaultDirs = listOf(
            "img", "contents", "repacked", "Payload",
            "boot", "boot_out", "img_src", "filesystem_work", "logs"
        )
        cleanSelected(defaultDirs)
    }

    fun cleanSelected(selectedDirs: List<String>) {
        val tempDir = getTempDir()
        // Safety: gunakan canonical path untuk cegah symlink traversal
        // Base untuk validasi adalah parent dari tempDir (yaitu workDir)
        val workDir = tempDir.parentFile ?: return
        val canonWork = try { workDir.canonicalPath } catch (e: Exception) { workDir.absolutePath }
        val canonTemp = try { tempDir.canonicalPath } catch (e: Exception) { tempDir.absolutePath }
        if (!canonTemp.startsWith(canonWork)) {
            addLog("[ERROR] Safety abort: temp dir is outside work directory!")
            return
        }
        if (!tempDir.exists()) {
            addLog("[WARN] Temp dir does not exist, nothing to clean")
            return
        }
        clearLogs()
        addLog("=== Clean Selected ===")

        for (dirName in selectedDirs) {
            val dir = File(tempDir, dirName)
            if (dir.exists()) {
                // Double-check path with canonical
                val canonDir = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
                val canonTempSep = try { tempDir.canonicalPath + File.separator } catch (e: Exception) { tempDir.absolutePath + File.separator }
                if (!canonDir.startsWith(canonTempSep)) {
                    addLog("[ERROR] Safety abort: $dirName/ is outside temp dir!")
                    continue
                }
                dir.deleteRecursively()
                addLog("Cleaned: $dirName/")
            } else {
                addLog("[INFO] $dirName/ does not exist, skipping")
            }
        }
        ensureDirs()

        addLog("[OK] Clean selesai untuk ${selectedDirs.size} folder")
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

                val subdirsToCopy = listOf("Payload", "img", "repacked", "boot_out", "contents", "logs")
                var copiedCount = 0
                for (subdir in subdirsToCopy) {
                    val src = File(tempDir, subdir)
                    if (src.exists() && src.isDirectory) {
                        val files = src.listFiles()
                        if (!files.isNullOrEmpty()) {
                            if (moveDirectoryToDownloads(src, subdir)) {
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
                        if (moveDirectoryToDownloads(inputDir, "input")) {
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
                // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
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
                                    if (moveDirectoryToDownloads(inputDir, "input")) {
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
                                    if (moveDirectoryToDownloads(src, subdir)) {
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
                // Flush remaining logs
            _logs.value = logBuffer.toList()
            // Flush remaining logs
            _logs.value = logBuffer.toList()
            _isRunning.value = false
            ensureForegroundStopped()
            }
            result
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

    private suspend fun moveDirectoryToDownloads(srcDir: File, subdirName: String): Boolean {
        return try {
            // Gunakan direct path (app punya MANAGE_EXTERNAL_STORAGE)
            val baseDir = File("/storage/emulated/0/Download/AFFT")
            if (!baseDir.exists()) baseDir.mkdirs()
            val destDir = File(baseDir, subdirName)
            // Hapus dulu jika sudah ada
            if (destDir.exists()) destDir.deleteRecursively()
            
            // Coba rename dulu (instan kalo satu filesystem)
            var moved = srcDir.renameTo(destDir)
            
            if (!moved) {
                // rename gagal (beda partisi), fallback copy (lebih lambat tapi aman)
                addLog("  [INFO] rename gagal (beda partisi?), fallback copy...")
                srcDir.copyRecursively(destDir, overwrite = true)
                if (destDir.exists()) {
                    // Hapus sumber, tapi jangan gagalkan export kalau hapus gagal
                    try {
                        srcDir.deleteRecursively()
                    } catch (e: Exception) {
                        addLog("  [WARN] Gagal menghapus sumber: ${e.message}")
                    }
                    moved = true
                }
            }
            
            if (moved && destDir.exists()) {
                val fileCount = destDir.walkTopDown().count() - 1
                addLog("  Dipindah: $subdirName/ ($fileCount items)")
                true
            } else {
                addLog("  [ERROR] Gagal memindah $subdirName/")
                false
            }
        } catch (e: Exception) {
            addLog("  [ERROR] Export $subdirName gagal: ${e.message}")
            false
        }
    }

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
