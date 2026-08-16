package website.woodendoor.dashboard.service

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

interface ProcessExecutor {
    suspend fun execute(command: List<String>, timeoutMs: Long = 5000): ProcessResult
    fun executeStreaming(command: List<String>): Flow<String>
}

class DefaultProcessExecutor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProcessExecutor {

    override suspend fun execute(command: List<String>, timeoutMs: Long): ProcessResult =
        withContext(ioDispatcher) {
            try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start()

                val stdoutDeferred = async { process.inputStream.bufferedReader().readText() }
                val stderrDeferred = async { process.errorStream.bufferedReader().readText() }

                val finished = withTimeoutOrNull(timeoutMs) {
                    process.waitFor()
                }

                if (finished == null) {
                    process.destroyForcibly()
                    process.waitFor()
                    return@withContext ProcessResult(
                        exitCode = -1,
                        stdout = "",
                        stderr = "Command timed out after ${timeoutMs}ms"
                    )
                }

                val exitCode = process.exitValue()
                val stdout = stdoutDeferred.await()
                val stderr = stderrDeferred.await()
                ProcessResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProcessResult(exitCode = -1, stdout = "", stderr = e.message ?: e::class.simpleName.orEmpty())
            }
        }

    override fun executeStreaming(command: List<String>): Flow<String> = flow {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        try {
            process.inputStream.bufferedReader().use { reader ->
                while (currentCoroutineContext().isActive) {
                    val line = withContext(ioDispatcher) {
                        reader.readLine()
                    } ?: break
                    emit(line)
                }
            }
        } finally {
            withContext(NonCancellable + ioDispatcher) {
                process.destroyForcibly()
                process.waitFor()
            }
        }
    }.flowOn(ioDispatcher)
}

class CliDockerClient(
    private val dockerExecutable: String = "docker",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    processExecutor: ProcessExecutor? = null
) : DockerClient {

    private val executor: ProcessExecutor = processExecutor ?: DefaultProcessExecutor(ioDispatcher)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun buildComposeCommand(
        projectDir: String,
        composeFile: String?,
        subcommandAndArgs: List<String>
    ): List<String> {
        val cmd = mutableListOf(dockerExecutable, "compose", "--project-directory", projectDir)
        if (!composeFile.isNullOrBlank()) {
            val fileObj = File(composeFile)
            val resolvedFile = if (fileObj.isAbsolute) {
                composeFile
            } else {
                File(projectDir, composeFile).path
            }
            cmd.add("-f")
            cmd.add(resolvedFile)
        }
        cmd.addAll(subcommandAndArgs)
        return cmd
    }

    override suspend fun isDockerAvailable(): Boolean {
        val cmd = listOf(dockerExecutable, "version", "--format", "{{.Server.Version}}")
        return try {
            val result = executor.execute(cmd, timeoutMs = 3000)
            result.exitCode == 0 && result.stdout.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getContainerState(target: LogSource): ContainerState = when (target) {
        is LogSource.Docker -> getStandaloneContainerState(target.containerName)
        is LogSource.DockerCompose -> getComposeContainerState(target.projectDir, target.serviceName, target.composeFile)
        else -> ContainerState.Unknown("Unsupported log source: $target")
    }

    private suspend fun getStandaloneContainerState(nameOrId: String): ContainerState {
        val cmd = listOf(dockerExecutable, "inspect", nameOrId, "--format", "{{json .State}}")
        val result = executor.execute(cmd)
        if (result.exitCode != 0) {
            val errorMsg = result.stderr.ifBlank { result.stdout }.ifBlank { "Container not found" }.trim()
            return ContainerState.NotFound(reason = errorMsg)
        }

        val stdoutTrimmed = result.stdout.trim()
        if (stdoutTrimmed.isEmpty() || !stdoutTrimmed.startsWith("{")) {
            return ContainerState.Unknown(rawStatus = stdoutTrimmed)
        }

        return try {
            val raw = json.decodeFromString<RawDockerInspectState>(stdoutTrimmed)
            raw.toContainerState()
        } catch (_: Exception) {
            ContainerState.Unknown(rawStatus = stdoutTrimmed)
        }
    }

    private suspend fun getComposeContainerState(
        projectDir: String,
        serviceName: String,
        composeFile: String?
    ): ContainerState {
        val cmd = buildComposeCommand(
            projectDir = projectDir,
            composeFile = composeFile,
            subcommandAndArgs = listOf("ps", "-a", "--format", "json", serviceName)
        )
        val result = executor.execute(cmd)
        if (result.exitCode != 0) {
            val errorMsg = result.stderr.ifBlank { result.stdout }.ifBlank { "Service not found" }.trim()
            return ContainerState.NotFound(reason = errorMsg)
        }

        val stdoutTrimmed = result.stdout.trim()
        if (stdoutTrimmed.isEmpty()) {
            return ContainerState.NotFound(reason = "No containers found for service $serviceName")
        }

        return try {
            if (stdoutTrimmed.startsWith("[")) {
                val list = json.decodeFromString<List<RawComposePsOutput>>(stdoutTrimmed)
                val first = list.firstOrNull() ?: return ContainerState.NotFound(reason = "No containers found for service $serviceName")
                first.toContainerState()
            } else {
                val firstLine = stdoutTrimmed.lines().firstOrNull { it.trim().startsWith("{") }?.trim()
                    ?: return ContainerState.Unknown(rawStatus = stdoutTrimmed)
                val raw = json.decodeFromString<RawComposePsOutput>(firstLine)
                raw.toContainerState()
            }
        } catch (_: Exception) {
            ContainerState.Unknown(rawStatus = stdoutTrimmed)
        }
    }

    override fun streamLogs(target: LogSource, tail: Int): Flow<String> = when (target) {
        is LogSource.Docker -> {
            val cmd = listOf(
                dockerExecutable,
                "logs",
                "-f",
                "--tail",
                tail.toString(),
                target.containerName
            )
            executor.executeStreaming(cmd)
        }
        is LogSource.DockerCompose -> {
            val cmd = buildComposeCommand(
                projectDir = target.projectDir,
                composeFile = target.composeFile,
                subcommandAndArgs = listOf("logs", "-f", "--tail", tail.toString(), target.serviceName)
            )
            executor.executeStreaming(cmd)
        }
        else -> emptyFlow()
    }

    override suspend fun start(target: LogSource) {
        when (target) {
            is LogSource.Docker -> {
                val cmd = listOf(dockerExecutable, "start", target.containerName)
                val result = executor.execute(cmd)
                if (result.exitCode != 0) {
                    val error = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to start container ${target.containerName}" }
                    throw RuntimeException(error.trim())
                }
            }
            is LogSource.DockerCompose -> {
                val cmd = buildComposeCommand(
                    projectDir = target.projectDir,
                    composeFile = target.composeFile,
                    subcommandAndArgs = listOf("start", target.serviceName)
                )
                val result = executor.execute(cmd)
                if (result.exitCode != 0) {
                    val upCmd = buildComposeCommand(
                        projectDir = target.projectDir,
                        composeFile = target.composeFile,
                        subcommandAndArgs = listOf("up", "-d", target.serviceName)
                    )
                    val upResult = executor.execute(upCmd, timeoutMs = 30000)
                    if (upResult.exitCode != 0) {
                        val error = upResult.stderr.ifBlank { upResult.stdout }.ifBlank { "Failed to start service ${target.serviceName}" }
                        throw RuntimeException(error.trim())
                    }
                }
            }
            else -> throw UnsupportedOperationException("Unsupported target for Docker start: $target")
        }
    }

    override suspend fun stop(target: LogSource) {
        when (target) {
            is LogSource.Docker -> {
                val cmd = listOf(dockerExecutable, "stop", target.containerName)
                val result = executor.execute(cmd, timeoutMs = 15000)
                if (result.exitCode != 0) {
                    val error = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to stop container ${target.containerName}" }
                    throw RuntimeException(error.trim())
                }
            }
            is LogSource.DockerCompose -> {
                val cmd = buildComposeCommand(
                    projectDir = target.projectDir,
                    composeFile = target.composeFile,
                    subcommandAndArgs = listOf("stop", target.serviceName)
                )
                val result = executor.execute(cmd, timeoutMs = 15000)
                if (result.exitCode != 0) {
                    val error = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to stop service ${target.serviceName}" }
                    throw RuntimeException(error.trim())
                }
            }
            else -> throw UnsupportedOperationException("Unsupported target for Docker stop: $target")
        }
    }

    override suspend fun restart(target: LogSource) {
        when (target) {
            is LogSource.Docker -> {
                val cmd = listOf(dockerExecutable, "restart", target.containerName)
                val result = executor.execute(cmd, timeoutMs = 20000)
                if (result.exitCode != 0) {
                    val error = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to restart container ${target.containerName}" }
                    throw RuntimeException(error.trim())
                }
            }
            is LogSource.DockerCompose -> {
                val cmd = buildComposeCommand(
                    projectDir = target.projectDir,
                    composeFile = target.composeFile,
                    subcommandAndArgs = listOf("restart", target.serviceName)
                )
                val result = executor.execute(cmd, timeoutMs = 20000)
                if (result.exitCode != 0) {
                    val error = result.stderr.ifBlank { result.stdout }.ifBlank { "Failed to restart service ${target.serviceName}" }
                    throw RuntimeException(error.trim())
                }
            }
            else -> throw UnsupportedOperationException("Unsupported target for Docker restart: $target")
        }
    }

    @Serializable
    private data class RawDockerInspectState(
        @SerialName("Status") val status: String = "",
        @SerialName("Running") val running: Boolean = false,
        @SerialName("Paused") val paused: Boolean = false,
        @SerialName("Restarting") val restarting: Boolean = false,
        @SerialName("Dead") val dead: Boolean = false,
        @SerialName("ExitCode") val exitCode: Int = 0,
        @SerialName("Error") val error: String = ""
    ) {
        fun toContainerState(): ContainerState {
            val normalized = status.trim().lowercase()
            return when {
                paused || normalized == "paused" -> ContainerState.Paused(status = status.ifEmpty { "paused" })
                restarting || normalized == "restarting" -> ContainerState.Restarting(status = status.ifEmpty { "restarting" })
                dead || normalized == "dead" -> ContainerState.Dead(status = status.ifEmpty { "dead" })
                running || normalized == "running" -> ContainerState.Running(status = status.ifEmpty { "running" })
                normalized == "exited" || exitCode != 0 -> ContainerState.Exited(exitCode = exitCode, status = status.ifEmpty { "exited" })
                else -> parseContainerState(status)
            }
        }
    }

    @Serializable
    private data class RawComposePsOutput(
        @SerialName("ID") val id: String = "",
        @SerialName("Name") val name: String = "",
        @SerialName("State") val state: String = "",
        @SerialName("ExitCode") val exitCode: Int = 0,
        @SerialName("Status") val status: String = ""
    ) {
        fun toContainerState(): ContainerState {
            val normalized = state.trim().lowercase()
            return when {
                normalized == "running" -> ContainerState.Running(status = state.ifEmpty { "running" })
                normalized == "paused" -> ContainerState.Paused(status = state.ifEmpty { "paused" })
                normalized == "restarting" -> ContainerState.Restarting(status = state.ifEmpty { "restarting" })
                normalized == "dead" -> ContainerState.Dead(status = state.ifEmpty { "dead" })
                normalized == "exited" -> ContainerState.Exited(exitCode = exitCode, status = state.ifEmpty { "exited" })
                normalized.isEmpty() -> ContainerState.Unknown(rawStatus = "")
                else -> ContainerState.Unknown(rawStatus = state)
            }
        }
    }

    companion object {
        private fun parseContainerState(stateStr: String, statusStr: String = ""): ContainerState {
            val normalized = stateStr.trim().lowercase()
            return when {
                normalized == "running" -> ContainerState.Running(status = stateStr.ifEmpty { "running" })
                normalized == "paused" -> ContainerState.Paused(status = stateStr.ifEmpty { "paused" })
                normalized == "restarting" -> ContainerState.Restarting(status = stateStr.ifEmpty { "restarting" })
                normalized == "exited" -> {
                    val exitCode = extractExitCode(statusStr) ?: 0
                    ContainerState.Exited(exitCode = exitCode, status = stateStr.ifEmpty { "exited" })
                }
                normalized == "dead" -> ContainerState.Dead(status = stateStr.ifEmpty { "dead" })
                normalized.isEmpty() -> ContainerState.Unknown(rawStatus = "")
                else -> ContainerState.Unknown(rawStatus = stateStr)
            }
        }

        private fun extractExitCode(statusStr: String): Int? {
            val regex = Regex("""Exited\s*\(([0-9]+)\)""", RegexOption.IGNORE_CASE)
            return regex.find(statusStr)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}
