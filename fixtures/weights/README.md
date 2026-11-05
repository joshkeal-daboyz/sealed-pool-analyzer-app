# Pool adjustment weights

`spa pool-grades` reads its weights from `default.json` here unless `--weights <file>` names another file. A weights file is one JSON object with exactly these four numbers, each 0 or more:

| Key | Default | Meaning |
| --- | --- | --- |
| `synergy` | 0.5 | The most the synergy part moves a grade, up or down. |
| `curve` | 0.25 | The most the curve part moves a grade. |
| `colour` | 0.25 | The most the colour depth part moves a grade. |
| `cap` | 1.0 | The most the three parts together move a grade (G-004 Decided 6). |

The defaults are G-004's starting weights (SPA-038). They are tuned on `msh` and `hob` and then frozen in SPA-039; until then they are untuned. Any other key, a missing key, a negative number or a value that is not a number makes the command print one line and exit 1.
