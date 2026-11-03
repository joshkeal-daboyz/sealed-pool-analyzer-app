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
val poolFixtures = rootProject.layout.projectDirectory.dir("fixtures/pools")
val baselineFixtures = rootProject.layout.projectDirectory.dir("fixtures/baselines")

tasks.test {
    useJUnitPlatform()
    // Tests read the committed Scryfall caches; they never reach the network.
    inputs.dir(scryfallFixtures).withPropertyName("scryfallFixtures")
    systemProperty("spa.scryfall.fixtures", scryfallFixtures.asFile.absolutePath)
    // Tests load the committed example briefs, syn.json among them.
    inputs.dir(briefFixtures).withPropertyName("briefFixtures")
    systemProperty("spa.brief.fixtures", briefFixtures.asFile.absolutePath)
    // FixturePoolsTest parses every committed pool fixture against its manifest.
    inputs.dir(poolFixtures).withPropertyName("poolFixtures")
    systemProperty("spa.pool.fixtures", poolFixtures.asFile.absolutePath)
    // The golden baseline tests rebuild every committed baseline and compare it byte for byte.
    inputs.dir(baselineFixtures).withPropertyName("baselineFixtures")
    systemProperty("spa.baseline.fixtures", baselineFixtures.asFile.absolutePath)
}

// ./gradlew :core:buildBaseline -Pset=<code> -Pseed=<seed> -Pkits=<kits> writes
// fixtures/baselines/<code>.json from the committed Scryfall cache and brief.
tasks.register<JavaExec>("buildBaseline") {
    group = "fixtures"
    description = "Samples kits for one set and writes its baseline to fixtures/baselines."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("spa.core.baseline.BuildBaselineMainKt")
    val setCode = providers.gradleProperty("set").orElse("")
    val seed = providers.gradleProperty("seed").orElse("")
    val kits = providers.gradleProperty("kits").orElse("")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                scryfallFixtures.asFile.absolutePath,
                briefFixtures.asFile.absolutePath,
                baselineFixtures.asFile.absolutePath,
                setCode.get(),
                seed.get(),
                kits.get(),
            )
        }
    )
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

// ./gradlew :core:generateFixtures rewrites fixtures/pools/ from the seeds in
// FixtureGenerator.plan,
// keeping hand-edited fixtures (manifest entries with a null seed) as they are.
tasks.register<JavaExec>("generateFixtures") {
    group = "fixtures"
    description = "Regenerates the sampled pool fixtures and manifest under fixtures/pools."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("spa.core.sim.FixtureGeneratorMainKt")
    args(
        scryfallFixtures.asFile.absolutePath,
        briefFixtures.asFile.absolutePath,
        poolFixtures.asFile.absolutePath,
    )
}

ktfmt { kotlinLangStyle() }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
