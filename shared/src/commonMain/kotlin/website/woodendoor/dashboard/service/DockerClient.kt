package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState

interface DockerClient {
    suspend fun isDockerAvailable(): Boolean
    suspend fun getContainerState(nameOrId: String): ContainerState
    fun streamLogs(nameOrId: String, tail: Int = 100): Flow<String>
    suspend fun startContainer(nameOrId: String)
    suspend fun stopContainer(nameOrId: String)
    suspend fun restartContainer(nameOrId: String)

    suspend fun getComposeServiceState(
        projectDir: String,
        serviceName: String,
        composeFile: String? = null
    ): ContainerState

    fun streamComposeLogs(
        projectDir: String,
        serviceName: String,
        composeFile: String? = null,
        tail: Int = 100
    ): Flow<String>

    suspend fun startComposeService(
        projectDir: String,
        serviceName: String,
        composeFile: String? = null
    )

    suspend fun stopComposeService(
        projectDir: String,
        serviceName: String,
        composeFile: String? = null
    )

    suspend fun restartComposeService(
        projectDir: String,
        serviceName: String,
        composeFile: String? = null
    )
}
