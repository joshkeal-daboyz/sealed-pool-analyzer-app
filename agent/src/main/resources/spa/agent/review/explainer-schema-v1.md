Answer with one JSON object and nothing else:
{
  "confidence": <number from 0 to 1>,
  "verdict": "<at most four sentences>",
  "decks": [ { "rank": <1, 2 or 3>, "note": "<one or two sentences>", "cards": ["<exact card name>", ...] } ],
  "disagreements": [ { "topic": "<short>", "agents": ["<agent id>", "<agent id>"], "cards": ["<exact card name>", ...], "note": "<one or two sentences>" } ]
}
