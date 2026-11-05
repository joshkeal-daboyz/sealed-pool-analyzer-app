# Synergy tag files

Each file, `<code>.json`, is a set's synergy tags (`agent/src/main/resources/spa/agent/tags/tags.schema.json`): the set's synergy axes, each with an id, a name and a one-line definition, and for every card of the sealed pool (the set and its companions, basic lands excluded, one entry per distinct card, in collector order) its roles on those axes, `enabler` or `payoff` with a strength of 1 to 3, and up to 5 named partners, each a card of the pool (the card itself allowed) with a strength of 1 to 3. The provenance names the model, the tags prompt version and the date. The files are read with `TagFiles.read`. From tags prompt v2 each axis also carries `suppliedBy`: the card facts (`spa facts`) that supply it just by a card having them, each with a strength of 1 to 3, possibly none; a set then has 3 to 20 axes. A v1 file has no `suppliedBy` and reads and scores exactly as before. A reviewed file also has a `review` block (pass 3, below), which scoring ignores.

The four committed files, `trk`, `msh`, `hob` and `fra`, were made with tags prompt v1.

## How a tag file is made

No API key and no model call in this repository. The tagging is a Claude Code agent reading two packets in turn, written outside the repository.

1. Write the pass-1 packet, from the repository root:

   ```bash
   ./gradlew :cli:installDist
   cli/build/install/spa/bin/spa tags-packet <code> --out <dir>
   ```

   `<dir>` gets `instructions.md` (the axes prompt `tags-axes-v2.md`, then the set's format brief, theme counts and card fact counts), `axes.schema.json`, `facts.json` (each card's facts), and the cards in `cards-01.json`, `cards-02.json`, ..., 50 per file, the same files `grade-packet` writes. The packet holds no win rates, no 17Lands data and no reviews.

2. A tagging agent (Claude Fable in Claude Code) is told to read `<dir>/instructions.md` and follow it. It reads every card file, uses no web or other tool, and writes `<dir>/axes.response.json`.

3. Write the pass-2 packet into the same directory:

   ```bash
   cli/build/install/spa/bin/spa tags-packet <code> --axes <dir>/axes.response.json --out <dir>
   ```

   The axes file is checked first (3 to 20 axes, distinct ids of lowercase letters, digits and hyphens, each with a name, a definition and a `suppliedBy` list whose facts some card of the set has, each once, with a strength of 1 to 3); on a problem nothing is written, so fix the axes file or rerun pass 1. Otherwise `instructions.md` becomes the roles prompt `tags-roles-v2.md` with the axes and their supply listed, and `response.schema.json` and `axes.json` are added beside `facts.json`.

4. A tagging agent is told to read `<dir>/instructions.md` and follow it. It writes `cards-NN.response.json` beside each card file. Record each run's token count and the plan's weekly usage before and after.

5. Import and check the responses:

   ```bash
   cli/build/install/spa/bin/spa tags-import <code> --packet <dir> --provenance "claude-fable-5-1 via Claude Code, tags prompt v2, <yyyy-mm-dd>"
   ```

   It writes `fixtures/tags/<code>.json` only when every card is tagged exactly once, every axis is the packet's, every role and strength is allowed, no card repeats an axis and role, every partner is a pool card, at most 5 and none twice, and every axis's `suppliedBy` passes the checks of step 3. The provenance names the prompt version; write the one the packets used. Otherwise it prints one line per problem and writes nothing; fix or retag the named files and import again.

## How a tag file is reviewed (pass 3)

After step 5, a fresh agent reviews the tags. Still no API key and no model call in this repository.

1. Write the review packet, outside the repository:

   ```bash
   cli/build/install/spa/bin/spa tags-review-packet <code> --out <review-dir>
   ```

   `<review-dir>` gets `instructions.md` (the review prompt `tags-review-v1.md`, then the axes with their supply, the format brief and theme counts), the card files, `tags-NN.json` beside each (the cards' current roles and partners), `facts.json`, `tag-file.json` (which tag file the packet was built from, and the SHA-256 of its bytes), `review.schema.json` and `fact-suggestions.schema.json`. Like the tagging packets, it holds no win rates, no 17Lands data, no reviews and none of the owner's synergy checks.

2. A fresh review agent (Claude Fable in Claude Code) is told to read `<review-dir>/instructions.md` and follow it. It writes a `cards-NN.review.json` beside every card file, possibly an empty array, each correction an action (`add-role`, `remove-role`, `set-strength`, `add-partner`, `remove-partner`) with a one-line reason, and, if it has any, `fact-suggestions.json`. Record the run's token count and the plan's weekly usage before and after.

3. Import the corrections:

   ```bash
   cli/build/install/spa/bin/spa tags-review-import <code> --packet <review-dir> --provenance "claude-fable-5-1 via Claude Code, tags review prompt v1, <yyyy-mm-dd>"
   ```

   It stops with one line if the tag file changed since step 1. It writes `fixtures/tags/<code>.json` only when every correction names a card of its card file, has a reason, removes or changes only what the card has and adds only what it lacks, and the corrected tags pass every check of step 5. The file keeps its set, axes and tagging provenance, and gets a `review` block: the review provenance, the count of applied corrections by action, and each applied correction with its reason. The fact suggestions are printed, one per line, for the Architect; they are never applied, because facts are code (`spa facts`).

A new prompt version is a new pair of resource files, `tags-axes-<version>.md` and `tags-roles-<version>.md`, used with `--prompt <version>` in steps 1 and 3. The default is `v2`; `--prompt v1` writes the v1 packets, with no facts and no `suppliedBy` (3 to 15 axes), exactly as the committed files were made.
