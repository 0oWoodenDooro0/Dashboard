package website.woodendoor.dashboard.ui.util

import website.woodendoor.dashboard.viewmodel.LogConsoleSession

typealias HighlightRange = website.woodendoor.dashboard.viewmodel.HighlightRange

object LogHighlightHelper {

    fun findHighlightRanges(text: String, query: String): List<HighlightRange> {
        return LogConsoleSession.findHighlightRanges(text, query)
    }
}
