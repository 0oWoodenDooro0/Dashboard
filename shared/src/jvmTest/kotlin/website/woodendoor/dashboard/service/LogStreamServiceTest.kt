package website.woodendoor.dashboard.service

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import website.woodendoor.dashboard.model.ContainerState
import website.woodendoor.dashboard.model.DockerContainerInfo
import website.woodendoor.dashboard.model.LogSource

@OptIn(ExperimentalCoroutinesApi::class)
class LogStreamServiceTest {

    private lateinit var tempDir: File
    private lateinit var fakeDockerClient: FakeDockerClient
    private lateinit var logStreamService: DefaultLogStreamService

    private class FakeDockerClient : DockerClient {
        var lastRequestedContainer: String? = null
        var lastRequestedTail: Int? = null
        var customLogFlow: Flow<String> = flowOf("docker line 1", "docker line 2")

        override suspend fun isDockerAvailable(): Boolean = true

        override suspend fun listContainers(all: Boolean): List<DockerContainerInfo> = emptyList()

        override suspend fun getContainerState(nameOrId: String): ContainerState =
            ContainerState.Running(status = "running")

        override fun streamLogs(nameOrId: String, tail: Int): Flow<String> {
            lastRequestedContainer = nameOrId
            lastRequestedTail = tail
            return customLogFlow
        }
    }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("log-stream-test").toFile()
        fakeDockerClient = FakeDockerClient()
        logStreamService = DefaultLogStreamService(
            dockerClient = fakeDockerClient,
            pollDelayMs = 20L
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testDockerSourceDelegation() = runTest {
        // Given Docker log source
        val dockerSource = LogSource.Docker(containerName = "web-backend")
        fakeDockerClient.customLogFlow = listOf("log: server started", "log: ready on port 8080").asFlow()

        // When streaming logs
        val result = logStreamService.streamLogs(dockerSource, tail = 50).toList()

        // Then verify DockerClient was invoked with correct arguments
        assertEquals("web-backend", fakeDockerClient.lastRequestedContainer)
        assertEquals(50, fakeDockerClient.lastRequestedTail)
        assertEquals(listOf("log: server started", "log: ready on port 8080"), result)
    }

    @Test
    fun testNoneSourceReturnsEmptyFlow() = runTest {
        // Given LogSource.None
        val source = LogSource.None

        // When streaming logs
        val result = logStreamService.streamLogs(source).toList()

        // Then empty flow is returned
        assertTrue(result.isEmpty(), "LogSource.None should emit no logs")
    }

    @Test
    fun testLocalFileStreamingInitialTail() = runTest {
        // Given a file with 10 lines
        val logFile = File(tempDir, "app.log")
        val allLines = (1..10).map { "log line $it" }
        logFile.writeText(allLines.joinToString("\n") + "\n")

        // When streaming with tail = 3
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 3)
        val result = withTimeout(3000) {
            flow.take(3).toList()
        }

        // Then verify last 3 lines are received
        val expected = listOf("log line 8", "log line 9", "log line 10")
        assertEquals(expected, result)
    }

    @Test
    fun testLocalFileStreamingWhenFileHasFewerLinesThanTail() = runTest {
        // Given a file with 3 lines
        val logFile = File(tempDir, "app.log")
        val lines = listOf("first message", "second message", "third message")
        logFile.writeText(lines.joinToString("\n") + "\n")

        // When streaming with tail = 10
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 10)
        val result = withTimeout(3000) {
            flow.take(3).toList()
        }

        // Then verify all 3 lines are received
        assertEquals(lines, result)
    }

    @Test
    fun testLocalFileStreamingZeroTailOnlyStreamsNewLines() = runTest {
        // Given a file with initial lines
        val logFile = File(tempDir, "app.log")
        logFile.writeText("initial line 1\ninitial line 2\n")

        // When streaming with tail = 0
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 0)

        val collector = async {
            withTimeout(3000) {
                flow.take(2).toList()
            }
        }

        delay(50)
        logFile.appendText("appended line 1\nappended line 2\n")

        val result = collector.await()

        // Then verify only newly appended lines were received
        assertEquals(listOf("appended line 1", "appended line 2"), result)
    }

    @Test
    fun testLocalFileStreamingDynamicAppends() = runTest {
        // Given a file with 1 line
        val logFile = File(tempDir, "app.log")
        logFile.writeText("initial-log\n")

        // When streaming with tail = 1 and appending new lines over time
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 1)

        val collector = async {
            withTimeout(4000) {
                flow.take(3).toList()
            }
        }

        delay(50)
        logFile.appendText("dynamic-1\n")
        delay(50)
        logFile.appendText("dynamic-2\n")

        val result = collector.await()

        // Then verify initial tail and appended lines are received in order
        assertEquals(listOf("initial-log", "dynamic-1", "dynamic-2"), result)
    }

    @Test
    fun testLocalFileStreamingHandlesCrlfLineEndings() = runTest {
        // Given a file with CRLF line endings
        val logFile = File(tempDir, "crlf.log")
        logFile.writeBytes("win line 1\r\nwin line 2\r\n".toByteArray(Charsets.UTF_8))

        // When streaming
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 2)
        val result = withTimeout(3000) {
            flow.take(2).toList()
        }

        // Then verify carriage returns are stripped
        assertEquals(listOf("win line 1", "win line 2"), result)
    }

    @Test
    fun testLocalFileStreamingNonExistentFileCreatedLater() = runTest {
        // Given a log file that does not yet exist
        val logFile = File(tempDir, "future.log")

        // When streaming starts before file creation
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 5)

        val collector = async {
            withTimeout(4000) {
                flow.take(2).toList()
            }
        }

        delay(60)
        // File is created later
        logFile.writeText("delayed line 1\ndelayed line 2\n")

        val result = collector.await()

        // Then verify lines are captured once file is created
        assertEquals(listOf("delayed line 1", "delayed line 2"), result)
    }

    @Test
    fun testLocalFileStreamingFileTruncationAndRotation() = runTest {
        // Given a file with initial lines
        val logFile = File(tempDir, "rotated.log")
        logFile.writeText("old line 1\nold line 2\nold line 3\n")

        // When streaming with tail = 1
        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 1)

        val collector = async {
            withTimeout(4000) {
                flow.take(3).toList()
            }
        }

        delay(50)
        // Simulate log rotation / truncation (file rewritten from beginning with shorter content)
        logFile.writeText("rotated new line 1\nrotated new line 2\n")

        val result = collector.await()

        // Then verify initial tail and newly written lines after truncation are emitted
        assertEquals(listOf("old line 3", "rotated new line 1", "rotated new line 2"), result)
    }

    @Test
    fun testGracefulCancellationAndResourceCleanup() = runTest {
        // Given an active file stream
        val logFile = File(tempDir, "cleanup.log")
        logFile.writeText("line A\nline B\nline C\n")

        val flow = logStreamService.streamLogs(LogSource.LocalFile(logFile.absolutePath), tail = 2)

        // When taking a limited count (disposes/cancels the flow)
        val result = withTimeout(3000) {
            flow.take(2).toList()
        }

        // Then verify normal termination without hang or leakage
        assertEquals(listOf("line B", "line C"), result)
    }
}
