# Rules for every agent in this repository

You are one role in a pipeline. Find your role brief in `pipeline/roles/` and follow it. These rules apply to all roles.

## The pipeline in one paragraph

A goal in `tickets/goals/` is decomposed by the Architect into tickets in `tickets/`. A Developer implements one ticket in its own git worktree and branch, writes the unit tests for its change, and runs the automated gate (`pipeline/gate/`) before pushing. A red gate bounces the ticket to the Developer with the failure list; no model reads a red branch. A green branch goes to the Test Inspector (reads the diff, judges whether the tests are honest) and the Acceptance Tester (never reads the diff, checks the ticket's acceptance criteria black-box). The Architect merges when CI is green and both verdicts pass. Exhausted retries climb to the Architect's re-plan, and then to the human.

## Principles

1. The ticket is the contract. Implement what it says and nothing more. Scope creep is a bounce to the Architect, not a bigger change.
2. Tests ship with the code. Code that cannot be unit tested must say why in the ticket's change note.
3. Deterministic before probabilistic. No model reviews a branch that has not passed the gate. The gate's output is the first thing a Testing agent reads.
4. One writer per set of files. Tickets that touch the same files carry `serialize_with` and never run at the same time.
5. Bounded retries. Every stage has a limit; hitting it escalates, never loops. Defaults: 3 Developer attempts, 2 inspector bounces, 2 re-plans.
6. Nothing merges without a green gate and both Testing agent verdicts. The Architect merges; it does not skip steps.

## Tickets

- One file per ticket: `tickets/SPA-###.md`, made from `pipeline/ticket-template.md`.
- `status` is one of `queued`, `in_progress`, `gate_red`, `inspecting`, `accepted`, `integrated`, `escalated`. Only the Architect changes it, by commit, until a script owns it.
- `## History` is append-only. Every gate run, verdict, bounce, and retry count is a dated line there.
- A Developer never edits a ticket except to add to `test_plan` (never remove) and to write `## Change note`.

## Where things live

- `tickets/goals/G-###.md`: goals, with goal-level acceptance criteria. The human writes these.
- `tickets/lessons.md`: what the Architect learned; read it before writing any ticket.
- `design/`: Designer specs, cited by a ticket's `design_ref`. Only UI tickets have one.
- `pipeline/gate/`: the gate contract and, once a ticket creates it, the gate script. The same script runs locally (pre-push) and in CI.
- `fixtures/`: pools used by tests. Real pools from events go here as ManaBox-format CSVs.

## What a model never does here

- Review or inspect a branch whose gate is red.
- Re-run the gate hoping it passes this time. A flaky test is a ticket.
- Read the whole repository to find a failure. The gate verdict names the file and line.
- Lower a coverage threshold, delete a failing test, or edit a stored snapshot baseline without the ticket saying so.

## Product constraints that bind every ticket

- The core library (`core/`) is pure Kotlin with no Android dependencies, so it is tested on the JVM.
- No model calls in the core library's metric engine. Model calls happen only in the agent layer, later.
- Card data comes from Scryfall and carries attribution. Play data from 17Lands is non-commercial. The tool is free.
- Users supply their own Anthropic API key later; nothing in this repository ever embeds one.
