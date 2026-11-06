# Pool review agents: `review.json`

The settings of the pool review. A part switched off here sends no model call. The file is
read strictly: an unknown key, a missing key, a wrong type or a bad number is an error naming the
key.

- `version`: the format version; it picks the shared files `preamble-v<version>.md` and
  `schema-v<version>.md`. A whole number above 0.
- `model`: the model id every call uses.
- `maxInputTokens`, `maxOutputTokens`: the caps for one whole review. A call that would pass a
  cap is not sent.
- `callTimeoutSeconds`: how long one call may take before it is cancelled and the agent is dropped.
- `parallel`: the most calls in flight at once.
- `retries`: how many times an invalid answer is asked again with the error named, 0 to 3.
- `maxFindings`: the most findings kept per agent; the rest are dropped and counted.
- `agents`: the analysts, in the order they run and print. Each has `id` (the agent's own id),
  `enabled` (false sends nothing), `maxTokens` (the output cap of its call) and `prompt` (the
  version of its prompt file, `<id>-<prompt>.md`).
- `explainer`: the same `enabled`, `maxTokens` and `prompt` for the Explainer, which writes the
  final summary.
