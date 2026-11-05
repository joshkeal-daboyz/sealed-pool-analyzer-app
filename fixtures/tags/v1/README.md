# Frozen v1 tag files

`trk.json`, `msh.json`, `hob.json` and `fra.json` are byte-for-byte copies of `fixtures/tags/<code>.json` as they were made with tags prompt v1, before the v2 tag files replaced them (SPA-053). They are frozen test data: never edit, regenerate or replace them.

A test that pins a number or a line computed from a tag file reads one of these files (through `--tags <file>` on the synergy commands, or `--tags` on `tags-review-packet`), or a hand-built tag file, never the live `fixtures/tags/<code>.json`, so the live files can change without changing a pinned value. Tests that only check structure may keep reading the live files.

No command reads this directory by default.
