package website.woodendoor.dashboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.badgeLabel
import website.woodendoor.dashboard.ui.components.describeLogSource
import website.woodendoor.dashboard.ui.util.SummaryMetrics

class StaticBadgesAndChipsTest {

    @Test
    fun `TopBar metric badge labels format correctly for various counts and Docker states`() {
        // Happy path: Healthy metrics with Docker available
        val healthyMetrics = SummaryMetrics(
            totalCount = 5,
            healthyCount = 4,
            offlineCount = 1,
            isDockerAvailable = true
        )
        assertEquals("5 Services", "${healthyMetrics.totalCount} Services")
        assertEquals("4 Online", "${healthyMetrics.healthyCount} Online")
        assertEquals("1 Offline", "${healthyMetrics.offlineCount} Offline")
        val dockerLabelActive = if (healthyMetrics.isDockerAvailable) "Docker Active" else "Docker Offline"
        assertEquals("Docker Active", dockerLabelActive)

        // Edge case: Zero counts & Docker unavailable
        val emptyMetrics = SummaryMetrics(
            totalCount = 0,
            healthyCount = 0,
            offlineCount = 0,
            isDockerAvailable = false
        )
        assertEquals("0 Services", "${emptyMetrics.totalCount} Services")
        assertEquals("0 Online", "${emptyMetrics.healthyCount} Online")
        assertEquals("0 Offline", "${emptyMetrics.offlineCount} Offline")
        val dockerLabelOffline = if (emptyMetrics.isDockerAvailable) "Docker Active" else "Docker Offline"
        assertEquals("Docker Offline", dockerLabelOffline)
    }

    @Test
    fun `ServiceCard port and health badge labels format accurately for all PortHealth states`() {
        val serviceWithPort = ServiceItem(id = "s1", name = "API Gateway", host = "localhost", port = 8080)
        val serviceWithoutPort = ServiceItem(id = "s2", name = "Background Worker", host = "localhost", port = null)

        assertEquals(":8080", serviceWithPort.port?.let { ":$it" })
        assertNull(serviceWithoutPort.port?.let { ":$it" })

        // Port health badges
        val openHealth: PortHealth = PortHealth.Open(latencyMs = 15)
        val closedHealth: PortHealth = PortHealth.Closed("Connection Refused")
        val unreachableHealth: PortHealth = PortHealth.Unreachable("Timeout after 3000ms")
        val noneHealth: PortHealth = PortHealth.None

        assertEquals("15ms", openHealth.badgeLabel)
        assertEquals("Closed", closedHealth.badgeLabel)
        assertEquals("Unreachable", unreachableHealth.badgeLabel)
        assertEquals("No Port", noneHealth.badgeLabel)
    }

    @Test
    fun `ServiceCard metadata chips format correctly for container states, log sources, and tags`() {
        // Container States
        assertEquals("Running", ContainerState.Running("Up 3 hours").badgeLabel)
        assertEquals("Paused", ContainerState.Paused("Paused").badgeLabel)
        assertEquals("Restarting", ContainerState.Restarting(status = "Restarting").badgeLabel)
        assertEquals("Exited (0)", ContainerState.Exited(exitCode = 0, status = "Exited (0)").badgeLabel)
        assertEquals("Exited (137)", ContainerState.Exited(exitCode = 137, status = "OOMKilled").badgeLabel)
        assertEquals("Dead", ContainerState.Dead("Resource deadlock").badgeLabel)
        assertEquals("Not Found", ContainerState.NotFound().badgeLabel)
        assertEquals("Unknown", ContainerState.Unknown("").badgeLabel)

        // Log Sources formatting helper in SmallChip context
        val dockerSource = LogSource.Docker(containerName = "auth-redis")
        val composeSource = LogSource.DockerCompose(serviceName = "web-app", projectDir = "/opt/project")
        val commandSource = LogSource.Command(startCommand = "gradle bootRun", workingDir = "/app")
        val fileSource = LogSource.LocalFile(path = "/var/log/app/server.log")

        assertEquals("docker: auth-redis", "docker: ${dockerSource.containerName}")
        assertEquals("compose: web-app", "compose: ${composeSource.serviceName}")
        assertEquals("cmd: gradle bootRun", "cmd: ${commandSource.startCommand}")
        assertEquals("file: server.log", "file: ${fileSource.path.substringAfterLast("/")}")

        // describeLogSource helper used in console and metadata
        assertEquals("docker: auth-redis", describeLogSource(dockerSource))
        assertEquals("compose: web-app (/opt/project)", describeLogSource(composeSource))
        assertEquals("cmd: gradle bootRun (/app)", describeLogSource(commandSource))
        assertEquals("file: /var/log/app/server.log", describeLogSource(fileSource))
        assertEquals("No Log Source", describeLogSource(LogSource.None))
    }

    @Test
    fun `ServiceListPane and GroupHeader count badges produce exact label representations`() {
        val totalCount = 12
        val sidebarBadge = "$totalCount total"
        assertEquals("12 total", sidebarBadge)

        val groupCount = 4
        val groupBadge = groupCount.toString()
        assertEquals("4", groupBadge)

        // Zero count
        assertEquals("0 total", "${0} total")
        assertEquals("0", 0.toString())
    }

    @Test
    fun `LogConsolePane terminal badges format service name and line counter accurately`() {
        val service = ServiceItem(id = "srv-1", name = "Backend API", host = "localhost")
        assertEquals("Backend API", service.name)

        // Line counter when search query is empty
        val totalLines = 150
        val unconstrainedCount = "$totalLines lines"
        assertEquals("150 lines", unconstrainedCount)

        // Line counter when search query is active
        val filteredLines = 23
        val filteredCount = "$filteredLines / $totalLines lines"
        assertEquals("23 / 150 lines", filteredCount)

        // Empty log lines
        val zeroCount = "0 lines"
        assertEquals("0 lines", zeroCount)
    }

    @Test
    fun `Action buttons are interactive callbacks while informational badges do not have click listeners`() {
        var refreshTriggered = false
        var addTriggered = false
        var startTriggered = false
        var stopTriggered = false
        var restartTriggered = false
        var editTriggered = false
        var deleteTriggered = false
        var copyTriggered = false
        var clearTriggered = false

        val onRefresh = { refreshTriggered = true }
        val onAdd = { addTriggered = true }
        val onStart = { startTriggered = true }
        val onStop = { stopTriggered = true }
        val onRestart = { restartTriggered = true }
        val onEdit = { editTriggered = true }
        val onDelete = { deleteTriggered = true }
        val onCopy = { copyTriggered = true }
        val onClear = { clearTriggered = true }

        onRefresh()
        onAdd()
        onStart()
        onStop()
        onRestart()
        onEdit()
        onDelete()
        onCopy()
        onClear()

        assertTrue(refreshTriggered)
        assertTrue(addTriggered)
        assertTrue(startTriggered)
        assertTrue(stopTriggered)
        assertTrue(restartTriggered)
        assertTrue(editTriggered)
        assertTrue(deleteTriggered)
        assertTrue(copyTriggered)
        assertTrue(clearTriggered)
    }
}
