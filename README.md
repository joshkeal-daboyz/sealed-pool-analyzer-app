# Sealed Pool Analyzer

An Android app that takes the cards a player opens at a Magic: The Gathering prerelease and recommends ranked 40-card decks with a written rationale and a sideboard plan.

This repository is built through an agentic development pipeline: a central Architect agent writes tickets from goals, Developer agents implement them in their own worktrees, an automated gate runs formatting, lint, type checks, and tests before any model reads a branch, and two Testing agents inspect what passes. The pipeline's own files live here beside the code they govern.

## Layout

| Path | What it is |
| --- | --- |
| `CLAUDE.md` | The rules every agent reads first |
| `pipeline/` | Role briefs, the gate contract, the ticket template, kickoff prompts |
| `tickets/` | Goals, tickets, and the lessons file; one markdown file per ticket |
| `design/` | Design specs written by the Designer, one per feature with a UI |
| `fixtures/` | Real and synthetic pools used by tests (created by the first goal) |
| `core/`, `app/` | The Kotlin core library and the Android app (created by tickets, not by hand) |

## Design documents

The product design and the pipeline design live in a separate repository: [sealed-pool-analyzer-designdoc](https://github.com/joshkeal-daboyz/sealed-pool-analyzer-designdoc). Read `docs/design-brief.md` there for what the app is, and `docs/agentic-development-pipeline.md` for how this repository is worked.

## Starting work

Open a Claude Code session in this repository and paste the prompt in `pipeline/prompts/architect-kickoff.md`. The Architect reads the first goal, writes the backlog, and stops for approval before dispatching anything.

## Toolchain

**JDK.** JDK 21 is required, on `PATH` or in `JAVA_HOME`. On Windows, `winget install EclipseAdoptium.Temurin.21.JDK` installs it (from an administrator prompt); a shell opened before the install may still see an old `java` stub, so set `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-21...` if `java -version` does not report 21. Nothing else needs installing: the committed Gradle wrapper (`./gradlew`) downloads Gradle itself. On Windows, run every command below in Git Bash.

**Running the gate.** From the repository root:

```bash
pipeline/gate/run.sh
```

It runs `./gradlew check`, which runs the seven steps of `pipeline/gate/README.md` in order (`format`, `lint`, `compile`, `unit-tests`, `ui-tests`, `coverage`, `dependencies`) and stops at the first that fails. It exits 0 on pass and 1 on fail. The ticket is `--ticket SPA-###` if given, else the `GATE_TICKET` environment variable, else the first `SPA-###` in the branch name (`GATE_BRANCH` overrides the branch name, which CI uses), else `none`. With `none`, the coverage threshold is 0 and `gradle/libs.versions.toml` must be unchanged from `git merge-base origin/main HEAD`. One step can be run alone with `./gradlew gateFormat`, `gateLint`, `gateCompile`, `gateUnitTests`, `gateUiTests`, `gateCoverage`, or `gateDependencies`. `./gradlew ktfmtFormat` fixes formatting. The gate's own code is the `spa.gate` Gradle plugin in `build-logic/`.

**The pre-push hook.** Once per clone:

```bash
pipeline/gate/install-hook.sh
```

This sets `core.hooksPath` to `.githooks`. From then on `git push` runs the gate on the working tree and refuses the push when it is red. A push that only deletes remote branches skips it.

**The verdict.** Every run writes `gate-verdict.json` at the repository root (gitignored): `ticket`, `commit` (short SHA), `result` (`pass` or `fail`), `failures` (each with `step`, `file`, `line` or null, `message`, and a `reproduce` command), and `steps` (each step's `pass`, `fail`, or `not_run`, with a note such as `skipped: no app module`). Read `failures` first; the full Gradle log is in `build/gate/gradle.log`. CI runs the same script on every pull request to `main` and every push to `main`, and uploads the verdict as the `gate-verdict` workflow artifact.

**Dependencies.** Every dependency coordinate and version lives in `gradle/libs.versions.toml`; build scripts use catalog aliases, never string coordinates. A ticket that adds an entry to `[libraries]` or `[plugins]` must list it in its Scope section in exactly this form, one backticked coordinate per sub-bullet, `group:artifact` for a library and `plugin:<plugin id>` for a Gradle plugin:

```markdown
- Dependencies this ticket may add (none unless listed):
  - `plugin:org.jetbrains.kotlin.jvm`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json`
```

The `dependencies` step fails, naming the coordinate, for any added entry the ticket does not list.
