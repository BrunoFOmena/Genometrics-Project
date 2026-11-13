#!/usr/bin/env bash
# Same checks as .github/workflows/ci.yml (backend tests + package).
# Does not push images. Run from anywhere: ./scripts/ci-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "== backend: mvn test + package =="
(cd "$ROOT/backend" && mvn -B test && mvn -B -DskipTests package)

echo "== ci-check: all green =="
