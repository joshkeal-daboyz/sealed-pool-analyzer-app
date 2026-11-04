---
id: G-002
title: First installable app, a pool and its metrics on the phone
status: closed
owner: human
target_set_code: trk
companion_set_codes: [sds]
depends_on: [G-001]
---

# G-002: First installable app

Build the first Android app on top of the finished G-001 engine: an APK the owner sideloads on their phone that takes a pool (a ManaBox CSV first, or pasted text, or a collector-number list), runs the existing intake, metric engine, and set baseline on the device, and shows every number beside its typical value for a `trk` pool. This is the "pool entry screen" and "metrics dashboard" half of step 9 (Interface) in the design brief, built on steps 1 to 4. Nothing in it calls a model, so the app needs no account, no key, and no connection.

Approved by the owner on 2026-11-03, who answered its open questions; the answers are in "Decided" at the end.

## Why this goal next

- It is the first goal with a screen, so it exercises the pipeline roles G-001 skipped: the Designer, the gate's `ui-tests` step (Compose UI and snapshot tests), and the Acceptance Tester's screenshot pass on an emulator.
- It puts the engine in the owner's hand at the table. The metric layer is free and instant, so it is useful before any agent exists, and it is what every later goal builds on.
- It keeps the risk small: `core/` is already tested against 26 golden files, so "the app shows the same numbers as `spa analyze`" is checkable by data, not by judgement.

## Before this goal starts

Everything below is done by hand, by the owner, on the Windows 11 development PC and on the phone. Nothing in the pipeline can do these steps, and the first ticket stops if any is missing. Allow about 10 GB of free disk space (SDK, one emulator image, Gradle caches).

Run the PowerShell commands in a normal (not administrator) window unless the step says otherwise. After step 3, close and reopen every terminal and every Claude Code session, including the desktop app: a session started before the install does not see the new variables (the same trap as `JAVA_HOME` in G-001, see `tickets/lessons.md`, SPA-001).

**1. Install the Android SDK command-line tools.** Choose one route.

Route A (recommended, no IDE). Downloads the command-line tools, checks the file against the SHA-256 published on https://developer.android.com/studio (Command line tools only, build 15859902 on 2026-11-03), and unpacks them where Android Studio would put them:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
$zip = "$env:TEMP\commandlinetools-win.zip"
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip" -OutFile $zip
(Get-FileHash $zip -Algorithm SHA256).Hash -eq "90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a"
Expand-Archive $zip -DestinationPath "$sdk\cmdline-tools" -Force
Rename-Item "$sdk\cmdline-tools\cmdline-tools" "latest"
```

The fourth line must print `True`. If the page lists a newer build, use its file name and hash instead.

Route B (Android Studio). `winget install --id Google.AndroidStudio -e`, open it once, finish the setup wizard (it installs the SDK to `%LOCALAPPDATA%\Android\Sdk` and asks you to accept the licenses), then in Settings > Languages & Frameworks > Android SDK > SDK Tools tick "Android SDK Command-line Tools (latest)" and apply. The agents need the command-line tools either way.

**2. Point the tools at the SDK.** Sets `ANDROID_HOME` and puts `sdkmanager`, `adb`, and `emulator` on your user `PATH`:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
$path = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$path;$sdk\cmdline-tools\latest\bin;$sdk\platform-tools;$sdk\emulator", "User")
```

**3. Install the SDK packages.** Open a new PowerShell window first, then:

```powershell
sdkmanager --install "platform-tools" "emulator" "system-images;android-36;google_apis;x86_64"
```

Gradle downloads the SDK platform and build tools the app module asks for on its first build, once the licenses below are accepted, so they are not listed here. `android-36` (Android 16) is the emulator image (Decided 3).

**4. Accept the SDK licenses.** Only you can do this; no agent may accept a license for you:

```powershell
sdkmanager --licenses
```

Read each license and answer `y`. Gradle refuses to download SDK packages until this is done.

**5. Turn on emulator acceleration.** The emulator needs CPU virtualization. Check it:

```powershell
emulator -accel-check
```

If it does not report that acceleration is usable, turn on Windows Hypervisor Platform from an **administrator** PowerShell window and reboot:

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All
```

If it still fails after the reboot, virtualization is off in the PC's firmware settings (often called Intel VT-x, AMD-V, or SVM), which only you can change.

**6. Create the emulator the Acceptance Tester uses.** The name `spa-test` is what the tickets will cite:

```powershell
avdmanager create avd -n spa-test -k "system-images;android-36;google_apis;x86_64" -d pixel_6
emulator -avd spa-test
```

The second command should boot an emulated phone in a window. Close it when it reaches the home screen; the agents start it themselves (headless, with `-no-window`).

**7. Check everything from Git Bash.** The gate runs in Git Bash, so check there too, in a new window:

```bash
echo "$ANDROID_HOME" && adb version && sdkmanager.bat --version && java -version
```

Each should print a path or a version, and `java` should still report 21. (Git Bash needs the `.bat` on `sdkmanager`; PowerShell does not.)

**8. On the phone: allow installing the APK.** Android blocks apps that do not come from a store until you allow the app that opens the file. Pick one route.

- Copy the file over (no cable needed later). Copy `app-debug.apk` to the phone (USB, Google Drive, or email to yourself), tap it in the Files app (or Chrome's downloads), and when Android asks, allow that app: Settings > Apps > Special app access > Install unknown apps > Files (or Chrome) > Allow. The menu names differ slightly by manufacturer. Google Play Protect may warn that the app is from an unknown developer; choose "Install anyway". Turn the permission off again afterwards if you like.
- Install over USB with `adb`. On the phone: Settings > About phone > tap Build number seven times, then Settings > System > Developer options > USB debugging on. Connect the cable, accept the "Allow USB debugging" prompt on the phone, then on the PC run `adb devices` (the phone should be listed as `device`) and `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

**9. Approve the goal.** Check the "Decided" list at the end of this file, then change `status` to `open`.

## Deliverables

0. **Design spec, before any app ticket.** The Designer (`pipeline/roles/designer.md`) writes one spec in `design/` for this goal's feature: screen inventory, navigation, every state per screen (empty, loading, error, offline, and the pool-specific ones: unresolved lines, kit warnings, wrong set, an empty or unreadable file), the Compose component map, the accessibility checklist, and the snapshot list. The Architect writes no app ticket until that file is merged, and every app ticket cites it in `design_ref`. A ticket that only touches the build or the gate (deliverable 1) may come first.
1. **Android toolchain and the gate's UI step.** An `:app` module (Kotlin, Jetpack Compose, one Activity) that depends on the `:core` project directly, plus the gate changes an app needs, in the same ticket that creates the module (`tickets/lessons.md`, SPA-001): the `ui-tests` step runs Compose UI tests and snapshot tests on the JVM (no emulator in the gate), the `lint` step adds Android Lint with its accessibility checks, the `format` and `lint` steps cover `app/`, the `coverage` step covers `app/src/main/kotlin`, the dependency audit covers the new catalog entries, and the CI workflow gives the gate an Android SDK. Green on an app module that shows one empty screen before anything else in this goal is dispatched.
2. **Set data in the APK.** The `trk` and `sds` Scryfall caches, `fixtures/briefs/trk.json`, and `fixtures/baselines/trk.json` packaged into the APK at build time, straight from `fixtures/` (one source of truth, no hand-made copies), and loaded on the device through `core`'s existing loaders. The app reads no network.
3. **Pool entry.** Three ways in, all on the first screen:
   - Open a CSV with the system file picker.
   - Share a CSV to the app from another app (ManaBox, Files, Drive, Gmail), as the design brief requires: the app registers as a share target and as an "Open with" handler for CSV files.
   - Paste text into a box: a ManaBox CSV, a text list with counts, or a collector-number list. The format is detected the way `spa analyze` detects it, with a manual override for the three formats.
4. **Results.** For the pool: the totals, cards per color, removal and interaction, fixing sources, and for each of the ten color pairs its cards, playables, creatures, curve, splash candidates, and land recommendation, each number beside its typical `trk` value from the baseline (median, and the p10 to p90 range). Unresolved lines (line number, text, reason) and kit warnings (with the expected range) are shown before the numbers, never hidden. What is on which screen, and in what order, is the Designer's call.
5. **About.** Scryfall attribution and the Wizards of the Coast Fan Content notice, in the same words as the README's Card data section, reachable from the first screen, plus the app version and the date of the bundled Scryfall snapshot.
6. **README.** How to build the APK, install it on a phone (both routes in "Before this goal starts"), and run the app's tests, with the toolchain section extended to the Android SDK. Every promise the README makes about the app is an acceptance criterion of the ticket that writes it, or it is left out (`tickets/lessons.md`, SPA-009).
7. **The last pool is remembered.** The app keeps exactly one pool, the last one imported, across launches, so a closed or killed app does not lose the pool at the table. What is kept is the imported text, the format it was read as, and where it came from (a file name, or "pasted"), in one file in the app's private storage (`Context.filesDir`). Nothing else is stored, and the metrics are computed again from that text on launch, so what the screen shows is always what the current engine says. Importing a new pool replaces it. The owner clears it with a "Clear pool" action in the app, or with Android's Settings > Apps > Sealed Pool Analyzer > Storage > Clear storage; uninstalling removes it too. The file never leaves the phone: the app opts its data out of Android's cloud backup and device-to-device transfer, and it has no network permission (criterion 9).
8. **Collector numbers match regardless of case.** A fix in `core`, and the one `core` source change in this goal: a typed collector number matches a printing's number ignoring letter case, so `1701d`, `1701D`, `TRK 1701d`, and `trk 1701d` all resolve to `trk`'s `1701D`. The change is in collector-number lookup (`spa.core.intake.CollectorListParser` and/or `CardIndex.byCollectorNumber`); the Architect's ticket picks the place. It fixes `spa analyze` and the app together, since both call the same code. In `trk`, `sds`, and `syn` the only collector number with a letter is `1701D`, and no file in `fixtures/pools/` holds a letter-suffixed number in any case (checked 2026-11-03), so no golden file changes. It runs before the app tickets that show collector-list results, so the app is pinned to the fixed behaviour from the start.

## Goal-level acceptance criteria

1. **Gate.** `pipeline/gate/run.sh` exits 0 on a fresh clone of `main`, and the CI workflow shows the same result on the pull request. In `gate-verdict.json` the `ui-tests` step is `pass` with a note that is not `skipped: no app module`, and the run includes at least one Compose UI test and every snapshot on the Designer's snapshot list.
2. **One command to an APK.** On a machine with only the prerequisites in "Before this goal starts", `./gradlew :app:assembleDebug` from a fresh clone exits 0 and writes `app/build/outputs/apk/debug/app-debug.apk`. The README names this command.
3. **The engine is reused, not copied.** At goal close, `git diff <G-002 start commit> main -- core/src/main` touches only collector-number lookup (deliverable 8), and `git diff <G-002 start commit> main -- core/build.gradle.kts` is empty or changes only the bytecode target (Decided 5). `./gradlew :core:dependencies --configuration runtimeClasspath` lists no `com.android` or `androidx` coordinate. The app depends on `project(":core")`; no source file of `core` is duplicated under `app/`.
4. **Same numbers as the command line, for every fixture.** For each of the 23 `trk` pools in `fixtures/pools/manifest.json`, a JVM test feeds the file to the app's own import path (not to `core` directly) and checks that every value the results screens display equals the value at the same JSON pointer in `fixtures/golden/<file>.json`, and every typical value displayed equals the matching `fixtures/baselines/trk.json` entry. The 3 `syn` pools are not bundled in the APK and stay covered by `core`'s and `cli`'s JVM tests (Decided 6). The expected values are read from those files, never typed into the test (`tickets/lessons.md`, SPA-004 and SPA-008).
5. **ManaBox import on an emulator.** On the `spa-test` emulator, with the APK installed by `adb install -r` and the 11 ManaBox fixtures pushed by `adb push fixtures/pools/trk-manabox-*.csv /sdcard/Download/`, opening each through the system file picker shows the same total cards, entries, unresolved count, removal total, interaction total, and, for each of the ten pairs, creature count and land recommendation, as `spa analyze <file> --set trk` prints for it. The Acceptance Tester records a screenshot per pool and compares the spec's states against them.
6. **Share and Open with.** On the emulator, sharing `trk-manabox-01.csv` from the Files app to the app, and choosing the app from "Open with" for the same file, each lands on its results with the same numbers as criterion 5.
7. **Pasted pools.** On the emulator, pasting the contents of each `trk-text-*.txt` and `trk-collector-*.txt` fixture shows the format `spa analyze` detects for it and the same totals as criterion 5 lists. Choosing a different format by hand shows the same result `spa analyze --format <format>` gives for that file.
8. **Problems are shown.** For `trk-text-misspelled-01.txt`, `trk-manabox-wrong-set-01.csv`, and `trk-collector-bad-number-01.txt`, every entry of `pool.unresolved` that `spa analyze` prints appears on screen with its line number, text, and reason, before any metric. A pool with kit warnings shows each one with its expected range. An empty file, a file that is not a pool, and a file the app cannot read each show the error state the design spec gives them, and the app does not crash.
9. **Offline by construction.** `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` (from the SDK build tools) lists no `android.permission.INTERNET`, and the emulator with networking off (`adb shell cmd connectivity airplane-mode enable`) passes criterion 5 for `trk-manabox-01.csv`. This also proves the app makes no model call and sends no data anywhere.
10. **No key, anywhere.** The app has no field, setting, or file for an API key. `git grep -nE "sk-ant-|ANTHROPIC_API_KEY"` on `main` at goal close prints nothing outside this goal file and `CLAUDE.md`.
11. **Attribution.** The About screen shows the README's Scryfall attribution and Fan Content notice word for word and the bundled snapshot's fetch date from `fixtures/scryfall/trk/fetched-at.txt`. It is on the snapshot list, and a Compose UI test reaches it from the first screen in one tap.
12. **Accessibility.** Android Lint's accessibility checks are errors in the gate and pass. Every item of the design spec's accessibility checklist has a named test or Acceptance Tester scenario in some ticket's test plan, and the snapshot list includes the results screen at 200 percent font scale.
13. **Fast enough at the table.** On the `spa-test` emulator, with the app already open, the time from confirming `trk-manabox-01.csv` in the file picker to the results screen showing is under 3 seconds, as the median of 5 runs, timed from the app's own log lines (the ticket names them). A cold start to the first screen is under 5 seconds, by the same method.
14. **The owner's phone.** The owner installs `app-debug.apk` built from `main` at goal close on their own phone by one of the routes in "Before this goal starts", imports one real or fixture ManaBox CSV, and sees the same numbers as `spa analyze`. This is the one criterion only the human can check.
15. **The pipeline ran in order.** The design spec in `design/` merged before the first ticket with a `design_ref` was dispatched (checkable in `git log`), and every ticket that changes a file under `app/src/main` has a `design_ref`.
16. **The last pool survives a restart.** On the `spa-test` emulator: import `trk-manabox-01.csv`, run `adb shell am force-stop <app id>`, and relaunch; the same pool and results show without re-importing, with the same numbers as criterion 5. The same holds after `adb reboot`. Importing `trk-text-01.txt` and relaunching shows `trk-text-01.txt`, not the first pool. After "Clear pool" and a relaunch, the app shows its empty first screen, and `adb shell run-as <app id> ls files` no longer lists the pool file. The merged manifest sets `android:allowBackup="false"` and data extraction rules that exclude the app's files from cloud backup and device transfer (checkable with `aapt2 dump xmltree --file AndroidManifest.xml app-debug.apk`).
17. **Collector numbers ignore case.** A `core` unit test resolves `1701d`, `1701D`, `TRK 1701d`, and `trk 1701d` in a `trk` collector list to the printing whose collector number is `1701D`, and still reports `TRK 777` and `TRK 9001` from `trk-collector-bad-number-01.txt` as unknown collector numbers. `spa analyze` on a one-line file holding `TRK 1701d` reports 0 unresolved lines and 1 entry, and pasting the same line into the app on the emulator shows the same. `./gradlew :cli:test` passes with every file in `fixtures/golden/` unchanged (`git diff <G-002 start commit> main -- fixtures/golden` lists no existing file).

## Constraints

- `core/` is pure Kotlin with no Android dependencies, tested on the JVM, and reused as it is. The app calls the same `core` functions `spa analyze` calls (`CardIndex.load`, `BriefLoader`, `PoolParser.parse`, `MetricEngine.compute`, `Baseline.decode`). The one planned `core` source change is the collector-number case fix (deliverable 8), in its own ticket. If an app ticket finds it needs any other change in `core`, that is a bounce to the Architect and a question to the owner, not part of the app ticket.
- No model calls anywhere in this goal, and nothing in the repository or the APK embeds an API key. Users supply their own key in a later goal.
- Card data is Scryfall's and carries its attribution in the app (deliverable 5) as well as the README. The tool is free; the app has no ads, payments, or analytics.
- One set: `trk` with its companion `sds`. The bundled data is the committed snapshot in `fixtures/scryfall/`; the app does not refresh it.
- The development machine is Windows 11 with JDK 21 (Temurin), git, and Git Bash, and the Android SDK as set up in "Before this goal starts". The gate is the same script locally and in CI. CI runs on `ubuntu-latest`, whose image ships an Android SDK; the toolchain ticket says how the workflow finds it and how licenses are handled there.
- Every new dependency (the Android Gradle plugin, the Compose compiler plugin, Compose, Activity, the test and snapshot libraries) goes in `gradle/libs.versions.toml` and is listed in its ticket's scope, per the README's Dependencies section.
- The APK is a debug build, signed with the build machine's default debug key. Nothing goes to a store.

### Technical risks the toolchain ticket must settle first

These are for the Architect to turn into criteria of the first ticket; none needs the owner unless it ends in a `core` change.

- `core` is compiled with `jvmToolchain(21)`. The ticket proves the Android build tools dex `core`'s Java 21 bytecode. If they cannot, it lowers the bytecode target in `core/build.gradle.kts`, changes no `core` source, and says so in its change note (Decided 5).
- `core`'s loaders take `java.nio.file.Path`, so the app's `minSdk` is at least 26, and the bundled data must be real files on the device (for example, copied from the APK's assets to app storage on first launch) because assets are not paths.
- `core`'s Scryfall fetcher uses `java.net.http`, which Android does not have. The app never calls it; the build must not fail or crash because the class is present.
- The pre-push gate runs on Windows and CI runs on Linux. Snapshot libraries render fonts differently by OS, so the ticket proves one stored baseline verifies on both, or says how the gate keeps local and CI results identical. The gate's principle is that a Developer never sees a CI failure the local run would not have shown.
- The Kotlin version in the catalog (2.4.21) must be one the chosen Android Gradle plugin supports.
- The first end-to-end import (a file in, numbers on screen) should land as early as the dependencies allow, so upstream surprises show up before the screens are polished (`tickets/lessons.md`, SPA-009).

## Known issues carried from G-001

Only the ones the app exposes are carried. None blocks the goal.

- **Collector numbers are matched case-sensitively after one lowercase retry.** Exposed, because phone keyboards type lowercase: a pasted `1701d` does not resolve, while `1701D` does. **Fixed in this goal** (deliverable 8, criterion 17; Decided 4), so it leaves the cleanup list.
- **The tagger does not tag Guidance Failure's "target creature or Spacecraft ... top or bottom of their library" as BOUNCE.** Exposed: the app displays the removal and interaction counts, so a pool holding Guidance Failure shows the same undercount `spa analyze` does. Fixing it changes goldens, and criterion 4 pins the app to the goldens, so a fix belongs in its own `core` ticket that updates both together.

Not exposed, so not carried: the Thriving-land FIXING gap (no `trk` card uses that wording), `fetch-set`'s multi-line errors and "null" network message (the app does not fetch), and the untested default `--data` path and IOException branch (command line only).

## Where this draft departs from the design brief

The brief describes the finished product. This goal is one phase of it, and these are the deliberate gaps:

- **Fuzzy name matching.** The brief's pool ingestion "resolve[s] names fuzzily against the set". G-001 reports unresolved lines and never guesses, and this goal keeps that: a misspelled name shows as unresolved. Fuzzy matching is a later `core` goal.
- **Unresolved lines stop the run.** In the brief's orchestration, unresolved lines go back to the user before anything runs. That rule is for the agent run. Here, as in `spa analyze`, the numbers are shown with the unresolved lines listed first, so the owner sees both.
- **Arena and MTGO exports.** The brief lists them under pool ingestion. G-001 did not build them, so the app does not offer them.
- **Room database, saved pools, downloadable set bundles.** The brief puts the set's cards and the user's pools in SQLite (Room) and serves per-set bundles from a static host. This goal bundles one set in the APK and remembers one pool, the last one imported, as a single private file (deliverable 7; Decided 2). A list of saved pools, and Room, are later.
- **Pilot verification.** The brief verifies the interface with five people at a prerelease. This goal is verified by the owner alone (criterion 14); the five-person pilot is at the following set's prerelease, as the brief's roadmap says.

## Out of scope for this goal

Card grading, the analysis agents, deck building, ranked deck candidates, written rationale, sideboard plans, the Judge's disagreements view, and the per-card "which agent said what" drill-down. Any model call, the API-key entry screen, and the Android Keystore. Photo and OCR capture. Arena and MTGO import. Fuzzy name matching. Keeping more than one pool, pool history, user-recorded match results, and the 17Lands backtest. More than one set, set selection, and refreshing card data on the device. Release signing, a Play Store listing, and iOS. Card images (the app shows names and numbers only; Scryfall images are a later decision with its own attribution terms).

## Metrics to report when the goal closes

The same as G-001 so the two goals compare: gate green on first push, bounces per merged ticket by source, tokens per merged ticket by role, re-plans, escalations to the human, lead time per ticket, and share of criteria proven by deterministic tests. Add three for the first UI goal: Acceptance Tester screenshot-pass bounces, states the Acceptance Tester found that the design spec never described (each one should have become a spec update), and snapshot baselines changed on purpose. Append them here as a short table.

## Decided

The owner answered the draft's eight open questions on 2026-11-03 ("2. remember", "4. fix it", "the rest defult"). Each item gives the question, the answer, and where the goal now says it.

1. **Scope.** One set (`trk`), three ways in (file picker, share and Open with, paste), results beside the baseline, and About. Decided 2026-11-03: as written, plus the remembered pool (item 2).
2. **Keep the last pool?** Decided 2026-11-03: yes, remember it. The app keeps one pool, the last one imported, in a private file on the phone, cleared from the app or Android's storage settings (deliverable 7, criterion 16).
3. **Which phone?** Decided 2026-11-03: a Google Pixel 6, which is 64-bit (arm64), so the emulator uses the `pixel_6` device profile. The Android version is the default, Android 16, so the emulator image is `system-images;android-36;google_apis;x86_64` and `minSdk` is at least 26 ("Before this goal starts", steps 3 and 6). The owner confirms the phone's version under Settings > About phone > Android version. If it isn't 16, use that version's system image in steps 3 and 6 instead. `minSdk` 26 covers every version the Pixel 6 has run.
4. **Fix the collector-number case bug now?** Decided 2026-11-03: yes, fix it in this goal, as the one `core` source change (deliverable 8, criterion 17). No golden file changes.
5. **May `core`'s build target change if Android needs it?** Decided 2026-11-03: the default, approved in advance. Only the bytecode target in `core/build.gradle.kts` may change, with no source change, recorded in the ticket's change note (technical risks; criterion 3).
6. **Bundle `syn` in the APK?** Decided 2026-11-03: the default, no. The app holds `trk` and `sds` only; criterion 4 runs the 23 `trk` fixtures through the app, and the 3 `syn` fixtures stay in JVM tests.
7. **Real pools from 2026-11-06.** Decided 2026-11-03: the default, yes. If real ManaBox exports arrive before this goal starts, adding them to `fixtures/pools/` with their goldens is the goal's first ticket; otherwise it is its own ticket later.
8. **Debug key.** Decided 2026-11-03: the default, yes. The APK is built and signed on the owner's PC only for this goal.

## Architect notes (2026-11-03)

- Prerequisites checked from this PC on 2026-11-03: `ANDROID_HOME` visible in Git Bash, `adb` 1.0.41 (platform-tools 37.0.1), `sdkmanager` 22.0, emulator 37.2.13 with WHPX acceleration usable, every SDK license accepted, `system-images;android-36;google_apis;x86_64` installed, `spa-test` boots headless to Android 16 in 51 s, `java -version` Temurin 21.0.13.
- Design spec `design/first-app.md` written by the Designer (PR #15) and approved by the owner on 2026-11-03.
- Owner decision: per-pair playables are dropped from deliverable 4. `ColorPairMetrics` has no playables field and the baseline has no `/pairs/<pair>/playables` stat, so showing one would need a second `core` change. Playables are shown per colour (`/byColor/<c>/playables`), as the spec does.
- Owner decision: `castProbability` is not shown in this goal.
- Owner decision: the Claude API features (key entry, Keystore, agents, grading, deck building) stay out of G-002 and come in a later goal the owner writes. Criteria 9 and 10 stand.
- Settled from code, for the spec's open questions: `/fixingSources/count` is the number of entries in `metrics.fixingSources` (`MetricFlattener`), and curve bucket 7 is "7 or more" (`Curve.TOP`). No file size limit unless an import ticket finds one is needed.
- Visual direction B ("Low light") chosen by the owner on 2026-11-03 from three canvas directions, and written into the spec (PR #16). The theme is always dark, and the outline colour is `#6B7480` (the canvas's `#4A535E` failed 3:1).
- `minSdk` is 33, not 26: `core` calls `Files.readString` and `Files.writeString` (API 33), and both target devices run API 36. This avoids core library desugaring and changes no `core` source (SPA-013).
- Decomposed into SPA-013 to SPA-023. SPA-013 (app module and gate UI step) was dispatched alone on the owner's "dispatch" before the rest of the backlog was committed, since nothing else can start until it merges. G-002 start commit for the "unchanged" criteria (3, 17): 077e588.
- Order: SPA-014 (collector-number case fix) and SPA-015 (bundled data and import path) after SPA-013. SPA-016 (shell and first end-to-end import) after both, so the app shows collector-list results only with the fix in. Then the screens: SPA-017 (Pool), SPA-018 (Results), SPA-020 (About), then SPA-019 (Pair detail), SPA-021 (share and Open with), and SPA-022 (remembered pool and Clear pool) last. SPA-023 (README) needs only SPA-013.
- `serialize_with` follows shared files: `PoolViewModel.kt` (016, 017, 021, 022), `ResultsScreen` and `PoolHeaderCard` (016, 018, 022), `PoolEntryScreen` and `ImportErrorCard` (016, 017, 022), `AndroidManifest.xml` (016, 021, 022), `results/` shared components (018, 019), and `app/build.gradle.kts` (015, 016). Each screen keeps its copy in its own `strings_<screen>.xml`, so parallel tickets don't share a resource file.
- Every snapshot and accessibility item in the spec is assigned to a ticket's test plan: A1, A4, and A5 through SPA-016's shared `A11yChecks`, applied by each screen ticket.
- The emulator goal criteria (5, 6, 7, 8, 13, 16) are run in part by each ticket's Acceptance Tester on the pools it names. At goal close, an Acceptance Tester runs each of them in full on `main` (all 11 ManaBox pools for criterion 5, every paste fixture for criterion 7, and the five timed runs for criterion 13).
- 2026-11-03 SPA-013 integrated (PR #18, b868937) on attempt 1, after re-plan 1 of 2 (criterion 9: ATF skips Compose under Robolectric, so a semantics-tree check replaced it, and spec A4 changed in PR #17). Added SPA-024, which fixes the gate's ui-tests `reproduce` command (missing `:app`), found by SPA-013's Acceptance Tester. G-002 now has 12 tickets.
- 2026-11-03 Wave 2 integrated: SPA-014 (PR #19), SPA-015 (PR #20), SPA-024 (PR #21), each on attempt 1 with both verdicts PASS. Added SPA-025 (the baseline timing test failed the pre-push gate under PC load, 74.8 s, and took 11.7 s idle). The owner approved one `--no-verify` push of two text-only commits; CI on main and an idle local gate run then both passed. G-002 now has 13 tickets. Wave 3: SPA-016, SPA-023, SPA-025.
- 2026-11-03 Owner: run Developers one at a time from here, because parallel Developers overloaded this PC. Order after SPA-016: SPA-025, SPA-023, SPA-017, SPA-018, SPA-020, SPA-019, SPA-021, SPA-022. serialize_with still holds; a ticket's two testers may run together.
- 2026-11-04 Re-plan: SPA-016's Acceptance Tester found that the app crashes on the device because Android has no `java.nio.file.Files.readString`, which `core`'s loaders call. The JVM gate cannot see it. Added SPA-026: core library desugaring in the app build, no `core` change, with an on-device instrumented test. If desugaring cannot supply the method, the alternative is replacing `Files.readString` in `core` with `readAllBytes` (behaviour-identical), which is a `core` change and the owner's decision. G-002 now has 14 tickets. Order: SPA-026, then SPA-016 again, then SPA-025, SPA-023, SPA-017, SPA-018, SPA-020, SPA-019, SPA-021, SPA-022.
- 2026-11-04 Re-plan: desugaring fixes `Files.readString` on the device (SPA-026), but `core`'s mana-symbol regex `\{([^{}]*)}` does not compile on Android's ICU engine. Added SPA-027, three escaped braces in `core`, held for the owner's decision. Until then: SPA-026 merges, then SPA-025 and SPA-023 run; SPA-016 and every later screen ticket wait. G-002 now has 15 tickets.
- 2026-11-04 Overnight run: SPA-026 (PR #23), SPA-025 (PR #24), and SPA-023 (PR #25) integrated, one Developer at a time. 7 of 15 tickets integrated (SPA-013, 014, 015, 023, 024, 025, 026). SPA-016 and every later screen ticket (017 to 022) wait on SPA-027, the core regex fix held for the owner. Nothing else can dispatch until the owner decides.
- 2026-11-04 Owner approved SPA-027 (the core regex fix). It integrated as PR #26 on attempt 1. 8 of 15 tickets integrated (SPA-013, 014, 015, 023, 024, 025, 026, 027). Next: SPA-016 attempt 2, then 017, 018, 020, 019, 021, 022, one Developer at a time.
- 2026-11-04 SPA-016 integrated (PR #22) on attempt 2: paste import works end to end on spa-test. 9 of 15 tickets integrated (SPA-013, 014, 015, 016, 023, 024, 025, 026, 027). Next: SPA-017, then 018, 020, 019, 021, 022, one Developer at a time.
- 2026-11-04 SPA-017 integrated (PR #27): Pool screen complete with file picker, format chooser, and error cards, checked on spa-test. 10 of 15 tickets integrated. Next: SPA-018 (Results body).
- 2026-11-04 SPA-018 integrated (PR #28): Results screen complete beside the baseline, checked on spa-test against spa analyze. 11 of 15 tickets integrated. Next: SPA-020 (About), then 019, 021, 022.
- 2026-11-04 SPA-020 integrated (PR #29): About screen with Scryfall attribution, checked on spa-test. SPA-028 (README.md as a unit-test input) added from its Test Inspector's finding. 12 of 16 tickets integrated. Next: SPA-019 (Pair detail), then SPA-028, SPA-021, SPA-022.
- 2026-11-04 SPA-019 integrated (PR #30) on attempt 2: Pair detail checked on spa-test for all ten pairs. 13 of 16 tickets integrated. Next: SPA-028, then SPA-021, SPA-022.
- 2026-11-04 SPA-028 integrated (PR #31): README.md is a unit-test input. 14 of 16 tickets integrated. Next: SPA-021, then SPA-022.
- 2026-11-04 SPA-021 integrated (PR #32): share and Open with, checked on spa-test. 15 of 16 tickets integrated. Next: SPA-022, the last.
- 2026-11-04 SPA-022 integrated (PR #33) on attempt 3 after two re-plans: remembered pool, Clear pool, backup exclusion, checked on spa-test including a reboot. 16 of 16 tickets integrated. Next: the goal-level closing run on spa-test, then the owner's Pixel 6 check.
- 2026-11-04 Goal-level acceptance run on main a2d5d64 from a fresh clone: PASS on criteria 1 to 13 and 15 to 17. Gate green (330 UI tests, the spec's 16 snapshots); one command builds `app-debug.apk`; `core/src/main` holds only SPA-014 and SPA-027's changes and no Android dependency; all 11 ManaBox pools through the picker match `spa analyze` (0 mismatches in 385 values); share and Open with match; all 12 paste fixtures detect the CLI's format and match; problems, error cards, airplane mode, no key, About, accessibility trace, remembered pool through force-stop and reboot, Clear pool, backup exclusion, and case-insensitive collector numbers all pass. Timing medians: import to results 394 ms (limit 3000), cold start to first screen 1160 ms (limit 5000). Screenshots kept in the Architect's scratchpad. Criterion 14 (the owner's Pixel 6) is open; the goal closes when it passes.

### Metrics (goal-level run, 2026-11-04)

| Metric | G-002 |
| --- | --- |
| Tickets integrated | 16 (SPA-013 to SPA-028) |
| Gate green on first push | 16 of 16 (no red gate recorded) |
| Bounces by source | Developer to Architect before code: 5 (SPA-013 ATF tool; SPA-026 core regex found on device; SPA-019 no pool path; SPA-022 twice, unnamed baselines and discarded file text). Acceptance: 1 (SPA-016, device crash on `Files.readString`). Inspector: 0 |
| Re-plans | 7 across 6 tickets (SPA-013, 016, 019, 025, 026 once each; SPA-022 twice, its limit) |
| Escalations to the human | 0 from limits. One owner decision outside the pipeline's authority: the `core` change in SPA-027 |
| Lead time | About 19 hours from SPA-013's dispatch (2026-11-03 19:07) to SPA-022's merge (2026-11-04), one Developer at a time from SPA-016 on |
| Tokens per merged ticket by role | Not measured this goal |
| Criteria proven by deterministic tests | 9 of 17 (1, 2, 3, 4, 10, 11, 12, 15, 17); 7 by emulator scenario (5, 6, 7, 8, 9, 13, 16); 1 by the owner (14) |
| Acceptance screenshot-pass bounces | 0 |
| States not in the design spec | 6, none blocking: `rare-count` kit warning shows its raw code; `unparsed` reason has no words; Problems with only kit warnings has no summary line; clean Problems outline vs the spec's accent border; "Pool cleared" snackbar uses light colours; default launcher icon. Spec updates go to the owner |
| Snapshot baselines changed on purpose | 5 (SPA-022 re-plan 1: results_clean, results_clean_fontScale200, results_problems, results_problems_fontScale200, results_wrongSetLines) |
| Bounces caused by ticket gaps vs code | All 5 bounces before code were ticket or upstream gaps; no bounce was a code defect caught by an inspector |
- 2026-11-04 Owner decision: the look and feel is revisited after the functionality is ironed out, in a later goal, taking inspiration from the MTG Arena app (familiar to players). The six states not in the spec go to that goal rather than spec updates now. The owner is installing the APK on a spare Pixel 6 for criterion 14.
- 2026-11-04 Criterion 14 PASS: the owner installed `app-debug.apk` built from main on a spare Pixel 6, opened `trk-manabox-01.csv` through Open file, and confirmed the app showed the correct numbers (85 cards in 71 entries; White–Blue 33 cards, 19 creatures), matching `spa analyze`. All 17 criteria pass. G-002 closed.
