#!/bin/sh
# Analyze FASTQ/VCF files with the local GENOMETRICS API; prints the ids and the QC checks of each analysis.
# Starts the API (Maven + H2) when it is not already running.
# Usage: analyze.sh [-n sample-name] [-P project-id] file...
# Requires: JDK 21, Maven 3.9+, curl. Auth must be disabled (the default).
set -eu

ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
API="${API_URL:-http://localhost:8080}"
START_TIMEOUT="${START_TIMEOUT:-600}"       # first run downloads Maven dependencies
ANALYSIS_TIMEOUT="${ANALYSIS_TIMEOUT:-600}"

name=""
project=""
while getopts "n:P:" opt; do
  case $opt in
    n) name=$OPTARG ;;
    P) project=$OPTARG ;;
    *) echo "Usage: $0 [-n sample-name] [-P project-id] file..." >&2; exit 2 ;;
  esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] || { echo "Usage: $0 [-n sample-name] [-P project-id] file..." >&2; exit 2; }
for f in "$@"; do [ -f "$f" ] || { echo "File not found: $f" >&2; exit 2; }; done
[ -n "$name" ] || name=$(basename "$1")
case $project in *[!0-9a-f-]*) echo "-P must be a project id (UUID)" >&2; exit 2 ;; esac
for f in "$@"; do
  case $f in *[\;,\"]*) echo "File names with ; , or \" are not supported: $f" >&2; exit 2 ;; esac
done
# Sample names go into a JSON body: drop control characters, escape backslash and quote.
name=$(printf '%s' "$name" | tr -d '\000-\037' | sed 's/\\/\\\\/g; s/"/\\"/g')

healthy() { curl -fs "$API/actuator/health" 2>/dev/null | grep -q '"UP"'; }
json_id() { sed -n 's/.*"id":"\([^"]*\)".*/\1/p'; }
post_json() { curl -fsS -X POST -H 'Content-Type: application/json' -d "$2" "$API$1"; }

if ! healthy; then
  [ -z "${API_URL:-}" ] || { echo "No healthy API at $API_URL (the script only starts the default one on port 8080)" >&2; exit 1; }
  command -v mvn >/dev/null || { echo "Maven not found: install JDK 21 and Maven 3.9+" >&2; exit 1; }
  mkdir -p "$ROOT/data"
  echo "== starting API (log: data/api.log; stop it with: kill \$(cat data/api.pid)) ==" >&2
  (cd "$ROOT/backend" && nohup mvn -q spring-boot:run >"$ROOT/data/api.log" 2>&1 </dev/null & echo $! >"$ROOT/data/api.pid")
  pid=$(cat "$ROOT/data/api.pid")
  i=0
  until healthy; do
    kill -0 "$pid" 2>/dev/null || { echo "API failed to start:" >&2; tail -n 20 "$ROOT/data/api.log" >&2; exit 1; }
    i=$((i + 2)); [ "$i" -lt "$START_TIMEOUT" ] || { echo "API not healthy after ${START_TIMEOUT}s (see data/api.log)" >&2; exit 1; }
    sleep 2
  done
fi

[ -n "$project" ] || project=$(post_json /api/projects '{"name":"Agent analyses"}' | json_id)
sample=$(post_json "/api/projects/$project/samples" "{\"name\":\"$name\"}" | json_id)
[ -n "$sample" ] || { echo "Could not create sample in project $project" >&2; exit 1; }
mkdir -p "$ROOT/data"
for f in "$@"; do
  code=$(curl -sS -o "$ROOT/data/upload.out" -w '%{http_code}' -F "file=@$f" "$API/api/samples/$sample/files") || exit 1
  [ "$code" = 200 ] || { echo "Upload of $f rejected (HTTP $code): $(cat "$ROOT/data/upload.out")" >&2; exit 1; }
  echo "uploaded $f" >&2
done

i=0
while :; do
  analyses=$(curl -fsS "$API/api/samples/$sample/analyses")
  statuses=$(echo "$analyses" | grep -o '"status":"[A-Z]*"' || true)
  if echo "$statuses" | grep -q FAILED; then echo "Analysis failed: $analyses" >&2; exit 1; fi
  if [ -n "$statuses" ] && ! echo "$statuses" | grep -qv DONE; then break; fi
  i=$((i + 1)); [ "$i" -lt "$ANALYSIS_TIMEOUT" ] || { echo "Analyses not done after ${ANALYSIS_TIMEOUT}s" >&2; exit 1; }
  sleep 1
done

echo "project=$project sample=$sample"
# One line per analysis. A paired-end sample has two: a single-end run on R1 and the paired run (paired=yes).
echo "$analyses" | sed 's/},{/}\n{/g' | while IFS= read -r a; do
  analysis=$(echo "$a" | sed -n 's/^[^{]*{"id":"\([^"]*\)".*/\1/p')
  paired=no; echo "$a" | grep -q '"mateFileAssetId":"' && paired=yes
  echo "analysis=$analysis paired=$paired qc=$(curl -fsS "$API/api/analytics/analyses/$analysis/qc")"
done
echo "report=$API/api/reports/$sample"
