import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
    // Loaded here so every project shares one Kotlin plugin classloader with ktfmt and detekt.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    id("spa.gate")
}

ktfmt { kotlinLangStyle() }

// The root project formats and lints the gate's own sources in build-logic, so a problem there
// is reported by the same gate step, in the same order, as a problem in core.
val buildLogicKotlin = fileTree("build-logic") { include("src/**/*.kt", "*.gradle.kts") }

val ktfmtCheckBuildLogic =
    tasks.register<KtfmtCheckTask>("ktfmtCheckBuildLogic") { source(buildLogicKotlin) }

val ktfmtFormatBuildLogic =
    tasks.register<KtfmtFormatTask>("ktfmtFormatBuildLogic") { source(buildLogicKotlin) }

tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckBuildLogic) }

tasks.named("ktfmtFormat") { dependsOn(ktfmtFormatBuildLogic) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    source.setFrom("build-logic/src/main/kotlin", "build-logic/src/test/kotlin")
}
