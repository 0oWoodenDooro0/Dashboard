package website.woodendoor.dashboard.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DockerContainerInfo

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

    override suspend fun isDockerAvailable(): Boolean {
        val cmd = listOf(dockerExecutable, "version", "--format", "{{.Server.Version}}")
        return try {
            val result = executor.execute(cmd, timeoutMs = 3000)
            result.exitCode == 0 && result.stdout.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun listContainers(all: Boolean): List<DockerContainerInfo> {
        val cmd = mutableListOf(dockerExecutable, "ps")
        if (all) {
            cmd.add("-a")
        }
        cmd.addAll(listOf("--format", "{{json .}}"))

        val result = executor.execute(cmd)
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            return emptyList()
        }

        val list = mutableListOf<DockerContainerInfo>()
        for (line in result.stdout.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) continue
            try {
                val raw = json.decodeFromString<RawDockerPsOutput>(trimmed)
                list.add(raw.toDockerContainerInfo())
            } catch (_: Exception) {
                // Ignore malformed lines
            }
        }
        return list
    }

    override suspend fun getContainerState(nameOrId: String): ContainerState {
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

    override fun streamLogs(nameOrId: String, tail: Int): Flow<String> {
        val cmd = listOf(
            dockerExecutable,
            "logs",
            "-f",
            "--tail",
            tail.toString(),
            nameOrId
        )
        return executor.executeStreaming(cmd)
    }

    @Serializable
    private data class RawDockerPsOutput(
        @SerialName("ID") val id: String = "",
        @SerialName("Names") val names: String = "",
        @SerialName("Image") val image: String = "",
        @SerialName("State") val state: String = "",
        @SerialName("Status") val status: String = "",
        @SerialName("CreatedAt") val createdAt: String = "",
        @SerialName("Ports") val ports: String = ""
    ) {
        fun toDockerContainerInfo(): DockerContainerInfo {
            val parsedNames = names.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val parsedPorts = ports.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val createdTimestamp = createdAt.toLongOrNull() ?: 0L
            val containerState = parseContainerState(state, status)

            return DockerContainerInfo(
                id = id,
                names = parsedNames,
                image = image,
                state = containerState,
                status = status,
                created = createdTimestamp,
                ports = parsedPorts
            )
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
