package website.woodendoor.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PortHealth {
    @Serializable
    @SerialName("open")
    data class Open(val latencyMs: Long) : PortHealth

    @Serializable
    @SerialName("closed")
    data class Closed(val reason: String? = null) : PortHealth

    @Serializable
    @SerialName("unreachable")
    data class Unreachable(val reason: String? = null) : PortHealth

    @Serializable
    @SerialName("none")
    data object None : PortHealth
}

@Serializable
data class ServiceRuntimeStatus(
    val serviceId: String,
    val portHealth: PortHealth = PortHealth.None,
    val containerState: ContainerState = ContainerState.Unknown(""),
    val isHealthy: Boolean = (portHealth is PortHealth.Open || portHealth is PortHealth.None),
    val lastCheckedTimestamp: Long = 0L
)

typealias ServiceStatus = ServiceRuntimeStatus
