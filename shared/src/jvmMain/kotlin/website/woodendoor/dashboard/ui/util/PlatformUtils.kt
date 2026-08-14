package website.woodendoor.dashboard.ui.util

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

object PlatformUtils {

    fun openUrlInBrowser(url: String): Boolean {
        return try {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false

            val uri = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                URI("http://$trimmed")
            } else {
                URI(trimmed)
            }

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                true
            } else {
                // Fallback via process
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("win") -> {
                        ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start()
                        true
                    }
                    os.contains("mac") -> {
                        ProcessBuilder("open", uri.toString()).start()
                        true
                    }
                    else -> {
                        ProcessBuilder("xdg-open", uri.toString()).start()
                        true
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun copyToClipboard(text: String): Boolean {
        return try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            true
        } catch (_: Exception) {
            false
        }
    }
}
