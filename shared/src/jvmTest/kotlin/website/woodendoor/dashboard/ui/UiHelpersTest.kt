package website.woodendoor.dashboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus
import website.woodendoor.dashboard.ui.util.ContainerStatusType
import website.woodendoor.dashboard.ui.util.PortStatusType
import website.woodendoor.dashboard.ui.util.UiHelpers

class UiHelpersTest {

    @Test
    fun `calculateSummaryMetrics returns zero counts for empty service list`() {
        val metrics = UiHelpers.calculateSummaryMetrics(
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
    fun `calculateSummaryMetrics counts healthy and offline services correctly`() {
        val services = listOf(
            ServiceItem(id = "s1", name = "Web", port = 3000),
            ServiceItem(id = "s2", name = "API", port = 8000),
            ServiceItem(id = "s3", name = "DB", port = 5432)
        )

        val statuses = mapOf(
            "s1" to ServiceStatus(serviceId = "s1", portHealth = PortHealth.Open(latencyMs = 5), isHealthy = true),
            "s2" to ServiceStatus(serviceId = "s2", portHealth = PortHealth.Closed("Refused"), isHealthy = false),
            "s3" to ServiceStatus(serviceId = "s3", portHealth = PortHealth.Unreachable("Timeout"), isHealthy = false)
        )

        val metrics = UiHelpers.calculateSummaryMetrics(
            services = services,
            statuses = statuses,
            isDockerAvailable = true
        )

        assertEquals(3, metrics.totalCount)
        assertEquals(1, metrics.healthyCount)
        assertEquals(2, metrics.offlineCount)
        assertEquals(true, metrics.isDockerAvailable)
    }

    @Test
    fun `getPortHealthDisplayInfo returns appropriate label and status type for Open`() {
        val openHealth = PortHealth.Open(latencyMs = 12)
        val info = UiHelpers.getPortHealthDisplayInfo(openHealth)

        assertEquals("12ms", info.label)
        assertEquals(PortStatusType.HEALTHY, info.statusType)
    }

    @Test
    fun `getPortHealthDisplayInfo returns appropriate label and status type for Closed`() {
        val closedHealth = PortHealth.Closed(reason = "Connection refused")
        val info = UiHelpers.getPortHealthDisplayInfo(closedHealth)

        assertEquals("Closed", info.label)
        assertEquals(PortStatusType.CLOSED, info.statusType)
        assertEquals("Connection refused", info.details)
    }

    @Test
    fun `getPortHealthDisplayInfo returns appropriate label and status type for Unreachable`() {
        val unreachableHealth = PortHealth.Unreachable(reason = "Timeout")
        val info = UiHelpers.getPortHealthDisplayInfo(unreachableHealth)

        assertEquals("Unreachable", info.label)
        assertEquals(PortStatusType.UNREACHABLE, info.statusType)
        assertEquals("Timeout", info.details)
    }

    @Test
    fun `getPortHealthDisplayInfo returns appropriate label and status type for None`() {
        val info = UiHelpers.getPortHealthDisplayInfo(PortHealth.None)

        assertEquals("No Port", info.label)
        assertEquals(PortStatusType.NO_PORT, info.statusType)
    }

    @Test
    fun `getContainerStateDisplayInfo returns correct info for Running`() {
        val runningState = ContainerState.Running("Up 3 hours")
        val info = UiHelpers.getContainerStateDisplayInfo(runningState)

        assertEquals("Running", info.label)
        assertEquals(ContainerStatusType.RUNNING, info.statusType)
    }

    @Test
    fun `getContainerStateDisplayInfo returns correct info for Exited`() {
        val exitedState = ContainerState.Exited(exitCode = 1, status = "Exited (1)")
        val info = UiHelpers.getContainerStateDisplayInfo(exitedState)

        assertEquals("Exited (1)", info.label)
        assertEquals(ContainerStatusType.EXITED, info.statusType)
    }

    @Test
    fun `getContainerStateDisplayInfo returns correct info for Paused`() {
        val pausedState = ContainerState.Paused("Paused")
        val info = UiHelpers.getContainerStateDisplayInfo(pausedState)

        assertEquals("Paused", info.label)
        assertEquals(ContainerStatusType.PAUSED, info.statusType)
    }

    @Test
    fun `getContainerStateDisplayInfo returns correct info for NotFound`() {
        val notFoundState = ContainerState.NotFound("Container not found")
        val info = UiHelpers.getContainerStateDisplayInfo(notFoundState)

        assertEquals("Not Found", info.label)
        assertEquals(ContainerStatusType.NOT_FOUND, info.statusType)
    }

    @Test
    fun `getContainerStateDisplayInfo returns correct info for Unknown`() {
        val unknownState = ContainerState.Unknown("creating")
        val info = UiHelpers.getContainerStateDisplayInfo(unknownState)

        assertEquals("creating", info.label)
        assertEquals(ContainerStatusType.UNKNOWN, info.statusType)
    }
}
