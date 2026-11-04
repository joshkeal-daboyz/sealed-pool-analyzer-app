package spa.gate

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent

/**
 * Lives for one build. Hears every task finish, files each failure under its gate step with the
 * file and line the tool reported, and writes `gate-verdict.json` when the build ends, whether it
 * passed or failed. It writes nothing for builds that did not schedule the `gate` task.
 */
abstract class GateService :
    BuildService<GateService.Params>, OperationCompletionListener, AutoCloseable {
    interface Params : BuildServiceParameters {
        val rootDir: DirectoryProperty
    }

    /** The gate step a task belongs to and the directory of the project it runs in. */
    data class TaskInfo(val step: GateStep, val projectDir: File)

    val run = GateRun()

    private val tasks = ConcurrentHashMap<String, TaskInfo>()
    private val capturedOutput = ConcurrentHashMap<String, StringBuffer>()

    @Volatile private var verdictFor: Pair<String, String>? = null

    private val root: File
        get() = parameters.rootDir.get().asFile

    fun register(taskPath: String, info: TaskInfo) {
        tasks[taskPath] = info
    }

    /** Keeps what a task printed, so compiler errors can be located when it fails. */
    fun capture(taskPath: String, text: CharSequence) {
        capturedOutput.getOrPut(taskPath) { StringBuffer() }.append(text)
    }

    /** Called once the `gate` task is known to run: the verdict is written at the end. */
    fun start(ticket: String, commit: String) {
        verdictFor = ticket to commit
    }

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        val path = event.descriptor.taskPath
        val info = tasks[path]
        val result = event.result
        when {
            result is TaskFailureResult -> onFailure(path, info, result.failures)
            info != null && path == ":${info.step.taskName}" -> run.pass(info.step)
        }
    }

    override fun close() {
        val (ticket, commit) = verdictFor ?: return
        File(root, VERDICT_FILE).writeText(VerdictJson.encode(run.verdict(ticket, commit)))
    }

    private fun onFailure(path: String, info: TaskInfo?, failures: List<Failure>) {
        val step = info?.step ?: run.firstOpenStep()
        // Gate step tasks record their own structured failures before they throw.
        if (run.hasFailures(step)) return
        val messages = failures.flatMap { flatten(it) }.distinct()
        val located =
            when (step) {
                GateStep.FORMAT -> info?.let { formatFailures(path, it, messages) }
                GateStep.LINT -> info?.let { lintFailures(path, it) }
                GateStep.COMPILE -> compileFailures(path)
                else -> null
            }
        val generic = GateFailure(step.id, null, null, summarize(messages), "./gradlew $path")
        run.fail(step, located.orEmpty().ifEmpty { listOf(generic) })
    }

    private fun formatFailures(path: String, info: TaskInfo, messages: List<String>) =
        ToolOutput.ktfmtFiles(messages.joinToString("\n")).map { file ->
            GateFailure(
                GateStep.FORMAT.id,
                RepoPaths.relative(root.path, File(info.projectDir, file).path),
                null,
                "ktfmt would reformat this file. Run ./gradlew ktfmtFormat to fix it.",
                "./gradlew $path",
            )
        }

    private fun lintFailures(path: String, info: TaskInfo): List<GateFailure> {
        val name = path.substringAfterLast(':')
        // The lint step runs detekt everywhere and Android Lint (lint<Variant>) on the app.
        val android = name.startsWith("lint")
        val report =
            File(
                info.projectDir,
                if (android) ToolOutput.androidLintReport(name)
                else "build/reports/detekt/$name.xml",
            )
        if (!report.isFile) return emptyList()
        val text = report.readText()
        val findings =
            if (android) ToolOutput.androidLintErrors(text) else ToolOutput.detektFindings(text)
        return findings.map {
            GateFailure(
                GateStep.LINT.id,
                it.path
                    .takeIf { p -> p.isNotEmpty() }
                    ?.let { p -> RepoPaths.relative(root.path, p) },
                it.line,
                it.message,
                "./gradlew $path",
            )
        }
    }

    private fun compileFailures(path: String): List<GateFailure> =
        ToolOutput.compilerErrors(capturedOutput[path]?.toString().orEmpty()).map {
            GateFailure(
                GateStep.COMPILE.id,
                RepoPaths.relative(root.path, it.path),
                it.line,
                it.message,
                "./gradlew $path",
            )
        }

    companion object {
        const val NAME = "spaGate"
        const val VERDICT_FILE = "gate-verdict.json"
    }
}

private fun flatten(failure: Failure): List<String> =
    listOfNotNull(failure.message) + failure.causes.flatMap { flatten(it) }

private fun summarize(messages: List<String>): String =
    messages.joinToString(" > ").ifBlank { "The task failed without a message." }
