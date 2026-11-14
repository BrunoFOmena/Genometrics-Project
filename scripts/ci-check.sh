#!/usr/bin/env bash
# Same as .github/workflows/ci.yml: `backend` (tests + package), `python` (lint + tests) and `demo` (API + fixtures + MCP check).
# Stops the API again if this script started it. Run from anywhere: ./scripts/ci-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== backend: mvn test + package =="
(cd "$ROOT/backend" && mvn -B test && mvn -B -DskipTests package)

echo "== python: ruff + pytest =="
command -v uv >/dev/null || { echo "uv not found: install it from https://docs.astral.sh/uv/" >&2; exit 1; }
(cd "$ROOT/python" && uv run ruff check && uv run ruff format --check && uv run pytest -q)

echo "== demo: API + fixtures + MCP check =="
if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then
  echo "An API is already running on :8080; stop it first so the demo starts from a clean state." >&2
  exit 1
fi
cleanup() { [ -f "$ROOT/data/api.pid" ] && kill "$(cat "$ROOT/data/api.pid")" 2>/dev/null || true; }
trap cleanup EXIT
"$ROOT/scripts/demo.sh" >/dev/null
echo "demo ok"

echo "== ci-check: all green =="
