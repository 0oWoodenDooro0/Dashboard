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
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem

import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceRuntimeStatus

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DefaultServiceRuntimeManager(
    private val dockerClient: DockerClient = CliDockerClient(),
    private val dockerComposeClient: DockerComposeClient = CliDockerComposeClient(),
    private val processManager: ProcessManager = DefaultProcessManager(),
    private val portHealthChecker: PortHealthChecker = SocketPortHealthChecker(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val filePollDelayMs: Long = 100L
) : ServiceRuntimeManager {

    override suspend fun isDockerAvailable(): Boolean {
        return dockerClient.isDockerAvailable()
    }

    override suspend fun getServiceState(service: ServiceItem): ContainerState {
        return when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.getContainerState(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.getServiceState(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.getProcessState(service.id)
            is LogSource.LocalFile, is LogSource.None -> ContainerState.Unknown("")
        }
    }

    override suspend fun checkPortHealth(service: ServiceItem, timeoutMs: Long): PortHealth {
        val port = service.port ?: return PortHealth.None
        return portHealthChecker.checkPort(
            host = service.host,
            port = port,
            timeoutMs = timeoutMs
        )
    }

    override suspend fun inspectService(service: ServiceItem): ServiceRuntimeStatus {
        val isDocker = try {
            dockerClient.isDockerAvailable()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        return inspectServiceInternal(service, isDocker = isDocker)
    }

    override suspend fun inspectServices(services: List<ServiceItem>): Map<String, ServiceRuntimeStatus> =
        coroutineScope {
            if (services.isEmpty()) return@coroutineScope emptyMap()

            val isDocker = try {
                dockerClient.isDockerAvailable()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            services.map { service ->
                async(ioDispatcher) {
                    service.id to inspectServiceInternal(service, isDocker = isDocker)
                }
            }.awaitAll().toMap()
        }

    private suspend fun inspectServiceInternal(service: ServiceItem, isDocker: Boolean): ServiceRuntimeStatus {
        val now = System.currentTimeMillis()
        val portHealth = checkPortHealth(service)
        val containerState = try {
            when (val src = service.logSource) {
                is LogSource.Docker -> {
                    if (isDocker) {
                        dockerClient.getContainerState(src.containerName)
                    } else {
                        ContainerState.Unknown("Docker daemon unavailable")
                    }
                }
                is LogSource.DockerCompose -> {
                    if (isDocker) {
                        dockerComposeClient.getServiceState(
                            projectDir = src.projectDir,
                            serviceName = src.serviceName,
                            composeFile = src.composeFile
                        )
                    } else {
                        ContainerState.Unknown("Docker daemon unavailable")
                    }
                }
                is LogSource.Command -> processManager.getProcessState(service.id)
                is LogSource.LocalFile, is LogSource.None -> ContainerState.Unknown("")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ContainerState.Unknown("Error retrieving state")
        }

        val isPortHealthy = portHealth is PortHealth.Open || portHealth is PortHealth.None
        val isHealthy = when (service.logSource) {
            is LogSource.Docker, is LogSource.DockerCompose, is LogSource.Command -> {
                containerState is ContainerState.Running && isPortHealthy
            }
            is LogSource.LocalFile, is LogSource.None -> {
                isPortHealthy && containerState !is ContainerState.Dead && containerState !is ContainerState.Exited
            }
        }

        return ServiceRuntimeStatus(
            serviceId = service.id,
            portHealth = portHealth,
            containerState = containerState,
            isHealthy = isHealthy,
            lastCheckedTimestamp = now
        )
    }

    override fun streamLogs(service: ServiceItem, tail: Int): Flow<String> {
        return when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.streamLogs(src.containerName, tail = tail)
            is LogSource.DockerCompose -> dockerComposeClient.streamLogs(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile,
                tail = tail
            )
            is LogSource.Command -> processManager.streamLogs(service.id, tail = tail)
            is LogSource.LocalFile -> streamLocalFile(File(src.path), tail = tail)
            is LogSource.None -> emptyFlow()
        }
    }

    override suspend fun startService(service: ServiceItem) {
        when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.startContainer(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.startService(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.startProcess(
                serviceId = service.id,
                workingDir = src.workingDir,
                command = src.startCommand,
                environment = src.environment
            )
            is LogSource.LocalFile, is LogSource.None -> {
                throw UnsupportedOperationException("Service ${service.name} does not support lifecycle start")
            }
        }
    }

    override suspend fun stopService(service: ServiceItem) {
        when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.stopContainer(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.stopService(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.stopProcess(
                serviceId = service.id,
                stopCommand = src.stopCommand,
                workingDir = src.workingDir
            )
            is LogSource.LocalFile, is LogSource.None -> {
                throw UnsupportedOperationException("Service ${service.name} does not support lifecycle stop")
            }
        }
    }

    override suspend fun restartService(service: ServiceItem) {
        when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.restartContainer(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.restartService(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.restartProcess(
                serviceId = service.id,
                workingDir = src.workingDir,
                command = src.startCommand,
                stopCommand = src.stopCommand,
                environment = src.environment
            )
            is LogSource.LocalFile, is LogSource.None -> {
                throw UnsupportedOperationException("Service ${service.name} does not support lifecycle restart")
            }
        }
    }

    override fun isRunning(service: ServiceItem): Boolean {
        return when (service.logSource) {
            is LogSource.Command -> processManager.isRunning(service.id)
            else -> false
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
                delay(filePollDelayMs)
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

            delay(filePollDelayMs)
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

