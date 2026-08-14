package website.woodendoor.dashboard

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 820.dp)
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "WoodenDoor Dashboard - Service & Log Observability"
    ) {
        App()
    }
}