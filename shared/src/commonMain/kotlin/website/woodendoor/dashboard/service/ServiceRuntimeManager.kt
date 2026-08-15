package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceRuntimeStatus

/**
 * Deep module interface managing service runtime lifecycles, states, health checks, and log streaming
 * across multiple execution targets (Docker, Docker Compose, Directory Commands, and Files).
 */
interface ServiceRuntimeManager {

    /**
     * Checks if the Docker daemon is reachable and responding.
     */
    suspend fun isDockerAvailable(): Boolean

    /**
     * Retrieves the current container/process state for the given [service].
     */
    suspend fun getServiceState(service: ServiceItem): ContainerState

    /**
     * Probes the socket port health for the given [service].
     */
    suspend fun checkPortHealth(service: ServiceItem, timeoutMs: Long = 1000): PortHealth

    /**
     * Inspects both port connectivity and process/container runtime state for a single [service],
     * computing the consolidated [ServiceRuntimeStatus].
     */
    suspend fun inspectService(service: ServiceItem): ServiceRuntimeStatus

    /**
     * Concurrently inspects runtime status and port connectivity for a list of [services].
     */
    suspend fun inspectServices(services: List<ServiceItem>): Map<String, ServiceRuntimeStatus>

    /**
     * Opens a real-time log stream flow for the given [service].
     */
    fun streamLogs(service: ServiceItem, tail: Int = 100): Flow<String>

    /**
     * Starts the execution target for the given [service].
     */
    suspend fun startService(service: ServiceItem)

    /**
     * Stops the execution target for the given [service].
     */
    suspend fun stopService(service: ServiceItem)

    /**
     * Restarts the execution target for the given [service].
     */
    suspend fun restartService(service: ServiceItem)

    /**
     * Checks whether the given [service] is currently in a running state.
     */
    fun isRunning(service: ServiceItem): Boolean
}

