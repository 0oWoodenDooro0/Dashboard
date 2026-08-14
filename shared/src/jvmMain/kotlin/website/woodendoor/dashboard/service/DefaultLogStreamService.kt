package website.woodendoor.dashboard.service

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import website.woodendoor.dashboard.model.LogSource

class DefaultLogStreamService(
    private val dockerClient: DockerClient = CliDockerClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollDelayMs: Long = 100L
) : LogStreamService {

    override fun streamLogs(source: LogSource, tail: Int): Flow<String> {
        return when (source) {
            is LogSource.Docker -> dockerClient.streamLogs(source.containerName, tail = tail)
            is LogSource.LocalFile -> streamLocalFile(File(source.path), tail = tail)
            is LogSource.None -> emptyFlow()
        }
    }

    private fun streamLocalFile(file: File, tail: Int): Flow<String> = flow {
        var pointer = 0L
        var headerSample = ByteArray(0)

        if (file.exists() && file.length() > 0L) {
            headerSample = readHeaderSample(file)
            if (tail > 0) {
                val (initialLines, initialPointer) = readInitialTailLines(file, tail)
                pointer = initialPointer
                for (line in initialLines) {
                    emit(line)
                }
            } else if (tail == 0) {
                pointer = file.length()
            }
        }

        val lineBuffer = ByteArrayOutputStream()

        while (currentCoroutineContext().isActive) {
            if (!file.exists()) {
                pointer = 0L
                headerSample = ByteArray(0)
                lineBuffer.reset()
                delay(pollDelayMs)
                continue
            }

            val currentLength = file.length()

            // Detect truncation or rewrite/rotation
            var isRotated = false
            if (currentLength < pointer) {
                isRotated = true
            } else if (pointer > 0L && headerSample.isNotEmpty() && currentLength > 0L) {
                val currentHeader = readHeaderSample(file, headerSample.size)
                if (!headerSample.contentEquals(currentHeader)) {
                    isRotated = true
                }
            }

            if (isRotated) {
                pointer = 0L
                lineBuffer.reset()
                headerSample = readHeaderSample(file)
            }

            if (headerSample.isEmpty() && currentLength > 0L) {
                headerSample = readHeaderSample(file)
            }

            if (currentLength > pointer) {
                val linesToEmit = mutableListOf<String>()
                try {
                    RandomAccessFile(file, "r").use { raf ->
                        raf.seek(pointer)
                        val buffer = ByteArray(4096)
                        while (raf.filePointer < currentLength && currentCoroutineContext().isActive) {
                            val toRead = minOf(buffer.size.toLong(), currentLength - raf.filePointer).toInt()
                            val bytesRead = raf.read(buffer, 0, toRead)
                            if (bytesRead <= 0) break

                            for (i in 0 until bytesRead) {
                                val b = buffer[i]
                                if (b == '\n'.code.toByte()) {
                                    val lineBytes = lineBuffer.toByteArray()
                                    lineBuffer.reset()
                                    val line = decodeLine(lineBytes)
                                    linesToEmit.add(line)
                                } else {
                                    lineBuffer.write(b.toInt())
                                }
                            }
                        }
                        pointer = raf.filePointer
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Ignore transient file I/O errors
                }

                for (line in linesToEmit) {
                    emit(line)
                }
            }

            delay(pollDelayMs)
        }
    }.flowOn(ioDispatcher)

    private fun readInitialTailLines(file: File, tail: Int): Pair<List<String>, Long> {
        val lines = ArrayDeque<String>()
        var pointer = 0L
        try {
            RandomAccessFile(file, "r").use { raf ->
                pointer = raf.length()
            }
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (lines.size >= tail) {
                        lines.removeFirst()
                    }
                    lines.addLast(line!!)
                }
            }
        } catch (_: Exception) {
            // Ignore transient read errors
        }
        return Pair(lines.toList(), pointer)
    }

    private fun readHeaderSample(file: File, maxBytes: Int = 64): ByteArray {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val sampleSize = minOf(raf.length(), maxBytes.toLong()).toInt()
                if (sampleSize <= 0) return ByteArray(0)
                val sample = ByteArray(sampleSize)
                raf.readFully(sample)
                sample
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun decodeLine(bytes: ByteArray): String {
        val length = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) {
            bytes.size - 1
        } else {
            bytes.size
        }
        return String(bytes, 0, length, Charsets.UTF_8)
    }
}
