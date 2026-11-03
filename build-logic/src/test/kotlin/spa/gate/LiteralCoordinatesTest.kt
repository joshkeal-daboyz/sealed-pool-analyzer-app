package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class LiteralCoordinatesTest {
    @Test
    fun `flags library coordinates and literal plugin versions with their lines`() {
        val script =
            """
            plugins {
                id("org.example.plugin") version "1.2"
                kotlin("jvm") version "2.4.21"
                alias(libs.plugins.kover)
            }
            dependencies {
                implementation("org.example:lib:1.0")
                testImplementation("org.example:no-version")
                implementation(libs.kotlinx.serialization.json)
            }
            """
                .trimIndent()
        assertEquals(
            listOf(
                LiteralHit(2, "id(\"org.example.plugin\") version \"1.2\""),
                LiteralHit(3, "kotlin(\"jvm\") version \"2.4.21\""),
                LiteralHit(7, "org.example:lib:1.0"),
                LiteralHit(8, "org.example:no-version"),
            ),
            LiteralCoordinates.scan(script),
        )
    }

    @Test
    fun `ignores aliases, task paths, plain strings, templates, and comments`() {
        val script =
            """
            // implementation("org.example:in-a-comment:1.0")
            /* implementation("org.example:block:1.0") */
            tasks.named(":core:test")
            description = "Runs: everything"
            val url = "https://repo.example.org:8443/maven"
            implementation("${'$'}group:artifact:1.0")
            id("spa.gate")
            alias(libs.plugins.ktfmt)
            """
                .trimIndent()
        assertEquals(emptyList(), LiteralCoordinates.scan(script))
    }
}
