#!/bin/sh
# One-command demo: starts the API if needed, analyzes the datasets/ fixtures
# and checks the MCP endpoint. Requires: JDK 21, Maven 3.9+, curl.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
API="${API_URL:-http://localhost:8080}"
ANALYZE="$ROOT/.claude/skills/genometrics/scripts/analyze.sh"
D="$ROOT/datasets"

mcp() { curl -fsS -X POST -H 'Content-Type: application/json' -d "$1" "$API/mcp"; }

# analyze.sh prints "project=<id> sample=<id>"; reuse the project for the other samples.
out=$("$ANALYZE" -n "FASTQ single-end" "$D/sample.fastq")
project=$(echo "$out" | sed -n 's/^project=\([^ ]*\) .*/\1/p')
[ -n "$project" ] || { echo "Could not read the project id from: $out" >&2; exit 1; }
"$ANALYZE" -P "$project" -n "FASTQ paired-end" "$D/sample_R1.fastq" "$D/sample_R2.fastq" >/dev/null
"$ANALYZE" -P "$project" -n "VCF variants" "$D/sample.vcf" >/dev/null

echo "== MCP tools/list =="
tools=$(mcp '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | grep -o '"name":"[a-z_]*"')
[ "$(echo "$tools" | wc -l)" -eq 5 ] || { echo "Expected 5 MCP tools, got: $tools" >&2; exit 1; }
echo "$tools" | sed 's/^/  /'

echo "== MCP query_metrics =="
rows=$(mcp '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"query_metrics","arguments":{"metrics":["mean_quality","variant_count"]}}}')
for metric in mean_quality variant_count; do
  echo "$rows" | grep -q "metric[^,]*$metric" || { echo "query_metrics returned no $metric: $rows" >&2; exit 1; }
done
echo "  mean_quality and variant_count facts returned"

echo "== MCP over stdio =="
stdio=$(printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"query_metrics","arguments":{"metrics":["variant_count"]}}}' \
  | "$ROOT/scripts/mcp-stdio.sh")
echo "$stdio" | grep -q 'metric[^,]*variant_count' || { echo "stdio query_metrics returned no variant_count: $stdio" >&2; exit 1; }
echo "  stdio transport returned variant_count"

echo "== demo ready: MCP at $API/mcp (if this script started the API: kill \$(cat data/api.pid)) =="
