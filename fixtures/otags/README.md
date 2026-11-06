# Scryfall oracle tags

Card tags from Scryfall Tagger (tagger.scryfall.com), from Scryfall's Oracle Tags bulk file (SPA-054).

- `<code>.json` (`trk`, `msh`, `hob`, `fra`): each card of the set's sealed pool, in collector order, with its oracle tags and every ancestor tag through `parent_ids`, sorted. Tags of other types (artwork and the like) are left out. Written by `spa fetch-tags --set <code> --bulk <file>`; `source` names the bulk file and `updatedAt` is its last-modified time (a download sets it to Scryfall's `updated_at`).
- `cares.json`: the cares table, curated by the Architect. Each Scryfall "cares about" tag maps to the facts it cares about, each with a strength from 1 to 3. A card with such a tag gets `cares:<fact>` at that strength (the largest when two of its tags give the same fact). A fact is a code fact name (`land`, `legendary`, `kw:station`, ...) or `otag:<slug>`. A new tag is a new line here, with no code change; `spa fetch-tags` names every fact that is not a known fact name and then writes nothing.

The four committed set files were generated from the Oracle Tags bulk file of 2026-11-05 (Scryfall's 22:00 UTC update, downloaded at 23:33 UTC, which is the `updatedAt` they carry). Scryfall updates the file daily, so a fresh `spa fetch-tags --set <code>` gives different tags and different numbers; tests pin the committed files by their counts.

`spa facts` and the synergy commands read `<code>.json` and `cares.json` by default when the set's file exists: each tag becomes the fact `otag:<slug>`, and the cares tags feed the cares term of the pair score. `--otags <file>` reads another file, and `--otags none` reads none, as for a set with no file. `pool-grades` and `deck-pick` do not read otags.

## Tags file and Fable's cares (SPA-055)

- `<code>.tags.json`: each tag present in the set's `<code>.json` (ancestors included), sorted by slug, with Scryfall's `description` (null when it has none) and its `parents` (slugs, sorted). Written by `spa fetch-tags` from the same bulk file, with the same `source`, `updatedAt` and `attribution`, so the same bulk file gives the same bytes. `spa cares-packet` reads it for `vocabulary.json` and to find the name tags: `name-matters` and every tag whose parents lead to it, which stay out of the packet's vocabulary and `otag:` facts.
- `<code>.fable.json`: the cares tags Fable added where Scryfall's tags leave a gap, `{ "set", "provenance", "cards": [{ "id", "cares": [{ "fact", "strength", "reason" }] }] }`, only the cards with additions. Written by `spa cares-import` after a checked run of `spa cares-packet`; the synergy commands and `spa facts` join its cares with the cares table's, keeping the larger strength per fact. A set without the file scores as before.

## Frozen trk otags and trk's Fable file (SPA-058)

- `v1/trk.json`: a byte copy of `trk.json` as committed by SPA-054, with no Fable file beside it. Tests that pin numbers on trk's otags without Fable's cares (`CaresScoringTest`, `OtagsFixturesTest`) read it, so `trk.json` can be regenerated. A test compares the two files byte for byte until that happens; the regeneration ticket then drops that comparison.
- `trk.fable.json`: trk's Fable cares file, 105 cards with 180 additions, from a Claude Fable 5.1 run of 2026-11-06 written by `spa cares-import`. A test pins its SHA-256 and counts, and that the live trk otags with it give 186 cards with a cares tag, against 109 without.
