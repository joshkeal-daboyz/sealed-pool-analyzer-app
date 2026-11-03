# Lessons

What the Architect learned from merged tickets. One line each, newest first. Read before writing any ticket.

- 2026-11-03 SPA-001: The gate does not find a JDK on its own, and sessions launched by the desktop app can predate the JDK install. Every ticket's background says how to set `JAVA_HOME` (README, Toolchain section).
- 2026-11-03 SPA-001: When one input drives two behaviours (ticket id `none` sets both the dependency rule and coverage threshold 0), give each behaviour its own test-plan row; the Inspector found the second one untested because the plan named only the resolver.
- 2026-11-03 SPA-001: The gate's `ui-tests` step fails once an `:app` project exists. The ticket that creates the app module must also implement that step, and say so in its scope.
- 2026-11-03 SPA-001: detekt runs its default rules on every new file, scratch files included. Criteria that say "add a scratch file" should expect it to pass lint, or the scenario fails at the wrong step.
