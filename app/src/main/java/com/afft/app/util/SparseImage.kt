package com.afft.app.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseImage {

    private val SPARSE_MAGIC = 0xED26FF3A.toInt()
    private const val RAW_CHUNK_TYPE = 0xCAC1
    private const val FILL_CHUNK_TYPE = 0xCAC2
    private const val DONTCARE_CHUNK_TYPE = 0xCAC3
    private const val CRC32_CHUNK_TYPE = 0xCAC4

    fun isSparseImage(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                val bb = ByteBuffer.wrap(magic).order(ByteOrder.LITTLE_ENDIAN)
                bb.getInt() == SPARSE_MAGIC
            }
        } catch (e: Exception) {
            false
        }
    }

    fun detectFilesystemType(file: File): String {
        if (!file.exists()) return "unknown"

        // Jika sparse image, konversi dulu ke raw baru deteksi
        var targetFile = file
        val tempRawFile: File? = if (isSparseImage(file)) {
            try {
                val raw = File(file.parentFile, "${file.nameWithoutExtension}_raw_detect.img")
                if (sparseToRaw(file, raw)) {
                    android.util.Log.d("SparseImage", "detectFilesystemType: converted sparse->raw for detection")
                    raw
                } else null
            } catch (e: Exception) {
                android.util.Log.w("SparseImage", "detectFilesystemType: sparse->raw failed: ${e.message}")
                null
            }
        } else null

        if (tempRawFile != null && tempRawFile.exists() && tempRawFile.length() > 0) {
            targetFile = tempRawFile
        }

        val result = try {
            RandomAccessFile(targetFile, "r").use { raf ->
                // ==================== EROFS CHECK ====================
                // EROFS superblock is at offset 0x400 with magic 0xE0F5E1E2
                raf.seek(0x400)
                val erofsMagic = ByteArray(4)
                raf.readFully(erofsMagic)
                if ((erofsMagic[0].toInt() and 0xFF) == 0xE2 &&
                    (erofsMagic[1].toInt() and 0xFF) == 0xE1 &&
                    (erofsMagic[2].toInt() and 0xFF) == 0xF5 &&
                    (erofsMagic[3].toInt() and 0xFF) == 0xE0) {
                    "erofs"
                } else {
                    // ==================== F2FS CHECK ====================
                    raf.seek(0)
                    val magic4 = ByteArray(4)
                    raf.readFully(magic4)
                    val b0 = magic4[0].toInt() and 0xFF
                    val b1 = magic4[1].toInt() and 0xFF
                    val b2 = magic4[2].toInt() and 0xFF
                    val b3 = magic4[3].toInt() and 0xFF

                    if ((b0 == 0x10 && b1 == 0x20 && b2 == 0xF5 && b3 == 0xF2) ||
                        (b0 == 0xF2 && b1 == 0xF5 && b2 == 0x20 && b3 == 0x10)) {
                        "f2fs"
                    } else if (b0 == 0x1F && b1 == 0x8B) {
                        "gzip"
                    } else {
                        // ==================== EXT4 CHECK ====================
                        raf.seek(0x438)
                        val ext4magic = ByteArray(2)
                        raf.readFully(ext4magic)
                        val ext4b0 = ext4magic[0].toInt() and 0xFF
                        val ext4b1 = ext4magic[1].toInt() and 0xFF
                        if (ext4b0 == 0x53 && ext4b1 == 0xEF) "ext4"
                        else "unknown"
                    }
                }
            }
        } catch (e: Exception) {
            "unknown"
        } finally {
            // Hapus file temporary raw jika ada
            if (tempRawFile != null && tempRawFile.exists()) {
                tempRawFile.delete()
            }
        }

        return result
    }

    fun rawToSparse(rawFile: File, sparseFile: File, blockSize: Int = 4096): Boolean {
        return try {
            val rawData = rawFile.readBytes()
            val dataLen = rawData.size

            val blocks = (dataLen + blockSize - 1) / blockSize
            val paddedLen = blocks * blockSize

            val chunkHeaderSize = 12
            val fileHeaderSize = 28

            val maxBlocksPerChunk = (0xFFFFFEL).toInt() // safe max blocks

            if (maxBlocksPerChunk <= 0) return false

            val totalChunks: Int
            val chunksData: MutableList<Pair<Int, ByteArray>> = mutableListOf()

            if (blocks <= maxBlocksPerChunk) {
                totalChunks = 1
                val padding = ByteArray(paddedLen - dataLen)
                chunksData.add(RAW_CHUNK_TYPE to (rawData + padding))
            } else {
                var remaining = blocks
                var offset = 0
                val chunks = mutableListOf<Pair<Int, ByteArray>>()

                while (remaining > 0) {
                    val chunkBlocks = minOf(remaining, maxBlocksPerChunk)
                    val chunkDataLen = chunkBlocks * blockSize
                    val dataEnd = minOf(offset + chunkDataLen, dataLen)
                    val chunkData = rawData.copyOfRange(offset, dataEnd) +
                        ByteArray(chunkDataLen - (dataEnd - offset))
                    chunks.add(RAW_CHUNK_TYPE to chunkData)
                    offset += chunkDataLen
                    remaining -= chunkBlocks
                }
                totalChunks = chunks.size
                chunksData.addAll(chunks)
            }

            sparseFile.outputStream().use { out ->
                val headerBuf = ByteBuffer.allocate(fileHeaderSize)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(SPARSE_MAGIC)
                    .putShort(1.toShort())
                    .putShort(0.toShort())
                    .putShort(fileHeaderSize.toShort())
                    .putShort(chunkHeaderSize.toShort())
                    .putInt(blockSize)
                    .putInt(blocks)
                    .putInt(totalChunks)
                    .putInt(0)
                out.write(headerBuf.array())

                for ((chunkType, chunkData) in chunksData) {
                    val chunkTotalSize = chunkHeaderSize + chunkData.size
                    val chunkBuf = ByteBuffer.allocate(chunkHeaderSize)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(chunkType.toShort())
                        .putShort(0.toShort())
                        .putInt(chunkData.size / blockSize)
                        .putInt(chunkTotalSize)
                    out.write(chunkBuf.array())
                    out.write(chunkData)
                }
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("SparseImage", "rawToSparse failed: ${e.message}")
            false
        }
    }

    /**
     * Convert Android sparse image to raw image (pure Kotlin, no external binary).
     * Android Sparse Image format:
     *   - File header (28 bytes): magic, version, header sizes, block size, blocks, chunks, crc
     *   - Chunks: RAW (0xCAC1), FILL (0xCAC2), DONTCARE (0xCAC3), CRC32 (0xCAC4)
     */
    fun sparseToRaw(sparseFile: File, rawFile: File): Boolean {
        return try {
            if (!sparseFile.exists() || !isSparseImage(sparseFile)) {
                android.util.Log.e("SparseImage", "Not a valid sparse image")
                // If not sparse, just copy the file as-is
                if (!sparseFile.exists()) return false
                sparseFile.copyTo(rawFile, overwrite = true)
                return true
            }

            RandomAccessFile(sparseFile, "r").use { raf ->
                val headerBuf = ByteArray(28)
                raf.readFully(headerBuf)
                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                
                val magic = bb.getInt()
                if (magic != SPARSE_MAGIC) {
                    android.util.Log.e("SparseImage", "Bad sparse magic: ${String.format("0x%08X", magic)}")
                    sparseFile.copyTo(rawFile, overwrite = true)
                    return false
                }
                
                val majorVer = bb.getShort().toInt() and 0xFFFF
                val minorVer = bb.getShort().toInt() and 0xFFFF
                val fileHdrSize = bb.getShort().toInt() and 0xFFFF
                val chunkHdrSize = bb.getShort().toInt() and 0xFFFF
                val blockSize = bb.getInt()
                val totalBlocks = bb.getInt()
                val totalChunks = bb.getInt()
                val crc32 = bb.getInt()
                
                android.util.Log.d("SparseImage", 
                    "sparseToRaw: ver=$majorVer.$minorVer blocks=$totalBlocks chunks=$totalChunks blockSize=$blockSize")
                
                val expectedRawSize = totalBlocks.toLong() * blockSize
                
                rawFile.outputStream().use { out ->
                    for (i in 0 until totalChunks) {
                        val chunkHeader = ByteArray(12)
                        raf.readFully(chunkHeader)
                        val cb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
                        
                        val chunkType = cb.getShort().toInt() and 0xFFFF
                        val reserved = cb.getShort().toInt() and 0xFFFF
                        val chunkBlocks = cb.getInt()
                        val chunkTotalSize = cb.getInt()
                        val chunkDataSize = chunkTotalSize - chunkHdrSize
                        
                        when (chunkType) {
                            RAW_CHUNK_TYPE -> {
                                // RAW chunk: contains actual data
                                val data = ByteArray(chunkDataSize)
                                raf.readFully(data)
                                out.write(data)
                            }
                            FILL_CHUNK_TYPE -> {
                                // FILL chunk: contains a 4-byte fill pattern
                                val fillBytes = ByteArray(4)
                                raf.readFully(fillBytes)
                                val fillSize = chunkBlocks.toLong() * blockSize
                                // Write fill pattern repeatedly
                                var written = 0L
                                while (written < fillSize) {
                                    val toWrite = minOf(fillSize - written, 8192L).toInt()
                                    val fillBuf = ByteArray(toWrite)
                                    for (j in fillBuf.indices) {
                                        fillBuf[j] = fillBytes[j % 4]
                                    }
                                    out.write(fillBuf)
                                    written += toWrite
                                }
                            }
                            DONTCARE_CHUNK_TYPE -> {
                                // DONTCARE chunk: skip (write zeros to output)
                                val skipSize = chunkBlocks.toLong() * blockSize
                                var written = 0L
                                while (written < skipSize) {
                                    val toWrite = minOf(skipSize - written, 8192L).toInt()
                                    out.write(ByteArray(toWrite))
                                    written += toWrite
                                }
                            }
                            CRC32_CHUNK_TYPE -> {
                                // CRC32 chunk: skip 4 bytes
                                val crcData = ByteArray(4)
                                raf.readFully(crcData)
                            }
                            else -> {
                                android.util.Log.w("SparseImage", 
                                    "Unknown chunk type: ${String.format("0x%04X", chunkType)}")
                                // Skip unknown chunk
                                if (chunkDataSize > 0) {
                                    raf.skipBytes(chunkDataSize)
                                }
                            }
                        }
                    }
                }
                
                android.util.Log.d("SparseImage", 
                    "sparseToRaw OK: ${rawFile.length()} bytes (expected ~$expectedRawSize)")
            }
            
            rawFile.exists() && rawFile.length() > 0
        } catch (e: Exception) {
            android.util.Log.e("SparseImage", "sparseToRaw failed: ${e.message}")
            // If conversion fails, just copy the file
            try {
                sparseFile.copyTo(rawFile, overwrite = true)
            } catch (_: Exception) {}
            false
        }
    }
}
