package website.woodendoor.dashboard.service

import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus

interface PortHealthChecker {
    suspend fun checkPort(host: String = "127.0.0.1", port: Int, timeoutMs: Long = 1000): PortHealth
    suspend fun checkServices(services: List<ServiceItem>): Map<String, ServiceStatus>
}
