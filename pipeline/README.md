# Pipeline files

This folder holds the pipeline's own artifacts. The design behind it is in the design-doc repository, `docs/agentic-development-pipeline.md`; this folder is the working copy agents read.

| File | Purpose |
| --- | --- |
| `roles/architect.md` | Decomposes goals, writes tickets, dispatches, merges, re-plans, escalates |
| `roles/developer.md` | Implements one ticket in a worktree, writes tests, runs the gate, opens the PR |
| `roles/designer.md` | Writes the design spec for a feature with a UI, once, before its tickets |
| `roles/test-inspector.md` | Reads the diff; judges whether the tests prove the criteria |
| `roles/acceptance-tester.md` | Never reads the diff; proves the criteria black-box |
| `gate/README.md` | What the gate runs, in order, and the verdict it writes |
| `ticket-template.md` | The ticket contract as a file |
| `prompts/architect-kickoff.md` | What the human pastes to start a goal |

Budgets and limits, as defaults the Architect tunes per ticket:

| Stage | Limit | On limit |
| --- | --- | --- |
| Developer, gate red | 3 attempts | Escalate to Architect with the gate history |
| Test Inspector bounce | 2 bounces | Escalate to Architect: the test plan was likely wrong |
| Acceptance Tester bounce | 2 bounces | Escalate to Architect: a criterion is likely untestable or ambiguous |
| Architect re-plan | 2 per ticket | Escalate to the human with the full history |
| Developers running at once | 3 | Queue the rest |
