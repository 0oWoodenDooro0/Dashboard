package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource

interface ProcessManager {
    suspend fun startProcess(
        serviceId: String,
        workingDir: String,
        command: String,
        environment: Map<String, String> = emptyMap()
    )

    suspend fun stopProcess(
        serviceId: String,
        stopCommand: String? = null,
        workingDir: String? = null,
        timeoutSeconds: Int = 5
    )

    suspend fun restartProcess(
        serviceId: String,
        workingDir: String,
        command: String,
        stopCommand: String? = null,
        environment: Map<String, String> = emptyMap()
    )

    fun isRunning(serviceId: String): Boolean

    fun getProcessState(serviceId: String): ContainerState

    fun streamLogs(serviceId: String, tail: Int = 100): Flow<String>

    fun streamLogs(source: LogSource.Command, tail: Int = 100): Flow<String>
}
