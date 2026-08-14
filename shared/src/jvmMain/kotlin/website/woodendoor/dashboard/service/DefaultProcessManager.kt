package website.woodendoor.dashboard.service

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
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

    private data class ProcessInstance(
        val serviceId: String,
        val process: Process,
        val workingDir: String,
        val command: String,
        val stopCommand: String?,
        val logs: MutableList<String> = mutableListOf(),
        val logJob: Job
    )

    private val activeProcesses = ConcurrentHashMap<String, ProcessInstance>()
    private val historyStates = ConcurrentHashMap<String, ContainerState>()
    private val historyLogs = ConcurrentHashMap<String, MutableList<String>>()
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

            val logs = historyLogs.computeIfAbsent(serviceId) { mutableListOf() }
            historyStates[serviceId] = ContainerState.Running("running")

            val logJob = scope.launch {
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        var line: String? = null
                        while (reader.readLine().also { line = it } != null) {
                            val logLine = line!!
                            synchronized(logs) {
                                logs.add(logLine)
                                if (logs.size > maxLogBufferSize) {
                                    logs.removeAt(0)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }

            val exitCode = try {
                process.waitFor()
            } catch (_: Exception) {
                -1
            }

            historyStates[serviceId] = ContainerState.Exited(
                exitCode = exitCode,
                status = "exited with code $exitCode"
            )
            activeProcesses.remove(serviceId)
        }

        val instance = ProcessInstance(
            serviceId = serviceId,
            process = process,
            workingDir = workingDir,
            command = command,
            stopCommand = null,
            logs = logs,
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
        var lastEmittedIndex = 0
        val initialInstance = activeProcesses[serviceId]
        val initialLogs = historyLogs[serviceId] ?: initialInstance?.logs

        if (initialLogs != null) {
            val initial = synchronized(initialLogs) {
                val start = if (tail > 0) (initialLogs.size - tail).coerceAtLeast(0) else initialLogs.size
                lastEmittedIndex = initialLogs.size
                initialLogs.subList(start, initialLogs.size).toList()
            }
            for (line in initial) {
                emit(line)
            }
        }

        while (currentCoroutineContext().isActive) {
            val currentInstance = activeProcesses[serviceId]
            val currentLogs = historyLogs[serviceId] ?: currentInstance?.logs

            if (currentLogs != null) {
                val newLines = synchronized(currentLogs) {
                    if (currentLogs.size > lastEmittedIndex) {
                        val slice = currentLogs.subList(lastEmittedIndex, currentLogs.size).toList()
                        lastEmittedIndex = currentLogs.size
                        slice
                    } else {
                        emptyList()
                    }
                }
                for (line in newLines) {
                    emit(line)
                }
            }

            val isStillProcessing = currentInstance != null &&
                (currentInstance.process.isAlive || currentInstance.logJob.isActive)

            val hasUnemittedLogs = currentLogs != null && synchronized(currentLogs) { currentLogs.size > lastEmittedIndex }

            if (!isStillProcessing && !hasUnemittedLogs) {
                break
            }

            delay(pollIntervalMs)
        }
    }.flowOn(ioDispatcher)

    override fun streamLogs(source: LogSource.Command, tail: Int): Flow<String> = flow {
        val targetServiceId = activeProcesses.values.find {
            it.workingDir == source.workingDir && it.command == source.startCommand
        }?.serviceId ?: historySources.entries.find { (_, src) ->
            src.workingDir == source.workingDir && src.startCommand == source.startCommand
        }?.key ?: historyLogs.keys.firstOrNull()

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
