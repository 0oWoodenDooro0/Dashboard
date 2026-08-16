package website.woodendoor.dashboard.viewmodel

data class HighlightRange(val start: Int, val end: Int)

data class LogConsoleSession(
    val logs: List<String> = emptyList(),
    val searchQuery: String = "",
    val maxCapacity: Int = DEFAULT_MAX_CAPACITY
) {
    val totalCount: Int get() = logs.size
    val isFiltered: Boolean get() = searchQuery.isNotBlank()

    val filteredLogs: List<String>
        get() = if (searchQuery.isBlank()) {
            logs
        } else {
            logs.filter { it.contains(searchQuery, ignoreCase = true) }
        }

    val filteredCount: Int get() = filteredLogs.size

    val countSummary: String
        get() = if (isFiltered) "$filteredCount / $totalCount lines" else "$totalCount lines"

    fun appendLine(line: String): LogConsoleSession {
        val updated = logs + line
        return if (updated.size > maxCapacity) {
            copy(logs = updated.takeLast(maxCapacity))
        } else {
            copy(logs = updated)
        }
    }

    fun appendLines(newLines: Collection<String>): LogConsoleSession {
        val updated = logs + newLines
        return if (updated.size > maxCapacity) {
            copy(logs = updated.takeLast(maxCapacity))
        } else {
            copy(logs = updated)
        }
    }

    fun withSearchQuery(query: String): LogConsoleSession = copy(searchQuery = query)

    fun cleared(): LogConsoleSession = copy(logs = emptyList())

    fun withCapacity(newCapacity: Int): LogConsoleSession {
        require(newCapacity > 0) { "maxCapacity must be > 0" }
        return if (logs.size > newCapacity) {
            copy(logs = logs.takeLast(newCapacity), maxCapacity = newCapacity)
        } else {
            copy(maxCapacity = newCapacity)
        }
    }

    fun findHighlightRanges(text: String): List<HighlightRange> =
        Companion.findHighlightRanges(text, searchQuery)

    companion object {
        const val DEFAULT_MAX_CAPACITY = 1000

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
}
