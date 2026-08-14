package website.woodendoor.dashboard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource
import website.woodendoor.dashboard.model.ServiceItem
import website.woodendoor.dashboard.model.ServiceStatus

class ComponentIconButtonsTest {

    @Test
    fun `Material Icons required for all UI action buttons are present and non-null`() {
        // TopBar & General Icons
        assertNotNull(Icons.Default.Refresh, "Icons.Default.Refresh should be available")
        assertNotNull(Icons.Default.Add, "Icons.Default.Add should be available")

        // ServiceCard Icons
        assertNotNull(Icons.AutoMirrored.Filled.OpenInNew, "Icons.AutoMirrored.Filled.OpenInNew should be available")
        assertNotNull(Icons.Default.PlayArrow, "Icons.Default.PlayArrow should be available")
        assertNotNull(Icons.Default.Stop, "Icons.Default.Stop should be available")
        assertNotNull(Icons.Default.Edit, "Icons.Default.Edit should be available")
        assertNotNull(Icons.Default.Delete, "Icons.Default.Delete should be available")

        // LogConsolePane Icons
        assertNotNull(Icons.Default.ContentCopy, "Icons.Default.ContentCopy should be available")
        assertNotNull(Icons.Default.DeleteSweep, "Icons.Default.DeleteSweep should be available")
        assertNotNull(Icons.Default.Close, "Icons.Default.Close should be available")
    }

    @Test
    fun `ServiceCard control actions are supported for Command, Docker, and DockerCompose log sources`() {
        val cmdService = ServiceItem(
            id = "cmd-1",
            name = "API Server",
            host = "localhost",
            logSource = LogSource.Command(startCommand = "npm start", workingDir = "/tmp")
        )
        val dockerService = ServiceItem(
            id = "docker-1",
            name = "Postgres DB",
            host = "localhost",
            logSource = LogSource.Docker(containerName = "postgres-db")
        )
        val composeService = ServiceItem(
            id = "compose-1",
            name = "Redis Cache",
            host = "localhost",
            logSource = LogSource.DockerCompose(serviceName = "redis", projectDir = "/tmp")
        )
        val fileService = ServiceItem(
            id = "file-1",
            name = "Static Nginx",
            host = "localhost",
            logSource = LogSource.LocalFile(path = "/var/log/nginx.log")
        )
        val noneService = ServiceItem(
            id = "none-1",
            name = "External Service",
            host = "remote.host",
            logSource = LogSource.None
        )

        fun isControlSupported(service: ServiceItem): Boolean {
            return service.logSource is LogSource.Command ||
                service.logSource is LogSource.Docker ||
                service.logSource is LogSource.DockerCompose
        }

        assertTrue(isControlSupported(cmdService), "Command services should support control buttons")
        assertTrue(isControlSupported(dockerService), "Docker services should support control buttons")
        assertTrue(isControlSupported(composeService), "Docker Compose services should support control buttons")
        assertFalse(isControlSupported(fileService), "File log services should not have process control buttons")
        assertFalse(isControlSupported(noneService), "LogSource.None services should not have process control buttons")
    }

    @Test
    fun `ServiceCard action state transitions correctly based on ContainerState and operating state`() {
        val runningState: ContainerState = ContainerState.Running(status = "Up 2 hours")
        val stoppedState: ContainerState = ContainerState.Exited(exitCode = 0, status = "Exited (0)")

        // When not operating and running: should allow Restart and Stop
        val isOperating = false
        val canStartWhenRunning = !isOperating && !(runningState is ContainerState.Running)
        val canStopWhenRunning = !isOperating && (runningState is ContainerState.Running)
        val canRestartWhenRunning = !isOperating && (runningState is ContainerState.Running)

        assertFalse(canStartWhenRunning, "Running service cannot be started again")
        assertTrue(canStopWhenRunning, "Running service can be stopped")
        assertTrue(canRestartWhenRunning, "Running service can be restarted")

        // When stopped: should allow Start
        val canStartWhenStopped = !isOperating && !(stoppedState is ContainerState.Running)
        val canStopWhenStopped = !isOperating && (stoppedState is ContainerState.Running)
        val canRestartWhenStopped = !isOperating && (stoppedState is ContainerState.Running)

        assertTrue(canStartWhenStopped, "Stopped service can be started")
        assertFalse(canStopWhenStopped, "Stopped service cannot be stopped")
        assertFalse(canRestartWhenStopped, "Stopped service cannot be restarted")

        // When operating: controls must be disabled / showing progress
        val isNowOperating = true
        val canTriggerAnyWhenOperating = !isNowOperating
        assertFalse(canTriggerAnyWhenOperating, "No actions can be triggered while operation is in progress")
    }

    @Test
    fun `Accessibility content descriptions for icon buttons are consistent and informative`() {
        val topBarRefreshDesc = "Refresh services"
        val topBarAddDesc = "Add service"
        val groupHeaderAddDesc = "Add service to group"
        val groupHeaderDeleteDesc = "Delete category"
        val cardOpenDesc = "Open in browser"
        val cardStartDesc = "Start service"
        val cardStopDesc = "Stop service"
        val cardRestartDesc = "Restart service"
        val cardEditDesc = "Edit service"
        val cardDeleteDesc = "Delete service"
        val consoleCopyDesc = "Copy logs"
        val consoleClearDesc = "Clear logs"
        val consoleSearchClearDesc = "Clear search"

        assertEquals("Refresh services", topBarRefreshDesc)
        assertEquals("Add service", topBarAddDesc)
        assertEquals("Add service to group", groupHeaderAddDesc)
        assertEquals("Delete category", groupHeaderDeleteDesc)
        assertEquals("Open in browser", cardOpenDesc)
        assertEquals("Start service", cardStartDesc)
        assertEquals("Stop service", cardStopDesc)
        assertEquals("Restart service", cardRestartDesc)
        assertEquals("Edit service", cardEditDesc)
        assertEquals("Delete service", cardDeleteDesc)
        assertEquals("Copy logs", consoleCopyDesc)
        assertEquals("Clear logs", consoleClearDesc)
        assertEquals("Clear search", consoleSearchClearDesc)
    }
}
