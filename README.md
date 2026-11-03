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
