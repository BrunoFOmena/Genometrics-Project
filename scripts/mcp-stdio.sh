#!/bin/sh
# MCP server over stdio for clients that cannot speak HTTP. Builds the jar on first use (build output goes to stderr).
# Client config: {"mcpServers":{"genometrics":{"command":"/absolute/path/to/scripts/mcp-stdio.sh"}}}
# Uses the same H2 database as `mvn spring-boot:run` (backend/../data), so both can run at the same time.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT/backend"
JAR=$(ls target/ngs-analytics-api-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" -type f 2>/dev/null | head -n 1)" ]; then
  mvn -q -B -DskipTests package >&2
  JAR=$(ls target/ngs-analytics-api-*.jar | head -n 1)
fi
exec java -jar "$JAR" --spring.profiles.active=dev-h2,mcp-stdio
