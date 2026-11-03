package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class RepoPathsTest {
    @Test
    fun `makes Windows and Unix paths repository-relative`() {
        assertEquals("core/src/A.kt", RepoPaths.relative("D:\\repo", "D:\\repo\\core\\src\\A.kt"))
        assertEquals("core/src/A.kt", RepoPaths.relative("D:\\repo", "d:/repo/core/src/A.kt"))
        assertEquals(
            "core/src/A.kt",
            RepoPaths.relative("/home/ci/repo/", "/home/ci/repo/core/src/A.kt"),
        )
    }

    @Test
    fun `leaves paths outside the repository absolute`() {
        assertEquals("/elsewhere/A.kt", RepoPaths.relative("/home/ci/repo", "/elsewhere/A.kt"))
        assertEquals(
            "/home/ci/repository/A.kt",
            RepoPaths.relative("/home/ci/repo", "/home/ci/repository/A.kt"),
        )
    }

    @Test
    fun `reads file URIs`() {
        assertEquals("D:/repo/A.kt", RepoPaths.fromFileUri("file:///D:/repo/A.kt"))
        assertEquals("/home/ci/a b.kt", RepoPaths.fromFileUri("file:///home/ci/a%20b.kt"))
    }
}
