package website.woodendoor.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LogSource {
    @Serializable
    @SerialName("docker")
    data class Docker(val containerName: String) : LogSource

    @Serializable
    @SerialName("docker-compose")
    data class DockerCompose(
        val projectDir: String,
        val serviceName: String,
        val composeFile: String? = null
    ) : LogSource

    @Serializable
    @SerialName("file")
    data class LocalFile(val path: String) : LogSource

    @Serializable
    @SerialName("command")
    data class Command(
        val workingDir: String,
        val startCommand: String,
        val stopCommand: String? = null,
        val environment: Map<String, String> = emptyMap()
    ) : LogSource

    @Serializable
    @SerialName("none")
    data object None : LogSource
}

@Serializable
data class ServiceItem(
    val id: String,
    val name: String,
    val host: String = "127.0.0.1",
    val port: Int? = null,
    val healthUrl: String? = null,
    val openUrl: String? = null,
    val logSource: LogSource = LogSource.None,
    val description: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class ServiceGroup(
    val id: String,
    val name: String,
    val services: List<ServiceItem> = emptyList()
)

@Serializable
data class DashboardConfig(
    val version: Int = 1,
    val pollingIntervalSeconds: Long = 5,
    val groups: List<ServiceGroup> = emptyList()
)
