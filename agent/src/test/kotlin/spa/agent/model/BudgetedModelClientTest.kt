package spa.agent.model

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetedModelClientTest {
    /** 4,000 characters estimate to 1,000 input tokens. */
    private fun request(maxTokens: Long = 100) =
        ModelRequest("m", maxTokens, "s".repeat(3_000), "u".repeat(1_000))

    private fun response(usage: ModelUsage) = ModelResponse("m", "ok", "end_turn", usage)

    private class Fake(val answer: (ModelRequest) -> ModelResponse) : ModelClient {
        val calls = AtomicInteger()

        override fun send(request: ModelRequest): ModelResponse {
            calls.incrementAndGet()
            return answer(request)
        }
    }

    @Test
    fun `an input reservation past the cap sends nothing and says so in one line`() {
        val inner = Fake { response(ModelUsage(1_000, 100)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 999, maxOutputTokens = 1_000)
        val e = assertFailsWith<BudgetExceededException> { client.send(request()) }
        assertEquals("budget: input cap would be passed (1000 of 999)", e.message)
        assertFalse(e is AssertionError)
        assertEquals(0, inner.calls.get())
    }

    @Test
    fun `an output reservation past the cap sends nothing and says so in one line`() {
        val inner = Fake { response(ModelUsage(1_000, 100)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 5_000, maxOutputTokens = 99)
        val e = assertFailsWith<BudgetExceededException> { client.send(request(maxTokens = 100)) }
        assertEquals("budget: output cap would be passed (100 of 99)", e.message)
        assertEquals(0, inner.calls.get())
    }

    @Test
    fun `the message counts what is already used`() {
        val inner = Fake { response(ModelUsage(1_000, 100)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_500, maxOutputTokens = 1_000)
        client.send(request())
        val e = assertFailsWith<BudgetExceededException> { client.send(request()) }
        assertEquals("budget: input cap would be passed (2000 of 1500)", e.message)
        val out = BudgetedModelClient(inner, maxInputTokens = 9_000, maxOutputTokens = 150)
        out.send(request())
        val e2 = assertFailsWith<BudgetExceededException> { out.send(request()) }
        assertEquals("budget: output cap would be passed (200 of 150)", e2.message)
    }

    @Test
    fun `a reservation exactly equal to the cap is sent`() {
        val inner = Fake { response(ModelUsage(1_000, 100)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_000, maxOutputTokens = 100)
        assertEquals("ok", client.send(request()).text)
        assertEquals(1, inner.calls.get())
    }

    @Test
    fun `a refused call keeps nothing reserved`() {
        val inner = Fake { response(ModelUsage(1, 1)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_000, maxOutputTokens = 100)
        val big = request().copy(userMessage = "u".repeat(2_000))
        assertFailsWith<BudgetExceededException> { client.send(big) }
        client.send(request())
        assertEquals(1, inner.calls.get())
    }

    @Test
    fun `an input refusal does not hold the output reservation`() {
        val inner = Fake { response(ModelUsage(1, 1)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_000, maxOutputTokens = 150)
        val big = request().copy(userMessage = "u".repeat(2_000))
        assertFailsWith<BudgetExceededException> { client.send(big) }
        assertFailsWith<BudgetExceededException> { client.send(big) }
        client.send(request()) // needs 100 of 150 output; fails if a refusal kept its 100
        assertEquals(1, inner.calls.get())
    }

    @Test
    fun `actual usage replaces the reservation`() {
        val inner = Fake { response(ModelUsage(200, 10)) }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_800, maxOutputTokens = 1_000)
        client.send(request()) // estimated 1,000, used 200
        client.send(request()) // 200 + 1,000 fits
        // 400 used; an 1,400-token call fits only because the first two settled low.
        client.send(request().copy(system = "s".repeat(4_600)))
        assertEquals(3, inner.calls.get())
    }

    @Test
    fun `input counts cache reads and cache writes in full`() {
        val inner = Fake {
            response(
                ModelUsage(100, 10, cacheReadInputTokens = 600, cacheCreationInputTokens = 300)
            )
        }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_900, maxOutputTokens = 1_000)
        client.send(request()) // settles at 1,000
        val e =
            assertFailsWith<BudgetExceededException> {
                client.send(request().copy(system = "s".repeat(3_600)))
            }
        // 1,000 settled + 1,150 estimated (4,600 characters).
        assertEquals("budget: input cap would be passed (2150 of 1900)", e.message)
    }

    @Test
    fun `totals hold the sum of actual usage with cache numbers separate`() {
        val usages = ArrayDeque(listOf(ModelUsage(100, 10, 5, 7), ModelUsage(200, 20, 50, 70)))
        val inner = Fake { response(usages.removeFirst()) }
        val client = BudgetedModelClient(inner, maxInputTokens = 10_000, maxOutputTokens = 1_000)
        assertEquals(UsageTotals(0, 0, 0, 0, 0), client.totals)
        client.send(request())
        client.send(request())
        assertEquals(
            UsageTotals(calls = 2, input = 300, output = 30, cacheRead = 55, cacheWrite = 77),
            client.totals,
        )
    }

    @Test
    fun `a failing inner call releases its reservation and counts in no total`() {
        var fail = true
        val inner = Fake {
            if (fail) error("boom")
            response(ModelUsage(1_000, 100))
        }
        val client = BudgetedModelClient(inner, maxInputTokens = 1_000, maxOutputTokens = 100)
        assertFailsWith<IllegalStateException> { client.send(request()) }
        assertEquals(UsageTotals(0, 0, 0, 0, 0), client.totals)
        fail = false
        client.send(request()) // would be refused if the failed call still held its reservation
        assertEquals(UsageTotals(1, 1_000, 100, 0, 0), client.totals)
    }

    @Test
    fun `eight threads against a cap that fits three send exactly three, every time`() {
        repeat(20) { run ->
            val inner = Fake {
                Thread.sleep(2) // hold the reservation while the other threads arrive
                response(ModelUsage(1_000, 100))
            }
            val client = BudgetedModelClient(inner, maxInputTokens = 3_000, maxOutputTokens = 300)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(8)
            try {
                val results =
                    (1..8).map {
                        pool.submit<Boolean> {
                            start.await()
                            try {
                                client.send(request())
                                true
                            } catch (_: BudgetExceededException) {
                                false
                            }
                        }
                    }
                start.countDown()
                val sent = results.count { it.get(30, TimeUnit.SECONDS) }
                assertEquals(3, sent, "run $run")
                assertEquals(3, inner.calls.get(), "run $run")
                assertEquals(3L, client.totals.calls)
                assertTrue(client.totals.input <= 3_000)
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
