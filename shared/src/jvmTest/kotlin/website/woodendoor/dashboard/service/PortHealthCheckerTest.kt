package website.woodendoor.dashboard.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import website.woodendoor.dashboard.model.PortHealth
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PortHealthCheckerTest {

    private lateinit var checker: PortHealthChecker

    @BeforeTest
    fun setup() {
        checker = SocketPortHealthChecker()
    }

    @Test
    fun checkPort_openPort_returnsOpenWithLatency() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        val isRunning = AtomicBoolean(true)

        val acceptJob = launch(Dispatchers.IO) {
            while (isRunning.get()) {
                try {
                    val socket = server.accept()
                    socket.close()
                } catch (_: Exception) {
                    break
                }
            }
        }

        try {
            val result = checker.checkPort(host = "127.0.0.1", port = port, timeoutMs = 1000)
            assertIs<PortHealth.Open>(result)
            assertTrue(result.latencyMs >= 0, "Latency should be non-negative")
        } finally {
            isRunning.set(false)
            server.close()
            acceptJob.cancelAndJoin()
        }
    }

    @Test
    fun checkPort_closedPort_returnsClosed() = runTest {
        val tempServer = ServerSocket(0)
        val closedPort = tempServer.localPort
        tempServer.close()

        val result = checker.checkPort(host = "127.0.0.1", port = closedPort, timeoutMs = 1000)
        assertIs<PortHealth.Closed>(result)
        assertNotNull(result.reason, "Closed reason should not be null")
    }

    @Test
    fun checkPort_unknownHost_returnsUnreachable() = runTest {
        val result = checker.checkPort(
            host = "nonexistent.invalid.localdomain.test",
            port = 8080,
            timeoutMs = 500
        )
        assertIs<PortHealth.Unreachable>(result)
        assertNotNull(result.reason, "Unreachable reason should not be null")
    }

    @Test
    fun checkPort_timeout_returnsUnreachable() = runTest {
        // 192.0.2.1 is TEST-NET-1 (RFC 5737), which discards packets, causing connection timeout
        val result = checker.checkPort(
            host = "192.0.2.1",
            port = 81,
            timeoutMs = 100
        )
        assertIs<PortHealth.Unreachable>(result)
        assertNotNull(result.reason, "Timeout reason should not be null")
    }
}
