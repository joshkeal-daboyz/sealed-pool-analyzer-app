package spa.cli

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import spa.core.cards.ScryfallSetFetcher

/**
 * SPA-054: the real [OtagSource] sends a GET with Scryfall's required headers and returns the body,
 * on a fake [HttpClient], so the test never reaches the network.
 */
class ScryfallOtagSourceTest {
    /** Answers every request with [status] and [body], recording the requests. */
    private class FakeClient(private val status: Int, private val body: String) : HttpClient() {
        val requests = mutableListOf<HttpRequest>()

        override fun <T : Any?> send(
            request: HttpRequest,
            handler: HttpResponse.BodyHandler<T>,
        ): HttpResponse<T> {
            requests += request
            @Suppress("UNCHECKED_CAST")
            return FakeResponse(request, status, ByteArrayInputStream(body.toByteArray()) as T)
        }

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            handler: HttpResponse.BodyHandler<T>,
        ): CompletableFuture<HttpResponse<T>> = throw UnsupportedOperationException()

        override fun <T : Any?> sendAsync(
            request: HttpRequest,
            handler: HttpResponse.BodyHandler<T>,
            pushPromiseHandler: HttpResponse.PushPromiseHandler<T>,
        ): CompletableFuture<HttpResponse<T>> = throw UnsupportedOperationException()

        override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()

        override fun connectTimeout(): Optional<Duration> = Optional.empty()

        override fun followRedirects(): Redirect = Redirect.NORMAL

        override fun proxy(): Optional<ProxySelector> = Optional.empty()

        override fun sslContext(): SSLContext = throw UnsupportedOperationException()

        override fun sslParameters(): SSLParameters = SSLParameters()

        override fun authenticator(): Optional<Authenticator> = Optional.empty()

        override fun version(): Version = Version.HTTP_1_1

        override fun executor(): Optional<Executor> = Optional.empty()
    }

    private class FakeResponse<T>(
        private val request: HttpRequest,
        private val status: Int,
        private val body: T,
    ) : HttpResponse<T> {
        override fun statusCode() = status

        override fun request() = request

        override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()

        override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }

        override fun body() = body

        override fun sslSession(): Optional<SSLSession> = Optional.empty()

        override fun uri(): URI = request.uri()

        override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
    }

    private val uri = URI.create(FetchTagsCommand.BULK_INDEX)

    @Test
    fun `a GET with Scryfall's headers returns the body`() {
        val client = FakeClient(200, "hello")

        val body = ScryfallOtagSource(client).get(uri).use(InputStream::readAllBytes)

        assertEquals("hello", String(body))
        val request = client.requests.single()
        assertEquals("GET", request.method())
        assertEquals(uri, request.uri())
        assertEquals(
            ScryfallSetFetcher.USER_AGENT,
            request.headers().firstValue("User-Agent").orElse(""),
        )
        assertEquals("*/*", request.headers().firstValue("Accept").orElse(""))
    }

    @Test
    fun `the default client is made without sending anything`() {
        assertEquals(ScryfallOtagSource::class, ScryfallOtagSource()::class)
    }

    @Test
    fun `a status other than 200 is an IOException naming it`() {
        val error =
            assertFailsWith<IOException> { ScryfallOtagSource(FakeClient(404, "gone")).get(uri) }

        assertEquals("HTTP 404 from $uri", error.message)
    }
}
