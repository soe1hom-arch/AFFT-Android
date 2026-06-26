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
        if (isSparseImage(file)) return "unknown"

        return try {
            RandomAccessFile(file, "r").use { raf ->
                // EROFS superblock is at offset 0x400 with magic 0xE0F5E1E2
                raf.seek(0x400)
                val erofsMagic = ByteArray(4)
                raf.readFully(erofsMagic)
                if (erofsMagic[0] == 0xE2.toByte() && erofsMagic[1] == 0xE1.toByte() &&
                    erofsMagic[2] == 0xF5.toByte() && erofsMagic[3] == 0xE0.toByte()) {
                    return "erofs"
                }

                raf.seek(0)
                val magic4 = ByteArray(4)
                raf.readFully(magic4)

                if ((magic4[0] == 0x10.toByte() && magic4[1] == 0x20.toByte() &&
                     magic4[2] == 0xF5.toByte() && magic4[3] == 0xF2.toByte()) ||
                    (magic4[0] == 0xF2.toByte() && magic4[1] == 0xF5.toByte() &&
                     magic4[2] == 0x20.toByte() && magic4[3] == 0x10.toByte())) {
                    return "f2fs"
                }

                if (magic4[0] == 0x1F.toByte() && magic4[1] == 0x8B.toByte()) {
                    return "gzip"
                }

                raf.seek(0x438)
                val ext4magic = ByteArray(2)
                raf.readFully(ext4magic)
                if (ext4magic[0] == 0x53.toByte() && ext4magic[1] == 0xEF.toByte()) {
                    return "ext4"
                }

                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
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
}
