package website.woodendoor.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ContainerState {
    @Serializable
    @SerialName("running")
    data class Running(val status: String = "running") : ContainerState

    @Serializable
    @SerialName("paused")
    data class Paused(val status: String = "paused") : ContainerState

    @Serializable
    @SerialName("restarting")
    data class Restarting(val status: String = "restarting") : ContainerState

    @Serializable
    @SerialName("exited")
    data class Exited(val exitCode: Int, val status: String = "exited") : ContainerState

    @Serializable
    @SerialName("dead")
    data class Dead(val status: String = "dead") : ContainerState

    @Serializable
    @SerialName("not_found")
    data class NotFound(val reason: String = "Container not found") : ContainerState

    @Serializable
    @SerialName("unknown")
    data class Unknown(val rawStatus: String) : ContainerState
}

val ContainerState.isRunning: Boolean
    get() = this is ContainerState.Running

val ContainerState.isPaused: Boolean
    get() = this is ContainerState.Paused

val ContainerState.isExited: Boolean
    get() = this is ContainerState.Exited

val ContainerState.isNotFound: Boolean
    get() = this is ContainerState.NotFound

@Serializable
data class DockerContainerInfo(
    val id: String,
    val names: List<String>,
    val image: String,
    val state: ContainerState,
    val status: String,
    val created: Long = 0L,
    val ports: List<String> = emptyList()
) {
    val primaryName: String
        get() = names.firstOrNull()?.removePrefix("/") ?: id
}
