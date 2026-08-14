package website.woodendoor.dashboard.viewmodel

import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DashboardConfig
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus

data class DashboardUiState(
    val config: DashboardConfig = DashboardConfig(),
    val selectedServiceId: String? = null,
    val serviceStatuses: Map<String, ServiceStatus> = emptyMap(),
    val containerStates: Map<String, ContainerState> = emptyMap(),
    val isDockerAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
    val logSearchQuery: String = "",
    val isAutoScrollEnabled: Boolean = true
) {
    /**
     * Flattened list of all configured services across all groups.
     */
    val allServices: List<ServiceItem>
        get() = config.groups.flatMap { it.services }

    /**
     * Currently selected service, or null if no service is selected or found.
     */
    val selectedService: ServiceItem?
        get() = allServices.find { it.id == selectedServiceId }

    /**
     * Port health status of the currently selected service.
     */
    val selectedServiceStatus: ServiceStatus?
        get() = selectedServiceId?.let { serviceStatuses[it] }

    /**
     * Docker container state of the currently selected service (if its log source is Docker).
     */
    val selectedServiceContainerState: ContainerState?
        get() = when (val source = selectedService?.logSource) {
            is LogSource.Docker -> containerStates[source.containerName]
            else -> null
        }

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
