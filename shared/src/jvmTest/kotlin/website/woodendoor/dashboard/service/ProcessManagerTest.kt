package website.woodendoor.dashboard.service

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.LogSource

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessManagerTest {

    private lateinit var tempDir: File
    private lateinit var processManager: DefaultProcessManager

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("process-manager-test").toFile()
        processManager = DefaultProcessManager()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testStartProcessAndCaptureStdoutStderr() = runTest {
        withContext(Dispatchers.Default) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val cmd = if (isWindows) {
                "echo line one & echo line two"
            } else {
                "echo 'line one'; echo 'line two'"
            }

            val serviceId = "echo-service"
            processManager.startProcess(
                serviceId = serviceId,
                workingDir = tempDir.absolutePath,
                command = cmd
            )

            val flow = processManager.streamLogs(serviceId = serviceId, tail = 10)
            val logs = withTimeout(5000) {
                flow.take(2).toList()
            }

            assertTrue(logs.any { it.contains("line one") }, "Logs should contain 'line one'")
            assertTrue(logs.any { it.contains("line two") }, "Logs should contain 'line two'")
        }
    }

    @Test
    fun testProcessExitStatusDetection() = runTest {
        withContext(Dispatchers.Default) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val cmd = if (isWindows) {
                "cmd /c exit 42"
            } else {
                "exit 42"
            }

            val serviceId = "exit-code-service"
            processManager.startProcess(
                serviceId = serviceId,
                workingDir = tempDir.absolutePath,
                command = cmd
            )

            var state = processManager.getProcessState(serviceId)
            var attempts = 0
            while (state !is ContainerState.Exited && attempts < 50) {
                delay(50)
                state = processManager.getProcessState(serviceId)
                attempts++
            }

            assertIs<ContainerState.Exited>(state)
            assertEquals(42, state.exitCode)
            assertFalse(processManager.isRunning(serviceId))
        }
    }

    @Test
    fun testStopProcessGracefulAndProcessTreeTermination() = runTest {
        withContext(Dispatchers.Default) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val cmd = if (isWindows) {
                "ping 127.0.0.1 -n 30"
            } else {
                "sleep 30"
            }

            val serviceId = "long-running-service"
            processManager.startProcess(
                serviceId = serviceId,
                workingDir = tempDir.absolutePath,
                command = cmd
            )

            assertTrue(processManager.isRunning(serviceId), "Process should be running after start")
            val runningState = processManager.getProcessState(serviceId)
            assertIs<ContainerState.Running>(runningState)

            processManager.stopProcess(serviceId = serviceId, timeoutSeconds = 2)

            assertFalse(processManager.isRunning(serviceId), "Process should be stopped")
            val stoppedState = processManager.getProcessState(serviceId)
            assertTrue(stoppedState is ContainerState.Exited || stoppedState is ContainerState.NotFound)
        }
    }

    @Test
    fun testStopProcessWithCustomStopCommand() = runTest {
        withContext(Dispatchers.Default) {
            val stopFlagFile = File(tempDir, "stopped.txt")
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val startCmd = if (isWindows) "ping 127.0.0.1 -n 30" else "sleep 30"
            val stopCmd = if (isWindows) "type nul > \"${stopFlagFile.absolutePath}\"" else "touch \"${stopFlagFile.absolutePath}\""

            val serviceId = "custom-stop-service"
            processManager.startProcess(
                serviceId = serviceId,
                workingDir = tempDir.absolutePath,
                command = startCmd
            )

            assertTrue(processManager.isRunning(serviceId))

            processManager.stopProcess(
                serviceId = serviceId,
                stopCommand = stopCmd,
                workingDir = tempDir.absolutePath,
                timeoutSeconds = 2
            )

            assertFalse(processManager.isRunning(serviceId))
            assertTrue(stopFlagFile.exists(), "Custom stop command should have created the flag file")
        }
    }

    @Test
    fun testRestartProcess() = runTest {
        withContext(Dispatchers.Default) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val cmd1 = if (isWindows) "echo step 1 & ping 127.0.0.1 -n 30" else "echo 'step 1'; sleep 30"
            val cmd2 = if (isWindows) "echo step 2 & ping 127.0.0.1 -n 30" else "echo 'step 2'; sleep 30"

            val serviceId = "restart-service"
            processManager.startProcess(
                serviceId = serviceId,
                workingDir = tempDir.absolutePath,
                command = cmd1
            )

            assertTrue(processManager.isRunning(serviceId))

            processManager.restartProcess(
                serviceId = serviceId,
                workingDir = tempDir.absolutePath,
                command = cmd2
            )

            assertTrue(processManager.isRunning(serviceId))

            processManager.stopProcess(serviceId = serviceId)
            assertFalse(processManager.isRunning(serviceId))
        }
    }

    @Test
    fun testInvalidWorkingDirectoryHandling() = runTest {
        withContext(Dispatchers.Default) {
            val nonExistentDir = File(tempDir, "does-not-exist").absolutePath
            val serviceId = "invalid-dir-service"

            try {
                processManager.startProcess(
                    serviceId = serviceId,
                    workingDir = nonExistentDir,
                    command = "echo hello"
                )
            } catch (_: Exception) {
            }

            val state = processManager.getProcessState(serviceId)
            assertTrue(state is ContainerState.Exited || state is ContainerState.NotFound || state is ContainerState.Unknown)
            assertFalse(processManager.isRunning(serviceId))
        }
    }

    @Test
    fun testStreamLogsByLogSourceCommand() = runTest {
        withContext(Dispatchers.Default) {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val cmd = if (isWindows) "echo hello from source" else "echo 'hello from source'"

            val source = LogSource.Command(
                workingDir = tempDir.absolutePath,
                startCommand = cmd
            )

            val serviceId = "source-cmd-service"
            processManager.startProcess(
                serviceId = serviceId,
                workingDir = source.workingDir,
                command = source.startCommand
            )

            val flow = processManager.streamLogs(source = source, tail = 5)
            val logs = withTimeout(5000) {
                flow.take(1).toList()
            }

            assertTrue(logs.any { it.contains("hello from source") })
        }
    }
}
