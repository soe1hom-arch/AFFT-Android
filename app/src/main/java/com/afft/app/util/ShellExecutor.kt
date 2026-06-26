package com.afft.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class CommandResult(
    val exitCode: Int,
    val output: List<String>,
    val errorOutput: List<String>
)

object ShellExecutor {

    private const val TAG = "ShellExecutor"

    suspend fun execute(
        command: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null
    ): CommandResult {
        // Attempt 1: Direct execution
        val result = runCommand(command, workingDir, envVars, onOutput)

        // If process couldn't start (IOException, exitCode==-1) and the first arg is a file,
        // try linker64 fallback for native binary execution on Android 14+ where exec may be blocked
        if (result.exitCode == -1 && command.isNotEmpty()) {
            val firstArg = command[0]
            val binaryFile = File(firstArg)

            // Only attempt fallback if first arg is a file (not a shell command like "sh", "chmod")
            if (binaryFile.isFile()) {
                Log.w(TAG, "execute: direct exec failed for $firstArg, trying linker64 fallback")

                // Attempt 2: Fallback to linker64 (/system/bin/linker64)
                // This works because linker64 has exec_type SELinux context to load ELF binaries
                // even when the binary file itself isn't directly executable.
                val linkerCommand = listOf("/system/bin/linker64") + command
                val linkerResult = runCommand(linkerCommand, workingDir, envVars, onOutput)
                if (linkerResult.exitCode >= 0) {
                    return linkerResult
                }

                // Attempt 3: Last resort - try via sh -c wrapper
                Log.w(TAG, "execute: linker64 also failed, trying sh -c fallback")
                val shCommand = listOf("sh", "-c", command.joinToString(" "))
                return runCommand(shCommand, workingDir, envVars, onOutput)
            }
        }

        return result
    }

    suspend fun executeWithProgress(
        command: List<String>,
        workingDir: File? = null,
        onProgress: ((String) -> Unit)? = null
    ): CommandResult = execute(command, workingDir, onOutput = onProgress)

    fun buildBinaryCommand(
        context: Context,
        binaryName: String,
        args: List<String>
    ): List<String> {
        val binaryPath = BinaryManager.getBinaryPath(context, binaryName)
            ?: throw IllegalStateException("Binary $binaryName not found")

        val binaryFile = File(binaryPath)
        // For dynamically linked binaries that fail direct execution (selinux/noexec),
        // we try direct first and fall back to linker64 via executeBinary()
        return listOf(binaryPath) + args
    }

    /**
     * Execute a native binary with automatic fallback to linker64 if direct
     * execution fails due to SELinux/noexec restrictions on Android 14+.
     *
     * The linker64 fallback runs the binary through the system linker:
     *   /system/bin/linker64 /path/to/binary args...
     * This works because linker64 has the proper SELinux domain (exec_type)
     * to load ELF binaries even from app data directories.
     */
    suspend fun executeBinary(
        binaryPath: String,
        args: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null
    ): CommandResult {
        val binaryFile = File(binaryPath)
        val isExecutable = binaryFile.canExecute()

        Log.d(TAG, "executeBinary($binaryPath): exists=${binaryFile.exists()} execute=$isExecutable")

        // Attempt 1: Direct execution
        if (isExecutable) {
            val directCommand = listOf(binaryPath) + args
            Log.d(TAG, "executeBinary: trying direct execution")
            val result = runCommand(directCommand, workingDir, envVars, onOutput)
            if (result.exitCode >= 0) {
                return result
            }
            // If direct execution had IO error (process couldn't start), try fallback
            if (result.exitCode == -1) {
                Log.w(TAG, "executeBinary: direct execution failed, trying linker64 fallback")
            } else {
                return result
            }
        } else {
            Log.w(TAG, "executeBinary: binary not directly executable, will try linker64")
        }

        // Attempt 2: Fallback to linker64 (/system/bin/linker64)
        // This works because linker64 has exec_type SELinux context.
        // The binary file only needs to be readable, not executable.
        val linkerCommand = listOf("/system/bin/linker64", binaryPath) + args
        Log.d(TAG, "executeBinary: trying linker64: ${linkerCommand.joinToString(" ")}")
        val linkerResult = runCommand(linkerCommand, workingDir, envVars, onOutput)

        if (linkerResult.exitCode >= 0) {
            return linkerResult
        }

        // Attempt 3: Last resort - try via sh -c wrapper
        // Note: sh -c doesn't bypass SELinux, but may help if there's a
        // ProcessBuilder quirk on certain devices/ROMs
        Log.w(TAG, "executeBinary: linker64 also failed, trying sh -c fallback")
        val escapedArgs = args.joinToString(" ") { "'${it.replace("'", "'\''")}'" }
        val shCommand = listOf("sh", "-c", "$binaryPath $escapedArgs")
        return runCommand(shCommand, workingDir, envVars, onOutput)
    }

    private suspend fun runCommand(
        command: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Command: ${command.joinToString(" ")}")

        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(false)

        if (workingDir != null) {
            processBuilder.directory(workingDir)
        }

        if (envVars != null) {
            processBuilder.environment().putAll(envVars)
        }

        val process: Process
        try {
            process = processBuilder.start()
        } catch (e: Exception) {
            Log.e(TAG, "ProcessBuilder.start() failed for command: ${command.joinToString(" ")}", e)
            return@withContext CommandResult(
                exitCode = -1,
                output = emptyList(),
                errorOutput = listOf("ProcessBuilder.start() failed: ${e.message}")
            )
        }

        val output = mutableListOf<String>()
        val errorOutput = mutableListOf<String>()

        val outputReader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        val outputThread = Thread {
            outputReader.use { reader ->
                reader.lines().forEach { line ->
                    synchronized(output) {
                        output.add(line)
                    }
                    onOutput?.invoke(line)
                }
            }
        }

        val errorThread = Thread {
            errorReader.use { reader ->
                reader.lines().forEach { line ->
                    synchronized(errorOutput) {
                        errorOutput.add(line)
                    }
                    onOutput?.invoke("[ERROR] $line")
                }
            }
        }

        outputThread.start()
        errorThread.start()

        val exitCode = process.waitFor()
        outputThread.join()
        errorThread.join()

        CommandResult(
            exitCode = exitCode,
            output = output.toList(),
            errorOutput = errorOutput.toList()
        )
    }
}
