package website.woodendoor.dashboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceRuntimeStatusTest {

    @Test
    fun `of for Docker service evaluates healthy only when running and port is open or none`() {
        val dockerServiceWithPort = ServiceItem(
            id = "d1",
            name = "Docker App",
            port = 8080,
            logSource = LogSource.Docker("app-container")
        )
        val dockerServiceNoPort = ServiceItem(
            id = "d2",
            name = "Docker Worker",
            port = null,
            logSource = LogSource.Docker("worker-container")
        )

        // Running + Open -> Healthy
        val status1 = ServiceRuntimeStatus.of(
            service = dockerServiceWithPort,
            portHealth = PortHealth.Open(latencyMs = 15),
            containerState = ContainerState.Running("Up 2 hours")
        )
        assertTrue(status1.isHealthy)
        assertEquals("d1", status1.serviceId)
        assertEquals("15ms", status1.portBadgeLabel)
        assertEquals("Running", status1.containerBadgeLabel)

        // Running + None -> Healthy
        val status2 = ServiceRuntimeStatus.of(
            service = dockerServiceNoPort,
            portHealth = PortHealth.None,
            containerState = ContainerState.Running("Up 2 hours")
        )
        assertTrue(status2.isHealthy)
        assertEquals("No Port", status2.portBadgeLabel)

        // Running + Closed -> Unhealthy
        val status3 = ServiceRuntimeStatus.of(
            service = dockerServiceWithPort,
            portHealth = PortHealth.Closed("Connection refused"),
            containerState = ContainerState.Running("Up 2 hours")
        )
        assertFalse(status3.isHealthy)
        assertEquals("Closed", status3.portBadgeLabel)

        // Running + Unreachable -> Unhealthy
        val status4 = ServiceRuntimeStatus.of(
            service = dockerServiceWithPort,
            portHealth = PortHealth.Unreachable("Timeout"),
            containerState = ContainerState.Running("Up 2 hours")
        )
        assertFalse(status4.isHealthy)
        assertEquals("Unreachable", status4.portBadgeLabel)

        // Exited + Open -> Unhealthy
        val status5 = ServiceRuntimeStatus.of(
            service = dockerServiceWithPort,
            portHealth = PortHealth.Open(latencyMs = 15),
            containerState = ContainerState.Exited(exitCode = 1, status = "Exited (1)")
        )
        assertFalse(status5.isHealthy)
        assertEquals("Exited (1)", status5.containerBadgeLabel)

        // Paused / Dead / NotFound / Unknown -> Unhealthy
        assertFalse(
            ServiceRuntimeStatus.of(
                service = dockerServiceWithPort,
                portHealth = PortHealth.Open(latencyMs = 15),
                containerState = ContainerState.Paused("Paused")
            ).isHealthy
        )
        assertFalse(
            ServiceRuntimeStatus.of(
                service = dockerServiceWithPort,
                portHealth = PortHealth.Open(latencyMs = 15),
                containerState = ContainerState.Dead("Dead")
            ).isHealthy
        )
        assertFalse(
            ServiceRuntimeStatus.of(
                service = dockerServiceWithPort,
                portHealth = PortHealth.Open(latencyMs = 15),
                containerState = ContainerState.NotFound("Not Found")
            ).isHealthy
        )
        assertFalse(
            ServiceRuntimeStatus.of(
                service = dockerServiceWithPort,
                portHealth = PortHealth.Open(latencyMs = 15),
                containerState = ContainerState.Unknown("creating")
            ).isHealthy
        )
    }

    @Test
    fun `of for DockerCompose service behaves consistently with Docker service`() {
        val composeService = ServiceItem(
            id = "c1",
            name = "Compose Web",
            port = 3000,
            logSource = LogSource.DockerCompose("/app", "web", "docker-compose.yml")
        )

        val runningHealthy = ServiceRuntimeStatus.of(
            service = composeService,
            portHealth = PortHealth.Open(latencyMs = 5),
            containerState = ContainerState.Running("Up 10 minutes")
        )
        assertTrue(runningHealthy.isHealthy)

        val runningClosed = ServiceRuntimeStatus.of(
            service = composeService,
            portHealth = PortHealth.Closed("Refused"),
            containerState = ContainerState.Running("Up 10 minutes")
        )
        assertFalse(runningClosed.isHealthy)

        val exited = ServiceRuntimeStatus.of(
            service = composeService,
            portHealth = PortHealth.None,
            containerState = ContainerState.Exited(0, "Exited (0)")
        )
        assertFalse(exited.isHealthy)
    }

    @Test
    fun `of for Command service evaluates healthy when running and port is open or none`() {
        val commandService = ServiceItem(
            id = "cmd1",
            name = "Vite Dev Server",
            port = 5173,
            logSource = LogSource.Command(workingDir = "/app", startCommand = "npm run dev")
        )

        val runningHealthy = ServiceRuntimeStatus.of(
            service = commandService,
            portHealth = PortHealth.Open(latencyMs = 2),
            containerState = ContainerState.Running("pid 12345")
        )
        assertTrue(runningHealthy.isHealthy)

        val exitedUnhealthy = ServiceRuntimeStatus.of(
            service = commandService,
            portHealth = PortHealth.Open(latencyMs = 2),
            containerState = ContainerState.Exited(1, "Process terminated")
        )
        assertFalse(exitedUnhealthy.isHealthy)

        val unknownStateUnhealthy = ServiceRuntimeStatus.of(
            service = commandService,
            portHealth = PortHealth.Open(latencyMs = 2),
            containerState = ContainerState.Unknown("not started")
        )
        assertFalse(unknownStateUnhealthy.isHealthy)
    }

    @Test
    fun `of for LocalFile and None log sources evaluates healthy unless port closed or state exited or dead`() {
        val fileService = ServiceItem(
            id = "f1",
            name = "System Logs",
            port = null,
            logSource = LogSource.LocalFile("/var/log/syslog")
        )
        val noneService = ServiceItem(
            id = "n1",
            name = "External Service",
            port = 80,
            logSource = LogSource.None
        )

        // File service with Unknown("") state and PortHealth.None is healthy
        val fileStatus = ServiceRuntimeStatus.of(
            service = fileService,
            portHealth = PortHealth.None,
            containerState = ContainerState.Unknown("")
        )
        assertTrue(fileStatus.isHealthy)

        // None service with Open port and Unknown("") state is healthy
        val noneOpenStatus = ServiceRuntimeStatus.of(
            service = noneService,
            portHealth = PortHealth.Open(latencyMs = 8),
            containerState = ContainerState.Unknown("")
        )
        assertTrue(noneOpenStatus.isHealthy)

        // None service with Closed port is unhealthy
        val noneClosedStatus = ServiceRuntimeStatus.of(
            service = noneService,
            portHealth = PortHealth.Closed("Refused"),
            containerState = ContainerState.Unknown("")
        )
        assertFalse(noneClosedStatus.isHealthy)

        // None service with Dead state is unhealthy
        val noneDeadStatus = ServiceRuntimeStatus.of(
            service = noneService,
            portHealth = PortHealth.Open(latencyMs = 8),
            containerState = ContainerState.Dead("Dead")
        )
        assertFalse(noneDeadStatus.isHealthy)

        // None service with Exited state is unhealthy
        val noneExitedStatus = ServiceRuntimeStatus.of(
            service = noneService,
            portHealth = PortHealth.Open(latencyMs = 8),
            containerState = ContainerState.Exited(1, "Exited (1)")
        )
        assertFalse(noneExitedStatus.isHealthy)
    }

    @Test
    fun `PortHealth presentation extensions provide correct labels and details`() {
        val open = PortHealth.Open(latencyMs = 42)
        assertEquals("42ms", open.badgeLabel)
        assertEquals("Latency: 42ms", open.details)

        val closedWithReason = PortHealth.Closed(reason = "Connection refused by host")
        assertEquals("Closed", closedWithReason.badgeLabel)
        assertEquals("Connection refused by host", closedWithReason.details)

        val closedDefault = PortHealth.Closed()
        assertEquals("Closed", closedDefault.badgeLabel)
        assertEquals("Connection refused", closedDefault.details)

        val unreachableWithReason = PortHealth.Unreachable(reason = "Route timeout")
        assertEquals("Unreachable", unreachableWithReason.badgeLabel)
        assertEquals("Route timeout", unreachableWithReason.details)

        val unreachableDefault = PortHealth.Unreachable()
        assertEquals("Unreachable", unreachableDefault.badgeLabel)
        assertEquals("Host unreachable", unreachableDefault.details)

        val none = PortHealth.None
        assertEquals("No Port", none.badgeLabel)
        assertNull(none.details)
    }

    @Test
    fun `ContainerState presentation extensions provide correct labels and details`() {
        val running = ContainerState.Running("Up 5 hours")
        assertEquals("Running", running.badgeLabel)
        assertEquals("Up 5 hours", running.details)

        val paused = ContainerState.Paused("Paused")
        assertEquals("Paused", paused.badgeLabel)
        assertEquals("Paused", paused.details)

        val restarting = ContainerState.Restarting("Restarting (1) 5s ago")
        assertEquals("Restarting", restarting.badgeLabel)
        assertEquals("Restarting (1) 5s ago", restarting.details)

        val exited = ContainerState.Exited(exitCode = 137, status = "Exited (137) 2 minutes ago")
        assertEquals("Exited (137)", exited.badgeLabel)
        assertEquals("Exited (137) 2 minutes ago", exited.details)

        val dead = ContainerState.Dead("Dead container")
        assertEquals("Dead", dead.badgeLabel)
        assertEquals("Dead container", dead.details)

        val notFound = ContainerState.NotFound("No such container: foo")
        assertEquals("Not Found", notFound.badgeLabel)
        assertEquals("No such container: foo", notFound.details)

        val unknownCustom = ContainerState.Unknown("creating")
        assertEquals("creating", unknownCustom.badgeLabel)
        assertEquals("creating", unknownCustom.details)

        val unknownBlank = ContainerState.Unknown("")
        assertEquals("Unknown", unknownBlank.badgeLabel)
        assertNull(unknownBlank.details)
    }

    @Test
    fun `calculateSummaryMetrics returns zero counts for empty service list`() {
        val metrics = ServiceRuntimeStatus.calculateSummaryMetrics(
            services = emptyList(),
            statuses = emptyMap(),
            isDockerAvailable = false
        )

        assertEquals(0, metrics.totalCount)
        assertEquals(0, metrics.healthyCount)
        assertEquals(0, metrics.offlineCount)
        assertEquals(false, metrics.isDockerAvailable)
    }

    @Test
    fun `calculateSummaryMetrics aggregates healthy and offline services correctly`() {
        val services = listOf(
            ServiceItem(id = "s1", name = "Web", port = 3000),
            ServiceItem(id = "s2", name = "API", port = 8000),
            ServiceItem(id = "s3", name = "DB", port = 5432),
            ServiceItem(id = "s4", name = "NoPortWorker", port = null),
            ServiceItem(id = "s5", name = "PendingWeb", port = 9000)
        )

        val statuses = mapOf(
            "s1" to ServiceRuntimeStatus(
                serviceId = "s1",
                portHealth = PortHealth.Open(latencyMs = 5),
                isHealthy = true
            ),
            "s2" to ServiceRuntimeStatus(
                serviceId = "s2",
                portHealth = PortHealth.Closed("Refused"),
                isHealthy = false
            ),
            "s3" to ServiceRuntimeStatus(
                serviceId = "s3",
                portHealth = PortHealth.Unreachable("Timeout"),
                isHealthy = false
            )
            // s4 and s5 have no status yet
        )

        val metrics = ServiceRuntimeStatus.calculateSummaryMetrics(
            services = services,
            statuses = statuses,
            isDockerAvailable = true
        )

        // s1: healthy (status isHealthy = true)
        // s2: offline (status isHealthy = false)
        // s3: offline (status isHealthy = false)
        // s4: healthy (no status, but port == null)
        // s5: offline (no status, but port != null)
        assertEquals(5, metrics.totalCount)
        assertEquals(2, metrics.healthyCount)
        assertEquals(3, metrics.offlineCount)
        assertEquals(true, metrics.isDockerAvailable)
    }
}
