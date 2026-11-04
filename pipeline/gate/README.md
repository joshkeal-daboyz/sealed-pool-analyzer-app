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

The script is `pipeline/gate/run.sh`; the steps are the `spa.gate` plugin in `build-logic/`. It runs the same way locally (pre-push hook) and in CI (`.github/workflows/gate.yml`, on `ubuntu-latest` with its preinstalled Android SDK).

Tools: ktfmt (format), detekt and, for `:app`, Android Lint with its Accessibility category as errors (`app/lint.xml`) (lint), Gradle `test` with JUnit (unit tests for `core`, `cli`, `agent`, and `build-logic`), Robolectric and Roborazzi (ui-tests, for `:app`), Kover (coverage, which also covers `app/src/main/kotlin` against `:app`'s debug-variant report), and the catalog diff (dependencies).

Running it locally needs JDK 21 (`JAVA_HOME`) and the Android SDK (`ANDROID_HOME`).
