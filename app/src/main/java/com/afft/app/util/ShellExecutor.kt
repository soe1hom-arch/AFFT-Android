package com.afft.app.util

import android.content.Context
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

    suspend fun execute(
        command: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(false)

        if (workingDir != null) {
            processBuilder.directory(workingDir)
        }

        if (envVars != null) {
            processBuilder.environment().putAll(envVars)
        }

        val process = processBuilder.start()
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

        return listOf(binaryPath) + args
    }
}
