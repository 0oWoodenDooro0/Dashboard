package website.woodendoor.dashboard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import website.woodendoor.dashboard.ui.util.HighlightRange
import website.woodendoor.dashboard.ui.util.LogHighlightHelper

class LogHighlightHelperTest {

    @Test
    fun `findHighlightRanges returns empty list when query is empty or blank`() {
        val text = "2026-08-14 12:00:00 [INFO] Server started on port 8080"
        assertTrue(LogHighlightHelper.findHighlightRanges(text, "").isEmpty())
        assertTrue(LogHighlightHelper.findHighlightRanges(text, "   ").isEmpty())
    }

    @Test
    fun `findHighlightRanges returns empty list when query is not found`() {
        val text = "2026-08-14 12:00:00 [INFO] Server started"
        assertTrue(LogHighlightHelper.findHighlightRanges(text, "ERROR").isEmpty())
    }

    @Test
    fun `findHighlightRanges finds single match accurately`() {
        val text = "2026-08-14 [ERROR] Connection timed out"
        val ranges = LogHighlightHelper.findHighlightRanges(text, "ERROR")
        assertEquals(1, ranges.size)
        assertEquals(HighlightRange(start = 12, end = 17), ranges[0])
    }

    @Test
    fun `findHighlightRanges performs case-insensitive matching`() {
        val text = "Error: database error occurred, check server Error log"
        val ranges = LogHighlightHelper.findHighlightRanges(text, "error")
        assertEquals(3, ranges.size)
        assertEquals(HighlightRange(start = 0, end = 5), ranges[0])
        assertEquals(HighlightRange(start = 16, end = 21), ranges[1])
        assertEquals(HighlightRange(start = 45, end = 50), ranges[2])
    }

    @Test
    fun `findHighlightRanges handles special characters without regex crash`() {
        val text = "Exception in thread \"main\" java.lang.NullPointerException (App.kt:42)"
        val ranges = LogHighlightHelper.findHighlightRanges(text, "(App.kt:42)")
        assertEquals(1, ranges.size)
        assertEquals(HighlightRange(start = 58, end = 69), ranges[0])
    }

    @Test
    fun `findHighlightRanges handles adjacent matches`() {
        val text = "aaaa"
        val ranges = LogHighlightHelper.findHighlightRanges(text, "aa")
        assertEquals(2, ranges.size)
        assertEquals(HighlightRange(start = 0, end = 2), ranges[0])
        assertEquals(HighlightRange(start = 2, end = 4), ranges[1])
    }
}
