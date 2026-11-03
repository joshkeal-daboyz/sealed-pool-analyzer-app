plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("gate") {
            id = "spa.gate"
            implementationClass = "spa.gate.GatePlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // Inside the main build the gate reads the JUnit XML itself, so a failing test is reported
    // in step order instead of stopping the build early. Run standalone, failures fail normally.
    ignoreFailures = gradle.parent != null
}
