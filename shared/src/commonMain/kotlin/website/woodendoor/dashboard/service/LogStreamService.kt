package website.woodendoor.dashboard.service

import kotlinx.coroutines.flow.Flow
import website.woodendoor.dashboard.model.LogSource

interface LogStreamService {
    fun streamLogs(source: LogSource, tail: Int = 100): Flow<String>
}
