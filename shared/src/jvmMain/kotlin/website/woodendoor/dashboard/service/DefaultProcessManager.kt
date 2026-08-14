package website.woodendoor.dashboard.service

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource

class DefaultProcessManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxLogBufferSize: Int = 1000,
    private val pollIntervalMs: Long = 30L
) : ProcessManager {

    private val managerJob = SupervisorJob()
    private val scope = CoroutineScope(managerJob + ioDispatcher)

    private data class LogEntry(
        val seq: Long,
        val text: String,
        val isEndOfStream: Boolean = false
    )

    private class LogBuffer(private val capacity: Int) {
        private val buffer = ArrayDeque<String>()
        var totalLines: Long = 0L
            private set

        @Synchronized
        fun add(line: String): Long {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(line)
            totalLines++
            return totalLines
        }

        @Synchronized
        fun getTail(tail: Int): Pair<List<String>, Long> {
            if (tail <= 0) return Pair(emptyList(), totalLines)
            val count = minOf(tail, buffer.size)
            val start = buffer.size - count
            val snapshot = ArrayList<String>(count)
            for (i in start until buffer.size) {
                snapshot.add(buffer[i])
            }
            return Pair(snapshot, totalLines)
        }

        @Synchronized
        fun getLinesSince(lastSeq: Long): Pair<List<String>, Long> {
            if (totalLines <= lastSeq) return Pair(emptyList(), totalLines)
            val availableStartSeq = totalLines - buffer.size
            val effectiveStartSeq = maxOf(lastSeq, availableStartSeq)
            val skip = (effectiveStartSeq - availableStartSeq).toInt()
            val newLines = ArrayList<String>(buffer.size - skip)
            for (i in skip until buffer.size) {
                newLines.add(buffer[i])
            }
            return Pair(newLines, totalLines)
        }

        @Synchronized
        fun hasUnemittedLogs(lastSeq: Long): Boolean = totalLines > lastSeq
    }

    private data class ProcessInstance(
        val serviceId: String,
        val process: Process,
        val workingDir: String,
        val command: String,
        val stopCommand: String?,
        val logBuffer: LogBuffer,
        val logFlow: MutableSharedFlow<LogEntry>,
        val logJob: Job
    )

    private val activeProcesses = ConcurrentHashMap<String, ProcessInstance>()
    private val historyStates = ConcurrentHashMap<String, ContainerState>()
    private val historyLogBuffers = ConcurrentHashMap<String, LogBuffer>()
    private val historyLogFlows = ConcurrentHashMap<String, MutableSharedFlow<LogEntry>>()
    private val historySources = ConcurrentHashMap<String, LogSource.Command>()

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun buildShellCommand(command: String): List<String> =
        if (isWindows()) {
            listOf("cmd.exe", "/c", command)
        } else {
            listOf("/bin/sh", "-c", command)
        }

    override suspend fun startProcess(
        serviceId: String,
        workingDir: String,
        command: String,
        environment: Map<String, String>
    ) {
        withContext(ioDispatcher) {
            if (activeProcesses.containsKey(serviceId) && isRunning(serviceId)) {
                stopProcess(serviceId, workingDir = workingDir)
            }

            historySources[serviceId] = LogSource.Command(
                workingDir = workingDir,
                startCommand = command,
                environment = environment
            )

            val dir = File(workingDir)
            if (!dir.exists() || !dir.isDirectory) {
                val errorState = ContainerState.Exited(
                    exitCode = -1,
                    status = "Working directory does not exist: $workingDir"
                )
                historyStates[serviceId] = errorState
                throw IllegalArgumentException("Working directory does not exist: $workingDir")
            }

            val shellCmd = buildShellCommand(command)
            val processBuilder = ProcessBuilder(shellCmd)
                .directory(dir)
                .redirectErrorStream(true)

            processBuilder.environment().putAll(environment)

            val process: Process
            try {
                process = processBuilder.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorState = ContainerState.Exited(
                    exitCode = -1,
                    status = "Failed to start process: ${e.message}"
                )
                historyStates[serviceId] = errorState
                throw e
            }

            val logBuffer = historyLogBuffers.computeIfAbsent(serviceId) { LogBuffer(maxLogBufferSize) }
            val logFlow = MutableSharedFlow<LogEntry>(
                replay = 1,
                extraBufferCapacity = 65536,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            historyLogFlows[serviceId] = logFlow
            historyStates[serviceId] = ContainerState.Running("running")

            val logJob = scope.launch {
                var exitCode = -1
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String? = null
                        while (reader.readLine().also { line = it } != null) {
                            val text = line!!
                            val seq = logBuffer.add(text)
                            logFlow.tryEmit(LogEntry(seq = seq, text = text))
                        }
                    }
                    exitCode = try {
                        process.waitFor()
                    } catch (_: Exception) {
                        -1
                    }
                } catch (_: Exception) {
                } finally {
                    historyStates[serviceId] = ContainerState.Exited(
                        exitCode = exitCode,
                        status = "exited with code $exitCode"
                    )
                    activeProcesses.remove(serviceId)
                    logFlow.tryEmit(LogEntry(seq = logBuffer.totalLines, text = "", isEndOfStream = true))
                }
            }

            val instance = ProcessInstance(
                serviceId = serviceId,
                process = process,
                workingDir = workingDir,
                command = command,
                stopCommand = null,
                logBuffer = logBuffer,
                logFlow = logFlow,
                logJob = logJob
            )

            activeProcesses[serviceId] = instance
        }
    }

    override suspend fun stopProcess(
        serviceId: String,
        stopCommand: String?,
        workingDir: String?,
        timeoutSeconds: Int
    ) {
        withContext(ioDispatcher) {
            val instance = activeProcesses[serviceId]

            if (!stopCommand.isNullOrBlank()) {
                val dir = if (!workingDir.isNullOrBlank()) {
                    File(workingDir)
                } else if (instance != null) {
                    File(instance.workingDir)
                } else {
                    null
                }

                try {
                    val shellCmd = buildShellCommand(stopCommand)
                    val pb = ProcessBuilder(shellCmd).redirectErrorStream(true)
                    if (dir != null && dir.exists()) {
                        pb.directory(dir)
                    }
                    val stopProc = pb.start()
                    stopProc.waitFor()
                } catch (_: Exception) {
                }
            }

            if (instance != null && instance.process.isAlive) {
                destroyProcessTree(instance.process, timeoutMs = timeoutSeconds * 1000L)
                instance.logJob.cancel()
            }

            val exitCode = instance?.process?.let {
                if (!it.isAlive) {
                    try { it.exitValue() } catch (_: Exception) { 0 }
                } else {
                    0
                }
            } ?: 0

            historyStates[serviceId] = ContainerState.Exited(exitCode = exitCode, status = "stopped")
            activeProcesses.remove(serviceId)
            historyLogFlows[serviceId]?.tryEmit(
                LogEntry(seq = historyLogBuffers[serviceId]?.totalLines ?: 0L, text = "", isEndOfStream = true)
            )
        }
    }

    override suspend fun restartProcess(
        serviceId: String,
        workingDir: String,
        command: String,
        stopCommand: String?,
        environment: Map<String, String>
    ) {
        stopProcess(
            serviceId = serviceId,
            stopCommand = stopCommand,
            workingDir = workingDir
        )
        startProcess(
            serviceId = serviceId,
            workingDir = workingDir,
            command = command,
            environment = environment
        )
    }

    override fun isRunning(serviceId: String): Boolean {
        val instance = activeProcesses[serviceId]
        return instance != null && instance.process.isAlive
    }

    override fun getProcessState(serviceId: String): ContainerState {
        val instance = activeProcesses[serviceId]
        return if (instance != null && instance.process.isAlive) {
            ContainerState.Running("running")
        } else {
            historyStates[serviceId] ?: ContainerState.NotFound(reason = "Process not running")
        }
    }

    override fun streamLogs(serviceId: String, tail: Int): Flow<String> = flow {
        val logBuffer = historyLogBuffers[serviceId] ?: activeProcesses[serviceId]?.logBuffer
        val liveFlow = historyLogFlows[serviceId]

        var lastEmittedSeq = 0L
        if (logBuffer != null) {
            val (initialLines, currentSeq) = logBuffer.getTail(tail)
            lastEmittedSeq = currentSeq
            for (line in initialLines) {
                emit(line)
            }
        }

        if (liveFlow != null) {
            liveFlow.takeWhile { !it.isEndOfStream }.collect { entry ->
                if (entry.seq > lastEmittedSeq) {
                    lastEmittedSeq = entry.seq
                    emit(entry.text)
                }
            }
        }
    }.flowOn(ioDispatcher)

    override fun streamLogs(source: LogSource.Command, tail: Int): Flow<String> = flow {
        val targetServiceId = activeProcesses.values.find {
            it.workingDir == source.workingDir && it.command == source.startCommand
        }?.serviceId ?: historySources.entries.find { (_, src) ->
            src.workingDir == source.workingDir && src.startCommand == source.startCommand
        }?.key ?: historyLogBuffers.keys.firstOrNull()

        if (targetServiceId != null) {
            streamLogs(targetServiceId, tail).collect { line ->
                emit(line)
            }
        }
    }.flowOn(ioDispatcher)

    private fun destroyProcessTree(process: Process, timeoutMs: Long) {
        try {
            val handle = process.toHandle()
            val descendants = handle.descendants().toList()

            for (descendant in descendants) {
                try {
                    descendant.destroy()
                } catch (_: Exception) {}
            }
            process.destroy()

            val deadline = System.currentTimeMillis() + timeoutMs
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }

            if (process.isAlive) {
                for (descendant in descendants) {
                    try {
                        descendant.destroyForcibly()
                    } catch (_: Exception) {}
                }
                process.destroyForcibly()
                process.waitFor()
            }
        } catch (_: Exception) {
            try {
                process.destroyForcibly()
            } catch (_: Exception) {}
        }
    }
}
