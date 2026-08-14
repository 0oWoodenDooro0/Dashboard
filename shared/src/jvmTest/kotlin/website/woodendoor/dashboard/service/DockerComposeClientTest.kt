package website.woodendoor.dashboard.service

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.isExited
import website.woodendoor.dashboard.model.isNotFound
import website.woodendoor.dashboard.model.isPaused
import website.woodendoor.dashboard.model.isRunning

@OptIn(ExperimentalCoroutinesApi::class)
class DockerComposeClientTest {

    private class FakeProcessExecutor : ProcessExecutor {
        val recordedCommands = mutableListOf<List<String>>()
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
    fun isComposeAvailable_whenVersionSucceeds_returnsTrue() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(listOf("docker", "compose", "version"), cmd)
                ProcessResult(exitCode = 0, stdout = "Docker Compose version v2.24.5\n", stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val result = client.isComposeAvailable()

        assertTrue(result)
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun isComposeAvailable_whenCommandFails_returnsFalse() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "docker: 'compose' is not a docker command.")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val result = client.isComposeAvailable()

        assertFalse(result)
    }

    @Test
    fun isComposeAvailable_whenExceptionThrown_returnsFalse() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                throw IOException("docker: command not found")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val result = client.isComposeAvailable()

        assertFalse(result)
    }

    @Test
    fun getServiceState_runningState_parsesCorrectly() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/myproject", serviceName = "web")

        assertIs<ContainerState.Running>(state)
        assertEquals("running", state.status)
        assertTrue(state.isRunning)
    }

    @Test
    fun getServiceState_jsonArrayOutput_parsesFirstItem() = runTest {
        val jsonArrayOutput = """[{"ID":"def456","Name":"project-api-1","State":"running","ExitCode":0}]"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                ProcessResult(exitCode = 0, stdout = jsonArrayOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/api", serviceName = "api")

        assertIs<ContainerState.Running>(state)
        assertTrue(state.isRunning)
    }

    @Test
    fun getServiceState_exitedStateWithExitCode_returnsExited() = runTest {
        val jsonOutput = """{"ID":"789ghi","Name":"project-worker-1","State":"exited","ExitCode":137}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/worker", serviceName = "worker")

        assertIs<ContainerState.Exited>(state)
        assertEquals(137, state.exitCode)
        assertEquals("exited", state.status)
        assertTrue(state.isExited)
    }

    @Test
    fun getServiceState_pausedState_returnsPaused() = runTest {
        val jsonOutput = """{"ID":"111aaa","Name":"project-db-1","State":"paused","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/db", serviceName = "db")

        assertIs<ContainerState.Paused>(state)
        assertTrue(state.isPaused)
    }

    @Test
    fun getServiceState_restartingState_returnsRestarting() = runTest {
        val jsonOutput = """{"ID":"222bbb","Name":"project-cache-1","State":"restarting","ExitCode":1}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/cache", serviceName = "cache")

        assertIs<ContainerState.Restarting>(state)
    }

    @Test
    fun getServiceState_deadState_returnsDead() = runTest {
        val jsonOutput = """{"ID":"333ccc","Name":"project-dead-1","State":"dead","ExitCode":0}"""
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = jsonOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/dead", serviceName = "dead")

        assertIs<ContainerState.Dead>(state)
    }

    @Test
    fun getServiceState_nonExistentServiceOrEmptyOutput_returnsNotFound() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 0, stdout = "", stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/test", serviceName = "nonexistent")

        assertIs<ContainerState.NotFound>(state)
        assertTrue(state.isNotFound)
    }

    @Test
    fun getServiceState_commandFailure_returnsNotFound() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "no such service: foo")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(projectDir = "/apps/test", serviceName = "foo")

        assertIs<ContainerState.NotFound>(state)
        assertTrue(state.isNotFound)
    }

    @Test
    fun getServiceState_withRelativeComposeFile_resolvesRelativeToProjectDirectory() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(
            projectDir = "/apps/prod",
            serviceName = "web",
            composeFile = "docker-compose.prod.yml"
        )

        assertIs<ContainerState.Running>(state)
    }

    @Test
    fun getServiceState_withAbsoluteComposeFile_preservesAbsolutePath() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val state = client.getServiceState(
            projectDir = "/apps/prod",
            serviceName = "web",
            composeFile = "/opt/configs/compose.yml"
        )

        assertIs<ContainerState.Running>(state)
    }

    @Test
    fun listServices_success_returnsListOfServiceNames() = runTest {
        val configOutput = "backend\nfrontend\nredis\npostgres\n"
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/conflux", "config", "--services"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = configOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val services = client.listServices(projectDir = "/apps/conflux")

        assertEquals(listOf("backend", "frontend", "redis", "postgres"), services)
    }

    @Test
    fun listServices_withCustomComposeFile_resolvesRelativeToProjectDirectory() = runTest {
        val configOutput = "api\nworker\n"
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/conflux", "-f", "/apps/conflux/compose.dev.yaml", "config", "--services"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = configOutput, stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val services = client.listServices(projectDir = "/apps/conflux", composeFile = "compose.dev.yaml")

        assertEquals(listOf("api", "worker"), services)
    }

    @Test
    fun listServices_whenCommandFails_returnsEmptyList() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "no configuration file found")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val services = client.listServices(projectDir = "/invalid/path")

        assertTrue(services.isEmpty())
    }

    @Test
    fun streamLogs_emitsLogLinesSuccessfully() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val lines = client.streamLogs(projectDir = "/apps/web", serviceName = "web", tail = 50).toList()

        assertEquals(expectedLines, lines)
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun streamLogs_withCustomComposeFile_resolvesRelativeToProjectDirectory() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val lines = client.streamLogs(
            projectDir = "/apps/api",
            serviceName = "api",
            composeFile = "compose.prod.yml",
            tail = 100
        ).toList()

        assertEquals(expectedLines, lines)
    }

    @Test
    fun streamLogs_cancellation_cleansUpProcess() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)

        val collected = mutableListOf<String>()
        val job = launch {
            client.streamLogs(projectDir = "/apps/web", serviceName = "web").collect {
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
    fun startService_triesStartFirst_andSucceeds() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "start", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = "Container web Started\n", stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        client.startService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun startService_whenStartFails_fallsBackToUpDetached() = runTest {
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
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        client.startService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(2, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun startService_whenBothFail_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Compose start failed")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.startService(projectDir = "/apps/myproject", serviceName = "web")
        }
        assertTrue(exception.message?.contains("Compose start failed") == true)
    }

    @Test
    fun stopService_callsDockerComposeStop() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "stop", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = "Container web Stopped\n", stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        client.stopService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun stopService_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Failed to stop service")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.stopService(projectDir = "/apps/myproject", serviceName = "web")
        }
        assertTrue(exception.message?.contains("Failed to stop service") == true)
    }

    @Test
    fun restartService_callsDockerComposeRestart() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = { cmd ->
                assertEquals(
                    listOf("docker", "compose", "--project-directory", "/apps/myproject", "restart", "web"),
                    cmd
                )
                ProcessResult(exitCode = 0, stdout = "Container web Restarted\n", stderr = "")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        client.restartService(projectDir = "/apps/myproject", serviceName = "web")
        assertEquals(1, fakeExecutor.recordedCommands.size)
    }

    @Test
    fun restartService_whenCommandFails_throwsRuntimeException() = runTest {
        val fakeExecutor = FakeProcessExecutor().apply {
            executeHandler = {
                ProcessResult(exitCode = 1, stdout = "", stderr = "Failed to restart service")
            }
        }
        val client = CliDockerComposeClient(processExecutor = fakeExecutor)
        val exception = kotlin.test.assertFailsWith<RuntimeException> {
            client.restartService(projectDir = "/apps/myproject", serviceName = "web")
        }
        assertTrue(exception.message?.contains("Failed to restart service") == true)
    }
}

