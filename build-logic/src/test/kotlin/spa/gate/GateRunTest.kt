package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GateRunTest {
    private fun failure(step: GateStep, file: String = "x.kt") =
        GateFailure(step.id, file, 1, "broken", "./gradlew x")

    private fun results(verdict: Verdict) = verdict.steps.map { it.step to it.result }

    @Test
    fun `steps are the contract's seven names in the contract's order`() {
        assertEquals(
            listOf(
                "format",
                "lint",
                "compile",
                "unit-tests",
                "ui-tests",
                "coverage",
                "dependencies",
            ),
            GateStep.entries.map { it.id },
        )
        assertEquals(null, GateStep.FORMAT.previous)
        assertEquals(GateStep.COVERAGE, GateStep.DEPENDENCIES.previous)
    }

    @Test
    fun `all steps passing is a pass with no failures`() {
        val run = GateRun()
        GateStep.entries.forEach { run.pass(it) }
        val verdict = run.verdict("SPA-001", "abc1234")
        assertEquals("pass", verdict.result)
        assertEquals(emptyList(), verdict.failures)
        assertEquals(GateStep.entries.map { it.id to "pass" }, results(verdict))
    }

    @Test
    fun `the first failing step stops the run and later steps are not run`() {
        val run = GateRun()
        run.pass(GateStep.FORMAT)
        run.fail(GateStep.LINT, listOf(failure(GateStep.LINT)))
        val verdict = run.verdict("SPA-001", "abc1234")
        assertEquals("fail", verdict.result)
        assertEquals(listOf(failure(GateStep.LINT)), verdict.failures)
        assertEquals(
            listOf(
                "format" to "pass",
                "lint" to "fail",
                "compile" to "not_run",
                "unit-tests" to "not_run",
                "ui-tests" to "not_run",
                "coverage" to "not_run",
                "dependencies" to "not_run",
            ),
            results(verdict),
        )
    }

    @Test
    fun `only the earliest failing step is reported, whatever order failures arrive in`() {
        val run = GateRun()
        run.fail(GateStep.DEPENDENCIES, listOf(failure(GateStep.DEPENDENCIES)))
        run.fail(
            GateStep.FORMAT,
            listOf(failure(GateStep.FORMAT, "a.kt"), failure(GateStep.FORMAT, "b.kt")),
        )
        val verdict = run.verdict("none", "abc1234")
        assertEquals(listOf("a.kt", "b.kt"), verdict.failures.map { it.file })
        assertEquals("not_run", verdict.steps.last().result)
    }

    @Test
    fun `a build that stops without a recorded failure still fails at the open step`() {
        val run = GateRun()
        run.pass(GateStep.FORMAT)
        run.pass(GateStep.LINT)
        assertEquals(GateStep.COMPILE, run.firstOpenStep())
        val verdict = run.verdict("SPA-001", "abc1234")
        assertEquals("fail", verdict.result)
        assertEquals("compile", verdict.failures.single().step)
        assertEquals(null, verdict.failures.single().file)
    }

    @Test
    fun `a pass note survives a later pass without a note`() {
        val run = GateRun()
        GateStep.entries.forEach { run.pass(it) }
        run.pass(GateStep.UI_TESTS, "skipped: no app module")
        run.pass(GateStep.UI_TESTS)
        val ui = run.verdict("SPA-001", "abc1234").steps.single { it.step == "ui-tests" }
        assertEquals(StepReport("ui-tests", "pass", "skipped: no app module"), ui)
    }

    @Test
    fun `failures must be non-empty and belong to their step`() {
        val run = GateRun()
        assertFailsWith<IllegalArgumentException> { run.fail(GateStep.LINT, emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            run.fail(GateStep.LINT, listOf(failure(GateStep.FORMAT)))
        }
    }
}
