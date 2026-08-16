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
import kotlin.test.assertNotNull
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
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceRuntimeStatus

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceRuntimeManagerTest {

    private lateinit var tempDir: File
    private lateinit var fakeDockerClient: FakeDockerClient
    private lateinit var fakeProcessManager: FakeProcessManager
    private lateinit var fakePortHealthChecker: FakePortHealthChecker
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
        val states = mutableMapOf<LogSource, ContainerState>()
        val startCalls = mutableListOf<LogSource>()
        val stopCalls = mutableListOf<LogSource>()
        val restartCalls = mutableListOf<LogSource>()
        var shouldThrowOnStart = false
        var shouldThrowOnStop = false
        var shouldThrowOnRestart = false
        var lastRequestedTarget: LogSource? = null
        var lastRequestedTail: Int? = null
        var customLogFlow: Flow<String> = flowOf("docker line 1", "docker line 2")

        override suspend fun isDockerAvailable(): Boolean = isAvailable

        override suspend fun getContainerState(target: LogSource): ContainerState =
            states[target] ?: ContainerState.Running(status = "running")

        override fun streamLogs(target: LogSource, tail: Int): Flow<String> {
            lastRequestedTarget = target
            lastRequestedTail = tail
            return customLogFlow
        }

        override suspend fun start(target: LogSource) {
            if (shouldThrowOnStart) throw RuntimeException("Docker start failed for $target")
            startCalls.add(target)
            states[target] = ContainerState.Running(status = "running")
        }

        override suspend fun stop(target: LogSource) {
            if (shouldThrowOnStop) throw RuntimeException("Docker stop failed for $target")
            stopCalls.add(target)
            states[target] = ContainerState.Exited(exitCode = 0, status = "exited")
        }

        override suspend fun restart(target: LogSource) {
            if (shouldThrowOnRestart) throw RuntimeException("Docker restart failed for $target")
            restartCalls.add(target)
            states[target] = ContainerState.Running(status = "running")
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
    }

    private class FakePortHealthChecker : PortHealthChecker {
        val checkPortCalls = mutableListOf<Triple<String, Int, Long>>()
        var customHealth: PortHealth = PortHealth.Open(latencyMs = 10)
        val customHealthMap = mutableMapOf<String, PortHealth>()

        override suspend fun checkPort(host: String, port: Int, timeoutMs: Long): PortHealth {
            checkPortCalls.add(Triple(host, port, timeoutMs))
            return customHealthMap["$host:$port"] ?: customHealth
        }
    }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("runtime-manager-test").toFile()
        fakeDockerClient = FakeDockerClient()
        fakeProcessManager = FakeProcessManager()
        fakePortHealthChecker = FakePortHealthChecker()
        runtimeManager = DefaultServiceRuntimeManager(
            dockerClient = fakeDockerClient,
            processManager = fakeProcessManager,
            portHealthChecker = fakePortHealthChecker,
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
        assertEquals(listOf(dockerService.logSource), fakeDockerClient.startCalls)

        // State check
        val state = runtimeManager.getServiceState(dockerService)
        assertIs<ContainerState.Running>(state)

        // Restart
        runtimeManager.restartService(dockerService)
        assertEquals(listOf(dockerService.logSource), fakeDockerClient.restartCalls)

        // Stop
        runtimeManager.stopService(dockerService)
        assertEquals(listOf(dockerService.logSource), fakeDockerClient.stopCalls)

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
        assertEquals(listOf(composeService.logSource), fakeDockerClient.startCalls)

        // State
        val state = runtimeManager.getServiceState(composeService)
        assertIs<ContainerState.Running>(state)

        // Restart
        runtimeManager.restartService(composeService)
        assertEquals(listOf(composeService.logSource), fakeDockerClient.restartCalls)

        // Stop
        runtimeManager.stopService(composeService)
        assertEquals(listOf(composeService.logSource), fakeDockerClient.stopCalls)

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

        assertEquals(dockerService.logSource, fakeDockerClient.lastRequestedTarget)
        assertEquals(50, fakeDockerClient.lastRequestedTail)
        assertEquals(listOf("docker: init", "docker: started"), result)
    }

    @Test
    fun streamLogs_dockerComposeTarget_delegatesToDockerClientWithTail() = runTest {
        fakeDockerClient.customLogFlow = listOf("compose: web started").asFlow()
        val result = runtimeManager.streamLogs(composeService, tail = 75).toList()

        assertEquals(composeService.logSource, fakeDockerClient.lastRequestedTarget)
        assertEquals(75, fakeDockerClient.lastRequestedTail)
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

    // --- Unified Inspection & Health Tests ---

    @Test
    fun checkPortHealth_whenServiceHasPort_delegatesToPortHealthChecker() = runTest {
        val srvWithPort = ServiceItem(id = "srv-port", name = "Srv", host = "127.0.0.1", port = 8080)
        fakePortHealthChecker.customHealth = PortHealth.Open(latencyMs = 25)

        val health = runtimeManager.checkPortHealth(srvWithPort, timeoutMs = 1500)
        assertIs<PortHealth.Open>(health)
        assertEquals(25, health.latencyMs)
        assertEquals(listOf(Triple("127.0.0.1", 8080, 1500L)), fakePortHealthChecker.checkPortCalls)
    }

    @Test
    fun checkPortHealth_whenServiceHasNoPort_returnsNone() = runTest {
        val srvNoPort = ServiceItem(id = "srv-no-port", name = "Srv", host = "127.0.0.1", port = null)
        val health = runtimeManager.checkPortHealth(srvNoPort)

        assertIs<PortHealth.None>(health)
        assertTrue(fakePortHealthChecker.checkPortCalls.isEmpty())
    }

    @Test
    fun inspectService_forDockerService_combinesPortAndContainerState() = runTest {
        val srv = ServiceItem(
            id = "docker-web",
            name = "Docker Web",
            host = "127.0.0.1",
            port = 3000,
            logSource = LogSource.Docker("web-app")
        )
        fakeDockerClient.isAvailable = true
        fakeDockerClient.states[srv.logSource] = ContainerState.Running(status = "Up 10 minutes")
        fakePortHealthChecker.customHealthMap["127.0.0.1:3000"] = PortHealth.Open(latencyMs = 12)

        val status = runtimeManager.inspectService(srv)

        assertEquals("docker-web", status.serviceId)
        assertIs<PortHealth.Open>(status.portHealth)
        assertEquals(12, status.portHealth.latencyMs)
        assertIs<ContainerState.Running>(status.containerState)
        assertEquals("Up 10 minutes", status.containerState.status)
        assertTrue(status.isHealthy, "Service with Running container and Open port should be healthy")
        assertTrue(status.lastCheckedTimestamp > 0L)
    }

    @Test
    fun inspectService_whenDockerExited_evaluatesIsHealthyFalse() = runTest {
        val srv = ServiceItem(
            id = "docker-api",
            name = "Docker API",
            host = "127.0.0.1",
            port = 8000,
            logSource = LogSource.Docker("api-server")
        )
        fakeDockerClient.isAvailable = true
        fakeDockerClient.states[srv.logSource] = ContainerState.Exited(exitCode = 1, status = "Exited (1)")
        fakePortHealthChecker.customHealthMap["127.0.0.1:8000"] = PortHealth.Closed("Connection refused")

        val status = runtimeManager.inspectService(srv)

        assertEquals("docker-api", status.serviceId)
        assertIs<ContainerState.Exited>(status.containerState)
        assertIs<PortHealth.Closed>(status.portHealth)
        assertFalse(status.isHealthy, "Service with Exited container must NOT be healthy")
    }

    @Test
    fun inspectService_whenPortClosed_evaluatesIsHealthyFalse() = runTest {
        val srv = ServiceItem(
            id = "docker-backend",
            name = "Docker Backend",
            host = "127.0.0.1",
            port = 5000,
            logSource = LogSource.Docker("backend-srv")
        )
        fakeDockerClient.isAvailable = true
        fakeDockerClient.states[srv.logSource] = ContainerState.Running("running")
        fakePortHealthChecker.customHealthMap["127.0.0.1:5000"] = PortHealth.Closed("Connection refused")

        val status = runtimeManager.inspectService(srv)

        assertIs<ContainerState.Running>(status.containerState)
        assertIs<PortHealth.Closed>(status.portHealth)
        assertFalse(status.isHealthy, "Service with Closed port must NOT be healthy even if container is running")
    }

    @Test
    fun inspectService_forCommandService_combinesProcessStateAndPort() = runTest {
        val srv = ServiceItem(
            id = "vite-dev",
            name = "Vite Dev",
            host = "127.0.0.1",
            port = 5173,
            logSource = LogSource.Command(workingDir = "/app", startCommand = "npm run dev")
        )
        fakeProcessManager.runningIds.add("vite-dev")
        fakeProcessManager.states["vite-dev"] = ContainerState.Running("running")
        fakePortHealthChecker.customHealthMap["127.0.0.1:5173"] = PortHealth.Open(latencyMs = 8)

        val status = runtimeManager.inspectService(srv)

        assertEquals("vite-dev", status.serviceId)
        assertIs<ContainerState.Running>(status.containerState)
        assertIs<PortHealth.Open>(status.portHealth)
        assertTrue(status.isHealthy)
    }

    @Test
    fun inspectServices_executesConcurrentlyAndReturnsAllStatuses() = runTest {
        val srv1 = ServiceItem(
            id = "s1",
            name = "Service 1",
            host = "127.0.0.1",
            port = 3000,
            logSource = LogSource.Docker("c1")
        )
        val srv2 = ServiceItem(
            id = "s2",
            name = "Service 2",
            host = "127.0.0.1",
            port = 4000,
            logSource = LogSource.Command(workingDir = "/app2", startCommand = "npm start")
        )
        val srv3 = ServiceItem(
            id = "s3",
            name = "Service 3",
            host = "127.0.0.1",
            port = 5000,
            logSource = LogSource.None
        )

        fakeDockerClient.states[srv1.logSource] = ContainerState.Running("running")
        fakePortHealthChecker.customHealthMap["127.0.0.1:3000"] = PortHealth.Open(latencyMs = 5)

        fakeProcessManager.states["s2"] = ContainerState.Exited(exitCode = 137, status = "killed")
        fakePortHealthChecker.customHealthMap["127.0.0.1:4000"] = PortHealth.Closed("refused")

        fakePortHealthChecker.customHealthMap["127.0.0.1:5000"] = PortHealth.Open(latencyMs = 15)

        val results = runtimeManager.inspectServices(listOf(srv1, srv2, srv3))

        assertEquals(3, results.size)
        assertTrue(results.containsKey("s1"))
        assertTrue(results.containsKey("s2"))
        assertTrue(results.containsKey("s3"))

        assertTrue(results["s1"]!!.isHealthy)
        assertFalse(results["s2"]!!.isHealthy)
        assertTrue(results["s3"]!!.isHealthy)
    }

    @Test
    fun inspectServices_whenDockerUnavailable_handlesGracefully() = runTest {
        fakeDockerClient.isAvailable = false
        val srv = ServiceItem(
            id = "docker-srv",
            name = "Docker App",
            host = "127.0.0.1",
            port = 9090,
            logSource = LogSource.Docker("app-container")
        )
        fakePortHealthChecker.customHealthMap["127.0.0.1:9090"] = PortHealth.Open(latencyMs = 10)

        val results = runtimeManager.inspectServices(listOf(srv))

        val status = results["docker-srv"]
        assertNotNull(status)
        assertIs<ContainerState.Unknown>(status.containerState)
        assertEquals("Docker daemon unavailable", status.containerState.rawStatus)
        assertIs<PortHealth.Open>(status.portHealth)
    }

    @Test
    fun inspectServices_emptyList_returnsEmptyMapImmediately() = runTest {
        val results = runtimeManager.inspectServices(emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun inspectService_whenDockerUnavailable_returnsUnknownDaemonStatus() = runTest {
        fakeDockerClient.isAvailable = false
        val srv = ServiceItem(
            id = "docker-srv-single",
            name = "Docker App",
            host = "127.0.0.1",
            port = 9090,
            logSource = LogSource.Docker("app-container")
        )
        fakePortHealthChecker.customHealthMap["127.0.0.1:9090"] = PortHealth.Open(latencyMs = 10)

        val status = runtimeManager.inspectService(srv)

        assertEquals("docker-srv-single", status.serviceId)
        assertIs<ContainerState.Unknown>(status.containerState)
        assertEquals("Docker daemon unavailable", status.containerState.rawStatus)
        assertIs<PortHealth.Open>(status.portHealth)
    }

    @Test
    fun inspectServices_and_inspectService_produceConsistentStatusForSameTarget() = runTest {
        val srv = ServiceItem(
            id = "compose-web",
            name = "Compose Web",
            host = "127.0.0.1",
            port = 3000,
            logSource = LogSource.DockerCompose(
                projectDir = "/apps/web",
                serviceName = "frontend",
                composeFile = "compose.yaml"
            )
        )
        fakeDockerClient.isAvailable = true
        fakeDockerClient.states[srv.logSource] = ContainerState.Running("running")
        fakePortHealthChecker.customHealthMap["127.0.0.1:3000"] = PortHealth.Open(latencyMs = 7)

        val singleStatus = runtimeManager.inspectService(srv)
        val batchStatus = runtimeManager.inspectServices(listOf(srv))["compose-web"]

        assertNotNull(batchStatus)
        assertEquals(singleStatus.serviceId, batchStatus.serviceId)
        assertEquals(singleStatus.portHealth, batchStatus.portHealth)
        assertEquals(singleStatus.containerState, batchStatus.containerState)
        assertEquals(singleStatus.isHealthy, batchStatus.isHealthy)
    }
}
