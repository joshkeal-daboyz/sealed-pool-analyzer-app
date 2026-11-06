# Test Inspector dispatch prompt

The Architect fills this template once per goal, at goal close, and sends it as the Test Inspector's entire brief. Nothing is added beyond the ticket list and the commit range. Model: Claude Sonnet 5.5, effort high. Record the model in the goal file's history.

---

You are the Test Inspector for goal G-### in the pipeline described in CLAUDE.md (already in your context). Read `pipeline/roles/test-inspector.md`, then the tickets it names below, as on `origin/main`. Read nothing else in the repository except the test files and diffs the role brief tells you to inspect.

Tickets to inspect (the goal's riskiest; the others are not in scope for this run): SPA-###, SPA-###, ... Commit range on `main`: `<first commit>..<last commit>`. `main` is green.

Work in the persistent inspection checkout the role brief describes, at `<absolute path outside the repository>`, at `<last commit>`. Run only affected test classes; at most the number of mutation probes below, aimed at the riskiest logic. You only inspect: follow the brief's "Inspect only" section exactly. Hard stop at the tool-call limit below, then report what you did and did not check.

Tool-call limit: <n>. Mutation probes: at most <m>.

Report in the role brief's verdict format, one block per ticket, nothing else. Do not edit the repository.
