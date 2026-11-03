package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCatalogTest {
    private val base =
        """
        [versions]
        kotlin = "2.4.21"

        [libraries]
        json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlin" }

        [plugins]
        kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
        """
            .trimIndent()

    @Test
    fun `reads every entry form a catalog allows`() {
        val toml =
            """
            [libraries]
            a = { module = "org.example:alpha", version = "1" }   # trailing comment
            b = { group = "org.example", name = "beta", version.ref = "x" }
            c = "org.example:gamma:3.0"
            # d = "org.example:commented:1"

            [bundles]
            all = ["a", "b"]

            [plugins]
            p = { id = "org.example.plugin", version = "1" }
            q = "org.example.short:2.0"
            """
                .trimIndent()
        assertEquals(
            listOf(
                CatalogEntry("libraries", "a", "org.example:alpha", 2),
                CatalogEntry("libraries", "b", "org.example:beta", 3),
                CatalogEntry("libraries", "c", "org.example:gamma", 4),
                CatalogEntry("plugins", "p", "plugin:org.example.plugin", 11),
                CatalogEntry("plugins", "q", "plugin:org.example.short", 12),
            ),
            VersionCatalog.entries(toml),
        )
    }

    @Test
    fun `an entry it cannot read has a null coordinate`() {
        assertEquals(
            listOf(CatalogEntry("libraries", "odd", null, 2)),
            VersionCatalog.entries("[libraries]\nodd = { version = \"1\" }"),
        )
    }

    @Test
    fun `added lists only coordinates new since the base`() {
        val head =
            base +
                "\nkover = { id = \"org.jetbrains.kotlinx.kover\", version = \"0.9.11\" }" +
                "\n" +
                "\n[libraries]\nextra = \"com.example:extra:1.0\""
        assertEquals(
            listOf("plugin:org.jetbrains.kotlinx.kover", "com.example:extra"),
            VersionCatalog.added(base, head).map { it.coordinate },
        )
    }

    @Test
    fun `a version bump or alias rename is not an addition`() {
        val head = base.replace("2.4.21", "2.5.0").replace("json = ", "serialization-json = ")
        assertEquals(emptyList(), VersionCatalog.added(base, head))
    }

    @Test
    fun `with no base catalog every entry is an addition`() {
        assertEquals(2, VersionCatalog.added(null, base).size)
    }
}
