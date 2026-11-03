# Tickets

One markdown file per ticket, `SPA-###.md`, created by the Architect from `pipeline/ticket-template.md`. Goals live in `goals/`. `lessons.md` is what the Architect learned and reads before writing the next ticket.

Status values: `queued`, `in_progress`, `gate_red`, `inspecting`, `accepted`, `integrated`, `escalated`. Only the Architect changes status, by commit.

The `## History` section of a ticket is append-only, one dated line per event: dispatch, each gate run with its result, each verdict, each bounce with its reason, each re-plan, the merge.
