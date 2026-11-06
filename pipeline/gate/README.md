# Automated gate

The gate is code, not a model. It runs on every Developer push, locally through a pre-push hook and in CI, with the same script in both places. No model reads a branch until the gate is green.

## What it runs, in order, stopping at the first failure

1. Formatter check. A formatting diff is red; it is free to fix and hides real changes.
2. Lint and static analysis, including accessibility lint for Compose screens once the app module exists.
3. Compile.
4. Unit tests for every module, not only the files the ticket touched.
5. Compose UI tests and snapshot tests: the app's JVM tests (`app/src/test`), run under Robolectric with no emulator by `./gradlew :app:verifyRoborazziDebug`, which compares every Roborazzi snapshot with its committed baseline in `app/src/test/snapshots`. The step's note says how many UI tests and snapshots ran. A failing UI test is red with its file and line; a snapshot that differs from its stored baseline, or has none, is red and names the snapshot. The gate never records or updates a baseline: that is `./gradlew :app:recordRoborazziDebug`, run only when the ticket updates the baseline on purpose and the change note says so.
6. Coverage threshold on the files the ticket touched. Set per ticket by the Architect; never lowered by a Developer. It covers the main sources of `core` (`core/src/main/kotlin`) and `cli` (`cli/src/main/kotlin`), each file against its own module's Kover report.
7. Dependency audit: no dependency the ticket did not name in `scope`.

## Scope: what a local run checks

CI always runs the full gate (`pipeline/gate/run.sh --full`) on every pull request and every push to `main`. A local run (the pre-push hook, or `pipeline/gate/run.sh` without `--full`) checks only what the change can affect.

**Changed files** are the coverage step's list: the working tree against `git merge-base origin/main HEAD` (falling back to `main`), plus untracked files. A push to `main` from `main` sees only the commits being pushed.

**Documentation paths** are any path under `tickets/` or `design/`, and any path ending in `.md`. They never select a module.

**Docs-only skip.** When every changed file is a documentation path, `run.sh` skips the gate before Gradle starts: it prints `Gate: skipped, only tickets, design or Markdown files changed.`, writes `gate-verdict.json` with `"result": "pass"`, no failures, and all seven steps `not_run` with the note `skipped: docs-only change`, and exits 0.

**Scope rules**, applied to the changed files that are not documentation paths (`spa.gate.GateScope`):

- **Full gate** (every module, the app and the `build-logic` tests included) when `--full` (or `-Pgate.full=true` for `./gradlew check`) is given, when the merge-base cannot be found, when no non-documentation file changed (so `./gradlew check` on an unchanged tree checks everything), or when any path is outside `app/`, `core/`, `agent/`, `cli/` and `fixtures/`. That covers `build-logic/`, `gradle/`, `config/`, `pipeline/gate/`, `.githooks/`, `.github/`, the root build files, and anything new.
- Otherwise the JVM modules: a `core/` or `fixtures/` change runs `core`, `agent` and `cli`; else an `agent/` change runs `agent` and `cli`; else a `cli/` change runs `cli`.
- `app` runs only when an `app/` path changed (or the gate is full). A `core` or `fixtures` change that breaks the app is therefore not caught locally; CI's full run catches it before merge.

A module out of scope feeds no step: none of its format, lint, test-compile, test or coverage tasks, and not its own `check`, enter the task graph. A module that an in-scope module depends on still builds its main classes and jar. The Gradle log's first lines say `Gate scope: full (every module).` or name the modules, and `./gradlew check -m` lists the tasks a run would execute.

**Skip notes.** The unit-tests step passes with the note `modules: ...`, naming the modules whose tests ran (`build-logic` on a full run). When the app exists but is out of scope, the ui-tests step passes with the note `skipped: no app changes` (as it passes with `skipped: no app module` when there is no app). No new `result` value exists.

## The verdict

The script exits 0 on pass and non-zero on fail, and writes `gate-verdict.json` at the repository root (gitignored):

```json
{
  "ticket": "SPA-003",
  "commit": "abc1234",
  "result": "pass" | "fail",
  "failures": [
    { "step": "unit-tests", "file": "core/src/test/.../ManaBoxParserTest.kt", "line": 42,
      "message": "expected 85 cards, got 84", "reproduce": "./gradlew :core:test --tests ManaBoxParserTest" }
  ]
}
```

A Developer reads `failures` and nothing else. The Architect appends a one-line summary of each run to the ticket history.

## Status

The script is `pipeline/gate/run.sh`; the steps are the `spa.gate` plugin in `build-logic/`. It runs the same way locally (pre-push hook) and in CI (`.github/workflows/gate.yml`, on `ubuntu-latest` with its preinstalled Android SDK), except that CI passes `--full` (see Scope above).

Tools: ktfmt (format), detekt and, for `:app`, Android Lint with its Accessibility category as errors (`app/lint.xml`) (lint), Gradle `test` with JUnit (unit tests for `core`, `cli`, `agent`, and `build-logic`), Robolectric and Roborazzi (ui-tests, for `:app`), Kover (coverage, which also covers `app/src/main/kotlin` against `:app`'s debug-variant report), and the catalog diff (dependencies).

Running it locally needs JDK 21 (`JAVA_HOME`) and the Android SDK (`ANDROID_HOME`).
