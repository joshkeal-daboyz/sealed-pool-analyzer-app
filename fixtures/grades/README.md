# Model grade files

Each file is a grade file (`agent/src/main/resources/spa/agent/grades.schema.json`) with source `MODEL`: one grade for every card in a set's sealed pool (the set and its companions, basic lands excluded), each a score from 0.0 to 5.0 in steps of 0.5, relative to the set, and a confidence from 0 to 1. The header's provenance names the model, the prompt version and the date.

## How a grade file is made

No API key and no model call in this repository. The grading itself is a Claude Code agent reading a packet.

1. Write the blind packet outside the repository, from the repository root:

   ```bash
   ./gradlew :cli:installDist
   cli/build/install/spa/bin/spa grade-packet <code> --out <dir>
   ```

   `<dir>` gets `instructions.md` (the grading prompt `prompt-v1.md`, then the set's format brief), `response.schema.json`, and the cards in `cards-01.json`, `cards-02.json`, ..., 50 per file. The packet holds no win rates, no 17Lands data and no reviews.

2. A grading agent (Claude Fable 5.1 in Claude Code) is told to read `<dir>/instructions.md` and follow it. It reads every card file, uses no web or other tool, and writes `cards-NN.response.json` beside each card file. Record the run's token count and the plan's weekly usage before and after.

3. Import and check the responses:

   ```bash
   cli/build/install/spa/bin/spa grade-import <code> --responses <dir> --provenance "claude-fable-5-1 via Claude Code, prompt v1, <yyyy-mm-dd>"
   ```

   It writes `fixtures/grades/<code>.json` only when every card is graded exactly once, no id is unknown, every score is on the scale and every confidence is in [0, 1]. Otherwise it prints one line per problem and writes nothing; fix or regrade the named files and import again.

A changed prompt is a new file (`prompt-v2.md`) and a new provenance; a file graded with one prompt is never edited by hand.
