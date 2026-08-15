package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem

class DefaultServiceRuntimeManager(
    private val dockerClient: DockerClient = CliDockerClient(),
    private val dockerComposeClient: DockerComposeClient = CliDockerComposeClient(),
    private val processManager: ProcessManager = DefaultProcessManager(),
    private val logStreamService: LogStreamService = DefaultLogStreamService(
        dockerClient = dockerClient,
        dockerComposeClient = dockerComposeClient,
        processManager = processManager
    )
) : ServiceRuntimeManager {

    override suspend fun isDockerAvailable(): Boolean {
        return dockerClient.isDockerAvailable()
    }

    override suspend fun getServiceState(service: ServiceItem): ContainerState {
        return when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.getContainerState(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.getServiceState(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.getProcessState(service.id)
            is LogSource.LocalFile, is LogSource.None -> ContainerState.Unknown("")
        }
    }

    override fun streamLogs(service: ServiceItem, tail: Int): Flow<String> {
        return logStreamService.streamLogs(service.logSource, serviceId = service.id, tail = tail)
    }

    override suspend fun startService(service: ServiceItem) {
        when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.startContainer(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.startService(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.startProcess(
                serviceId = service.id,
                workingDir = src.workingDir,
                command = src.startCommand,
                environment = src.environment
            )
            is LogSource.LocalFile, is LogSource.None -> {
                throw UnsupportedOperationException("Service ${service.name} does not support lifecycle start")
            }
        }
    }

    override suspend fun stopService(service: ServiceItem) {
        when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.stopContainer(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.stopService(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.stopProcess(
                serviceId = service.id,
                stopCommand = src.stopCommand,
                workingDir = src.workingDir
            )
            is LogSource.LocalFile, is LogSource.None -> {
                throw UnsupportedOperationException("Service ${service.name} does not support lifecycle stop")
            }
        }
    }

    override suspend fun restartService(service: ServiceItem) {
        when (val src = service.logSource) {
            is LogSource.Docker -> dockerClient.restartContainer(src.containerName)
            is LogSource.DockerCompose -> dockerComposeClient.restartService(
                projectDir = src.projectDir,
                serviceName = src.serviceName,
                composeFile = src.composeFile
            )
            is LogSource.Command -> processManager.restartProcess(
                serviceId = service.id,
                workingDir = src.workingDir,
                command = src.startCommand,
                stopCommand = src.stopCommand,
                environment = src.environment
            )
            is LogSource.LocalFile, is LogSource.None -> {
                throw UnsupportedOperationException("Service ${service.name} does not support lifecycle restart")
            }
        }
    }

    override fun isRunning(service: ServiceItem): Boolean {
        return when (service.logSource) {
            is LogSource.Command -> processManager.isRunning(service.id)
            else -> false
        }
    }
}
