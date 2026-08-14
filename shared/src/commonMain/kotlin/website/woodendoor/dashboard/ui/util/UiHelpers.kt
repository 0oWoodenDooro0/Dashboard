package website.woodendoor.dashboard.ui.util

import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus

data class SummaryMetrics(
    val totalCount: Int,
    val healthyCount: Int,
    val offlineCount: Int,
    val isDockerAvailable: Boolean
)

enum class PortStatusType {
    HEALTHY,
    CLOSED,
    UNREACHABLE,
    NO_PORT
}

data class PortHealthDisplayInfo(
    val label: String,
    val statusType: PortStatusType,
    val details: String? = null
)

enum class ContainerStatusType {
    RUNNING,
    PAUSED,
    RESTARTING,
    EXITED,
    DEAD,
    NOT_FOUND,
    UNKNOWN
}

data class ContainerStateDisplayInfo(
    val label: String,
    val statusType: ContainerStatusType,
    val details: String? = null
)

object UiHelpers {

    fun calculateSummaryMetrics(
        services: List<ServiceItem>,
        statuses: Map<String, ServiceStatus>,
        isDockerAvailable: Boolean
    ): SummaryMetrics {
        val totalCount = services.size
        var healthyCount = 0
        var offlineCount = 0

        for (service in services) {
            val status = statuses[service.id]
            if (status != null && status.isHealthy) {
                healthyCount++
            } else if (status != null && !status.isHealthy) {
                offlineCount++
            } else {
                // If status not yet checked or no port
                if (service.port == null) {
                    healthyCount++
                } else {
                    offlineCount++
                }
            }
        }

        return SummaryMetrics(
            totalCount = totalCount,
            healthyCount = healthyCount,
            offlineCount = offlineCount,
            isDockerAvailable = isDockerAvailable
        )
    }

    fun getPortHealthDisplayInfo(portHealth: PortHealth?): PortHealthDisplayInfo {
        return when (portHealth) {
            is PortHealth.Open -> PortHealthDisplayInfo(
                label = "${portHealth.latencyMs}ms",
                statusType = PortStatusType.HEALTHY,
                details = "Latency: ${portHealth.latencyMs}ms"
            )
            is PortHealth.Closed -> PortHealthDisplayInfo(
                label = "Closed",
                statusType = PortStatusType.CLOSED,
                details = portHealth.reason ?: "Connection refused"
            )
            is PortHealth.Unreachable -> PortHealthDisplayInfo(
                label = "Unreachable",
                statusType = PortStatusType.UNREACHABLE,
                details = portHealth.reason ?: "Host unreachable"
            )
            PortHealth.None, null -> PortHealthDisplayInfo(
                label = "No Port",
                statusType = PortStatusType.NO_PORT,
                details = null
            )
        }
    }

    fun getContainerStateDisplayInfo(state: ContainerState?): ContainerStateDisplayInfo {
        return when (state) {
            is ContainerState.Running -> ContainerStateDisplayInfo(
                label = "Running",
                statusType = ContainerStatusType.RUNNING,
                details = state.status
            )
            is ContainerState.Paused -> ContainerStateDisplayInfo(
                label = "Paused",
                statusType = ContainerStatusType.PAUSED,
                details = state.status
            )
            is ContainerState.Restarting -> ContainerStateDisplayInfo(
                label = "Restarting",
                statusType = ContainerStatusType.RESTARTING,
                details = state.status
            )
            is ContainerState.Exited -> ContainerStateDisplayInfo(
                label = "Exited (${state.exitCode})",
                statusType = ContainerStatusType.EXITED,
                details = state.status
            )
            is ContainerState.Dead -> ContainerStateDisplayInfo(
                label = "Dead",
                statusType = ContainerStatusType.DEAD,
                details = state.status
            )
            is ContainerState.NotFound -> ContainerStateDisplayInfo(
                label = "Not Found",
                statusType = ContainerStatusType.NOT_FOUND,
                details = state.reason
            )
            is ContainerState.Unknown -> ContainerStateDisplayInfo(
                label = state.rawStatus.ifEmpty { "Unknown" },
                statusType = ContainerStatusType.UNKNOWN,
                details = state.rawStatus
            )
            null -> ContainerStateDisplayInfo(
                label = "N/A",
                statusType = ContainerStatusType.UNKNOWN,
                details = null
            )
        }
    }
}
