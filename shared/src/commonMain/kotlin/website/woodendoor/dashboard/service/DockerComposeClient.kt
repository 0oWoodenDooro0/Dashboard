package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState

interface DockerComposeClient {
    suspend fun isComposeAvailable(): Boolean
    suspend fun getServiceState(projectDir: String, serviceName: String, composeFile: String? = null): ContainerState
    suspend fun listServices(projectDir: String, composeFile: String? = null): List<String>
    fun streamLogs(projectDir: String, serviceName: String, composeFile: String? = null, tail: Int = 100): Flow<String>
    suspend fun startService(projectDir: String, serviceName: String, composeFile: String? = null)
    suspend fun stopService(projectDir: String, serviceName: String, composeFile: String? = null)
    suspend fun restartService(projectDir: String, serviceName: String, composeFile: String? = null)
}
