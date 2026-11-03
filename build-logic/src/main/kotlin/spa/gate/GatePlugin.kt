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
        steps.uiTests.configure { it.hasAppModule = project.findProject(":app") != null }

        project.allprojects { wireProject(it, steps, service) }
        val buildLogic = project.gradle.includedBuilds.firstOrNull { it.name == BUILD_LOGIC }
        if (buildLogic != null) steps.unitTests.configure { it.dependsOn(buildLogic.task(":test")) }

        project.gradle.taskGraph.whenReady { graph ->
            val unitTests = steps.unitTests.get()
            if (graph.hasTask(unitTests)) {
                // The unit-tests step reads the XML reports, so failing tests are reported as a
                // step
                // failure with file and line instead of stopping the build inside the test task.
                graph.allTasks.filterIsInstance<Test>().forEach { it.ignoreFailures = true }
                unitTests.sources = testSources(project, buildLogic?.projectDir)
            }
            if (graph.hasTask(gate.get()))
                service.get().start(ticket.get(), Git(project.rootDir).shortHead())
        }
    }

    private fun wireProject(
        project: Project,
        steps: StepTasks,
        service: Provider<GateService>,
    ) {
        project.tasks.configureEach { task ->
            val step = stepOf(task) ?: return@configureEach
            service.get().register(task.path, GateService.TaskInfo(step, project.projectDir))
            step.previous?.let { task.mustRunAfter(steps.of(it)) }
            if (step == GateStep.COMPILE) {
                task.logging.addStandardErrorListener(
                    StandardOutputListener { text -> service.get().capture(task.path, text) }
                )
            }
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

    private fun testSources(
        project: Project,
        buildLogicDir: File?,
    ): List<GateUnitTestsTask.ResultSource> {
        val root = project.rootDir.path
        val mainBuild =
            project.allprojects.flatMap { p ->
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
    private fun stepOf(task: Task): GateStep? =
        when {
            task.name.startsWith("ktfmtCheck") -> GateStep.FORMAT
            task.name.startsWith("detekt") -> GateStep.LINT
            task.name.startsWith("compile") -> GateStep.COMPILE
            task is Test -> GateStep.UNIT_TESTS
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
        const val BUILD_LOGIC = "build-logic"
        val TEST_SOURCE_DIRS = listOf("src/test/kotlin", "src/test/java")
        val KOVER_REPORT = Regex("^kover(Xml|Html|Binary)Report.*|^koverVerify.*|^koverLog.*")
    }
}
