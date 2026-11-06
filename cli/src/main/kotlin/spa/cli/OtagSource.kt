package spa.cli

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import spa.core.cards.ScryfallSetFetcher

/**
 * One GET request for `fetch-tags`; tests replace it with a fake, so they never reach the network.
 */
fun interface OtagSource {
    /** The body of [uri]; a failed request is an [IOException]. */
    fun get(uri: URI): InputStream
}

/**
 * The one real [OtagSource], on the JDK's [HttpClient], sending the User-Agent Scryfall asks for.
 */
class ScryfallOtagSource(
    private val client: HttpClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
) : OtagSource {
    override fun get(uri: URI): InputStream {
        val request =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(TIMEOUT_MINUTES))
                .header("User-Agent", ScryfallSetFetcher.USER_AGENT)
                .header("Accept", "*/*")
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != HTTP_OK) {
            response.body().close()
            throw IOException("HTTP ${response.statusCode()} from $uri")
        }
        return response.body()
    }

    private companion object {
        const val HTTP_OK = 200
        const val TIMEOUT_MINUTES = 5L
    }
}
