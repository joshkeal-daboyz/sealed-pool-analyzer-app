# Synergy tag files

Each file, `<code>.json`, is a set's synergy tags (`agent/src/main/resources/spa/agent/tags/tags.schema.json`): the set's synergy axes, each with an id, a name and a one-line definition, and for every card of the sealed pool (the set and its companions, basic lands excluded, one entry per distinct card, in collector order) its roles on those axes, `enabler` or `payoff` with a strength of 1 to 3, and up to 5 named partners, each a card of the pool (the card itself allowed) with a strength of 1 to 3. The provenance names the model, the tags prompt version and the date. The files are read with `TagFiles.read`.

No tag file is committed yet.

## How a tag file is made

No API key and no model call in this repository. The tagging is a Claude Code agent reading two packets in turn, written outside the repository.

1. Write the pass-1 packet, from the repository root:

   ```bash
   ./gradlew :cli:installDist
   cli/build/install/spa/bin/spa tags-packet <code> --out <dir>
   ```

   `<dir>` gets `instructions.md` (the axes prompt `tags-axes-v1.md`, then the set's format brief and theme counts), `axes.schema.json`, and the cards in `cards-01.json`, `cards-02.json`, ..., 50 per file, the same files `grade-packet` writes. The packet holds no win rates, no 17Lands data and no reviews.

2. A tagging agent (Claude Fable in Claude Code) is told to read `<dir>/instructions.md` and follow it. It reads every card file, uses no web or other tool, and writes `<dir>/axes.response.json`.

3. Write the pass-2 packet into the same directory:

   ```bash
   cli/build/install/spa/bin/spa tags-packet <code> --axes <dir>/axes.response.json --out <dir>
   ```

   The axes file is checked first (3 to 15 axes, distinct ids of lowercase letters, digits and hyphens, each with a name and a definition); on a problem nothing is written, so fix the axes file or rerun pass 1. Otherwise `instructions.md` becomes the roles prompt `tags-roles-v1.md` with the axes listed, and `response.schema.json` and `axes.json` are added.

4. A tagging agent is told to read `<dir>/instructions.md` and follow it. It writes `cards-NN.response.json` beside each card file. Record each run's token count and the plan's weekly usage before and after.

5. Import and check the responses:

   ```bash
   cli/build/install/spa/bin/spa tags-import <code> --packet <dir> --provenance "claude-fable-5-1 via Claude Code, tags prompt v1, <yyyy-mm-dd>"
   ```

   It writes `fixtures/tags/<code>.json` only when every card is tagged exactly once, every axis is the packet's, every role and strength is allowed, no card repeats an axis and role, and every partner is a pool card, at most 5 and none twice. Otherwise it prints one line per problem and writes nothing; fix or retag the named files and import again.

A new prompt version is a new pair of resource files, `tags-axes-<version>.md` and `tags-roles-<version>.md`, used with `--prompt <version>` in steps 1 and 3.
