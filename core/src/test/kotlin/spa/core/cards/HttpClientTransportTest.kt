package spa.core.cards

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the real JDK transport against a server on the loopback interface, so the request it builds
 * is checked without reaching the network.
 */
class HttpClientTransportTest {
    private lateinit var server: HttpServer
    private val received = mutableListOf<Map<String, String?>>()

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/ok") { exchange ->
            received +=
                mapOf(
                    "method" to exchange.requestMethod,
                    "User-Agent" to exchange.requestHeaders.getFirst("User-Agent"),
                    "Accept" to exchange.requestHeaders.getFirst("Accept"),
                )
            val body = """{"name":"Agadeem’s Awakening — ok"}""".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/busy") { exchange ->
            val body = "slow down".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(429, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun `sends a GET with the given headers and returns the status and UTF-8 body`() {
        val response = HttpClientTransport().get(url("/ok"), ScryfallSetFetcher.HEADERS)

        assertEquals(200, response.status)
        assertEquals("""{"name":"Agadeem’s Awakening — ok"}""", response.body)
        assertEquals(
            listOf(
                mapOf<String, String?>(
                    "method" to "GET",
                    "User-Agent" to ScryfallSetFetcher.USER_AGENT,
                    "Accept" to "application/json",
                )
            ),
            received,
        )
    }

    @Test
    fun `returns an error status instead of throwing`() {
        val response = HttpClientTransport().get(url("/busy"), emptyMap())

        assertEquals(TransportResponse(429, "slow down"), response)
    }
}
