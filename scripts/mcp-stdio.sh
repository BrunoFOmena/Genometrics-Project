#!/bin/sh
# MCP server over stdio for clients that cannot speak HTTP. Builds the jar if missing (build output goes to stderr).
# Build once beforehand (mvn -DskipTests package in backend/): a client waits only a few seconds for the server to start.
# Client config: {"mcpServers":{"genometrics":{"command":"/absolute/path/to/scripts/mcp-stdio.sh"}}}
# Uses the same H2 database as `mvn spring-boot:run` (backend/../data), so both can run at the same time.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT/backend"
JAR=$(ls target/ngs-analytics-api-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ]; then
  echo "No jar yet: building (run \`mvn -DskipTests package\` in backend/ beforehand to avoid client start-up timeouts)" >&2
  mvn -q -B -DskipTests package >&2
  JAR=$(ls target/ngs-analytics-api-*.jar | head -n 1)
elif [ -n "$(find src pom.xml -newer "$JAR" -type f 2>/dev/null | head -n 1)" ]; then
  echo "Warning: sources are newer than $JAR; run \`mvn -DskipTests package\` in backend/ to pick up changes" >&2
fi
exec java -Dstdout.encoding=UTF-8 -jar "$JAR" --spring.profiles.active=dev-h2,mcp-stdio
