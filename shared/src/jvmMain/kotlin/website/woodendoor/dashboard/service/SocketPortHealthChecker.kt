package website.woodendoor.dashboard.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import website.woodendoor.dashboard.model.PortHealth
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SocketPortHealthChecker(
    private val defaultTimeoutMs: Long = 1000,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PortHealthChecker {

    override suspend fun checkPort(host: String, port: Int, timeoutMs: Long): PortHealth =
        withContext(ioDispatcher) {
            val startTime = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                }
                val durationNanos = System.nanoTime() - startTime
                val latencyMs = (durationNanos / 1_000_000).coerceAtLeast(0)
                PortHealth.Open(latencyMs = latencyMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConnectException) {
                PortHealth.Closed(reason = e.message ?: "Connection refused")
            } catch (e: SocketTimeoutException) {
                PortHealth.Unreachable(reason = e.message ?: "Connection timed out")
            } catch (e: UnknownHostException) {
                PortHealth.Unreachable(reason = e.message ?: "Unknown host: $host")
            } catch (e: NoRouteToHostException) {
                PortHealth.Unreachable(reason = e.message ?: "No route to host: $host")
            } catch (e: Exception) {
                PortHealth.Closed(reason = e.message ?: e::class.simpleName ?: "Unknown error")
            }
        }
}
