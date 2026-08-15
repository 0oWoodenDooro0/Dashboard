package website.woodendoor.dashboard.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DockerContainerInfo
import website.woodendoor.dashboard.model.isExited
import website.woodendoor.dashboard.model.isNotFound
import website.woodendoor.dashboard.model.isPaused
import website.woodendoor.dashboard.model.isRunning
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DockerClientTest {

    private class FakeProcessExecutor : ProcessExecutor {
        var recordedCommands = mutableListOf<List<String>>()
        var executeHandler: ((List<String>) -> ProcessResult)? = null
        var streamHandler: ((List<String>) -> Flow<String>)? = null

        override suspend fun execute(command: List<String>, timeoutMs: Long): ProcessResult {
            recordedCommands.add(command)
            return executeHandler?.invoke(command)
                ?: ProcessResult(exitCode = 0, stdout = "", stderr = "")
        }

        override fun executeStreaming(command: List<String>): Flow<String> {
            recordedCommands.add(command)
            return streamHandler?.invoke(command) ?: flow { }
        }
    }

    // ==================== Docker Daemon & Standalone Container Tests ====================

    @Test
    fun isDockerAvailable_whenVersionSucceeds_returnsTrue() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(listOf("docker", "version", "--format", "{{.Server.Version}}"), cmd)
                ProcessResult(exitCode = 0, stdout = "29.6.1\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val result = client.isDockerAvailable()

        assertTrue(result)
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun isDockerAvailable_whenCommandFails_returnsFalse() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Cannot connect to the Docker daemon")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val result = client.isDockerAvailable()

        assertFalse(result)
    }

    @Test
    fun isDockerAvailable_whenCommandThrowsException_returnsFalse() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                throw IOException("docker: command not found")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val result = client.isDockerAvailable()

        assertFalse(result)
    }

    @Test
    fun getContainerState_runningState_returnsRunning() = runTest {
        val jsonInspect = """{"Status":"running","Running":true,"Paused":false,"Restarting":false,"Dead":false,"ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(listOf("docker", "inspect", "my-service", "--format", "{{json .State}}"), cmd)
                ProcessResult(exitCode = 0, stdout = jsonInspect, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("my-service")

        assertIs<ContainerState.Running>(state)
        assertEquals("running", state.status)
        assertTrue(state.isRunning)
    }

    @Test
    fun getContainerState_exitedStateWithExitCode_returnsExited() = runTest {
        val jsonInspect = """{"Status":"exited","Running":false,"Paused":false,"Restarting":false,"Dead":false,"ExitCode":137}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonInspect, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("c-exited")

        assertIs<ContainerState.Exited>(state)
        assertEquals(137, state.exitCode)
        assertEquals("exited", state.status)
        assertTrue(state.isExited)
    }

    @Test
    fun getContainerState_pausedState_returnsPaused() = runTest {
        val jsonInspect = """{"Status":"paused","Running":true,"Paused":true,"Restarting":false,"Dead":false,"ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonInspect, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("c-paused")

        assertIs<ContainerState.Paused>(state)
        assertTrue(state.isPaused)
    }

    @Test
    fun getContainerState_restartingState_returnsRestarting() = runTest {
        val jsonInspect = """{"Status":"restarting","Running":false,"Paused":false,"Restarting":true,"Dead":false,"ExitCode":1}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonInspect, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("c-restarting")

        assertIs<ContainerState.Restarting>(state)
    }

    @Test
    fun getContainerState_deadState_returnsDead() = runTest {
        val jsonInspect = """{"Status":"dead","Running":false,"Paused":false,"Restarting":false,"Dead":true,"ExitCode":1}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonInspect, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("c-dead")

        assertIs<ContainerState.Dead>(state)
    }

    @Test
    fun getContainerState_nonExistentContainer_returnsNotFound() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Error: No such container: nonexistent")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("nonexistent")

        assertIs<ContainerState.NotFound>(state)
        assertTrue(state.isNotFound)
    }

    @Test
    fun getContainerState_invalidJson_returnsUnknown() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = "malformed-json-result", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getContainerState("malformed")

        assertIs<ContainerState.Unknown>(state)
        assertEquals("malformed-json-result", state.rawStatus)
    }

    @Test
    fun streamLogs_emitsLogLinesSuccessfully() = runTest {
        val expectedLines = listOf("Line 1: starting server", "Line 2: listening on port 8080", "Line 3: ready")
        val fakeExecutor = FakeProcessExecutor().apply {
            streamHandler = { cmd ->
                assertEquals(listOf("docker", "logs", "-f", "--tail", "50", "app-container"), cmd)
                flow {
                    expectedLines.forEach { emit(it) }
                }
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val lines = client.streamLogs("app-container", tail = 50).toList()

        assertEquals(expectedLines, lines)
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun streamLogs_cancellation_cleansUpProcess() = runTest {
        val wasCleanedUp = AtomicBoolean(false)
        val fakeExecutor = FakeProcessExecutor().apply {
            streamHandler = {
                flow {
                    try {
                        emit("log message 1")
                        emit("log message 2")
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        wasCleanedUp.set(true)
                    }
                }
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val collected = mutableListOf<String>()
        val job = launch {
            client.streamLogs("app-container", tail = 100).collect {
                collected.add(it)
            }
        }

        kotlinx.coroutines.delay(50)
        assertEquals(listOf("log message 1", "log message 2"), collected)
        job.cancel()
        job.join()

        assertTrue(wasCleanedUp.get(), "Process stream should be cancelled and cleaned up")
    }

    @Test
    fun dockerContainerInfo_helperProperties_workAsExpected() {
        val infoWithSlash = DockerContainerInfo(
            id = "abc123",
            names = listOf("/my-web-app", "alias"),
            image = "nginx:alpine",
            state = ContainerState.Running(),
            status = "Up 3 days"
        )
        assertEquals("my-web-app", infoWithSlash.primaryName)

        val infoWithoutNames = DockerContainerInfo(
            id = "def456",
            names = emptyList(),
            image = "redis",
            state = ContainerState.Exited(0),
            status = "Exited"
        )
        assertEquals("def456", infoWithoutNames.primaryName)

        assertTrue(infoWithSlash.state.isRunning)
        assertFalse(infoWithSlash.state.isExited)
        assertTrue(infoWithoutNames.state.isExited)
        assertFalse(infoWithoutNames.state.isRunning)
    }

    @Test
    fun startContainer_callsDockerStartWithContainerName() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(listOf("docker", "start", "redis-db"), cmd)
                ProcessResult(exitCode = 0, stdout = "redis-db\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.startContainer("redis-db")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun startContainer_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Error response from daemon: container not found")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.startContainer("nonexistent")
        }
        assertTrue(exception.message?.contains("container not found") == true)
    }

    @Test
    fun stopContainer_callsDockerStopWithTimeout() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(listOf("docker", "stop", "redis-db"), cmd)
                ProcessResult(exitCode = 0, stdout = "redis-db\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.stopContainer("redis-db")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun stopContainer_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Error response from daemon: cannot stop container")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.stopContainer("redis-db")
        }
        assertTrue(exception.message?.contains("cannot stop container") == true)
    }

    @Test
    fun restartContainer_callsDockerRestartWithTimeout() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(listOf("docker", "restart", "redis-db"), cmd)
                ProcessResult(exitCode = 0, stdout = "redis-db\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.restartContainer("redis-db")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun restartContainer_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Error response from daemon: cannot restart container")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.restartContainer("redis-db")
        }
        assertTrue(exception.message?.contains("cannot restart container") == true)
    }

    // ==================== Docker Compose Service Tests ====================

    @Test
    fun getComposeServiceState_runningState_parsesCorrectly() = runTest {
        val jsonOutput = """{"ID":"abc123","Name":"project-web-1","State":"running","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "ps", "-a", "--format", "json", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/myproject", serviceName = "web")

        assertIs<ContainerState.Running>(state)
        assertEquals("running", state.status)
        assertTrue(state.isRunning)
    }

    @Test
    fun getComposeServiceState_jsonArrayOutput_parsesFirstItem() = runTest {
        val jsonArrayOutput = """[{"ID":"def456","Name":"project-api-1","State":"running","ExitCode":0}]"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                ProcessResult(exitCode = 0, stdout = jsonArrayOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/api", serviceName = "api")

        assertIs<ContainerState.Running>(state)
        assertTrue(state.isRunning)
    }

    @Test
    fun getComposeServiceState_exitedStateWithExitCode_returnsExited() = runTest {
        val jsonOutput = """{"ID":"789ghi","Name":"project-worker-1","State":"exited","ExitCode":137}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/worker", serviceName = "worker")

        assertIs<ContainerState.Exited>(state)
        assertEquals(137, state.exitCode)
        assertEquals("exited", state.status)
        assertTrue(state.isExited)
    }

    @Test
    fun getComposeServiceState_pausedState_returnsPaused() = runTest {
        val jsonOutput = """{"ID":"111aaa","Name":"project-db-1","State":"paused","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/db", serviceName = "db")

        assertIs<ContainerState.Paused>(state)
        assertTrue(state.isPaused)
    }

    @Test
    fun getComposeServiceState_restartingState_returnsRestarting() = runTest {
        val jsonOutput = """{"ID":"222bbb","Name":"project-cache-1","State":"restarting","ExitCode":1}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/cache", serviceName = "cache")

        assertIs<ContainerState.Restarting>(state)
    }

    @Test
    fun getComposeServiceState_deadState_returnsDead() = runTest {
        val jsonOutput = """{"ID":"333ccc","Name":"project-dead-1","State":"dead","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/dead", serviceName = "dead")

        assertIs<ContainerState.Dead>(state)
    }

    @Test
    fun getComposeServiceState_nonExistentServiceOrEmptyOutput_returnsNotFound() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = "", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/test", serviceName = "nonexistent")

        assertIs<ContainerState.NotFound>(state)
        assertTrue(state.isNotFound)
    }

    @Test
    fun getComposeServiceState_commandFailure_returnsNotFound() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "no such service: foo")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(projectDir = "/apps/test", serviceName = "foo")

        assertIs<ContainerState.NotFound>(state)
        assertTrue(state.isNotFound)
    }

    @Test
    fun getComposeServiceState_withRelativeComposeFile_resolvesRelativeToProjectDirectory() = runTest {
        val jsonOutput = """{"ID":"custom1","Name":"custom-service","State":"running","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/prod", "-f", "/apps/prod/docker-compose.prod.yml", "ps", "-a", "--format", "json", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(
            projectDir = "/apps/prod",
            serviceName = "web",
            composeFile = "docker-compose.prod.yml"
        )

        assertIs<ContainerState.Running>(state)
    }

    @Test
    fun getComposeServiceState_withAbsoluteComposeFile_preservesAbsolutePath() = runTest {
        val jsonOutput = """{"ID":"custom2","Name":"custom-service-2","State":"running","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/prod", "-f", "/opt/configs/compose.yml", "ps", "-a", "--format", "json", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val state = client.getComposeServiceState(
            projectDir = "/apps/prod",
            serviceName = "web",
            composeFile = "/opt/configs/compose.yml"
        )

        assertIs<ContainerState.Running>(state)
    }

    @Test
    fun streamComposeLogs_emitsLogLinesSuccessfully() = runTest {
        val expectedLines = listOf(
            "web_1 | [INFO] Server started on port 8000",
            "web_1 | [DEBUG] Database pool initialized",
            "web_1 | [INFO] Ready for connections"
        )
        val fakeExecutor = FakeProcessExecutor().apply {
            streamHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/web", "logs", "-f", "--tail", "50", "web"),
                    cmd
                )
                flow {
                    expectedLines.forEach { emit(it) }
                }
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val lines = client.streamComposeLogs(projectDir = "/apps/web", serviceName = "web", tail = 50).toList()

        assertEquals(expectedLines, lines)
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun streamComposeLogs_withCustomComposeFile_resolvesRelativeToProjectDirectory() = runTest {
        val expectedLines = listOf("api_1 | Starting production worker")
        val fakeExecutor = FakeProcessExecutor().apply {
            streamHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/api", "-f", "/apps/api/compose.prod.yml", "logs", "-f", "--tail", "100", "api"),
                    cmd
                )
                flow {
                    expectedLines.forEach { emit(it) }
                }
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val lines = client.streamComposeLogs(
            projectDir = "/apps/api",
            serviceName = "api",
            composeFile = "compose.prod.yml",
            tail = 100
        ).toList()

        assertEquals(expectedLines, lines)
    }

    @Test
    fun streamComposeLogs_cancellation_cleansUpProcess() = runTest {
        val wasCleanedUp = AtomicBoolean(false)
        val fakeExecutor = FakeProcessExecutor().apply {
            streamHandler = {
                flow {
                    try {
                        emit("compose log line 1")
                        emit("compose log line 2")
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        wasCleanedUp.set(true)
                    }
                }
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val collected = mutableListOf<String>()
        val job = launch {
            client.streamComposeLogs(projectDir = "/apps/web", serviceName = "web").collect {
                collected.add(it)
            }
        }

        kotlinx.coroutines.delay(50)
        assertEquals(listOf("compose log line 1", "compose log line 2"), collected)
        job.cancel()
        job.join()

        assertTrue(wasCleanedUp.get(), "Compose process stream should be cancelled and cleaned up")
    }

    @Test
    fun startComposeService_triesStartFirst_andSucceeds() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "start", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = "Container web Started\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.startComposeService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun startComposeService_whenStartFails_fallsBackToUpDetached() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                if (cmd.contains("start")) {
                    ProcessResult(exitCode = 1, stdout = "", stderr = "No such container")
                } else {
                    assertEquals(
                        listOf("docker", "compose", "--project-directory", "/apps/myproject", "up", "-d", "web"),
                        cmd
                    )
                    ProcessResult(exitCode = 0, stdout = "Container web Created and Started\n", stderr = "")
                }
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.startComposeService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(2, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun startComposeService_whenBothFail_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Compose start failed")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.startComposeService(projectDir = "/apps/myproject", serviceName = "web")
        }
        assertTrue(exception.message?.contains("Compose start failed") == true)
    }

    @Test
    fun stopComposeService_callsDockerComposeStop() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "stop", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = "Container web Stopped\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.stopComposeService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun stopComposeService_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Failed to stop service")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.stopComposeService(projectDir = "/apps/myproject", serviceName = "web")
        }
        assertTrue(exception.message?.contains("Failed to stop service") == true)
    }

    @Test
    fun restartComposeService_callsDockerComposeRestart() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "restart", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = "Container web Restarted\n", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        client.restartComposeService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun restartComposeService_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Failed to restart service")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.restartComposeService(projectDir = "/apps/myproject", serviceName = "web")
        }
        assertTrue(exception.message?.contains("Failed to restart service") == true)
    }
}
