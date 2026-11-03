plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    application
}

version = "0.1.0"

kotlin { jvmToolchain(21) }

application {
    applicationName = "spa"
    mainClass.set("spa.cli.Main")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The tool's version reaches the code as a generated resource, spa/cli/version.properties, so the
// JSON output carries the version Gradle builds.
val versionResources = layout.buildDirectory.dir("generated/version-resources")
val writeVersion =
    tasks.register("writeVersion") {
        val versionText = project.version.toString()
        val outputDir = versionResources
        inputs.property("version", versionText)
        outputs.dir(outputDir)
        doLast {
            val file = outputDir.get().file("spa/cli/version.properties").asFile
            file.parentFile.mkdirs()
            file.writeText("version=$versionText\n")
        }
    }

sourceSets.main { resources.srcDir(writeVersion) }

val fixtures = rootProject.layout.projectDirectory.dir("fixtures")

tasks.test {
    useJUnitPlatform()
    // Tests run the commands in-process against the committed fixtures; they never reach the
    // network.
    inputs.dir(fixtures).withPropertyName("fixtures")
    systemProperty("spa.fixtures", fixtures.asFile.absolutePath)
}

ktfmt { kotlinLangStyle() }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
