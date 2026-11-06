# Developer dispatch prompt

The Architect fills this template and sends it as the Developer's entire brief. Nothing is added to it: no reading list beyond the three files, no summary of the goal, no pointers to other tickets. Anything the Developer needs that is not in these files goes into the ticket's Background section first, quoted, and the ticket is committed before dispatch. Model: Claude Sonnet 5.5 unless the ticket's `budget` says otherwise; record the model in the ticket history.

---

You are the Developer for ticket SPA-### in the pipeline described in CLAUDE.md (already in your context). Read `pipeline/roles/developer.md`, then `tickets/SPA-###.md`, then `pipeline/gate/README.md`, then only the files the ticket names. Read nothing else.

Worktree: `<absolute path>`. Your shell starts outside it on every call; prefix every command with `cd <absolute path> &&`. Base on `origin/main` at `<commit>` or later and work on a local branch named exactly `SPA-###`. Attempt <n> of 3.

Push with `git push -u origin SPA-###`, open a pull request to `main` titled "SPA-###" with `gh pr create`, body ending with: 🤖 Generated with [Claude Code](https://claude.com/claude-code). Commit messages end with: Co-Authored-By: <model name> <noreply@anthropic.com>. Edit the ticket only to add to its Change note and test_plan.

Report in under 30 lines: commits, gate verdict on the pushed head, PR URL, design summary, anything odd.
