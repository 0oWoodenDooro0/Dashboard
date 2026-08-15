package website.woodendoor.dashboard.service

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DockerContainerInfo
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRuntimeManagerTest {

    private lateinit var fakeDockerClient: FakeDockerClient
    private lateinit var fakeDockerComposeClient: FakeDockerComposeClient
    private lateinit var fakeProcessManager: FakeProcessManager
    private lateinit var fakeLogStreamService: FakeLogStreamService
    private lateinit var runtimeManager: DefaultServiceRuntimeManager

    private val dockerService = ServiceItem(
        id = "docker-srv",
        name = "Docker Backend",
        logSource = LogSource.Docker(containerName = "backend-container")
    )

    private val composeService = ServiceItem(
        id = "compose-srv",
        name = "Compose Frontend",
        logSource = LogSource.DockerCompose(
            projectDir = "/apps/my-app",
            serviceName = "web",
            composeFile = "docker-compose.yml"
        )
    )

    private val commandService = ServiceItem(
        id = "cmd-srv",
        name = "Vite Dev Server",
        logSource = LogSource.Command(
            workingDir = "/apps/vite-dev",
            startCommand = "npm run dev",
            stopCommand = "npm run stop",
            environment = mapOf("NODE_ENV" to "development")
        )
    )

    private val fileService = ServiceItem(
        id = "file-srv",
        name = "File Log Service",
        logSource = LogSource.LocalFile(path = "/var/log/app.log")
    )

    private val noneService = ServiceItem(
        id = "none-srv",
        name = "No Log Service",
        logSource = LogSource.None
    )

    private class FakeDockerClient : DockerClient {
        var isAvailable = true
        val states = mutableMapOf<String, ContainerState>()
        val startCalls = mutableListOf<String>()
        val stopCalls = mutableListOf<String>()
        val restartCalls = mutableListOf<String>()
        var shouldThrowOnStart = false
        var shouldThrowOnStop = false
        var shouldThrowOnRestart = false

        override suspend fun isDockerAvailable(): Boolean = isAvailable
        override suspend fun listContainers(all: Boolean): List<DockerContainerInfo> = emptyList()
        override suspend fun getContainerState(nameOrId: String): ContainerState =
            states[nameOrId] ?: ContainerState.Running(status = "running")

        override fun streamLogs(nameOrId: String, tail: Int): Flow<String> = flow {}
        override suspend fun startContainer(nameOrId: String) {
            if (shouldThrowOnStart) throw RuntimeException("Docker start failed for $nameOrId")
            startCalls.add(nameOrId)
            states[nameOrId] = ContainerState.Running(status = "running")
        }

        override suspend fun stopContainer(nameOrId: String) {
            if (shouldThrowOnStop) throw RuntimeException("Docker stop failed for $nameOrId")
            stopCalls.add(nameOrId)
            states[nameOrId] = ContainerState.Exited(exitCode = 0, status = "exited")
        }

        override suspend fun restartContainer(nameOrId: String) {
            if (shouldThrowOnRestart) throw RuntimeException("Docker restart failed for $nameOrId")
            restartCalls.add(nameOrId)
            states[nameOrId] = ContainerState.Running(status = "running")
        }
    }

    private class FakeDockerComposeClient : DockerComposeClient {
        var isAvailable = true
        val states = mutableMapOf<String, ContainerState>()
        val startCalls = mutableListOf<Triple<String, String, String?>>()
        val stopCalls = mutableListOf<Triple<String, String, String?>>()
        val restartCalls = mutableListOf<Triple<String, String, String?>>()
        var shouldThrowOnStart = false
        var shouldThrowOnStop = false
        var shouldThrowOnRestart = false

        override suspend fun isComposeAvailable(): Boolean = isAvailable
        override suspend fun getServiceState(projectDir: String, serviceName: String, composeFile: String?): ContainerState =
            states["$projectDir:$serviceName:$composeFile"] ?: ContainerState.Running(status = "running")

        override suspend fun listServices(projectDir: String, composeFile: String?): List<String> = emptyList()
        override fun streamLogs(projectDir: String, serviceName: String, composeFile: String?, tail: Int): Flow<String> = flow {}

        override suspend fun startService(projectDir: String, serviceName: String, composeFile: String?) {
            if (shouldThrowOnStart) throw RuntimeException("Compose start failed for $serviceName")
            startCalls.add(Triple(projectDir, serviceName, composeFile))
            states["$projectDir:$serviceName:$composeFile"] = ContainerState.Running(status = "running")
        }

        override suspend fun stopService(projectDir: String, serviceName: String, composeFile: String?) {
            if (shouldThrowOnStop) throw RuntimeException("Compose stop failed for $serviceName")
            stopCalls.add(Triple(projectDir, serviceName, composeFile))
            states["$projectDir:$serviceName:$composeFile"] = ContainerState.Exited(exitCode = 0, status = "exited")
        }

        override suspend fun restartService(projectDir: String, serviceName: String, composeFile: String?) {
            if (shouldThrowOnRestart) throw RuntimeException("Compose restart failed for $serviceName")
            restartCalls.add(Triple(projectDir, serviceName, composeFile))
            states["$projectDir:$serviceName:$composeFile"] = ContainerState.Running(status = "running")
        }
    }

    private class FakeProcessManager : ProcessManager {
        val runningIds = mutableSetOf<String>()
        val states = mutableMapOf<String, ContainerState>()
        val startCalls = mutableListOf<Triple<String, String, String>>()
        val stopCalls = mutableListOf<Triple<String, String?, String?>>()
        val restartCalls = mutableListOf<Triple<String, String, String>>()
        var shouldThrowOnStart = false
        var shouldThrowOnStop = false
        var shouldThrowOnRestart = false

        override suspend fun startProcess(serviceId: String, workingDir: String, command: String, environment: Map<String, String>) {
            if (shouldThrowOnStart) throw RuntimeException("Process start failed for $serviceId")
            startCalls.add(Triple(serviceId, workingDir, command))
            runningIds.add(serviceId)
            states[serviceId] = ContainerState.Running(status = "running")
        }

        override suspend fun stopProcess(serviceId: String, stopCommand: String?, workingDir: String?, timeoutSeconds: Int) {
            if (shouldThrowOnStop) throw RuntimeException("Process stop failed for $serviceId")
            stopCalls.add(Triple(serviceId, stopCommand, workingDir))
            runningIds.remove(serviceId)
            states[serviceId] = ContainerState.Exited(exitCode = 0, status = "stopped")
        }

        override suspend fun restartProcess(serviceId: String, workingDir: String, command: String, stopCommand: String?, environment: Map<String, String>) {
            if (shouldThrowOnRestart) throw RuntimeException("Process restart failed for $serviceId")
            restartCalls.add(Triple(serviceId, workingDir, command))
            runningIds.add(serviceId)
            states[serviceId] = ContainerState.Running(status = "running")
        }

        override fun isRunning(serviceId: String): Boolean = serviceId in runningIds
        override fun getProcessState(serviceId: String): ContainerState =
            states[serviceId] ?: ContainerState.NotFound(reason = "Process not found")

        override fun streamLogs(serviceId: String, tail: Int): Flow<String> = flow {}
        override fun streamLogs(source: LogSource.Command, tail: Int): Flow<String> = flow {}
    }

    private class FakeLogStreamService : LogStreamService {
        val flowMap = mutableMapOf<String, MutableSharedFlow<String>>()
        var lastRequestedSource: LogSource? = null
        var lastRequestedServiceId: String? = null
        var lastRequestedTail: Int = 100

        fun getOrCreateFlow(key: String): MutableSharedFlow<String> =
            flowMap.getOrPut(key) { MutableSharedFlow(extraBufferCapacity = 64) }

        override fun streamLogs(source: LogSource, serviceId: String?, tail: Int): Flow<String> {
            lastRequestedSource = source
            lastRequestedServiceId = serviceId
            lastRequestedTail = tail
            val key = serviceId ?: source.toString()
            return getOrCreateFlow(key)
        }
    }

    @BeforeTest
    fun setUp() {
        fakeDockerClient = FakeDockerClient()
        fakeDockerComposeClient = FakeDockerComposeClient()
        fakeProcessManager = FakeProcessManager()
        fakeLogStreamService = FakeLogStreamService()
        runtimeManager = DefaultServiceRuntimeManager(
            dockerClient = fakeDockerClient,
            dockerComposeClient = fakeDockerComposeClient,
            processManager = fakeProcessManager,
            logStreamService = fakeLogStreamService
        )
    }

    @Test
    fun isDockerAvailable_delegatesToDockerClient() = runTest {
        fakeDockerClient.isAvailable = true
        assertTrue(runtimeManager.isDockerAvailable())

        fakeDockerClient.isAvailable = false
        assertFalse(runtimeManager.isDockerAvailable())
    }

    // --- Docker Target Tests ---

    @Test
    fun dockerTarget_lifecycleOperations_delegateCorrectly() = runTest {
        // Start
        runtimeManager.startService(dockerService)
        assertEquals(listOf("backend-container"), fakeDockerClient.startCalls)

        // State check
        val state = runtimeManager.getServiceState(dockerService)
        assertIs<ContainerState.Running>(state)

        // Restart
        runtimeManager.restartService(dockerService)
        assertEquals(listOf("backend-container"), fakeDockerClient.restartCalls)

        // Stop
        runtimeManager.stopService(dockerService)
        assertEquals(listOf("backend-container"), fakeDockerClient.stopCalls)

        val stoppedState = runtimeManager.getServiceState(dockerService)
        assertIs<ContainerState.Exited>(stoppedState)
    }

    @Test
    fun dockerTarget_whenDockerClientThrows_propagatesException() = runTest {
        fakeDockerClient.shouldThrowOnStart = true
        assertFailsWith<RuntimeException> {
            runtimeManager.startService(dockerService)
        }
    }

    // --- Docker Compose Target Tests ---

    @Test
    fun dockerComposeTarget_lifecycleOperations_delegateCorrectly() = runTest {
        // Start
        runtimeManager.startService(composeService)
        assertEquals(1, fakeDockerComposeClient.startCalls.size)
        val (projDir, srvName, file) = fakeDockerComposeClient.startCalls.first()
        assertEquals("/apps/my-app", projDir)
        assertEquals("web", srvName)
        assertEquals("docker-compose.yml", file)

        // State
        val state = runtimeManager.getServiceState(composeService)
        assertIs<ContainerState.Running>(state)

        // Restart
        runtimeManager.restartService(composeService)
        assertEquals(1, fakeDockerComposeClient.restartCalls.size)

        // Stop
        runtimeManager.stopService(composeService)
        assertEquals(1, fakeDockerComposeClient.stopCalls.size)

        val stoppedState = runtimeManager.getServiceState(composeService)
        assertIs<ContainerState.Exited>(stoppedState)
    }

    // --- Directory Command (Process) Target Tests ---

    @Test
    fun commandTarget_lifecycleOperations_delegateCorrectly() = runTest {
        assertFalse(runtimeManager.isRunning(commandService))

        // Start
        runtimeManager.startService(commandService)
        assertEquals(1, fakeProcessManager.startCalls.size)
        val (srvId, dir, cmd) = fakeProcessManager.startCalls.first()
        assertEquals("cmd-srv", srvId)
        assertEquals("/apps/vite-dev", dir)
        assertEquals("npm run dev", cmd)
        assertTrue(runtimeManager.isRunning(commandService))

        // State
        val state = runtimeManager.getServiceState(commandService)
        assertIs<ContainerState.Running>(state)

        // Restart
        runtimeManager.restartService(commandService)
        assertEquals(1, fakeProcessManager.restartCalls.size)

        // Stop
        runtimeManager.stopService(commandService)
        assertEquals(1, fakeProcessManager.stopCalls.size)
        assertFalse(runtimeManager.isRunning(commandService))
    }

    // --- Local File & None Target Tests ---

    @Test
    fun fileAndNoneTarget_lifecycleOperations_handleGracefully() = runTest {
        // LocalFile and None services do not run as manageable processes
        assertFalse(runtimeManager.isRunning(fileService))
        assertFalse(runtimeManager.isRunning(noneService))

        // Starting/stopping file or none services should be safe no-op or throw unsupported
        assertFailsWith<UnsupportedOperationException> {
            runtimeManager.startService(fileService)
        }
        assertFailsWith<UnsupportedOperationException> {
            runtimeManager.startService(noneService)
        }
    }

    // --- Log Streaming Tests ---

    @Test
    fun streamLogs_delegatesToLogStreamServiceWithCorrectServiceIdAndSource() = runTest {
        val flow = runtimeManager.streamLogs(dockerService, tail = 50)
        assertEquals(dockerService.logSource, fakeLogStreamService.lastRequestedSource)
        assertEquals("docker-srv", fakeLogStreamService.lastRequestedServiceId)
        assertEquals(50, fakeLogStreamService.lastRequestedTail)

        // Verify flow connection
        val testFlow = fakeLogStreamService.getOrCreateFlow("docker-srv")
        testFlow.tryEmit("log line 1")
    }

    @Test
    fun streamLogs_forCommandService_passesServiceId() = runTest {
        runtimeManager.streamLogs(commandService, tail = 100)
        assertEquals(commandService.logSource, fakeLogStreamService.lastRequestedSource)
        assertEquals("cmd-srv", fakeLogStreamService.lastRequestedServiceId)
        assertEquals(100, fakeLogStreamService.lastRequestedTail)
    }
}
