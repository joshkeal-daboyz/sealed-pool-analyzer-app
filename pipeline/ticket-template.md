---
id: SPA-000
goal: G-000
title: One line, imperative
status: queued
depends_on: []
serialize_with: []
design_ref: none
budget:
  developer_attempts: 3
  inspector_bounces: 2
  replans: 2
  wall_clock: 4h
coverage_threshold: 80
---

# SPA-000: One line, imperative

**Goal.** Which sentence of G-000 this advances.

## Scope

- Files or modules expected to change:
- Not in scope:
- Dependencies this ticket may add (none unless listed):

## Background the Developer needs

(Architect: everything the Developer must know that is not in the criteria, quoted word for word from the goal, `tickets/lessons.md`, earlier tickets, the README or the design brief. Name the exact source files to read. The Developer reads this section and those files, and nothing else in the repository. Over about 60 lines, split the ticket.)

- Files to read:
- From the goal:
- From earlier tickets and lessons:

## Acceptance criteria

1. Checkable by a command or scenario.
2. ...

## Test plan

| Criterion | Proven by | Order |
| --- | --- | --- |
| 1 | unit test / Compose UI test / snapshot test / Acceptance Tester scenario / cannot be automated because ... | tests-first / gate / acceptance |
| 2 | ... | ... |

`tests-first`: a failing test is committed before the code, the default for every unit, Compose UI, and snapshot test. `gate`: a gate step proves it on its own (toolchain, build, and wiring criteria). `acceptance`: only the Acceptance Tester can prove it. A ticket written before this column existed is read as `tests-first` for every unit, Compose UI, or snapshot test it names.

## Change note

(Developer writes three to five lines here: what changed, what it chose not to do, what the next ticket should know.)

## History

- YYYY-MM-DD Architect: created from G-000.
