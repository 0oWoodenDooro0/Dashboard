package website.woodendoor.dashboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceRuntimeStatus
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
            "s1" to ServiceRuntimeStatus(serviceId = "s1", portHealth = PortHealth.Open(latencyMs = 5), isHealthy = true),
            "s2" to ServiceRuntimeStatus(serviceId = "s2", portHealth = PortHealth.Closed("Refused"), isHealthy = false),
            "s3" to ServiceRuntimeStatus(serviceId = "s3", portHealth = PortHealth.Unreachable("Timeout"), isHealthy = false)
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
}
