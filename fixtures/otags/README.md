# Scryfall oracle tags

Card tags from Scryfall Tagger (tagger.scryfall.com), from Scryfall's Oracle Tags bulk file (SPA-054).

- `<code>.json` (`trk`, `msh`, `hob`, `fra`): each card of the set's sealed pool, in collector order, with its oracle tags and every ancestor tag through `parent_ids`, sorted. Tags of other types (artwork and the like) are left out. Written by `spa fetch-tags --set <code> --bulk <file>`; `source` names the bulk file and `updatedAt` is its last-modified time (a download sets it to Scryfall's `updated_at`).
- `cares.json`: the cares table, curated by the Architect. Each Scryfall "cares about" tag maps to the facts it cares about, each with a strength from 1 to 3. A card with such a tag gets `cares:<fact>` at that strength (the largest when two of its tags give the same fact). A fact is a code fact name (`land`, `legendary`, `kw:station`, ...) or `otag:<slug>`. A new tag is a new line here, with no code change; `spa fetch-tags` names every fact that is not a known fact name and then writes nothing.

The four committed set files were generated from the Oracle Tags bulk file of 2026-11-05 (Scryfall's 22:00 UTC update, downloaded at 23:33 UTC, which is the `updatedAt` they carry). Scryfall updates the file daily, so a fresh `spa fetch-tags --set <code>` gives different tags and different numbers; tests pin the committed files by their counts.

`spa facts` and the synergy commands read `<code>.json` and `cares.json` by default when the set's file exists: each tag becomes the fact `otag:<slug>`, and the cares tags feed the cares term of the pair score. `--otags <file>` reads another file, and `--otags none` reads none, as for a set with no file. `pool-grades` and `deck-pick` do not read otags.
