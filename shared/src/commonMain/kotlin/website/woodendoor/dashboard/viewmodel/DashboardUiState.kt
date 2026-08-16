package website.woodendoor.dashboard.viewmodel

import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceRuntimeStatus

data class DashboardUiState(
    val config: DashboardConfig = DashboardConfig(),
    val selectedServiceId: String? = null,
    val serviceStatuses: Map<String, ServiceRuntimeStatus> = emptyMap(),
    val isDockerAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
    val logSearchQuery: String = "",
    val isAutoScrollEnabled: Boolean = true,
    val operatingServiceIds: Set<String> = emptySet()
) {
    /**
     * Flattened list of all configured services across all groups.
     */
    val allServices: List<ServiceItem>
        get() = config.allServices

    /**
     * Currently selected service, or null if no service is selected or found.
     */
    val selectedService: ServiceItem?
        get() = selectedServiceId?.let { config.findService(it) }

    /**
     * Port health and runtime status of the currently selected service.
     */
    val selectedServiceStatus: ServiceRuntimeStatus?
        get() = selectedServiceId?.let { serviceStatuses[it] }

    /**
     * Docker container or process state of the currently selected service.
     */
    val selectedServiceContainerState: ContainerState?
        get() = selectedServiceStatus?.containerState

    /**
     * Filtered log lines based on [logSearchQuery] (case-insensitive substring match).
     */
    val filteredLogs: List<String>
        get() = if (logSearchQuery.isBlank()) {
            logs
        } else {
            logs.filter { it.contains(logSearchQuery, ignoreCase = true) }
        }
}
