# MCP over HTTP and stdio

GENOMETRICS exposes read-only metric tools to AI agents. The endpoint is JSON-RPC 2.0 on the API process:

```text
POST http://localhost:8080/mcp
```

This is the HTTP transport, which Claude Code and Cursor speak directly. For clients that only support stdio, see [Clients that only support stdio](#clients-that-only-support-stdio). With the default setup, authentication is off and the API uses the built-in dev user. If you set `NGS_AUTH_DISABLED=false`, send `Authorization: Bearer <jwt>` (see the README for how to get a token).

The endpoint rejects requests with a foreign `Host` header (auth off) and POSTs with a foreign `Origin` header, which protects against web pages in your browser. Clients that run outside a browser send neither and work unchanged. The API sends no CORS headers, so a browser-based MCP client cannot call it directly; use a stdio or HTTP client outside the browser. For another host name see `NGS_SECURITY_ALLOWED_HOSTS` in `SECURITY.md`.

Tools query stored facts; they do not upload files or start analyses (`explain_qc` only records the question and answer in its history). Load data through the REST API first (README, "Load data").

## Handshake

```bash
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

Then list tools:

```bash
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

## Tools

| Tool | Required arguments | What it returns |
|------|--------------------|-----------------|
| `list_metrics` | none | Metric catalog (key, label, unit) |
| `query_metrics` | `metrics` (string array) | Rows from stored facts. Optional: `dimensions`, `filters`, `aggregation`, `limit` (max 1000) |
| `explain_qc` | `question` | Grounded answer plus citations. Optional: `projectId`, `sampleIds`, `metrics` |
| `get_analysis_context` | `analysisId` | One analysis and its metric facts |

`explain_qc` uses [Ollama](https://ollama.com) only when `NGS_AI_ENABLED=true` (off by default) and the model responds within the timeout. Otherwise `answer` is a deterministic summary: one block per analysis with its FASTQ QC verdict (PASS / WARN / FAIL), one line per metric and the checks that did not pass, then the requested metrics that have no data. `citations` lists every fact behind it. Missing facts are reported as unavailable; the tool does not invent a value. Treat model-phrased answers as untrusted text: check the cited facts.

Ollama listens on `127.0.0.1` by default, which is what the API expects (`OLLAMA_BASE_URL=http://localhost:11434`). Do **not** expose Ollama on `0.0.0.0` on untrusted networks, since its API has no authentication.

To compare samples, group by sample and metric:

```json
{"metrics": ["mean_quality", "gc_content"], "dimensions": ["sample", "metric"], "aggregation": "AVG"}
```

A paired-end sample has two analyses (a single-end run on R1 and the paired run); filter on the paired `analysisId` when that matters.

## Call a tool

```bash
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "query_metrics",
      "arguments": {
        "metrics": ["mean_quality"],
        "aggregation": "NONE",
        "limit": 20
      }
    }
  }'
```

Explain a result:

```bash
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "explain_qc",
      "arguments": {
        "question": "What is the mean read quality?",
        "metrics": ["mean_quality"]
      }
    }
  }'
```

The `result.content[0].text` field is a JSON string of the tool payload.

## Claude Code

```bash
claude mcp add --transport http genometrics http://localhost:8080/mcp
# with auth enabled:
claude mcp add --transport http genometrics http://localhost:8080/mcp --header "Authorization: Bearer $TOKEN"
```

## Cursor

Add an HTTP MCP server (Cursor Settings → MCP):

```json
{
  "mcpServers": {
    "genometrics": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

With auth enabled, add `"headers": {"Authorization": "Bearer <jwt>"}` next to `url`.

## Clients that only support stdio

`scripts/mcp-stdio.sh` runs the same tools over stdio (one JSON-RPC message per line), with no HTTP port and no bridge package. Point the client at its absolute path:

```json
{
  "mcpServers": {
    "genometrics": {
      "command": "/absolute/path/to/Genometrics-Project/scripts/mcp-stdio.sh"
    }
  }
}
```

- Needs JDK 21 and Maven. Build once before configuring the client, because clients wait only a few seconds for the server to start: `cd backend && mvn -DskipTests package`. If the jar is missing the script builds it (output on stderr); if sources are newer than the jar it only warns on stderr, so rebuild after pulling changes.
- It uses the same H2 database as `mvn spring-boot:run` (`data/`), so data loaded through the HTTP API is visible over stdio, and both can run at the same time.
- The process is local and runs as the built-in dev user, so no token is needed. It only exposes the read-only tools; loading data still goes through the REST API (or `analyze.sh`).
- stdout carries only protocol messages; all logs go to stderr.

Try it from a shell:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | ./scripts/mcp-stdio.sh
```
