package spa.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the toolchain compiles and runs a JVM test while `core/src/main` is still empty. */
class SmokeTest {
    @Test
    fun `the test runner executes`() {
        assertEquals(4, 2 + 2)
    }
}
