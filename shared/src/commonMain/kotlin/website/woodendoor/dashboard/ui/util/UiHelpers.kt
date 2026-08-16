package website.woodendoor.dashboard.ui.util

import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceRuntimeStatus
import website.woodendoor.dashboard.model.SummaryMetrics

typealias SummaryMetrics = website.woodendoor.dashboard.model.SummaryMetrics

object UiHelpers {

    fun calculateSummaryMetrics(
        services: List<ServiceItem>,
        statuses: Map<String, ServiceRuntimeStatus>,
        isDockerAvailable: Boolean
    ): SummaryMetrics {
        return ServiceRuntimeStatus.calculateSummaryMetrics(
            services = services,
            statuses = statuses,
            isDockerAvailable = isDockerAvailable
        )
    }
}
