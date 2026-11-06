package spa.gate

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Base for the gate steps that compute their own failures. They never count as up to date. */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateStepTask : DefaultTask() {
    @get:ServiceReference(GateService.NAME) abstract val service: Property<GateService>

    @get:Internal abstract val rootDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    protected val root: File
        @Internal get() = rootDir.get().asFile

    /** Records [failures] for [step] and fails the task; does nothing when there are none. */
    protected fun failIfAny(step: GateStep, failures: List<GateFailure>) {
        if (failures.isNotEmpty()) failWith(step, failures)
    }

    /** Records [failures] for [step] and fails the task. */
    protected fun failWith(step: GateStep, failures: List<GateFailure>): Nothing {
        service.get().run.fail(step, failures)
        val lines =
            failures.joinToString("\n") { "  ${it.file ?: "-"}:${it.line ?: "-"} ${it.message}" }
        throw GradleException("Gate step ${step.id} failed:\n$lines")
    }
}

/** `unit-tests`: reads the JUnit XML of every test task the gate ran. */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateUnitTestsTask : GateStepTask() {
    /**
     * Where one test task writes its XML, where its sources live, and the command that runs it
     * (`--tests <class>` is appended to rerun one class).
     */
    data class ResultSource(
        val resultsDir: File,
        val sourceRoots: List<String>,
        val command: String,
    )

    @get:Internal var sources: List<ResultSource> = emptyList()

    /** The modules whose tests this run wired, for the pass note. */
    @get:Internal var modules: List<String> = emptyList()

    @TaskAction
    fun evaluate() {
        val failures = sources.flatMap { source ->
            val reports =
                source.resultsDir
                    .listFiles { f -> f.name.startsWith("TEST-") && f.name.endsWith(".xml") }
                    .orEmpty()
                    .sortedBy { it.name }
            if (reports.isEmpty()) {
                val dir = RepoPaths.relative(root.path, source.resultsDir.path)
                listOf(
                    GateFailure(
                        GateStep.UNIT_TESTS.id,
                        null,
                        null,
                        "No test results in $dir.",
                        source.command,
                    )
                )
            } else {
                reports
                    .flatMap { JUnitResults.parse(it.readText()) }
                    .map { toGateFailure(it, source) }
            }
        }
        failIfAny(GateStep.UNIT_TESTS, failures)
        val note = GateScope.unitTestsNote(modules)
        logger.lifecycle("unit-tests: $note")
        service.get().run.pass(GateStep.UNIT_TESTS, note)
    }

    private fun toGateFailure(failure: TestFailure, source: ResultSource) =
        GateFailure(
            GateStep.UNIT_TESTS.id,
            JUnitResults.sourcePath(failure, source.sourceRoots) { File(root, it).isFile },
            failure.line,
            "${failure.testName}: ${failure.message}",
            "${source.command} --tests ${JUnitResults.testClassFilter(failure.className)}",
        )
}

/**
 * `ui-tests`: the app's JVM Compose UI tests and snapshot verification (Robolectric and Roborazzi,
 * no emulator), read from the JUnit XML and Roborazzi's results summary. It never records a
 * baseline. Without an app module, or when the change does not reach the app, it passes with a
 * note.
 */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateUiTestsTask : GateStepTask() {
    @get:Internal var hasAppModule: Boolean = false

    /** Whether this run's scope includes the app. */
    @get:Internal var appInScope: Boolean = true

    /** Where the app's test task writes its XML, its source roots, and the rerun command. */
    @get:Internal var results: GateUnitTestsTask.ResultSource? = null

    /** Roborazzi's `results-summary.json` for the verified variant. */
    @get:Internal var snapshotSummary: File? = null

    @TaskAction
    fun evaluate() {
        val source = results
        val skipped =
            if (source == null) UiTests.NO_APP_MODULE
            else UiTests.skipNote(hasAppModule, appInScope)
        if (source == null || skipped != null) {
            logger.lifecycle("ui-tests: $skipped")
            service.get().run.pass(GateStep.UI_TESTS, skipped)
            return
        }
        val reports =
            source.resultsDir
                .listFiles { f -> f.name.startsWith("TEST-") && f.name.endsWith(".xml") }
                .orEmpty()
                .sortedBy { it.name }
                .map { it.readText() }
        val summary = snapshotSummary?.takeIf { it.isFile }
        val missing = buildList {
            if (reports.isEmpty()) add("No UI test results in ${relative(source.resultsDir)}.")
            if (summary == null)
                add(
                    "No snapshot results at ${snapshotSummary?.let(::relative)}: snapshot " +
                        "verification did not run."
                )
        }
        if (missing.isNotEmpty()) {
            failWith(
                GateStep.UI_TESTS,
                missing.map { GateFailure(GateStep.UI_TESTS.id, null, null, it, source.command) },
            )
        }
        val failed = reports.flatMap { JUnitResults.parse(it) }
        failIfAny(
            GateStep.UI_TESTS,
            UiTests.failures(failed, root.path, source) { File(root, it).isFile },
        )
        val note =
            UiTests.note(
                reports.sumOf { JUnitResults.testCount(it) },
                UiTests.snapshotCount(checkNotNull(summary).readText()),
            )
        logger.lifecycle("ui-tests: $note")
        service.get().run.pass(GateStep.UI_TESTS, note)
    }

    private fun relative(file: File) = RepoPaths.relative(root.path, file.path)

    companion object {
        const val NO_APP_MODULE = UiTests.NO_APP_MODULE
    }
}

/** `coverage`: Kover line coverage of each touched main file against the ticket threshold. */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateCoverageTask : GateStepTask() {
    @get:Internal abstract val ticket: Property<String>

    @TaskAction
    fun evaluate() {
        val id = ticket.get()
        val reproduce = reproduce("gateCoverage", id)
        val threshold = threshold(id, reproduce)
        val git = Git(root)
        val touched =
            Coverage.touchedMainFiles(git.changedSince(git.mergeBase())).filter {
                File(root, it).isFile
            }
        if (touched.isEmpty()) {
            logger.lifecycle("coverage: no touched files under ${Coverage.MAIN_SOURCES}")
            return
        }
        // Each module has its own Kover report; a file is checked against its module's.
        val failures =
            touched.groupBy(Coverage::reportPath).flatMap { (reportPath, paths) ->
                val report = File(root, reportPath)
                val coverage =
                    if (report.isFile) Coverage.parseKoverXml(report.readText()) else null
                val sources = paths.map {
                    TouchedSource(it, Coverage.reportKey(File(it).name, File(root, it).readText()))
                }
                Coverage.failures(sources, coverage, threshold, reproduce)
            }
        failIfAny(GateStep.COVERAGE, failures)
    }

    private fun threshold(id: String, reproduce: String): Double {
        if (id == TicketId.NONE) return 0.0
        val path = "tickets/$id.md"
        val file = File(root, path)
        val parsed =
            if (file.isFile) runCatching { FrontMatter.coverageThreshold(file.readText()) }
            else Result.failure(IllegalStateException("Ticket file $path not found."))
        return parsed.getOrElse { problem ->
            val message = "Cannot read coverage_threshold: ${problem.message}"
            failWith(
                GateStep.COVERAGE,
                listOf(GateFailure(GateStep.COVERAGE.id, path, null, message, reproduce)),
            )
        }
    }
}

/** `dependencies`: catalog additions against the ticket, and coordinates written as literals. */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateDependenciesTask : GateStepTask() {
    @get:Internal abstract val ticket: Property<String>

    @TaskAction
    fun evaluate() {
        val id = ticket.get()
        val reproduce = reproduce("gateDependencies", id)
        val git = Git(root)
        val base = git.mergeBase()
        val ticketText = File(root, "tickets/$id.md").takeIf { it.isFile }?.readText()
        val headCatalog = File(root, DependencyAudit.CATALOG).takeIf { it.isFile }?.readText()
        val baseCatalog = base?.let { git.show(it, DependencyAudit.CATALOG) }
        val scripts =
            buildScripts().associate { RepoPaths.relative(root.path, it.path) to it.readText() }
        failIfAny(
            GateStep.DEPENDENCIES,
            DependencyAudit.catalogFailures(id, ticketText, baseCatalog, headCatalog, reproduce) +
                DependencyAudit.literalFailures(scripts, reproduce),
        )
    }

    private fun buildScripts(): List<File> =
        root
            .walkTopDown()
            .onEnter { dir -> dir == root || dir.name !in SKIPPED_DIRS }
            .filter { it.isFile && it.name.endsWith(".gradle.kts") }
            .toList()

    private companion object {
        val SKIPPED_DIRS =
            setOf("build-logic", "build", ".gradle", ".git", ".claude", ".kotlin", ".idea")
    }
}

private fun reproduce(task: String, ticket: String): String =
    if (ticket == TicketId.NONE) "./gradlew $task" else "./gradlew $task -Pticket=$ticket"
