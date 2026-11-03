#!/usr/bin/env bash
# The gate: runs ./gradlew check for one ticket and leaves gate-verdict.json at the repository root.
# Usage: pipeline/gate/run.sh [--ticket SPA-###]
# Exits 0 on pass, 1 on fail, 2 on a usage error.
#
# The steps, their order, and the verdict are the spa.gate plugin's job (build-logic/). This script
# only passes the ticket through and guarantees a verdict exists afterwards: if Gradle dies before
# the plugin can write one (a broken build script, build-logic that does not compile, no JDK), it
# writes a fail verdict from the Gradle log.
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root" || exit 2
# Git Bash prints /d/repo; tools on Windows print D:/repo. pwd -W gives the latter.
root_native="$(pwd -W 2>/dev/null || pwd)"
verdict="gate-verdict.json"
log="build/gate/gradle.log"

usage() { sed -n '3,4p' "${BASH_SOURCE[0]}" | sed 's/^# //'; }

ticket=""
while [ $# -gt 0 ]; do
  case "$1" in
    --ticket)
      [ $# -ge 2 ] || { usage >&2; exit 2; }
      ticket="$2"
      shift 2
      ;;
    --ticket=*) ticket="${1#--ticket=}"; shift ;;
    -h | --help) usage; exit 0 ;;
    *) echo "run.sh: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
  esac
done
if [ -n "$ticket" ] && ! [[ "$ticket" =~ ^SPA-[0-9]{3}$ ]]; then
  echo "run.sh: --ticket must look like SPA-###, got '$ticket'" >&2
  exit 2
fi

# The same resolution order the plugin uses (spa.gate.TicketId), needed only for a fallback verdict.
resolve_ticket() {
  if [ -n "$ticket" ]; then echo "$ticket"; return; fi
  if [ -n "${GATE_TICKET:-}" ]; then echo "$GATE_TICKET"; return; fi
  local branch="${GATE_BRANCH:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null)}"
  local id
  id="$(printf '%s' "$branch" | grep -oE 'SPA-[0-9]{3}' | head -n 1)"
  echo "${id:-none}"
}

json_string() {
  printf '%s' "$1" | tr -d '\r' | tr '\t\n' '  ' | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/[[:cntrl:]]//g'
}

write_fallback_verdict() {
  local why="$1" message file="null" line="null" error_line
  message="$(sed -n '/^\* What went wrong:/,/^\* Try:/p' "$log" | sed -e '1d' -e '$d' | grep -v '^[[:space:]]*$' | head -n 20)"
  [ -n "$message" ] || message="$(grep -v '^[[:space:]]*$' "$log" | tail -n 5)"
  # A Kotlin error looks like "e: file:///D:/repo/x/A.kt:12:5 message" (or file:///home/...).
  error_line="$(grep -m 1 -oE '^e: file:[^ ]+:[0-9]+:[0-9]+' "$log" || true)"
  if [ -n "$error_line" ]; then
    local location="${error_line#e: file://}"
    location="${location%:*}"
    line="${location##*:}"
    local path="${location%:*}"
    [[ "$path" =~ ^/[A-Za-z]:/ ]] && path="${path#/}"
    path="${path#"$root_native"/}"
    file="\"$(json_string "$path")\""
  fi
  local steps="" step result
  for step in format lint compile unit-tests ui-tests coverage dependencies; do
    result="not_run"
    [ "$step" = compile ] && result="fail"
    steps="$steps${steps:+,}
    { \"step\": \"$step\", \"result\": \"$result\", \"note\": null }"
  done
  cat >"$verdict" <<JSON
{
  "ticket": "$(json_string "$(resolve_ticket)")",
  "commit": "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)",
  "result": "fail",
  "failures": [
    {
      "step": "compile",
      "file": $file,
      "line": $line,
      "message": "$(json_string "$why $message")",
      "reproduce": "./gradlew check"
    }
  ],
  "steps": [$steps
  ]
}
JSON
}

rm -f "$verdict"
mkdir -p "$(dirname "$log")"
args=(check --console=plain)
[ -n "$ticket" ] && args+=("-Pticket=$ticket")
./gradlew "${args[@]}" 2>&1 | tee "$log"
gradle_status=${PIPESTATUS[0]}

# The top-level result is the first "result" key: it comes before failures and steps.
verdict_result() { grep -m 1 -oE '"result": *"[a-z_]+"' "$verdict" | grep -oE '[a-z_]+"$' | tr -d '"'; }

if [ ! -f "$verdict" ]; then
  write_fallback_verdict "Gradle stopped before the gate could run (exit $gradle_status)."
elif [ "$gradle_status" -ne 0 ] && [ "$(verdict_result)" = pass ]; then
  write_fallback_verdict "Gradle failed (exit $gradle_status) after the gate steps passed."
fi

echo
if [ "$(verdict_result)" = pass ]; then
  echo "Gate: pass. Verdict in $verdict."
  exit 0
fi
echo "Gate: FAIL. Verdict in $verdict:"
cat "$verdict"
exit 1
