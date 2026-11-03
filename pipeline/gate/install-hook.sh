#!/usr/bin/env bash
# Points git at .githooks so that every git push runs the gate first. Run once per clone.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
git -C "$root" config core.hooksPath .githooks
chmod +x "$root/.githooks/pre-push" "$root/pipeline/gate/run.sh" 2>/dev/null || true
echo "core.hooksPath is now .githooks: git push runs pipeline/gate/run.sh and refuses a red gate."
