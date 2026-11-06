package spa.gate

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry

/**
 * The gate, as Gradle tasks in the root build. One task per step (`gateFormat` ...
 * `gateDependencies`) runs in contract order; `gate` runs them all and `check` depends on `gate`,
 * so `./gradlew check` is the gate. Every tool task a step depends on is ordered after the previous
 * step's task, so a failing step stops the build before any later step's tools start.
 */
abstract class GatePlugin @Inject constructor(private val listeners: BuildEventsListenerRegistry) :
    Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) { "Apply spa.gate to the root project only." }
        project.pluginManager.apply("base")
        val service =
            project.gradle.sharedServices.registerIfAbsent(
                GateService.NAME,
                GateService::class.java,
            ) {
                it.parameters.rootDir.set(project.layout.projectDirectory)
            }
        listeners.onTaskCompletion(service)

        val steps = StepTasks(project)
        GateStep.entries.forEach { step ->
            service.get().register(":${step.taskName}", GateService.TaskInfo(step, project.rootDir))
        }
        val gate =
            project.tasks.register("gate") { t ->
                t.group = GROUP
                t.description = "Runs the seven gate steps in order and writes gate-verdict.json."
                t.dependsOn(steps.all)
            }
        project.tasks.named("check") { it.dependsOn(gate) }

        val ticket = ticketProvider(project)
        steps.coverage.configure { it.ticket.set(ticket) }
        steps.dependencies.configure { it.ticket.set(ticket) }
        val scope = scopeOf(project)
        project.logger.lifecycle(describe(scope))
        excludeOutOfScopeChecks(project, scope)
        val app = project.findProject(APP)
        steps.uiTests.configure { task ->
            task.hasAppModule = app != null
            task.appInScope = scope.runsApp
            if (app != null) {
                task.results =
                    GateUnitTestsTask.ResultSource(
                        File(app.projectDir, APP_TEST_RESULTS),
                        TEST_SOURCE_DIRS.map {
                            RepoPaths.relative(project.rootDir.path, File(app.projectDir, it).path)
                        },
                        UiTests.reproduceCommand(app.path),
                    )
                task.snapshotSummary = File(app.projectDir, APP_SNAPSHOT_SUMMARY)
            }
        }

        project.allprojects { wireProject(it, steps, service, inScope(it, scope)) }
        val buildLogic =
            project.gradle.includedBuilds
                .firstOrNull { it.name == BUILD_LOGIC }
                .takeIf { scope.full }
        if (buildLogic != null) steps.unitTests.configure { it.dependsOn(buildLogic.task(":test")) }

        project.gradle.taskGraph.whenReady { graph ->
            val unitTests = steps.unitTests.get()
            if (graph.hasTask(unitTests)) {
                // The unit-tests step reads the XML reports, so failing tests are reported as a
                // step
                // failure with file and line instead of stopping the build inside the test task.
                graph.allTasks.filterIsInstance<Test>().forEach { it.ignoreFailures = true }
                val tested = project.subprojects.filter { it.path != APP && inScope(it, scope) }
                unitTests.sources = testSources(project, tested, buildLogic?.projectDir)
                unitTests.modules =
                    tested.map { it.name } + listOfNotNull(buildLogic?.let { BUILD_LOGIC })
            }
            if (graph.hasTask(gate.get()))
                service.get().start(ticket.get(), Git(project.rootDir).shortHead())
        }
    }

    private fun wireProject(
        project: Project,
        steps: StepTasks,
        service: Provider<GateService>,
        inScope: Boolean,
    ) {
        val isApp = project.path == APP
        project.tasks.configureEach { task ->
            val step = stepOf(task, isApp) ?: return@configureEach
            service.get().register(task.path, GateService.TaskInfo(step, project.projectDir))
            // Android Lint reads the compiled classes of the app and of core, so while an app
            // exists every compile task runs after the format step instead of waiting for the lint
            // step that needs it. A compile error found before lint finishes is reported as
            // compile.
            val hasApp = project.rootProject.findProject(APP) != null
            val after = if (hasApp && step == GateStep.COMPILE) GateStep.FORMAT else step.previous
            after?.let { task.mustRunAfter(steps.of(it)) }
            if (step == GateStep.COMPILE) {
                task.logging.addStandardErrorListener(
                    StandardOutputListener { text -> service.get().capture(task.path, text) }
                )
            }
        }
        // A module out of this run's scope feeds no step: none of its tools enter the graph.
        if (!inScope) return
        if (isApp) {
            wireApp(project.path, steps)
            return
        }
        steps.format.configure {
            it.dependsOn(project.tasks.named { name -> name == "ktfmtCheck" })
        }
        steps.lint.configure { it.dependsOn(project.tasks.named { name -> name == "detekt" }) }
        steps.compile.configure {
            it.dependsOn(project.tasks.named { name -> name == "classes" || name == "testClasses" })
        }
        steps.unitTests.configure { it.dependsOn(project.tasks.withType(Test::class.java)) }
        steps.coverage.configure {
            it.dependsOn(project.tasks.named { name -> name == "koverXmlReport" })
        }
    }

    /**
     * The app's step tasks, named by path: filtering the app's tasks by name would create every
     * task AGP registers, and AGP 9.5's release Compose Preview task fails when created. The app's
     * JVM tests are Compose UI and snapshot tests, so the ui-tests step runs them, in Roborazzi's
     * verify mode, and the unit-tests step does not.
     */
    private fun wireApp(path: String, steps: StepTasks) {
        steps.format.configure { it.dependsOn("$path:ktfmtCheck") }
        steps.lint.configure { it.dependsOn("$path:detekt", "$path:lintDebug") }
        steps.compile.configure {
            it.dependsOn("$path:assembleDebug", "$path:compileDebugUnitTestKotlin")
        }
        steps.uiTests.configure { it.dependsOn("$path:verifyRoborazziDebug") }
        steps.coverage.configure { it.dependsOn("$path:koverXmlReportDebug") }
    }

    private fun testSources(
        project: Project,
        tested: List<Project>,
        buildLogicDir: File?,
    ): List<GateUnitTestsTask.ResultSource> {
        val root = project.rootDir.path
        val mainBuild = tested.flatMap { p ->
            val roots = TEST_SOURCE_DIRS.map {
                RepoPaths.relative(root, File(p.projectDir, it).path)
            }
            p.tasks.withType(Test::class.java).map { test ->
                GateUnitTestsTask.ResultSource(
                    test.reports.junitXml.outputLocation.get().asFile,
                    roots,
                    "./gradlew ${test.path}",
                )
            }
        }
        val gateBuild = buildLogicDir?.let { dir ->
            GateUnitTestsTask.ResultSource(
                File(dir, "build/test-results/test"),
                TEST_SOURCE_DIRS.map { RepoPaths.relative(root, File(dir, it).path) },
                "./gradlew -p $BUILD_LOGIC test",
            )
        }
        return mainBuild + listOfNotNull(gateBuild)
    }

    /**
     * The modules this run checks (see [GateScope]): from `-Pgate.full=true` and the files changed
     * since the merge-base with `main`, the same list the coverage step reads.
     */
    private fun scopeOf(project: Project): GateScope {
        val full = project.providers.gradleProperty(FULL_PROPERTY).orNull == "true"
        if (full) return GateScope.of(emptyList(), fullFlag = true, hasMergeBase = true)
        val git = Git(project.rootDir)
        val base = git.mergeBase()
        val changed = if (base == null) emptyList() else git.changedSince(base)
        return GateScope.of(changed, fullFlag = false, hasMergeBase = base != null)
    }

    private fun describe(scope: GateScope): String =
        if (scope.full) "Gate scope: full (every module)."
        else "Gate scope: ${scope.modules.sortedBy { GateScope.ALL_MODULES.indexOf(it) }}."

    /** The root project is the build-logic sources' format and lint; it runs only when full. */
    private fun inScope(project: Project, scope: GateScope): Boolean =
        scope.full || (project != project.rootProject && project.name in scope.modules)

    /**
     * `./gradlew check` selects every project's own `check`, which pulls in that project's tests,
     * lint and coverage apart from the gate. Out-of-scope projects' `check` is excluded, as `-x`
     * would, so neither it nor what only it needs enters the graph. A module a run needs still
     * builds its main classes and jar for the modules that depend on it.
     */
    private fun excludeOutOfScopeChecks(project: Project, scope: GateScope) {
        if (scope.full) return
        val start = project.gradle.startParameter
        if (CHECK !in start.taskNames) return
        val excluded =
            project.subprojects.filterNot { inScope(it, scope) }.map { "${it.path}:$CHECK" }
        start.setExcludedTaskNames(start.excludedTaskNames + excluded)
    }

    private fun ticketProvider(project: Project): Provider<String> {
        val explicit = project.providers.gradleProperty("ticket")
        val environment = project.providers.environmentVariable("GATE_TICKET")
        val branchOverride = project.providers.environmentVariable("GATE_BRANCH")
        val root = project.rootDir
        val resolved by lazy {
            val branch = branchOverride.orNull?.takeIf { it.isNotBlank() } ?: Git(root).branch()
            TicketId.resolve(explicit.orNull, environment.orNull, branch)
        }
        return project.providers.provider { resolved }
    }

    /** Which step a tool task belongs to, by the names the plugins give their tasks. */
    private fun stepOf(task: Task, isApp: Boolean): GateStep? =
        when {
            task.name.startsWith("ktfmtCheck") -> GateStep.FORMAT
            task.name.startsWith("detekt") -> GateStep.LINT
            isApp && task.name.startsWith("lint") -> GateStep.LINT
            task.name.startsWith("compile") -> GateStep.COMPILE
            task is Test -> if (isApp) GateStep.UI_TESTS else GateStep.UNIT_TESTS
            KOVER_REPORT.matches(task.name) -> GateStep.COVERAGE
            else -> null
        }

    /** Registers the step tasks, each ordered after the one before it. */
    private class StepTasks(private val project: Project) {
        private val registered = mutableListOf<TaskProvider<out Task>>()

        val format = add(GateStep.FORMAT, DefaultTask::class.java)
        val lint = add(GateStep.LINT, DefaultTask::class.java)
        val compile = add(GateStep.COMPILE, DefaultTask::class.java)
        val unitTests = add(GateStep.UNIT_TESTS, GateUnitTestsTask::class.java)
        val uiTests = add(GateStep.UI_TESTS, GateUiTestsTask::class.java)
        val coverage = add(GateStep.COVERAGE, GateCoverageTask::class.java)
        val dependencies = add(GateStep.DEPENDENCIES, GateDependenciesTask::class.java)

        val all: List<TaskProvider<out Task>>
            get() = registered.toList()

        fun of(step: GateStep): TaskProvider<out Task> = registered[step.ordinal]

        private fun <T : Task> add(step: GateStep, type: Class<T>): TaskProvider<T> {
            val previous = registered.lastOrNull()
            val provider =
                project.tasks.register(step.taskName, type) { task ->
                    task.group = GROUP
                    task.description = "Gate step '${step.id}'."
                    previous?.let { task.mustRunAfter(it) }
                    if (task is GateStepTask) task.rootDir.set(project.layout.projectDirectory)
                }
            registered += provider
            return provider
        }
    }

    private companion object {
        const val GROUP = "gate"
        const val BUILD_LOGIC = GateScope.BUILD_LOGIC
        const val FULL_PROPERTY = "gate.full"
        const val CHECK = "check"
        const val APP = ":app"
        const val APP_TEST_RESULTS = "build/test-results/testDebugUnitTest"
        const val APP_SNAPSHOT_SUMMARY = "build/test-results/roborazzi/debug/results-summary.json"
        val TEST_SOURCE_DIRS = listOf("src/test/kotlin", "src/test/java")
        val KOVER_REPORT = Regex("^kover(Xml|Html|Binary)Report.*|^koverVerify.*|^koverLog.*")
    }
}
