package spa.gate

/** The gate's steps, in the order `pipeline/gate/README.md` runs them. */
enum class GateStep(val id: String, val taskName: String) {
    FORMAT("format", "gateFormat"),
    LINT("lint", "gateLint"),
    COMPILE("compile", "gateCompile"),
    UNIT_TESTS("unit-tests", "gateUnitTests"),
    UI_TESTS("ui-tests", "gateUiTests"),
    COVERAGE("coverage", "gateCoverage"),
    DEPENDENCIES("dependencies", "gateDependencies");

    /** The step that runs before this one, or null for the first step. */
    val previous: GateStep?
        get() = entries.getOrNull(ordinal - 1)
}
