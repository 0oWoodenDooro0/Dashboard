package website.woodendoor.dashboard.viewmodel

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.DockerContainerInfo
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceGroup
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus
import website.woodendoor.dashboard.service.ConfigRepository
import website.woodendoor.dashboard.service.DefaultServiceRuntimeManager
import website.woodendoor.dashboard.service.DockerClient
import website.woodendoor.dashboard.service.LogStreamService
import website.woodendoor.dashboard.service.PortHealthChecker
import website.woodendoor.dashboard.service.ServiceRuntimeManager

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var fakeConfigRepository: FakeConfigRepository
    private lateinit var fakeHealthChecker: FakePortHealthChecker
    private lateinit var fakeLogStreamService: FakeLogStreamService
    private lateinit var fakeDockerClient: FakeDockerClient
    private lateinit var fakeDockerComposeClient: FakeDockerComposeClient
    private lateinit var fakeProcessManager: FakeProcessManager
    private val activeViewModels = mutableListOf<DashboardViewModel>()

    private val sampleService1 = ServiceItem(
        id = "web-1",
        name = "Web App",
        host = "127.0.0.1",
        port = 3000,
        logSource = LogSource.LocalFile("/var/log/web.log")
    )

    private val sampleService2 = ServiceItem(
        id = "db-1",
        name = "Database",
        host = "127.0.0.1",
        port = 5432,
        logSource = LogSource.Docker("postgres-db")
    )

    private val sampleService3 = ServiceItem(
        id = "cache-1",
        name = "Redis Cache",
        host = "127.0.0.1",
        port = 6379,
        logSource = LogSource.None
    )

    private val sampleService4 = ServiceItem(
        id = "compose-1",
        name = "Compose Service",
        host = "127.0.0.1",
        port = 8080,
        logSource = LogSource.DockerCompose(
            projectDir = "/apps/conflux",
            serviceName = "backend"
        )
    )

    private val sampleService5 = ServiceItem(
        id = "cmd-1",
        name = "Vite App",
        host = "127.0.0.1",
        port = 5173,
        logSource = LogSource.Command(
            workingDir = "/apps/vite-app",
            startCommand = "npm run dev",
            stopCommand = "npm run stop"
        )
    )

    private val sampleGroup = ServiceGroup(
        id = "core-group",
        name = "Core Services",
        services = listOf(sampleService1, sampleService2, sampleService3)
    )

    private val sampleConfig = DashboardConfig(
        version = 1,
        pollingIntervalSeconds = 5,
        groups = listOf(sampleGroup)
    )

    private class FakeConfigRepository(
        initialConfig: DashboardConfig = DashboardConfig()
    ) : ConfigRepository {
        var currentConfig: DashboardConfig = initialConfig
        val configUpdates = MutableSharedFlow<DashboardConfig>(extraBufferCapacity = 64)
        var saveCallCount = 0
        var shouldThrowOnLoad = false

        override fun loadConfig(): DashboardConfig {
            if (shouldThrowOnLoad) throw RuntimeException("Failed to read config file")
            return currentConfig
        }

        override fun saveConfig(config: DashboardConfig) {
            currentConfig = config
            saveCallCount++
            configUpdates.tryEmit(config)
        }

        override fun observeConfig(): Flow<DashboardConfig> = flow {
            emit(currentConfig)
            emitAll(configUpdates)
        }
    }

    private class FakePortHealthChecker : PortHealthChecker {
        var checkServicesCallCount = 0
        var customHealthMap = mutableMapOf<String, PortHealth>()

        override suspend fun checkPort(host: String, port: Int, timeoutMs: Long): PortHealth {
            return PortHealth.Open(latencyMs = 15)
        }

        override suspend fun checkServices(services: List<ServiceItem>): Map<String, ServiceStatus> {
            checkServicesCallCount++
            return services.associate { service ->
                val health = customHealthMap[service.id] ?: PortHealth.Open(latencyMs = 15)
                service.id to ServiceStatus(
                    serviceId = service.id,
                    portHealth = health
                )
            }
        }
    }

    private class FakeLogStreamService : LogStreamService {
        val flowMap = mutableMapOf<LogSource, MutableSharedFlow<String>>()
        var lastRequestedSource: LogSource? = null
        var lastRequestedServiceId: String? = null

        fun getOrCreateFlow(source: LogSource): MutableSharedFlow<String> {
            return flowMap.getOrPut(source) { MutableSharedFlow(extraBufferCapacity = 64) }
        }

        override fun streamLogs(source: LogSource, serviceId: String?, tail: Int): Flow<String> {
            lastRequestedSource = source
            lastRequestedServiceId = serviceId
            return when (source) {
                is LogSource.None -> flow {}
                else -> getOrCreateFlow(source)
            }
        }
    }

    private class FakeDockerClient(
        var isDockerAvailableResult: Boolean = true
    ) : DockerClient {
        val containerStates = mutableMapOf<String, ContainerState>()
        var onStartContainer: (suspend (String) -> Unit)? = null
        var onStopContainer: (suspend (String) -> Unit)? = null
        var onRestartContainer: (suspend (String) -> Unit)? = null
        var shouldThrowOnStart: Boolean = false
        var shouldThrowOnStop: Boolean = false
        var shouldThrowOnRestart: Boolean = false

        override suspend fun isDockerAvailable(): Boolean = isDockerAvailableResult

        override suspend fun getContainerState(nameOrId: String): ContainerState {
            return containerStates[nameOrId] ?: ContainerState.Running(status = "Up 2 hours")
        }

        override suspend fun listContainers(all: Boolean): List<DockerContainerInfo> = emptyList()

        override fun streamLogs(nameOrId: String, tail: Int): Flow<String> = flow {}

        override suspend fun startContainer(nameOrId: String) {
            if (shouldThrowOnStart) throw RuntimeException("Failed to start container $nameOrId")
            onStartContainer?.invoke(nameOrId)
            containerStates[nameOrId] = ContainerState.Running(status = "running")
        }

        override suspend fun stopContainer(nameOrId: String) {
            if (shouldThrowOnStop) throw RuntimeException("Failed to stop container $nameOrId")
            onStopContainer?.invoke(nameOrId)
            containerStates[nameOrId] = ContainerState.Exited(exitCode = 0, status = "exited")
        }

        override suspend fun restartContainer(nameOrId: String) {
            if (shouldThrowOnRestart) throw RuntimeException("Failed to restart container $nameOrId")
            onRestartContainer?.invoke(nameOrId)
            containerStates[nameOrId] = ContainerState.Running(status = "running")
        }
    }

    private class FakeDockerComposeClient(
        var isComposeAvailableResult: Boolean = true
    ) : website.woodendoor.dashboard.service.DockerComposeClient {
        val composeStates = mutableMapOf<String, ContainerState>()
        var customLogFlow: Flow<String> = flow {}
        var onStartService: (suspend (String) -> Unit)? = null
        var onStopService: (suspend (String) -> Unit)? = null
        var onRestartService: (suspend (String) -> Unit)? = null
        var shouldThrowOnStart: Boolean = false
        var shouldThrowOnStop: Boolean = false
        var shouldThrowOnRestart: Boolean = false

        override suspend fun isComposeAvailable(): Boolean = isComposeAvailableResult

        override suspend fun getServiceState(projectDir: String, serviceName: String, composeFile: String?): ContainerState {
            val key = if (composeFile != null) "compose:$projectDir:$serviceName:$composeFile" else "compose:$projectDir:$serviceName"
            return composeStates[key] ?: ContainerState.Running(status = "Up 1 hour")
        }

        override suspend fun listServices(projectDir: String, composeFile: String?): List<String> = emptyList()

        override fun streamLogs(projectDir: String, serviceName: String, composeFile: String?, tail: Int): Flow<String> = customLogFlow

        override suspend fun startService(projectDir: String, serviceName: String, composeFile: String?) {
            if (shouldThrowOnStart) throw RuntimeException("Failed to start compose service $serviceName")
            onStartService?.invoke(serviceName)
            val key = if (composeFile != null) "compose:$projectDir:$serviceName:$composeFile" else "compose:$projectDir:$serviceName"
            composeStates[key] = ContainerState.Running(status = "running")
        }

        override suspend fun stopService(projectDir: String, serviceName: String, composeFile: String?) {
            if (shouldThrowOnStop) throw RuntimeException("Failed to stop compose service $serviceName")
            onStopService?.invoke(serviceName)
            val key = if (composeFile != null) "compose:$projectDir:$serviceName:$composeFile" else "compose:$projectDir:$serviceName"
            composeStates[key] = ContainerState.Exited(exitCode = 0, status = "exited")
        }

        override suspend fun restartService(projectDir: String, serviceName: String, composeFile: String?) {
            if (shouldThrowOnRestart) throw RuntimeException("Failed to restart compose service $serviceName")
            onRestartService?.invoke(serviceName)
            val key = if (composeFile != null) "compose:$projectDir:$serviceName:$composeFile" else "compose:$projectDir:$serviceName"
            composeStates[key] = ContainerState.Running(status = "running")
        }
    }

    private class FakeProcessManager : website.woodendoor.dashboard.service.ProcessManager {
        var runningProcesses = mutableSetOf<String>()
        val processStates = mutableMapOf<String, ContainerState>()
        val startProcessCalls = mutableListOf<Triple<String, String, String>>()
        val stopProcessCalls = mutableListOf<String>()
        val restartProcessCalls = mutableListOf<Triple<String, String, String>>()
        val customLogFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        var onStartProcess: (suspend (String) -> Unit)? = null
        var onStopProcess: (suspend (String) -> Unit)? = null
        var onRestartProcess: (suspend (String) -> Unit)? = null
        var shouldThrowOnStart: Boolean = false
        var shouldThrowOnStop: Boolean = false
        var shouldThrowOnRestart: Boolean = false

        override suspend fun startProcess(serviceId: String, workingDir: String, command: String, environment: Map<String, String>) {
            if (shouldThrowOnStart) throw RuntimeException("Failed to start process $serviceId")
            onStartProcess?.invoke(serviceId)
            startProcessCalls.add(Triple(serviceId, workingDir, command))
            runningProcesses.add(serviceId)
            processStates[serviceId] = ContainerState.Running(status = "running")
        }

        override suspend fun stopProcess(serviceId: String, stopCommand: String?, workingDir: String?, timeoutSeconds: Int) {
            if (shouldThrowOnStop) throw RuntimeException("Failed to stop process $serviceId")
            onStopProcess?.invoke(serviceId)
            stopProcessCalls.add(serviceId)
            runningProcesses.remove(serviceId)
            processStates[serviceId] = ContainerState.Exited(exitCode = 0, status = "stopped")
        }

        override suspend fun restartProcess(serviceId: String, workingDir: String, command: String, stopCommand: String?, environment: Map<String, String>) {
            if (shouldThrowOnRestart) throw RuntimeException("Failed to restart process $serviceId")
            onRestartProcess?.invoke(serviceId)
            restartProcessCalls.add(Triple(serviceId, workingDir, command))
            runningProcesses.add(serviceId)
            processStates[serviceId] = ContainerState.Running(status = "running")
        }

        override fun isRunning(serviceId: String): Boolean = serviceId in runningProcesses

        override fun getProcessState(serviceId: String): ContainerState =
            processStates[serviceId] ?: ContainerState.NotFound(reason = "Process not running")

        override fun streamLogs(serviceId: String, tail: Int): Flow<String> = customLogFlow

        override fun streamLogs(source: LogSource.Command, tail: Int): Flow<String> = customLogFlow
    }

    @BeforeTest
    fun setUp() {
        fakeConfigRepository = FakeConfigRepository(sampleConfig)
        fakeHealthChecker = FakePortHealthChecker()
        fakeLogStreamService = FakeLogStreamService()
        fakeDockerClient = FakeDockerClient(isDockerAvailableResult = true)
        fakeDockerComposeClient = FakeDockerComposeClient(isComposeAvailableResult = true)
        fakeProcessManager = FakeProcessManager()
        activeViewModels.clear()
    }

    @AfterTest
    fun tearDown() {
        activeViewModels.forEach { it.onCleared() }
        activeViewModels.clear()
    }

    private fun createViewModel(
        testScope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        maxLogBufferSize: Int = 1000,
        serviceRuntimeManager: ServiceRuntimeManager? = null
    ): DashboardViewModel {
        val runtime = serviceRuntimeManager ?: DefaultServiceRuntimeManager(
            dockerClient = fakeDockerClient,
            dockerComposeClient = fakeDockerComposeClient,
            processManager = fakeProcessManager,
            logStreamService = fakeLogStreamService
        )
        val vm = DashboardViewModel(
            configRepository = fakeConfigRepository,
            healthChecker = fakeHealthChecker,
            serviceRuntimeManager = runtime,
            coroutineScope = testScope,
            defaultDispatcher = dispatcher,
            maxLogBufferSize = maxLogBufferSize
        )
        activeViewModels.add(vm)
        return vm
    }

    @Test
    fun initialization_loadsConfigAndPerformsInitialHealthCheck() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)

        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading, "isLoading should be false after initialization")
        assertEquals(sampleConfig, state.config)
        assertTrue(state.isDockerAvailable, "isDockerAvailable should be true")
        assertEquals(3, state.serviceStatuses.size)
        assertTrue(state.serviceStatuses.containsKey("web-1"))
        assertTrue(state.serviceStatuses.containsKey("db-1"))
        assertTrue(state.serviceStatuses.containsKey("cache-1"))
        assertIs<PortHealth.Open>(state.serviceStatuses["web-1"]?.portHealth)
        assertNull(state.selectedServiceId)
        assertTrue(state.logs.isEmpty())
    }

    @Test
    fun observeConfig_updatesStateWhenConfigChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val newService = ServiceItem(id = "api-1", name = "API Gateway", port = 8080)
        val updatedConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + newService))
        )

        fakeConfigRepository.saveConfig(updatedConfig)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(4, state.allServices.size)
        assertTrue(state.allServices.any { it.id == "api-1" })
        assertTrue(state.serviceStatuses.containsKey("api-1"))
    }

    @Test
    fun periodicPolling_updatesHealthAndContainerStates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        assertEquals(1, fakeHealthChecker.checkServicesCallCount)

        // Change health of web-1 to closed and docker container state to exited
        fakeHealthChecker.customHealthMap["web-1"] = PortHealth.Closed("Connection refused")
        fakeDockerClient.containerStates["postgres-db"] = ContainerState.Exited(exitCode = 1)

        // Advance by polling interval (5 seconds = 5000ms)
        advanceTimeBy(5100)
        runCurrent()

        val state = viewModel.state.value
        assertTrue(fakeHealthChecker.checkServicesCallCount >= 2)
        assertIs<PortHealth.Closed>(state.serviceStatuses["web-1"]?.portHealth)
        assertEquals("Connection refused", (state.serviceStatuses["web-1"]?.portHealth as PortHealth.Closed).reason)
        assertIs<ContainerState.Exited>(state.containerStates["db-1"])
    }

    @Test
    fun selectService_startsLogStreamAndPopulatesLogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        runCurrent()

        assertEquals("web-1", viewModel.state.value.selectedServiceId)
        assertEquals(sampleService1, viewModel.state.value.selectedService)

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        flow.emit("2026-08-14 Server started on port 3000")
        flow.emit("2026-08-14 GET /api/v1/health 200 OK")
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.logs.size)
        assertEquals("2026-08-14 Server started on port 3000", state.logs[0])
        assertEquals("2026-08-14 GET /api/v1/health 200 OK", state.logs[1])
    }

    @Test
    fun selectService_switchingService_clearsPreviousLogsAndCancelsOldStream() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        runCurrent()

        val webFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        webFlow.emit("web log 1")
        runCurrent()
        assertEquals(listOf("web log 1"), viewModel.state.value.logs)

        // Switch to db-1
        viewModel.selectService("db-1")
        runCurrent()

        assertEquals("db-1", viewModel.state.value.selectedServiceId)
        assertTrue(viewModel.state.value.logs.isEmpty(), "Logs should be reset on service switch")

        val dbFlow = fakeLogStreamService.getOrCreateFlow(sampleService2.logSource)
        dbFlow.emit("db log 1")
        runCurrent()

        assertEquals(listOf("db log 1"), viewModel.state.value.logs)

        // Emitting to previous web flow must NOT affect current logs
        webFlow.emit("web log 2 (should be ignored)")
        runCurrent()

        assertEquals(listOf("db log 1"), viewModel.state.value.logs)
    }

    @Test
    fun selectService_noneLogSource_hasEmptyLogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("cache-1")
        runCurrent()

        assertEquals("cache-1", viewModel.state.value.selectedServiceId)
        assertTrue(viewModel.state.value.logs.isEmpty())
        assertNull(fakeLogStreamService.lastRequestedSource)
    }

    @Test
    fun selectService_null_deselectsAndClearsLogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        val webFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        webFlow.emit("log line")
        runCurrent()
        assertEquals(1, viewModel.state.value.logs.size)

        viewModel.selectService(null)
        runCurrent()

        assertNull(viewModel.state.value.selectedServiceId)
        assertNull(viewModel.state.value.selectedService)
        assertTrue(viewModel.state.value.logs.isEmpty())
    }

    @Test
    fun logBuffer_capsAtMaxCapacity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher, maxLogBufferSize = 5)
        runCurrent()

        viewModel.selectService("web-1")
        runCurrent()

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        for (i in 1..8) {
            flow.emit("Line $i")
        }
        runCurrent()

        val logs = viewModel.state.value.logs
        assertEquals(5, logs.size, "Logs must be capped at maxLogBufferSize")
        assertEquals(listOf("Line 4", "Line 5", "Line 6", "Line 7", "Line 8"), logs)
    }

    @Test
    fun setLogSearchQuery_filtersLogsCorrectly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        flow.emit("INFO: User login successful")
        flow.emit("ERROR: Database connection timeout")
        flow.emit("DEBUG: Cache hit for key user_1")
        flow.emit("error: Failed to write audit log")
        runCurrent()

        assertEquals(4, viewModel.state.value.logs.size)
        assertEquals(4, viewModel.state.value.filteredLogs.size)

        // Filter by "error" (case-insensitive)
        viewModel.setLogSearchQuery("error")
        assertEquals("error", viewModel.state.value.logSearchQuery)
        val filtered = viewModel.state.value.filteredLogs
        assertEquals(2, filtered.size)
        assertEquals("ERROR: Database connection timeout", filtered[0])
        assertEquals("error: Failed to write audit log", filtered[1])

        // Reset query
        viewModel.setLogSearchQuery("")
        assertEquals(4, viewModel.state.value.filteredLogs.size)
    }

    @Test
    fun toggleAutoScroll_updatesState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        assertTrue(viewModel.state.value.isAutoScrollEnabled)

        viewModel.toggleAutoScroll(false)
        assertFalse(viewModel.state.value.isAutoScrollEnabled)

        viewModel.toggleAutoScroll(true)
        assertTrue(viewModel.state.value.isAutoScrollEnabled)
    }

    @Test
    fun clearLogs_clearsLogBuffer() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        flow.emit("line 1")
        flow.emit("line 2")
        runCurrent()
        assertEquals(2, viewModel.state.value.logs.size)

        viewModel.clearLogs()
        assertTrue(viewModel.state.value.logs.isEmpty())

        // Next line still streams properly
        flow.emit("line 3")
        runCurrent()
        assertEquals(listOf("line 3"), viewModel.state.value.logs)
    }

    @Test
    fun addService_appendsServiceToSpecifiedOrFirstGroupAndSaves() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val newService = ServiceItem(
            id = "worker-1",
            name = "Background Worker",
            port = 9000
        )

        viewModel.addService(newService, groupId = "core-group")
        runCurrent()

        assertEquals(1, fakeConfigRepository.saveCallCount)
        val updatedGroup = fakeConfigRepository.currentConfig.groups.find { it.id == "core-group" }
        assertNotNull(updatedGroup)
        assertTrue(updatedGroup.services.any { it.id == "worker-1" })
    }

    @Test
    fun updateService_modifiesExistingServiceAndSaves() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val updatedWeb1 = sampleService1.copy(name = "Updated Web App", port = 3001)
        viewModel.updateService(updatedWeb1)
        runCurrent()

        assertEquals(1, fakeConfigRepository.saveCallCount)
        val savedService = fakeConfigRepository.currentConfig.groups
            .flatMap { it.services }
            .find { it.id == "web-1" }
        assertNotNull(savedService)
        assertEquals("Updated Web App", savedService.name)
        assertEquals(3001, savedService.port)
    }

    @Test
    fun updateService_ifSelectedAndLogSourceChanged_restartsLogStream() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        val oldFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        oldFlow.emit("old log 1")
        runCurrent()
        assertEquals(listOf("old log 1"), viewModel.state.value.logs)

        val newLogSource = LogSource.LocalFile("/var/log/web_new.log")
        val updatedWeb1 = sampleService1.copy(logSource = newLogSource)
        viewModel.updateService(updatedWeb1)
        runCurrent()

        val newFlow = fakeLogStreamService.getOrCreateFlow(newLogSource)
        newFlow.emit("new log 1")
        runCurrent()

        assertEquals(listOf("new log 1"), viewModel.state.value.logs)
    }

    @Test
    fun deleteService_removesServiceAndClearsSelectionIfSelected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        runCurrent()
        assertEquals("web-1", viewModel.state.value.selectedServiceId)

        viewModel.deleteService("web-1")
        runCurrent()

        assertEquals(1, fakeConfigRepository.saveCallCount)
        val allServices = fakeConfigRepository.currentConfig.groups.flatMap { it.services }
        assertFalse(allServices.any { it.id == "web-1" })
        assertNull(viewModel.state.value.selectedServiceId)
        assertTrue(viewModel.state.value.logs.isEmpty())
    }

    @Test
    fun triggerRefresh_manuallyRefreshesState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val initialCount = fakeHealthChecker.checkServicesCallCount

        fakeHealthChecker.customHealthMap["web-1"] = PortHealth.Unreachable("Host unreachable")
        viewModel.triggerRefresh()
        runCurrent()

        assertTrue(fakeHealthChecker.checkServicesCallCount > initialCount)
        assertIs<PortHealth.Unreachable>(viewModel.state.value.serviceStatuses["web-1"]?.portHealth)
    }

    @Test
    fun errorHandling_survivesRepositoryOrDockerFailureWithoutCrashing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        fakeConfigRepository.shouldThrowOnLoad = true

        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        assertNotNull(viewModel.state.value.errorMessage)
        assertEquals("Failed to read config file", viewModel.state.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun periodicPolling_updatesDockerComposeContainerStates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val composeConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService4))
        )
        fakeConfigRepository.saveConfig(composeConfig)
        runCurrent()

        val composeKey = "compose:/apps/conflux:backend"
        fakeDockerComposeClient.composeStates[composeKey] = ContainerState.Exited(exitCode = 143)

        // Advance by polling interval
        advanceTimeBy(5100)
        runCurrent()

        val state = viewModel.state.value
        val containerState = state.containerStates["compose-1"]
        assertNotNull(containerState)
        assertIs<ContainerState.Exited>(containerState)
        assertEquals(143, containerState.exitCode)
    }

    @Test
    fun selectService_dockerComposeLogSource_startsLogStream() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val composeConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService4))
        )
        fakeConfigRepository.saveConfig(composeConfig)
        runCurrent()

        viewModel.selectService("compose-1")
        runCurrent()

        assertEquals("compose-1", viewModel.state.value.selectedServiceId)
        assertEquals(sampleService4, viewModel.state.value.selectedService)

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService4.logSource)
        flow.emit("compose backend | started")
        flow.emit("compose backend | ready")
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.logs.size)
        assertEquals("compose backend | started", state.logs[0])
        assertEquals("compose backend | ready", state.logs[1])
    }

    @Test
    fun updateService_switchingToDockerCompose_restartsLogStream() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        viewModel.selectService("web-1")
        val oldFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        oldFlow.emit("web log 1")
        runCurrent()
        assertEquals(listOf("web log 1"), viewModel.state.value.logs)

        val newComposeSource = LogSource.DockerCompose(
            projectDir = "/apps/new-web",
            serviceName = "web-service"
        )
        val updatedWeb1 = sampleService1.copy(logSource = newComposeSource)
        viewModel.updateService(updatedWeb1)
        runCurrent()

        val composeFlow = fakeLogStreamService.getOrCreateFlow(newComposeSource)
        composeFlow.emit("compose new log 1")
        runCurrent()

        assertEquals(listOf("compose new log 1"), viewModel.state.value.logs)
    }

    @Test
    fun startService_startsLocalProcessAndTriggersRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        viewModel.startService(sampleService5)
        runCurrent()

        assertEquals(1, fakeProcessManager.startProcessCalls.size)
        val (serviceId, workingDir, command) = fakeProcessManager.startProcessCalls.first()
        assertEquals("cmd-1", serviceId)
        assertEquals("/apps/vite-app", workingDir)
        assertEquals("npm run dev", command)

        val state = viewModel.state.value
        assertNotNull(state.containerStates["cmd-1"])
        assertIs<ContainerState.Running>(state.containerStates["cmd-1"])
    }

    @Test
    fun stopService_stopsLocalProcessAndTriggersRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        // Start it first
        viewModel.startService(sampleService5)
        runCurrent()
        assertTrue(fakeProcessManager.isRunning("cmd-1"))

        // Stop it
        viewModel.stopService(sampleService5)
        runCurrent()

        assertEquals(1, fakeProcessManager.stopProcessCalls.size)
        assertEquals("cmd-1", fakeProcessManager.stopProcessCalls.first())
        assertFalse(fakeProcessManager.isRunning("cmd-1"))

        val state = viewModel.state.value
        assertNotNull(state.containerStates["cmd-1"])
        assertIs<ContainerState.Exited>(state.containerStates["cmd-1"])
    }

    @Test
    fun restartService_restartsLocalProcessAndTriggersRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        viewModel.restartService(sampleService5)
        runCurrent()

        assertEquals(1, fakeProcessManager.restartProcessCalls.size)
        val (serviceId, workingDir, command) = fakeProcessManager.restartProcessCalls.first()
        assertEquals("cmd-1", serviceId)
        assertEquals("/apps/vite-app", workingDir)
        assertEquals("npm run dev", command)
        assertTrue(fakeProcessManager.isRunning("cmd-1"))
    }

    @Test
    fun deleteService_stopsProcessIfRunning() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        // Start service
        viewModel.startService(sampleService5)
        runCurrent()
        assertTrue(fakeProcessManager.isRunning("cmd-1"))

        // Delete service
        viewModel.deleteService("cmd-1")
        runCurrent()

        assertEquals(1, fakeProcessManager.stopProcessCalls.size)
        assertEquals("cmd-1", fakeProcessManager.stopProcessCalls.first())
        assertFalse(fakeProcessManager.isRunning("cmd-1"))
    }

    @Test
    fun periodicPolling_updatesCommandProcessStates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        val commandKey = "command:cmd-1"
        fakeProcessManager.processStates["cmd-1"] = ContainerState.Running(status = "running")

        advanceTimeBy(5100)
        runCurrent()

        val state = viewModel.state.value
        val containerState = state.containerStates["cmd-1"]
        assertNotNull(containerState)
        assertIs<ContainerState.Running>(containerState)
    }

    @Test
    fun selectService_commandLogSource_startsLogStream() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        viewModel.selectService("cmd-1")
        runCurrent()

        assertEquals("cmd-1", viewModel.state.value.selectedServiceId)
        assertEquals(sampleService5, viewModel.state.value.selectedService)

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService5.logSource)
        flow.emit("vite ready in 250ms")
        flow.emit("ready on http://localhost:5173")
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.logs.size)
        assertEquals("vite ready in 250ms", state.logs[0])
        assertEquals("ready on http://localhost:5173", state.logs[1])
    }

    @Test
    fun startStopRestart_dockerService_invokesDockerClient() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        // Start Docker container
        viewModel.startService(sampleService2)
        runCurrent()
        assertEquals(ContainerState.Running(status = "running"), viewModel.state.value.containerStates["db-1"])

        // Restart Docker container
        viewModel.restartService(sampleService2)
        runCurrent()
        assertEquals(ContainerState.Running(status = "running"), viewModel.state.value.containerStates["db-1"])

        // Stop Docker container
        viewModel.stopService(sampleService2)
        runCurrent()
        assertEquals(ContainerState.Exited(exitCode = 0, status = "exited"), viewModel.state.value.containerStates["db-1"])
    }

    @Test
    fun startStopRestart_dockerComposeService_invokesComposeClient() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val composeConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService4))
        )
        fakeConfigRepository.saveConfig(composeConfig)
        runCurrent()

        val composeKey = "compose:/apps/conflux:backend"

        // Start Docker Compose service
        viewModel.startService(sampleService4)
        runCurrent()
        assertEquals(ContainerState.Running(status = "running"), viewModel.state.value.containerStates["compose-1"])

        // Restart Docker Compose service
        viewModel.restartService(sampleService4)
        runCurrent()
        assertEquals(ContainerState.Running(status = "running"), viewModel.state.value.containerStates["compose-1"])

        // Stop Docker Compose service
        viewModel.stopService(sampleService4)
        runCurrent()
        assertEquals(ContainerState.Exited(exitCode = 0, status = "exited"), viewModel.state.value.containerStates["compose-1"])
    }

    @Test
    fun deleteGroup_stopsRunningProcessesInGroupAndClearsSelection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val multiServiceGroup = sampleGroup.copy(
            services = listOf(sampleService1, sampleService5)
        )
        val groupConfig = sampleConfig.copy(groups = listOf(multiServiceGroup))
        fakeConfigRepository.saveConfig(groupConfig)
        runCurrent()

        // Start command service and select it
        viewModel.startService(sampleService5)
        runCurrent()
        assertTrue(fakeProcessManager.isRunning("cmd-1"))
        assertEquals("cmd-1", viewModel.state.value.selectedServiceId)

        // Delete group containing the running service
        viewModel.deleteGroup(multiServiceGroup.id)
        runCurrent()

        // Verify running process in group was stopped
        assertEquals(1, fakeProcessManager.stopProcessCalls.size)
        assertEquals("cmd-1", fakeProcessManager.stopProcessCalls.first())
        assertFalse(fakeProcessManager.isRunning("cmd-1"))

        // Verify selection was cleared
        assertNull(viewModel.state.value.selectedServiceId)
        assertTrue(viewModel.state.value.config.groups.none { it.id == multiServiceGroup.id })
    }

    @Test
    fun startService_autoSelectsServiceAndStreamsLogs_whenUnselected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        // Ensure nothing is selected initially
        assertNull(viewModel.state.value.selectedServiceId)

        // Start service
        viewModel.startService(sampleService5)
        runCurrent()

        // Verify service is automatically selected and log stream connected
        assertEquals("cmd-1", viewModel.state.value.selectedServiceId)
        assertEquals(sampleService5, viewModel.state.value.selectedService)
        assertEquals("cmd-1", fakeLogStreamService.lastRequestedServiceId)

        // Emit log line to flow
        val flow = fakeLogStreamService.getOrCreateFlow(sampleService5.logSource)
        flow.emit("npm dev server started")
        runCurrent()

        assertEquals(listOf("npm dev server started"), viewModel.state.value.logs)
    }

    @Test
    fun startService_reattachesLogStream_whenAlreadySelected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        // Pre-select the service
        viewModel.selectService("cmd-1")
        runCurrent()
        assertEquals("cmd-1", viewModel.state.value.selectedServiceId)

        // Start service while already selected
        viewModel.startService(sampleService5)
        runCurrent()

        // Verify stream remains active and receives new lines
        val flow = fakeLogStreamService.getOrCreateFlow(sampleService5.logSource)
        flow.emit("new process output after start")
        runCurrent()

        assertTrue(viewModel.state.value.logs.contains("new process output after start"))
    }

    @Test
    fun restartService_autoSelectsServiceAndStreamsLogs_whenUnselected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val cmdConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + sampleService5))
        )
        fakeConfigRepository.saveConfig(cmdConfig)
        runCurrent()

        // Ensure unselected
        assertNull(viewModel.state.value.selectedServiceId)

        // Restart service
        viewModel.restartService(sampleService5)
        runCurrent()

        // Verify auto-focus
        assertEquals("cmd-1", viewModel.state.value.selectedServiceId)
        assertEquals("cmd-1", fakeLogStreamService.lastRequestedServiceId)

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService5.logSource)
        flow.emit("server restarted successfully")
        runCurrent()

        assertEquals(listOf("server restarted successfully"), viewModel.state.value.logs)
    }

    @Test
    fun startService_tracksOperatingServiceIdsDuringExecution() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        var wasOperatingDuringExecution = false
        fakeDockerClient.onStartContainer = { containerName ->
            wasOperatingDuringExecution = "db-1" in viewModel.state.value.operatingServiceIds
        }

        viewModel.startService(sampleService2)
        runCurrent()

        assertTrue(wasOperatingDuringExecution, "Service ID should be present in operatingServiceIds during start execution")
        assertTrue(viewModel.state.value.operatingServiceIds.isEmpty(), "operatingServiceIds should be empty after start completes")
    }

    @Test
    fun stopService_tracksOperatingServiceIdsDuringExecution() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        var wasOperatingDuringExecution = false
        fakeDockerClient.onStopContainer = { containerName ->
            wasOperatingDuringExecution = "db-1" in viewModel.state.value.operatingServiceIds
        }

        viewModel.stopService(sampleService2)
        runCurrent()

        assertTrue(wasOperatingDuringExecution, "Service ID should be present in operatingServiceIds during stop execution")
        assertTrue(viewModel.state.value.operatingServiceIds.isEmpty(), "operatingServiceIds should be empty after stop completes")
    }

    @Test
    fun restartService_tracksOperatingServiceIdsDuringExecution() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        var wasOperatingDuringExecution = false
        fakeDockerClient.onRestartContainer = { containerName ->
            wasOperatingDuringExecution = "db-1" in viewModel.state.value.operatingServiceIds
        }

        viewModel.restartService(sampleService2)
        runCurrent()

        assertTrue(wasOperatingDuringExecution, "Service ID should be present in operatingServiceIds during restart execution")
        assertTrue(viewModel.state.value.operatingServiceIds.isEmpty(), "operatingServiceIds should be empty after restart completes")
    }

    @Test
    fun startService_clearsOperatingServiceIdsOnError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        fakeDockerClient.shouldThrowOnStart = true

        viewModel.startService(sampleService2)
        runCurrent()

        assertTrue(viewModel.state.value.operatingServiceIds.isEmpty(), "operatingServiceIds should be cleared on error")
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage?.contains("Failed to start container") == true)
    }

    @Test
    fun stopService_clearsOperatingServiceIdsOnError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        fakeDockerClient.shouldThrowOnStop = true

        viewModel.stopService(sampleService2)
        runCurrent()

        assertTrue(viewModel.state.value.operatingServiceIds.isEmpty(), "operatingServiceIds should be cleared on error")
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage?.contains("Failed to stop container") == true)
    }

    @Test
    fun restartService_clearsOperatingServiceIdsOnError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        fakeDockerClient.shouldThrowOnRestart = true

        viewModel.restartService(sampleService2)
        runCurrent()

        assertTrue(viewModel.state.value.operatingServiceIds.isEmpty(), "operatingServiceIds should be cleared on error")
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage?.contains("Failed to restart container") == true)
    }

    @Test
    fun selectedServiceContainerState_resolvesDirectlyByServiceIdWithoutLogSourceMatching() {
        val testConfig = DashboardConfig(
            groups = listOf(
                ServiceGroup(
                    id = "g1",
                    name = "Group 1",
                    services = listOf(sampleService2)
                )
            )
        )
        val uiState = DashboardUiState(
            config = testConfig,
            selectedServiceId = "db-1",
            containerStates = mapOf("db-1" to ContainerState.Running("running"))
        )

        assertEquals(ContainerState.Running("running"), uiState.selectedServiceContainerState)
    }

    @Test
    fun refreshHealthAndDocker_keysContainerStatesByServiceIdAcrossAllLogSources() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        runCurrent()

        val allSourcesConfig = sampleConfig.copy(
            groups = listOf(
                sampleGroup.copy(
                    services = listOf(sampleService1, sampleService2, sampleService4, sampleService5)
                )
            )
        )
        fakeConfigRepository.saveConfig(allSourcesConfig)
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.containerStates.containsKey("db-1"), "Docker container state should be keyed by serviceId 'db-1'")
        assertTrue(state.containerStates.containsKey("compose-1"), "Docker Compose state should be keyed by serviceId 'compose-1'")
        assertTrue(state.containerStates.containsKey("cmd-1"), "Command process state should be keyed by serviceId 'cmd-1'")
    }
}

