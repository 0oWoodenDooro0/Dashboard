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
    fun listContainers_emptyOutput_returnsEmptyList() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = "", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val result = client.listContainers(all = true)

        assertTrue(result.isEmpty())
        assertEquals(
            listOf("docker", "ps", "-a", "--format", "{{json .}}"),
            fakeExecutor.recordedCommands.first()
        )
    }

    @Test
    fun listContainers_whenAllFalse_doesNotIncludeAllFlag() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = "", stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        client.listContainers(all = false)

        assertEquals(
            listOf("docker", "ps", "--format", "{{json .}}"),
            fakeExecutor.recordedCommands.first()
        )
    }

    @Test
    fun listContainers_multipleContainers_parsesAllFieldsCorrectly() = runTest {
        val jsonOutput = """
            {"ID":"c1a2b3c4d5e6","Names":"web-api,web-api-alias","Image":"my-repo/web-api:v1.0","State":"running","Status":"Up 2 hours","CreatedAt":"1700000000","Ports":"0.0.0.0:8080->80/tcp, :::8080->80/tcp"}
            {"ID":"d2e3f4a5b6c7","Names":"/db-redis","Image":"redis:7-alpine","State":"exited","Status":"Exited (0) 10 minutes ago","CreatedAt":"1699990000","Ports":"6379/tcp"}
        """.trimIndent()

        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val containers = client.listContainers(all = true)

        assertEquals(2, containers.size)

        val first = containers[0]
        assertEquals("c1a2b3c4d5e6", first.id)
        assertEquals(listOf("web-api", "web-api-alias"), first.names)
        assertEquals("web-api", first.primaryName)
        assertEquals("my-repo/web-api:v1.0", first.image)
        val firstState = first.state
        assertIs<ContainerState.Running>(firstState)
        assertEquals("running", firstState.status)
        assertEquals("Up 2 hours", first.status)
        assertEquals(1700000000L, first.created)
        assertEquals(listOf("0.0.0.0:8080->80/tcp", ":::8080->80/tcp"), first.ports)

        val second = containers[1]
        assertEquals("d2e3f4a5b6c7", second.id)
        assertEquals(listOf("/db-redis"), second.names)
        assertEquals("db-redis", second.primaryName)
        assertEquals("redis:7-alpine", second.image)
        assertIs<ContainerState.Exited>(second.state)
        assertEquals("Exited (0) 10 minutes ago", second.status)
        assertEquals(1699990000L, second.created)
        assertEquals(listOf("6379/tcp"), second.ports)
    }

    @Test
    fun listContainers_differentContainerStates_mapsCorrectly() = runTest {
        val jsonOutput = """
            {"ID":"1","Names":"c-running","Image":"img1","State":"running","Status":"Up 1h","CreatedAt":"0","Ports":""}
            {"ID":"2","Names":"c-paused","Image":"img2","State":"paused","Status":"Paused","CreatedAt":"0","Ports":""}
            {"ID":"3","Names":"c-restarting","Image":"img3","State":"restarting","Status":"Restarting (1) 2s ago","CreatedAt":"0","Ports":""}
            {"ID":"4","Names":"c-exited","Image":"img4","State":"exited","Status":"Exited (137) 5m ago","CreatedAt":"0","Ports":""}
            {"ID":"5","Names":"c-dead","Image":"img5","State":"dead","Status":"Dead","CreatedAt":"0","Ports":""}
            {"ID":"6","Names":"c-unknown","Image":"img6","State":"some_new_state","Status":"Special","CreatedAt":"0","Ports":""}
        """.trimIndent()

        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val containers = client.listContainers(all = true)

        assertEquals(6, containers.size)
        assertIs<ContainerState.Running>(containers[0].state)
        assertIs<ContainerState.Paused>(containers[1].state)
        assertIs<ContainerState.Restarting>(containers[2].state)
        assertIs<ContainerState.Exited>(containers[3].state)
        assertIs<ContainerState.Dead>(containers[4].state)
        assertIs<ContainerState.Unknown>(containers[5].state)
        assertEquals("some_new_state", (containers[5].state as ContainerState.Unknown).rawStatus)
    }

    @Test
    fun listContainers_handlesMalformedJsonGracefully() = runTest {
        val mixedOutput = """
            {"ID":"c1","Names":"valid-1","Image":"img1","State":"running","Status":"Up","CreatedAt":"0","Ports":""}
            INVALID_NON_JSON_LINE
            {"ID":"c2","Names":"valid-2","Image":"img2","State":"exited","Status":"Exited","CreatedAt":"0","Ports":""}
        """.trimIndent()

        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = mixedOutput, stderr = "")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val containers = client.listContainers(all = true)

        assertEquals(2, containers.size)
        assertEquals("c1", containers[0].id)
        assertEquals("c2", containers[1].id)
    }

    @Test
    fun listContainers_whenCommandFails_returnsEmptyList() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "docker daemon not running")
            }
        }
        val client = CliDockerClient(processExecutor = fakeExecutor)

        val containers = client.listContainers(all = true)

        assertTrue(containers.isEmpty())
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
}

