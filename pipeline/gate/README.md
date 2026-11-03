# Automated gate

The gate is code, not a model. It runs on every Developer push, locally through a pre-push hook and in CI, with the same script in both places. No model reads a branch until the gate is green.

## What it runs, in order, stopping at the first failure

1. Formatter check. A formatting diff is red; it is free to fix and hides real changes.
2. Lint and static analysis, including accessibility lint for Compose screens once the app module exists.
3. Compile.
4. Unit tests for every module, not only the files the ticket touched.
5. Compose UI tests and snapshot tests for the screens the ticket's test plan names. A snapshot that differs from its stored baseline is red unless the ticket updates the baseline on purpose and the change note says so.
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

The script does not exist yet. The first ticket of goal G-001 creates it, with the Kotlin toolchain, and proves it runs green on an empty `core` module both locally and in CI. Until then, nothing else dispatches.

Suggested tools for the Kotlin core, to be confirmed by that ticket: ktlint or ktfmt for formatting, detekt for static analysis, Gradle `test` for JUnit, Kover for coverage, and a snapshot library such as Paparazzi once the app module exists.
