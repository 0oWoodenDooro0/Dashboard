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
    val logSession: LogConsoleSession = LogConsoleSession(),
    val isAutoScrollEnabled: Boolean = true,
    val operatingServiceIds: Set<String> = emptySet()
) {
    constructor(
        config: DashboardConfig = DashboardConfig(),
        selectedServiceId: String? = null,
        serviceStatuses: Map<String, ServiceRuntimeStatus> = emptyMap(),
        isDockerAvailable: Boolean = false,
        isLoading: Boolean = false,
        errorMessage: String? = null,
        logs: List<String> = emptyList(),
        logSearchQuery: String = "",
        isAutoScrollEnabled: Boolean = true,
        operatingServiceIds: Set<String> = emptySet()
    ) : this(
        config = config,
        selectedServiceId = selectedServiceId,
        serviceStatuses = serviceStatuses,
        isDockerAvailable = isDockerAvailable,
        isLoading = isLoading,
        errorMessage = errorMessage,
        logSession = LogConsoleSession(logs = logs, searchQuery = logSearchQuery),
        isAutoScrollEnabled = isAutoScrollEnabled,
        operatingServiceIds = operatingServiceIds
    )

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
     * Active log lines in the log buffer.
     */
    val logs: List<String>
        get() = logSession.logs

    /**
     * Active search query for filtering log lines.
     */
    val logSearchQuery: String
        get() = logSession.searchQuery

    /**
     * Filtered log lines based on [logSearchQuery] (case-insensitive substring match).
     */
    val filteredLogs: List<String>
        get() = logSession.filteredLogs
}
