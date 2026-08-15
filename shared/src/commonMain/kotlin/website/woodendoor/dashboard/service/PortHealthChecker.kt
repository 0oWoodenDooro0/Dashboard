package website.woodendoor.dashboard.service

import website.woodendoor.dashboard.model.PortHealth

/**
 * Socket port health probe seam for inspecting TCP port connectivity and latency.
 */
interface PortHealthChecker {
    suspend fun checkPort(host: String = "127.0.0.1", port: Int, timeoutMs: Long = 1000): PortHealth
}
