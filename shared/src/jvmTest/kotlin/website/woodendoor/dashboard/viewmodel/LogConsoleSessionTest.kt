package website.woodendoor.dashboard.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogConsoleSessionTest {

    @Test
    fun `initial state defaults are empty and unconstrained`() {
        val session = LogConsoleSession()

        assertTrue(session.logs.isEmpty())
        assertEquals("", session.searchQuery)
        assertEquals(LogConsoleSession.DEFAULT_MAX_CAPACITY, session.maxCapacity)
        assertEquals(0, session.totalCount)
        assertEquals(0, session.filteredCount)
        assertFalse(session.isFiltered)
        assertTrue(session.filteredLogs.isEmpty())
        assertEquals("0 lines", session.countSummary)
    }

    @Test
    fun `appendLine adds lines in FIFO order within max capacity`() {
        val session = LogConsoleSession(maxCapacity = 5)
            .appendLine("Line 1")
            .appendLine("Line 2")
            .appendLine("Line 3")

        assertEquals(3, session.totalCount)
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), session.logs)
        assertEquals(3, session.filteredCount)
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), session.filteredLogs)
        assertEquals("3 lines", session.countSummary)
    }

    @Test
    fun `appendLine enforces circular buffer FIFO capacity trimming`() {
        var session = LogConsoleSession(maxCapacity = 3)
        for (i in 1..6) {
            session = session.appendLine("Log $i")
        }

        assertEquals(3, session.totalCount)
        assertEquals(listOf("Log 4", "Log 5", "Log 6"), session.logs)
        assertEquals(3, session.filteredCount)
    }

    @Test
    fun `appendLines handles bulk appending within capacity`() {
        val session = LogConsoleSession(maxCapacity = 5)
            .appendLines(listOf("Alpha", "Beta", "Gamma"))

        assertEquals(3, session.totalCount)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), session.logs)
    }

    @Test
    fun `appendLines handles bulk appending exceeding capacity`() {
        val session = LogConsoleSession(maxCapacity = 3)
            .appendLines(listOf("Line 1", "Line 2", "Line 3", "Line 4", "Line 5"))

        assertEquals(3, session.totalCount)
        assertEquals(listOf("Line 3", "Line 4", "Line 5"), session.logs)
    }

    @Test
    fun `search query filtering is case-insensitive`() {
        val session = LogConsoleSession()
            .appendLine("INFO: System initialized")
            .appendLine("ERROR: Connection refused at port 8080")
            .appendLine("WARN: High memory consumption detected")
            .appendLine("error: Disk write retry failed")

        val filteredSession = session.withSearchQuery("error")

        assertTrue(filteredSession.isFiltered)
        assertEquals("error", filteredSession.searchQuery)
        assertEquals(4, filteredSession.totalCount)
        assertEquals(2, filteredSession.filteredCount)
        assertEquals(
            listOf(
                "ERROR: Connection refused at port 8080",
                "error: Disk write retry failed"
            ),
            filteredSession.filteredLogs
        )
        assertEquals("2 / 4 lines", filteredSession.countSummary)
    }

    @Test
    fun `search query filtering with blank or empty query returns all logs`() {
        val session = LogConsoleSession()
            .appendLine("Line 1")
            .appendLine("Line 2")

        val emptyQuerySession = session.withSearchQuery("")
        assertFalse(emptyQuerySession.isFiltered)
        assertEquals(session.logs, emptyQuerySession.filteredLogs)
        assertEquals("2 lines", emptyQuerySession.countSummary)

        val blankQuerySession = session.withSearchQuery("   ")
        // Blank search query returns full list without filtering
        assertEquals(session.logs, blankQuerySession.filteredLogs)
    }

    @Test
    fun `cleared resets logs while preserving query and capacity`() {
        val session = LogConsoleSession(maxCapacity = 50)
            .appendLine("Message 1")
            .appendLine("Message 2")
            .withSearchQuery("msg")

        val clearedSession = session.cleared()

        assertTrue(clearedSession.logs.isEmpty())
        assertEquals(0, clearedSession.totalCount)
        assertEquals("msg", clearedSession.searchQuery)
        assertEquals(50, clearedSession.maxCapacity)
    }

    @Test
    fun `withCapacity updates capacity and trims logs if exceeding new limit`() {
        val session = LogConsoleSession(maxCapacity = 10)
            .appendLines((1..8).map { "Line $it" })

        assertEquals(8, session.totalCount)

        val shrunkSession = session.withCapacity(4)
        assertEquals(4, shrunkSession.maxCapacity)
        assertEquals(4, shrunkSession.totalCount)
        assertEquals(listOf("Line 5", "Line 6", "Line 7", "Line 8"), shrunkSession.logs)

        val expandedSession = shrunkSession.withCapacity(20)
        assertEquals(20, expandedSession.maxCapacity)
        assertEquals(4, expandedSession.totalCount)
        assertEquals(listOf("Line 5", "Line 6", "Line 7", "Line 8"), expandedSession.logs)
    }

    @Test
    fun `withCapacity requires strictly positive capacity`() {
        val session = LogConsoleSession()
        assertFailsWith<IllegalArgumentException> {
            session.withCapacity(0)
        }
        assertFailsWith<IllegalArgumentException> {
            session.withCapacity(-5)
        }
    }

    @Test
    fun `findHighlightRanges returns accurate character intervals`() {
        val text = "ERROR [2026-08-16] Service failed with ERROR_CODE_500"

        // Empty query
        assertTrue(LogConsoleSession.findHighlightRanges(text, "").isEmpty())
        assertTrue(LogConsoleSession.findHighlightRanges(text, "   ").isEmpty())
        assertTrue(LogConsoleSession.findHighlightRanges("", "ERROR").isEmpty())

        // Non-matching query
        assertTrue(LogConsoleSession.findHighlightRanges(text, "SUCCESS").isEmpty())

        // Single & multiple matches with case insensitivity
        val ranges = LogConsoleSession.findHighlightRanges(text, "error")
        assertEquals(2, ranges.size)
        assertEquals(HighlightRange(0, 5), ranges[0])
        assertEquals("ERROR", text.substring(ranges[0].start, ranges[0].end))
        assertEquals(HighlightRange(39, 44), ranges[1])
        assertEquals("ERROR", text.substring(ranges[1].start, ranges[1].end))

        // Instance method using session's active query
        val session = LogConsoleSession().withSearchQuery("failed")
        val instanceRanges = session.findHighlightRanges(text)
        assertEquals(1, instanceRanges.size)
        assertEquals(HighlightRange(27, 33), instanceRanges[0])
        assertEquals("failed", text.substring(instanceRanges[0].start, instanceRanges[0].end))
    }

    @Test
    fun `high throughput log appending benchmark maintains bounded capacity`() {
        var session = LogConsoleSession(maxCapacity = 1000)
        val lineCount = 20_000

        for (i in 1..lineCount) {
            session = session.appendLine("Log line #$i - Processing request")
        }

        assertEquals(1000, session.totalCount)
        assertEquals(1000, session.logs.size)
        assertEquals("Log line #19001 - Processing request", session.logs.first())
        assertEquals("Log line #20000 - Processing request", session.logs.last())
    }
}
