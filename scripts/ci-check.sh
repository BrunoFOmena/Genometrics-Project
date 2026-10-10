#!/usr/bin/env bash
# Same checks as .github/workflows/ci.yml (backend, frontend unit+build, Cypress).
# Does not push images. Run from anywhere: ./scripts/ci-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${CI_CHECK_PORT:-4300}"
BASE="http://127.0.0.1:${PORT}"

echo "== backend: mvn test =="
(cd "$ROOT/backend" && mvn -B test)

echo "== frontend: npm ci, build, unit tests =="
(
  cd "$ROOT/frontend"
  if [[ -z "${CHROME_BIN:-}" ]]; then
    if command -v chromium-browser >/dev/null 2>&1; then
      export CHROME_BIN="$(command -v chromium-browser)"
    elif command -v chromium >/dev/null 2>&1; then
      export CHROME_BIN="$(command -v chromium)"
    elif command -v google-chrome >/dev/null 2>&1; then
      export CHROME_BIN="$(command -v google-chrome)"
    fi
  fi
  npm ci
  npm run build
  npm test -- --watch=false --browsers=ChromeHeadless
)

echo "== e2e: ng serve :${PORT} + cypress =="
(
  cd "$ROOT/frontend"
  npx ng serve --host 127.0.0.1 --port "$PORT" >"$ROOT/frontend/ng-serve-ci.log" 2>&1 &
  SERVE_PID=$!
  cleanup() {
    kill "$SERVE_PID" 2>/dev/null || true
    wait "$SERVE_PID" 2>/dev/null || true
  }
  trap cleanup EXIT

  ready=0
  for _ in $(seq 1 90); do
    if curl -sf "$BASE" >/dev/null; then
      ready=1
      break
    fi
    if ! kill -0 "$SERVE_PID" 2>/dev/null; then
      echo "ng serve exited early. Log:"
      tail -n 80 "$ROOT/frontend/ng-serve-ci.log" || true
      exit 1
    fi
    sleep 2
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "Timed out waiting for $BASE"
    tail -n 80 "$ROOT/frontend/ng-serve-ci.log" || true
    exit 1
  fi

  if command -v google-chrome >/dev/null 2>&1 || command -v google-chrome-stable >/dev/null 2>&1; then
    BROWSER=chrome
  elif command -v chromium-browser >/dev/null 2>&1 || command -v chromium >/dev/null 2>&1; then
    BROWSER=chromium
  else
    BROWSER=electron
  fi
  echo "Cypress browser: ${BROWSER}"
  npx cypress run --browser "$BROWSER" --config "baseUrl=${BASE}"
)

echo "== ci-check: all green =="
