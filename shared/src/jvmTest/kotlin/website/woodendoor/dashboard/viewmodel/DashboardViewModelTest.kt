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
import kotlinx.coroutines.test.advanceUntilIdle
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
import website.woodendoor.dashboard.service.DockerClient
import website.woodendoor.dashboard.service.LogStreamService
import website.woodendoor.dashboard.service.PortHealthChecker

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var fakeConfigRepository: FakeConfigRepository
    private lateinit var fakeHealthChecker: FakePortHealthChecker
    private lateinit var fakeLogStreamService: FakeLogStreamService
    private lateinit var fakeDockerClient: FakeDockerClient

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

    private class FakePortHealthChecker(
        var defaultHealth: PortHealth = PortHealth.Open(latencyMs = 15)
    ) : PortHealthChecker {
        var customHealthMap = mutableMapOf<String, PortHealth>()
        var checkServicesCallCount = 0
        var shouldThrow = false

        override suspend fun checkPort(host: String, port: Int, timeoutMs: Long): PortHealth {
            return defaultHealth
        }

        override suspend fun checkServices(services: List<ServiceItem>): Map<String, ServiceStatus> {
            if (shouldThrow) throw RuntimeException("Network probe failure")
            checkServicesCallCount++
            val now = 1000L
            return services.associate { service ->
                val health = customHealthMap[service.id] ?: defaultHealth
                service.id to ServiceStatus(
                    serviceId = service.id,
                    portHealth = health,
                    isHealthy = health is PortHealth.Open || health is PortHealth.None,
                    lastCheckedTimestamp = now
                )
            }
        }
    }

    private class FakeLogStreamService : LogStreamService {
        val streams = mutableMapOf<LogSource, MutableSharedFlow<String>>()
        var streamCallCount = 0
        var lastRequestedSource: LogSource? = null

        fun getOrCreateFlow(source: LogSource): MutableSharedFlow<String> {
            return streams.getOrPut(source) {
                MutableSharedFlow(extraBufferCapacity = 64)
            }
        }

        override fun streamLogs(source: LogSource, tail: Int): Flow<String> {
            streamCallCount++
            lastRequestedSource = source
            return getOrCreateFlow(source)
        }
    }

    private class FakeDockerClient(
        var isDockerAvailableResult: Boolean = true
    ) : DockerClient {
        var containerStates = mutableMapOf<String, ContainerState>()
        var customLogFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        var shouldThrowOnAvailable = false

        override suspend fun isDockerAvailable(): Boolean {
            if (shouldThrowOnAvailable) throw RuntimeException("Docker daemon socket error")
            return isDockerAvailableResult
        }

        override suspend fun listContainers(all: Boolean): List<DockerContainerInfo> = emptyList()

        override suspend fun getContainerState(nameOrId: String): ContainerState {
            return containerStates[nameOrId] ?: ContainerState.Running(status = "running")
        }

        override fun streamLogs(nameOrId: String, tail: Int): Flow<String> = customLogFlow
    }

    @BeforeTest
    fun setUp() {
        fakeConfigRepository = FakeConfigRepository(sampleConfig)
        fakeHealthChecker = FakePortHealthChecker()
        fakeLogStreamService = FakeLogStreamService()
        fakeDockerClient = FakeDockerClient(isDockerAvailableResult = true)
    }

    private fun createViewModel(
        testScope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        maxLogBufferSize: Int = 1000
    ): DashboardViewModel {
        return DashboardViewModel(
            configRepository = fakeConfigRepository,
            healthChecker = fakeHealthChecker,
            logStreamService = fakeLogStreamService,
            dockerClient = fakeDockerClient,
            coroutineScope = testScope,
            defaultDispatcher = dispatcher,
            maxLogBufferSize = maxLogBufferSize
        )
    }

    @Test
    fun initialization_loadsConfigAndPerformsInitialHealthCheck() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)

        advanceUntilIdle()

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
        advanceUntilIdle()

        val newService = ServiceItem(id = "api-1", name = "API Gateway", port = 8080)
        val updatedConfig = sampleConfig.copy(
            groups = listOf(sampleGroup.copy(services = sampleGroup.services + newService))
        )

        fakeConfigRepository.saveConfig(updatedConfig)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(4, state.allServices.size)
        assertTrue(state.allServices.any { it.id == "api-1" })
        assertTrue(state.serviceStatuses.containsKey("api-1"))
    }

    @Test
    fun periodicPolling_updatesHealthAndContainerStates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        assertEquals(1, fakeHealthChecker.checkServicesCallCount)

        // Change health of web-1 to closed and docker container state to exited
        fakeHealthChecker.customHealthMap["web-1"] = PortHealth.Closed("Connection refused")
        fakeDockerClient.containerStates["postgres-db"] = ContainerState.Exited(exitCode = 1)

        // Advance by polling interval (5 seconds = 5000ms)
        advanceTimeBy(5100)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(fakeHealthChecker.checkServicesCallCount >= 2)
        assertIs<PortHealth.Closed>(state.serviceStatuses["web-1"]?.portHealth)
        assertEquals("Connection refused", (state.serviceStatuses["web-1"]?.portHealth as PortHealth.Closed).reason)
        assertIs<ContainerState.Exited>(state.containerStates["postgres-db"])
    }

    @Test
    fun selectService_startsLogStreamAndPopulatesLogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        viewModel.selectService("web-1")
        advanceUntilIdle()

        assertEquals("web-1", viewModel.state.value.selectedServiceId)
        assertEquals(sampleService1, viewModel.state.value.selectedService)

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        flow.emit("2026-08-14 Server started on port 3000")
        flow.emit("2026-08-14 GET /api/v1/health 200 OK")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.logs.size)
        assertEquals("2026-08-14 Server started on port 3000", state.logs[0])
        assertEquals("2026-08-14 GET /api/v1/health 200 OK", state.logs[1])
    }

    @Test
    fun selectService_switchingService_clearsPreviousLogsAndCancelsOldStream() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        viewModel.selectService("web-1")
        advanceUntilIdle()

        val webFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        webFlow.emit("web log 1")
        advanceUntilIdle()
        assertEquals(listOf("web log 1"), viewModel.state.value.logs)

        // Switch to db-1
        viewModel.selectService("db-1")
        advanceUntilIdle()

        assertEquals("db-1", viewModel.state.value.selectedServiceId)
        assertTrue(viewModel.state.value.logs.isEmpty(), "Logs should be reset on service switch")

        val dbFlow = fakeLogStreamService.getOrCreateFlow(sampleService2.logSource)
        dbFlow.emit("db log 1")
        advanceUntilIdle()

        assertEquals(listOf("db log 1"), viewModel.state.value.logs)

        // Emitting to previous web flow must NOT affect current logs
        webFlow.emit("web log 2 (should be ignored)")
        advanceUntilIdle()

        assertEquals(listOf("db log 1"), viewModel.state.value.logs)
    }

    @Test
    fun selectService_noneLogSource_hasEmptyLogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        viewModel.selectService("cache-1")
        advanceUntilIdle()

        assertEquals("cache-1", viewModel.state.value.selectedServiceId)
        assertTrue(viewModel.state.value.logs.isEmpty())
        assertNull(fakeLogStreamService.lastRequestedSource)
    }

    @Test
    fun selectService_null_deselectsAndClearsLogs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        viewModel.selectService("web-1")
        val webFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        webFlow.emit("log line")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.logs.size)

        viewModel.selectService(null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedServiceId)
        assertNull(viewModel.state.value.selectedService)
        assertTrue(viewModel.state.value.logs.isEmpty())
    }

    @Test
    fun logBuffer_capsAtMaxCapacity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher, maxLogBufferSize = 5)
        advanceUntilIdle()

        viewModel.selectService("web-1")
        advanceUntilIdle()

        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        for (i in 1..8) {
            flow.emit("Line $i")
        }
        advanceUntilIdle()

        val logs = viewModel.state.value.logs
        assertEquals(5, logs.size, "Logs must be capped at maxLogBufferSize")
        assertEquals(listOf("Line 4", "Line 5", "Line 6", "Line 7", "Line 8"), logs)
    }

    @Test
    fun setLogSearchQuery_filtersLogsCorrectly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        viewModel.selectService("web-1")
        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        flow.emit("INFO: User login successful")
        flow.emit("ERROR: Database connection timeout")
        flow.emit("DEBUG: Cache hit for key user_1")
        flow.emit("error: Failed to write audit log")
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        viewModel.selectService("web-1")
        val flow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        flow.emit("line 1")
        flow.emit("line 2")
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.logs.size)

        viewModel.clearLogs()
        assertTrue(viewModel.state.value.logs.isEmpty())

        // Next line still streams properly
        flow.emit("line 3")
        advanceUntilIdle()
        assertEquals(listOf("line 3"), viewModel.state.value.logs)
    }

    @Test
    fun addService_appendsServiceToSpecifiedOrFirstGroupAndSaves() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        val newService = ServiceItem(
            id = "worker-1",
            name = "Background Worker",
            port = 9000
        )

        viewModel.addService(newService, groupId = "core-group")
        advanceUntilIdle()

        assertEquals(1, fakeConfigRepository.saveCallCount)
        val updatedGroup = fakeConfigRepository.currentConfig.groups.find { it.id == "core-group" }
        assertNotNull(updatedGroup)
        assertTrue(updatedGroup.services.any { it.id == "worker-1" })
    }

    @Test
    fun updateService_modifiesExistingServiceAndSaves() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        val updatedWeb1 = sampleService1.copy(name = "Updated Web App", port = 3001)
        viewModel.updateService(updatedWeb1)
        advanceUntilIdle()

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
        advanceUntilIdle()

        viewModel.selectService("web-1")
        val oldFlow = fakeLogStreamService.getOrCreateFlow(sampleService1.logSource)
        oldFlow.emit("old log 1")
        advanceUntilIdle()
        assertEquals(listOf("old log 1"), viewModel.state.value.logs)

        val newLogSource = LogSource.LocalFile("/var/log/web_new.log")
        val updatedWeb1 = sampleService1.copy(logSource = newLogSource)
        viewModel.updateService(updatedWeb1)
        advanceUntilIdle()

        val newFlow = fakeLogStreamService.getOrCreateFlow(newLogSource)
        newFlow.emit("new log 1")
        advanceUntilIdle()

        assertEquals(listOf("new log 1"), viewModel.state.value.logs)
    }

    @Test
    fun deleteService_removesServiceAndClearsSelectionIfSelected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        viewModel.selectService("web-1")
        advanceUntilIdle()
        assertEquals("web-1", viewModel.state.value.selectedServiceId)

        viewModel.deleteService("web-1")
        advanceUntilIdle()

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
        advanceUntilIdle()

        val initialCount = fakeHealthChecker.checkServicesCallCount

        fakeHealthChecker.customHealthMap["web-1"] = PortHealth.Unreachable("Host unreachable")
        viewModel.triggerRefresh()
        advanceUntilIdle()

        assertTrue(fakeHealthChecker.checkServicesCallCount > initialCount)
        assertIs<PortHealth.Unreachable>(viewModel.state.value.serviceStatuses["web-1"]?.portHealth)
    }

    @Test
    fun errorHandling_survivesRepositoryOrDockerFailureWithoutCrashing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        fakeConfigRepository.shouldThrowOnLoad = true

        val viewModel = createViewModel(this, dispatcher)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.errorMessage)
        assertEquals("Failed to read config file", viewModel.state.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.state.value.errorMessage)
    }
}
