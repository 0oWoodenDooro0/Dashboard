package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.DashboardConfig

interface ConfigRepository {
    fun loadConfig(): DashboardConfig
    fun saveConfig(config: DashboardConfig)
    fun observeConfig(): Flow<DashboardConfig>
}
