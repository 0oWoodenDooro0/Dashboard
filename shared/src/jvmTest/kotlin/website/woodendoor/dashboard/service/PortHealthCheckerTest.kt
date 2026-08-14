package website.woodendoor.dashboard.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import website.woodendoor.dashboard.model.PortHealth
import website.woodendoor.dashboard.model.ServiceItem
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun checkServices_multipleServicesConcurrently_returnsAllStatusesCorrectly() = runTest {
        val server1 = ServerSocket(0)
        val server2 = ServerSocket(0)
        val isRunning = AtomicBoolean(true)

        val acceptJob1 = launch(Dispatchers.IO) {
            while (isRunning.get()) {
                try {
                    val s = server1.accept()
                    s.close()
                } catch (_: Exception) {
                    break
                }
            }
        }
        val acceptJob2 = launch(Dispatchers.IO) {
            while (isRunning.get()) {
                try {
                    val s = server2.accept()
                    s.close()
                } catch (_: Exception) {
                    break
                }
            }
        }

        val tempServer3 = ServerSocket(0)
        val closedPort = tempServer3.localPort
        tempServer3.close()

        val service1 = ServiceItem(id = "srv-1", name = "Service 1", port = server1.localPort)
        val service2 = ServiceItem(id = "srv-2", name = "Service 2", port = server2.localPort)
        val service3 = ServiceItem(id = "srv-3", name = "Service 3", port = closedPort)
        val service4 = ServiceItem(id = "srv-4", name = "Service 4", port = null)

        try {
            val results = checker.checkServices(listOf(service1, service2, service3, service4))

            assertEquals(4, results.size)

            val status1 = results["srv-1"]
            assertNotNull(status1)
            assertIs<PortHealth.Open>(status1.portHealth)
            assertTrue(status1.isHealthy)

            val status2 = results["srv-2"]
            assertNotNull(status2)
            assertIs<PortHealth.Open>(status2.portHealth)
            assertTrue(status2.isHealthy)

            val status3 = results["srv-3"]
            assertNotNull(status3)
            assertIs<PortHealth.Closed>(status3.portHealth)
            assertFalse(status3.isHealthy)

            val status4 = results["srv-4"]
            assertNotNull(status4)
            assertIs<PortHealth.None>(status4.portHealth)
            assertTrue(status4.isHealthy)
        } finally {
            isRunning.set(false)
            server1.close()
            server2.close()
            acceptJob1.cancelAndJoin()
            acceptJob2.cancelAndJoin()
        }
    }

    @Test
    fun checkServices_emptyList_returnsEmptyMap() = runTest {
        val result = checker.checkServices(emptyList())
        assertTrue(result.isEmpty())
    }
}
