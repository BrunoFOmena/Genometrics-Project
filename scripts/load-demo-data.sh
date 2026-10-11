#!/usr/bin/env bash
set -euo pipefail

SQL="${GENOMETRICS_DUMP:-$HOME/projetos/dumps/genometrics/demo-data.sql}"

if [[ ! -f "$SQL" ]]; then
  echo "Seed file not found: $SQL" >&2
  exit 1
fi

if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'ngs-postgres'; then
  docker exec -i ngs-postgres psql -U ngs -d ngs -v ON_ERROR_STOP=1 < "$SQL"
  echo "Demo data loaded into ngs-postgres."
  exit 0
fi

export PGPASSWORD="${PGPASSWORD:-ngs}"
psql -h "${PGHOST:-localhost}" -p "${PGPORT:-5432}" -U "${PGUSER:-ngs}" -d "${PGDATABASE:-ngs}" \
  -v ON_ERROR_STOP=1 -f "$SQL"
