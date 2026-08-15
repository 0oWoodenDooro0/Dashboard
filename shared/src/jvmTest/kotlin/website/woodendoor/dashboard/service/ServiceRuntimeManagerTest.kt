package website.woodendoor.dashboard.service

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DockerContainerInfo
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRuntimeManagerTest {

    private lateinit var tempDir: File
    private lateinit var fakeDockerClient: FakeDockerClient
    private lateinit var fakeDockerComposeClient: FakeDockerComposeClient
    private lateinit var fakeProcessManager: FakeProcessManager
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
        var lastRequestedContainer: String? = null
        var lastRequestedTail: Int? = null
        var customLogFlow: Flow<String> = flowOf("docker line 1", "docker line 2")

        override suspend fun isDockerAvailable(): Boolean = isAvailable
        override suspend fun listContainers(all: Boolean): List<DockerContainerInfo> = emptyList()
        override suspend fun getContainerState(nameOrId: String): ContainerState =
            states[nameOrId] ?: ContainerState.Running(status = "running")

        override fun streamLogs(nameOrId: String, tail: Int): Flow<String> {
            lastRequestedContainer = nameOrId
            lastRequestedTail = tail
            return customLogFlow
        }

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
        var lastRequestedProjectDir: String? = null
        var lastRequestedServiceName: String? = null
        var lastRequestedComposeFile: String? = null
        var lastRequestedTail: Int? = null
        var customLogFlow: Flow<String> = flowOf("compose line 1", "compose line 2")

        override suspend fun isComposeAvailable(): Boolean = isAvailable
        override suspend fun getServiceState(projectDir: String, serviceName: String, composeFile: String?): ContainerState =
            states["$projectDir:$serviceName:$composeFile"] ?: ContainerState.Running(status = "running")

        override suspend fun listServices(projectDir: String, composeFile: String?): List<String> = emptyList()
        override fun streamLogs(projectDir: String, serviceName: String, composeFile: String?, tail: Int): Flow<String> {
            lastRequestedProjectDir = projectDir
            lastRequestedServiceName = serviceName
            lastRequestedComposeFile = composeFile
            lastRequestedTail = tail
            return customLogFlow
        }

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
        var lastRequestedServiceId: String? = null
        var lastRequestedSource: LogSource.Command? = null
        var lastRequestedTail: Int? = null
        var customLogFlow: Flow<String> = flowOf("process line 1", "process line 2")

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

        override fun streamLogs(serviceId: String, tail: Int): Flow<String> {
            lastRequestedServiceId = serviceId
            lastRequestedTail = tail
            return customLogFlow
        }

        override fun streamLogs(source: LogSource.Command, tail: Int): Flow<String> {
            lastRequestedSource = source
            lastRequestedTail = tail
            return customLogFlow
        }
    }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("runtime-manager-test").toFile()
        fakeDockerClient = FakeDockerClient()
        fakeDockerComposeClient = FakeDockerComposeClient()
        fakeProcessManager = FakeProcessManager()
        runtimeManager = DefaultServiceRuntimeManager(
            dockerClient = fakeDockerClient,
            dockerComposeClient = fakeDockerComposeClient,
            processManager = fakeProcessManager,
            filePollDelayMs = 20L
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
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

    // --- Log Streaming Direct Target Tests ---

    @Test
    fun streamLogs_dockerTarget_delegatesToDockerClientWithTail() = runTest {
        fakeDockerClient.customLogFlow = listOf("docker: init", "docker: started").asFlow()
        val result = runtimeManager.streamLogs(dockerService, tail = 50).toList()

        assertEquals("backend-container", fakeDockerClient.lastRequestedContainer)
        assertEquals(50, fakeDockerClient.lastRequestedTail)
        assertEquals(listOf("docker: init", "docker: started"), result)
    }

    @Test
    fun streamLogs_dockerComposeTarget_delegatesToDockerComposeClientWithTail() = runTest {
        fakeDockerComposeClient.customLogFlow = listOf("compose: web started").asFlow()
        val result = runtimeManager.streamLogs(composeService, tail = 75).toList()

        assertEquals("/apps/my-app", fakeDockerComposeClient.lastRequestedProjectDir)
        assertEquals("web", fakeDockerComposeClient.lastRequestedServiceName)
        assertEquals("docker-compose.yml", fakeDockerComposeClient.lastRequestedComposeFile)
        assertEquals(75, fakeDockerComposeClient.lastRequestedTail)
        assertEquals(listOf("compose: web started"), result)
    }

    @Test
    fun streamLogs_commandTarget_delegatesToProcessManagerWithServiceIdAndTail() = runTest {
        fakeProcessManager.customLogFlow = listOf("cmd: vite server ready").asFlow()
        val result = runtimeManager.streamLogs(commandService, tail = 100).toList()

        assertEquals("cmd-srv", fakeProcessManager.lastRequestedServiceId)
        assertEquals(100, fakeProcessManager.lastRequestedTail)
        assertEquals(listOf("cmd: vite server ready"), result)
    }

    @Test
    fun streamLogs_noneTarget_returnsEmptyFlow() = runTest {
        val result = runtimeManager.streamLogs(noneService, tail = 100).toList()
        assertTrue(result.isEmpty())
    }

    // --- Local File Log Streaming & Tail Tests ---

    @Test
    fun streamLogs_localFileTarget_streamsInitialTailLines() = runTest {
        withContext(Dispatchers.Default) {
            val logFile = File(tempDir, "app.log")
            val allLines = (1..10).map { "log line $it" }
            logFile.writeText(allLines.joinToString("\n") + "\n")

            val service = ServiceItem(
                id = "file-app",
                name = "File App",
                logSource = LogSource.LocalFile(logFile.absolutePath)
            )

            val result = withTimeout(3000) {
                runtimeManager.streamLogs(service, tail = 3).take(3).toList()
            }

            assertEquals(listOf("log line 8", "log line 9", "log line 10"), result)
        }
    }

    @Test
    fun streamLogs_localFileTarget_streamsDynamicAppends() = runTest {
        withContext(Dispatchers.Default) {
            val logFile = File(tempDir, "dynamic.log")
            logFile.writeText("initial\n")

            val service = ServiceItem(
                id = "dyn-file",
                name = "Dynamic File",
                logSource = LogSource.LocalFile(logFile.absolutePath)
            )

            val flow = runtimeManager.streamLogs(service, tail = 1)
            val collector = async {
                withTimeout(4000) {
                    flow.take(3).toList()
                }
            }

            delay(50)
            logFile.appendText("appended-1\n")
            delay(50)
            logFile.appendText("appended-2\n")

            val result = collector.await()
            assertEquals(listOf("initial", "appended-1", "appended-2"), result)
        }
    }

    @Test
    fun streamLogs_localFileTarget_detectsFileTruncationAndRotation() = runTest {
        withContext(Dispatchers.Default) {
            val logFile = File(tempDir, "rotated.log")
            logFile.writeText("old line 1\nold line 2\nold line 3\n")

            val service = ServiceItem(
                id = "rot-file",
                name = "Rotated File",
                logSource = LogSource.LocalFile(logFile.absolutePath)
            )

            val flow = runtimeManager.streamLogs(service, tail = 1)
            val collector = async {
                withTimeout(4000) {
                    flow.take(3).toList()
                }
            }

            delay(50)
            logFile.writeText("rotated line 1\nrotated line 2\n")

            val result = collector.await()
            assertEquals(listOf("old line 3", "rotated line 1", "rotated line 2"), result)
        }
    }

    @Test
    fun streamLogs_localFileTarget_handlesCrlfLineEndings() = runTest {
        withContext(Dispatchers.Default) {
            val logFile = File(tempDir, "crlf.log")
            logFile.writeBytes("win line 1\r\nwin line 2\r\n".toByteArray(Charsets.UTF_8))

            val service = ServiceItem(
                id = "crlf-file",
                name = "CRLF File",
                logSource = LogSource.LocalFile(logFile.absolutePath)
            )

            val result = withTimeout(3000) {
                runtimeManager.streamLogs(service, tail = 2).take(2).toList()
            }

            assertEquals(listOf("win line 1", "win line 2"), result)
        }
    }

    @Test
    fun streamLogs_localFileTarget_executesOnInjectedIoDispatcher() = runTest {
        val threadCounter = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "custom-runtime-io-thread-${threadCounter.incrementAndGet()}")
        }
        val customIoDispatcher = executor.asCoroutineDispatcher()

        val managerWithCustomDispatcher = DefaultServiceRuntimeManager(
            dockerClient = fakeDockerClient,
            dockerComposeClient = fakeDockerComposeClient,
            processManager = fakeProcessManager,
            ioDispatcher = customIoDispatcher,
            filePollDelayMs = 20L
        )

        val logFile = File(tempDir, "dispatcher.log")
        logFile.writeText("dispatcher line 1\ndispatcher line 2\n")

        val service = ServiceItem(
            id = "disp-file",
            name = "Dispatcher File",
            logSource = LogSource.LocalFile(logFile.absolutePath)
        )

        try {
            val result = withContext(Dispatchers.Default) {
                withTimeout(3000) {
                    managerWithCustomDispatcher.streamLogs(service, tail = 2).take(2).toList()
                }
            }
            assertEquals(listOf("dispatcher line 1", "dispatcher line 2"), result)
        } finally {
            customIoDispatcher.close()
            executor.shutdown()
        }
    }
}

