plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.json.schema.validator)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val scryfallFixtures = rootProject.layout.projectDirectory.dir("fixtures/scryfall")
val briefFixtures = rootProject.layout.projectDirectory.dir("fixtures/briefs")

tasks.test {
    useJUnitPlatform()
    // Tests read the committed Scryfall caches; they never reach the network.
    inputs.dir(scryfallFixtures).withPropertyName("scryfallFixtures")
    systemProperty("spa.scryfall.fixtures", scryfallFixtures.asFile.absolutePath)
    // Tests load the committed example briefs, syn.json among them.
    inputs.dir(briefFixtures).withPropertyName("briefFixtures")
    systemProperty("spa.brief.fixtures", briefFixtures.asFile.absolutePath)
}

// ./gradlew :core:fetchSet -Pset=<code> [-PdataDir=<dir>] refreshes one set's cache from Scryfall.
tasks.register<JavaExec>("fetchSet") {
    group = "scryfall"
    description =
        "Fetches one set from Scryfall into <dataDir>/<code>/ (default fixtures/scryfall)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("spa.core.cards.FetchSetMainKt")
    val setCode = providers.gradleProperty("set").orElse("")
    val dataDir =
        providers
            .gradleProperty("dataDir")
            .map { rootProject.file(it).absolutePath }
            .orElse(scryfallFixtures.asFile.absolutePath)
    argumentProviders.add(CommandLineArgumentProvider { listOf(setCode.get(), dataDir.get()) })
}

ktfmt { kotlinLangStyle() }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
