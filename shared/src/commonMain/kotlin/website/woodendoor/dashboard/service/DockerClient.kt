package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DockerContainerInfo

interface DockerClient {
    suspend fun isDockerAvailable(): Boolean
    suspend fun listContainers(all: Boolean = true): List<DockerContainerInfo>
    suspend fun getContainerState(nameOrId: String): ContainerState
    fun streamLogs(nameOrId: String, tail: Int = 100): Flow<String>
    suspend fun startContainer(nameOrId: String)
    suspend fun stopContainer(nameOrId: String)
    suspend fun restartContainer(nameOrId: String)
}
