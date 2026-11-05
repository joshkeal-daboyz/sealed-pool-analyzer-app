# Owner synergy checks

Held-out tests of the synergy tags and the pair table, one file per set. The owner supplies the examples; the Architect turns each into a check. No packet, prompt or scoring rule reads these files. If a check fails, the Architect reports it to the owner; a fix must be general, and once a fix has seen a check, that check's `status` becomes `seen` (G-005 Architect notes, 2026-11-05).

Each check has:
- `id`;
- `owner`: the owner's words;
- `added`;
- `status`: `independent` or `seen`;
- `kind`, one of:
  - `pair`: `score(a, b) >= min`;
  - `groups`: more than `share` of the pairs (a in `groupA`, b in `groupB`, a ≠ b) have `score >= min`. The groups are built from card text by the rule in `listRule`.

`spa synergy-check --set <code>` runs them (SPA-050).
