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
| `agent/` | The agent layer, the only module that calls a model: card grades, the API key from `ANTHROPIC_API_KEY`, and a model client whose tests replay recorded responses |

## Design documents

The product design and the pipeline design live in a separate repository: [sealed-pool-analyzer-designdoc](https://github.com/joshkeal-daboyz/sealed-pool-analyzer-designdoc). Read `docs/design-brief.md` there for what the app is, and `docs/agentic-development-pipeline.md` for how this repository is worked.

## Starting work

Open a Claude Code session in this repository and paste the prompt in `pipeline/prompts/architect-kickoff.md`. The Architect reads the first goal, writes the backlog, and stops for approval before dispatching anything.

## Card data

Card data comes from [Scryfall](https://scryfall.com) and is used under Scryfall's terms. Sealed Pool Analyzer is unofficial Fan Content: it is not produced by, endorsed by, or affiliated with Scryfall or Wizards of the Coast, and Magic: The Gathering is a trademark of Wizards of the Coast. The card data in `fixtures/scryfall/` is a snapshot, refreshed deliberately by a ticket rather than on every build; `fixtures/scryfall/README.md` records when each set was fetched and how to refresh it.

Play data from [17Lands](https://www.17lands.com), free for non-commercial use. The win-rate tables in `fixtures/winrates/` are derived from 17Lands' public game data files; the downloads themselves are not in this repository. `fixtures/winrates/README.md` names each file and its date.

## Command line

The `cli` module builds `spa`, the command line the metric engine is tested through until there is an app. Install it from the repository root (needs the JDK in [Toolchain](#toolchain)):

```bash
./gradlew :cli:installDist
```

This writes `cli/build/install/spa/bin/spa` and, for Windows, `spa.bat`. `spa --help` lists the commands. Every command reads its data from `--data <dir>`, default `fixtures` relative to the current directory, so run it from the repository root or pass `--data`. That directory holds Scryfall caches in `scryfall/<code>/`, briefs in `briefs/<code>.json`, baselines in `baselines/<code>.json`, win-rate tables in `winrates/<code>.json`, and model grade files in `grades/<code>.json`. Exit codes: 0 ok, 1 an error (a missing or unreadable file, set cache, or brief, a missing `--set`, or a network error), 2 a bad command line (no command, an unknown command, or a bad flag). Errors are one line on stderr.

**analyze** parses a pool file and prints one JSON document to stdout: `tool` (name and version), `set`, `pool` (the file as given, the detected or given `format`, `entries` as the count of resolved pool entries, `skippedLines`, every `unresolved` line with its `line`, `text`, and `reason`, and the brief's `kitWarnings`, each with an `expected` range written as `min` and `max`), `metrics` (the engine's numbers), and the set's `baseline`. The format is detected unless `--format manabox|text|collector` is given; `--brief <file>` replaces `briefs/<code>.json` for kit warnings. `baseline` is `null` with `--no-baseline` or when the set has no baseline file, and stderr says which. Unresolved lines and kit warnings still exit 0. Keys print in a fixed order, so the same input prints the same bytes.

```bash
cli/build/install/spa/bin/spa analyze fixtures/pools/trk-manabox-01.csv --set trk
```

**validate-brief** checks a brief against its schema and rules. A valid brief prints `OK <file>` on stdout, then one `warning: <pointer>: '<name>' is not a card in set '<code>'` line for each signpost uncommon or key common the set's cards do not include, and exits 0. An invalid brief prints `<file>: <n> error(s)` and one `error: <pointer>: <message>` line per problem on stderr, and exits 1.

```bash
cli/build/install/spa/bin/spa validate-brief fixtures/briefs/trk.json --set trk
```

**fetch-set** refreshes one set's Scryfall cache under `<data>/scryfall/<code>/` and prints `<code>: <n> cards in <dir>`. `--companions a,b` writes those codes to the set's `companions.json` and refreshes each companion's cache too; without it, an existing `companions.json` is kept and a missing one is written as `[]`. It needs the network and exits 1 on a network or Scryfall error.

```bash
cli/build/install/spa/bin/spa fetch-set trk --companions sds
```

**winrates** derives a set's win-rate grade table from a 17Lands public game data file (`game_data_public.<SET>.PremierDraft.csv.gz`, gzipped or plain) and writes it to `<data>/winrates/<code>.json`, a grade file with source `WIN_RATE`. A card's GIH WR is its win rate over the games where it was in the opening hand or drawn. Each card with at least 500 such games gets a score of `2.5 + (gihwr - m) / s`, clamped to 0 to 5 and rounded to the nearest 0.5, where `m` and `s` are the mean and population standard deviation of GIH WR over the set's cards with 500 or more games, and a confidence of `games / (games + 2000)`; its `gihwr` and `games` are kept in the file. It prints the game and card counts, the scale, one `unmatched: <name>` line for each 17Lands name that is no card of the set (skipped), and the path written, and exits 0. The provenance names the file and its `Last-Modified` date, from `--dataset-date <yyyy-mm-dd>` or else the file's modified date. A missing file prints one line and exits 1.

```bash
cli/build/install/spa/bin/spa winrates ~/Downloads/game_data_public.MSH.PremierDraft.csv.gz --set msh --dataset-date 2026-07-27
```

**grade-backtest** scores a model grade file against a win-rate grade file: `--model <file>` (default `<data>/grades/<code>.json`) and `--winrates <file>` (default `<data>/winrates/<code>.json`). Over the cards the win-rate file grades that the model file grades too, matched by set code and collector number, it computes Spearman's rho of the two scores: each list is ranked, ties taking the average of the ranks they span, and rho is the Pearson correlation of the ranks. It prints one line for `all` cards, then `common`, `uncommon`, `rare` and `mythic` (rarity from the set's Scryfall cache), each `<group>: rho <rho to 3 places>, n <cards>`; a group with fewer than 5 cards, or whose scores are all equal, prints `rho n/a`. Then the model file's provenance, the win-rate file's provenance, the count of win-rate cards missing from the model file, and one `missing: <name> (<set> <number>)` line for each, and exits 0. A missing or invalid file prints one line naming it and exits 1. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa grade-backtest --set msh
```

## Android app

The `app` module builds the Android app, a debug APK signed with the build machine's debug key. It needs the JDK and the Android SDK in [Toolchain](#toolchain).

**Build.** From the repository root:

```bash
./gradlew :app:assembleDebug
```

This writes `app/build/outputs/apk/debug/app-debug.apk`.

**Test.** The app's JVM tests (Compose UI tests and snapshot tests under Robolectric, with no emulator or phone) run with:

```bash
./gradlew :app:verifyRoborazziDebug
```

The gate runs them too, as its `ui-tests` step.

**Install on a phone.** Android blocks apps that do not come from a store until you allow them. Pick one route.

- Copy the file over. Copy `app-debug.apk` to the phone (over USB, Google Drive, or email to yourself) and tap it in the Files app (or Chrome's downloads). When Android asks, allow the app that opened it: Settings > Apps > Special app access > Install unknown apps > Files (or Chrome) > Allow. Menu names differ slightly by manufacturer. If Google Play Protect warns that the app is from an unknown developer, choose "Install anyway". You can turn the permission off again afterwards.
- Install over USB with `adb`. On the phone, turn on USB debugging: Settings > About phone > tap Build number seven times, then Settings > System > Developer options > USB debugging. Connect the cable and accept the "Allow USB debugging" prompt on the phone; `adb devices` should list the phone as `device`. Then:

  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

**No account, no key, no network.** The app needs no account and no API key, and it has no network permission. To check the last, list the APK's permissions with `aapt2` from the SDK build tools the first build installed (`ls "$ANDROID_HOME/build-tools"` shows the version; use yours in place of `36.0.0`):

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump permissions app/build/outputs/apk/debug/app-debug.apk
```

`android.permission.INTERNET` is not in the list.

## Toolchain

**JDK.** JDK 21 is required, on `PATH` or in `JAVA_HOME`. On Windows, `winget install EclipseAdoptium.Temurin.21.JDK` installs it (from an administrator prompt); a shell opened before the install may still see an old `java` stub, so set `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-21...` if `java -version` does not report 21. Nothing else needs installing: the committed Gradle wrapper (`./gradlew`) downloads Gradle itself. On Windows, run every command below in Git Bash.

**Android SDK.** The app needs the Android SDK command-line tools, installed on their own from https://developer.android.com/studio ("Command line tools only", unpacked so that `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin` holds `sdkmanager`) or through Android Studio (Settings > Languages & Frameworks > Android SDK > SDK Tools > "Android SDK Command-line Tools (latest)"). Set the user environment variable `ANDROID_HOME` to the SDK folder, `%LOCALAPPDATA%\Android\Sdk`, and add its `cmdline-tools\latest\bin`, `platform-tools`, and `emulator` folders to your user `PATH`. A terminal opened before you set them does not see the new variables, so close every terminal and open a new one. Then install the packages, and accept the SDK licenses yourself with `sdkmanager --licenses`, reading each one and answering `y`:

```bash
sdkmanager.bat --install "platform-tools" "emulator" "system-images;android-36;google_apis;x86_64"
sdkmanager.bat --licenses
```

Git Bash needs the `.bat` on `sdkmanager`; PowerShell does not. Gradle downloads the SDK platform and build tools the app asks for on its first build, once the licenses are accepted. `ANDROID_HOME` must be visible in Git Bash, where the gate runs; in a new Git Bash window, this prints a path and two versions:

```bash
echo "$ANDROID_HOME" && adb version && sdkmanager.bat --version
```

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
