package website.woodendoor.dashboard.service

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import website.woodendoor.dashboard.model.ContainerState

class CliDockerComposeClient(
    private val dockerExecutable: String = "docker",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    processExecutor: ProcessExecutor? = null
) : DockerComposeClient {

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

    override suspend fun isComposeAvailable(): Boolean {
        val cmd = listOf(dockerExecutable, "compose", "version")
        return try {
            val result = executor.execute(cmd, timeoutMs = 3000)
            result.exitCode == 0 && result.stdout.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getServiceState(
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

    override suspend fun listServices(projectDir: String, composeFile: String?): List<String> {
        val cmd = buildComposeCommand(
            projectDir = projectDir,
            composeFile = composeFile,
            subcommandAndArgs = listOf("config", "--services")
        )
        val result = executor.execute(cmd)
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            return emptyList()
        }
        return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun streamLogs(
        projectDir: String,
        serviceName: String,
        composeFile: String?,
        tail: Int
    ): Flow<String> {
        val cmd = buildComposeCommand(
            projectDir = projectDir,
            composeFile = composeFile,
            subcommandAndArgs = listOf("logs", "-f", "--tail", tail.toString(), serviceName)
        )
        return executor.executeStreaming(cmd)
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
}
