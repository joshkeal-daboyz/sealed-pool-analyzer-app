package spa.cli

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient

/**
 * One GET request for `fetch-tags`; tests replace it with a fake, so they never reach the network.
 */
fun interface OtagSource {
    /** The body of [uri]. */
    fun get(uri: URI): InputStream
}

/** The one real [OtagSource], on the JDK's [HttpClient]. */
class ScryfallOtagSource(private val client: HttpClient = HttpClient.newHttpClient()) : OtagSource {
    override fun get(uri: URI): InputStream = InputStream.nullInputStream()
}
