package website.woodendoor.dashboard.ui.util

data class HighlightRange(val start: Int, val end: Int)

object LogHighlightHelper {

    fun findHighlightRanges(text: String, query: String): List<HighlightRange> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || text.isEmpty()) {
            return emptyList()
        }

        val ranges = mutableListOf<HighlightRange>()
        var startIndex = 0
        val queryLength = trimmedQuery.length

        while (startIndex < text.length) {
            val index = text.indexOf(trimmedQuery, startIndex, ignoreCase = true)
            if (index == -1) break
            ranges.add(HighlightRange(start = index, end = index + queryLength))
            startIndex = index + queryLength
        }

        return ranges
    }
}
