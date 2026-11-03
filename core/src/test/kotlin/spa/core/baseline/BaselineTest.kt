package spa.core.baseline

import kotlin.test.Test
import kotlin.test.assertEquals

class BaselineTest {
    private val baseline =
        Baseline(
            setCode = "syn",
            briefVersion = 1,
            cacheFetchedAt = "2026-11-03T18:20:00Z",
            seed = 9,
            kits = 100,
            stats =
                linkedMapOf(
                    "/b" to Stat(median = 2.0, p10 = 1.0, p90 = 3.0, mean = 2.0),
                    "/a" to Stat(median = 0.5, p10 = 0.0, p90 = 1.0, mean = 0.4567),
                ),
        )

    @Test
    fun `encode pretty-prints with every key sorted and a final newline`() {
        val expected =
            """
            {
                "briefVersion": 1,
                "cacheFetchedAt": "2026-11-03T18:20:00Z",
                "kits": 100,
                "seed": 9,
                "setCode": "syn",
                "stats": {
                    "/a": {
                        "mean": 0.4567,
                        "median": 0.5,
                        "p10": 0.0,
                        "p90": 1.0
                    },
                    "/b": {
                        "mean": 2.0,
                        "median": 2.0,
                        "p10": 1.0,
                        "p90": 3.0
                    }
                }
            }
            """
                .trimIndent() + "\n"
        assertEquals(expected, baseline.encode())
    }

    @Test
    fun `decode reads back what encode wrote`() {
        assertEquals(baseline, Baseline.decode(baseline.encode()))
    }
}
