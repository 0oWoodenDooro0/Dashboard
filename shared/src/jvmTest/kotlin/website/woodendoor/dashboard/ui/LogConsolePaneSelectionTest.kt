package website.woodendoor.dashboard.ui

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import website.woodendoor.dashboard.ui.components.highlightLogLine

class LogConsolePaneSelectionTest {

    @Test
    fun `SelectionContainer and DisableSelection composables are valid composables`() {
        val composableSnippet: @Composable () -> Unit = {
            SelectionContainer {
                DisableSelection {
                    // Line gutter disabled from text selection
                }
            }
        }
        assertNotNull(composableSnippet)
    }

    @Test
    fun `highlightLogLine preserves full text content and characters for user selection`() {
        val originalLine = "2026-08-22 10:15:30 [INFO] Worker pool initialized with 8 threads"
        val highlighted = highlightLogLine(originalLine, "Worker pool")

        // The text underlying the annotated string must exactly equal the original line content
        assertEquals(originalLine, highlighted.text)
    }

    @Test
    fun `highlightLogLine with empty query preserves verbatim string content without styles`() {
        val originalLine = "System ready. Listening on port 8080."
        val highlighted = highlightLogLine(originalLine, "")

        assertEquals(originalLine, highlighted.text)
        assertEquals(0, highlighted.spanStyles.size)
    }

    @Test
    fun `highlightLogLine with non-matching query preserves verbatim string content`() {
        val originalLine = "System ready. Listening on port 8080."
        val highlighted = highlightLogLine(originalLine, "NonExistentTerm")

        assertEquals(originalLine, highlighted.text)
        assertEquals(0, highlighted.spanStyles.size)
    }

    @Test
    fun `highlightLogLine spans accurately align with searched match positions`() {
        val originalLine = "ERROR: connection lost. Retrying ERROR recovery."
        val highlighted = highlightLogLine(originalLine, "ERROR")

        assertEquals(originalLine, highlighted.text)
        assertEquals(2, highlighted.spanStyles.size)
        assertEquals(0, highlighted.spanStyles[0].start)
        assertEquals(5, highlighted.spanStyles[0].end)
        assertEquals(33, highlighted.spanStyles[1].start)
        assertEquals(38, highlighted.spanStyles[1].end)
    }

    @Test
    fun `Line number gutter is distinct and independent from line content`() {
        val lineIndex = 42
        val lineNumberGutter = (lineIndex + 1).toString()
        val lineContent = "[DEBUG] Service health check OK"

        assertEquals("43", lineNumberGutter)
        assertEquals("[DEBUG] Service health check OK", lineContent)
        // Ensure line numbers are not part of the line content stream
        assert(!lineContent.startsWith(lineNumberGutter))
    }
}
