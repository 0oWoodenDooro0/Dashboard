package website.woodendoor.dashboard.model

import kotlinx.serialization.Serializable

@Serializable
data class SummaryMetrics(
    val totalCount: Int,
    val healthyCount: Int,
    val offlineCount: Int,
    val isDockerAvailable: Boolean
)

@Serializable
data class ServiceRuntimeStatus(
    val serviceId: String,
    val portHealth: PortHealth = PortHealth.None,
    val containerState: ContainerState = ContainerState.Unknown(""),
    val isHealthy: Boolean = (portHealth is PortHealth.Open || portHealth is PortHealth.None),
    val lastCheckedTimestamp: Long = 0L
) {
    val portBadgeLabel: String
        get() = portHealth.badgeLabel

    val containerBadgeLabel: String
        get() = containerState.badgeLabel

    val portDetails: String?
        get() = portHealth.details

    val containerDetails: String?
        get() = containerState.details

    companion object {
        fun calculateHealth(
            logSource: LogSource,
            portHealth: PortHealth,
            containerState: ContainerState
        ): Boolean {
            val isPortHealthy = portHealth is PortHealth.Open || portHealth is PortHealth.None
            return when (logSource) {
                is LogSource.Docker, is LogSource.DockerCompose, is LogSource.Command -> {
                    containerState is ContainerState.Running && isPortHealthy
                }
                is LogSource.LocalFile, is LogSource.None -> {
                    isPortHealthy && containerState !is ContainerState.Dead && containerState !is ContainerState.Exited
                }
            }
        }

        fun of(
            service: ServiceItem,
            portHealth: PortHealth,
            containerState: ContainerState,
            lastCheckedTimestamp: Long = System.currentTimeMillis()
        ): ServiceRuntimeStatus {
            return ServiceRuntimeStatus(
                serviceId = service.id,
                portHealth = portHealth,
                containerState = containerState,
                isHealthy = calculateHealth(service.logSource, portHealth, containerState),
                lastCheckedTimestamp = lastCheckedTimestamp
            )
        }

        fun calculateSummaryMetrics(
            services: List<ServiceItem>,
            statuses: Map<String, ServiceRuntimeStatus>,
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
    }
}

val PortHealth.badgeLabel: String
    get() = when (this) {
        is PortHealth.Open -> "${latencyMs}ms"
        is PortHealth.Closed -> "Closed"
        is PortHealth.Unreachable -> "Unreachable"
        PortHealth.None -> "No Port"
    }

val PortHealth.details: String?
    get() = when (this) {
        is PortHealth.Open -> "Latency: ${latencyMs}ms"
        is PortHealth.Closed -> reason ?: "Connection refused"
        is PortHealth.Unreachable -> reason ?: "Host unreachable"
        PortHealth.None -> null
    }

val ContainerState.badgeLabel: String
    get() = when (this) {
        is ContainerState.Running -> "Running"
        is ContainerState.Paused -> "Paused"
        is ContainerState.Restarting -> "Restarting"
        is ContainerState.Exited -> "Exited ($exitCode)"
        is ContainerState.Dead -> "Dead"
        is ContainerState.NotFound -> "Not Found"
        is ContainerState.Unknown -> rawStatus.ifEmpty { "Unknown" }
    }

val ContainerState.details: String?
    get() = when (this) {
        is ContainerState.Running -> status
        is ContainerState.Paused -> status
        is ContainerState.Restarting -> status
        is ContainerState.Exited -> status
        is ContainerState.Dead -> status
        is ContainerState.NotFound -> reason
        is ContainerState.Unknown -> rawStatus.ifEmpty { null }
    }
