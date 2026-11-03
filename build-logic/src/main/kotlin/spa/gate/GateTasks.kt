package spa.gate

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
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

/** `ui-tests`: passes with a note until an app module exists. */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateUiTestsTask : GateStepTask() {
    @get:Internal var hasAppModule: Boolean = false

    @TaskAction
    fun evaluate() {
        if (!hasAppModule) {
            logger.lifecycle("ui-tests: $NO_APP_MODULE")
            service.get().run.pass(GateStep.UI_TESTS, NO_APP_MODULE)
            return
        }
        failIfAny(
            GateStep.UI_TESTS,
            listOf(
                GateFailure(
                    GateStep.UI_TESTS.id,
                    "app",
                    null,
                    "An app module exists but the gate has no UI or snapshot test step yet.",
                    "./gradlew gateUiTests",
                )
            ),
        )
    }

    companion object {
        const val NO_APP_MODULE = "skipped: no app module"
    }
}

/** `coverage`: Kover line coverage of each touched main file against the ticket threshold. */
@DisableCachingByDefault(because = "A gate step must evaluate on every run")
abstract class GateCoverageTask : GateStepTask() {
    @get:Internal abstract val ticket: Property<String>

    @get:Internal abstract val koverReport: RegularFileProperty

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
        val report = koverReport.get().asFile
        val coverage = if (report.isFile) Coverage.parseKoverXml(report.readText()) else null
        val sources = touched.map {
            TouchedSource(it, Coverage.reportKey(File(it).name, File(root, it).readText()))
        }
        failIfAny(GateStep.COVERAGE, Coverage.failures(sources, coverage, threshold, reproduce))
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
