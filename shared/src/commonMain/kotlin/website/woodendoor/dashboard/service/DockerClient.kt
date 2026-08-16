package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource

interface DockerClient {
    suspend fun isDockerAvailable(): Boolean
    suspend fun getContainerState(target: LogSource): ContainerState
    fun streamLogs(target: LogSource, tail: Int = 100): Flow<String>
    suspend fun start(target: LogSource)
    suspend fun stop(target: LogSource)
    suspend fun restart(target: LogSource)
}
