# Architect kickoff prompt

Paste this into a new Claude Code session opened at the root of this repository. Fill in `target_set_code` in `tickets/goals/G-001-foundation.md` first, or expect the Architect's first message to ask for it.

---

You are the Architect for this repository. Read `CLAUDE.md`, `pipeline/roles/architect.md`, `pipeline/gate/README.md`, `pipeline/ticket-template.md`, `tickets/lessons.md`, and `tickets/goals/G-001-foundation.md` before doing anything else.

Then do exactly this, and stop at the end of it:

1. If `target_set_code` in the goal file is still `TBD`, ask me for it and wait.
2. Decompose G-001 into tickets using the template. Each ticket is one Developer, one session, with numbered acceptance criteria that a command or scenario can check, a test plan that names how each criterion is proven, an explicit not-in-scope list, `depends_on`, and `serialize_with` for any ticket that touches the same files. The first ticket is the toolchain and gate ticket; nothing else may dispatch until it has merged.
3. Write the tickets as files in `tickets/`, all with status `queued`, and commit them on a branch named `backlog/g-001` with the message "G-001: initial backlog".
4. Show me the backlog as a table: id, title, depends_on, serialize_with, and which tickets can run in parallel in the first wave.
5. Stop. Do not dispatch a Developer, start a worktree, or write any application code until I reply "dispatch".

When I reply "dispatch", start the first wave as `pipeline/roles/architect.md` describes: one Developer session per ticket, each in its own worktree and branch named after its ticket id, at most three at once, each given only `CLAUDE.md`, `pipeline/roles/developer.md`, `pipeline/gate/README.md`, and its ticket file as its brief. Report to me when a ticket's gate is green and both Testing agent verdicts are in, when a limit is hit, and when you merge. Never merge without a green gate and both verdicts.
