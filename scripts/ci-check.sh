#!/usr/bin/env bash
# Same as the `backend` job of .github/workflows/ci.yml (tests + package).
# The `demo` job is ./scripts/demo.sh (starts the API, leaves it running). Run from anywhere: ./scripts/ci-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== backend: mvn test + package =="
(cd "$ROOT/backend" && mvn -B test && mvn -B -DskipTests package)

echo "== ci-check: all green =="
