package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.ServiceItem

/**
 * Deep module interface managing service runtime lifecycles, states, and log streaming
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
