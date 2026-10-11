#!/usr/bin/env bash
# Reload docker/seed/demo-data.sql into the running Genometrics Postgres.
# Safe to repeat: only the fixed demo rows are replaced.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL="$ROOT/docker/seed/demo-data.sql"

if [[ ! -f "$SQL" ]]; then
  echo "Seed file not found: $SQL" >&2
  exit 1
fi

if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'ngs-postgres'; then
  docker exec -i ngs-postgres psql -U ngs -d ngs -v ON_ERROR_STOP=1 < "$SQL"
  echo "Demo data loaded into container ngs-postgres."
  exit 0
fi

export PGPASSWORD="${PGPASSWORD:-ngs}"
psql \
  -h "${PGHOST:-localhost}" \
  -p "${PGPORT:-5432}" \
  -U "${PGUSER:-ngs}" \
  -d "${PGDATABASE:-ngs}" \
  -v ON_ERROR_STOP=1 \
  -f "$SQL"
echo "Demo data loaded into ${PGHOST:-localhost}:${PGPORT:-5432}/${PGDATABASE:-ngs}."
