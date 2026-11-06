Answer with one JSON object and nothing else:
{
  "agent": "<your agent id>",
  "confidence": <number from 0 to 1>,
  "summary": "<two sentences at most>",
  "findings": [
    {
      "claim": "<one or two sentences>",
      "pair": "<two colour letters such as WU, or null>",
      "severity": "info" | "warn" | "key",
      "cards": ["<exact card name>", ...],
      "numbers": [ { "key": "<key from the number table>", "value": <the value the table shows> } ]
    }
  ]
}
severity: key = changes what the player should build, warn = a problem or risk, info = a fact worth knowing.
