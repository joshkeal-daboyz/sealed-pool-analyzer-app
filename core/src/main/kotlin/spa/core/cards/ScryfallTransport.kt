package spa.core.cards

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** An HTTP response reduced to what the fetcher reads. */
data class TransportResponse(val status: Int, val body: String)

/**
 * Sends one GET request. The fetcher owns headers, pacing, and retries, so an implementation only
 * moves bytes; tests replace it with a recording fake.
 */
fun interface ScryfallTransport {
    fun get(url: String, headers: Map<String, String>): TransportResponse
}

/** The one real transport, on the JDK's [HttpClient]. */
class HttpClientTransport(
    private val client: HttpClient = defaultClient(),
    private val requestTimeout: Duration = Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS),
) : ScryfallTransport {

    override fun get(url: String, headers: Map<String, String>): TransportResponse {
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout).GET()
        headers.forEach { (name, value) -> request.header(name, value) }
        val response =
            client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return TransportResponse(response.statusCode(), response.body())
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val REQUEST_TIMEOUT_SECONDS = 60L

        fun defaultClient(): HttpClient =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
    }
}
