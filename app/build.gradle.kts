plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "spa.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "spa.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // Android's java.nio.file.Files has no readString or writeString at any API level; core
        // calls them, so desugaring rewrites those calls inside the APK, core's classes included.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources; Roborazzi needs real pixels.
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Import tests compare the app's results with the committed pools and goldens.
                val fixtures = rootProject.file("fixtures")
                it.inputs.dir(fixtures).withPropertyName("fixtures")
                it.systemProperty("spa.fixtures", fixtures.absolutePath)
                // Robolectric reaches into FileDescriptor internals, which Java 21 keeps closed.
                it.jvmArgs(
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
            }
        }
    }

    lint { abortOnError = true }
}

kotlin { jvmToolchain(21) }

/**
 * Copies the bundled set data from `fixtures/` into generated assets under `setdata/`, keeping each
 * file's path below `fixtures/`. Fails, naming the file, when any of them is missing.
 */
abstract class CopySetData : DefaultTask() {
    /** The repository's `fixtures/` folder. */
    @get:Internal abstract val fixtures: DirectoryProperty

    /** The files to bundle, relative to [fixtures]. */
    @get:Input abstract val paths: ListProperty<String>

    /** The same files, for up-to-date checks. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sources: FileCollection
        get() = fixtures.get().files(paths.get())

    /** The generated assets folder. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = fixtures.get().asFile
        val missing = paths.get().filterNot { root.resolve(it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Set data missing: " + missing.joinToString { "fixtures/$it" })
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        paths.get().forEach { path ->
            root.resolve(path).copyTo(out.resolve("setdata").resolve(path), overwrite = true)
        }
    }
}

val bundledSets = listOf("trk", "sds")
val scryfallFiles = listOf("cards.json", "companions.json", "set.json", "fetched-at.txt")

val copySetData =
    tasks.register<CopySetData>("copySetData") {
        fixtures.set(rootProject.layout.projectDirectory.dir("fixtures"))
        paths.set(
            bundledSets.flatMap { set -> scryfallFiles.map { "scryfall/$set/$it" } } +
                listOf("briefs/trk.json", "baselines/trk.json")
        )
        outputDir.set(layout.buildDirectory.dir("generated/setdata"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copySetData, CopySetData::outputDir)
        // The on-device test reads its pool text from the test APK's assets.
        variant.androidTest
            ?.sources
            ?.assets
            ?.addStaticSourceDirectory(rootProject.file("fixtures/pools").absolutePath)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

ktfmt { kotlinLangStyle() }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // The app's build script is linted too, like its Kotlin sources.
    source.setFrom("src/main/kotlin", "src/test/kotlin", "build.gradle.kts")
}
