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

The server also answers `ping` with an empty result, for clients that check the connection.

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
| `get_analysis_context` | `analysisId` | One analysis, its status (`QUEUED`, `RUNNING`, `DONE`, `FAILED`) and its metric facts |
| `analyze_file` | `paths` (1 file, or R1 and R2) | Only when `NGS_MCP_ANALYZE_ROOTS` is set. Analyzes local files and returns `projectId`, `sampleId` and one entry per analysis (`analysisId`, `file`, `status`, `paired`). Optional: `sampleName`, `projectId` |

`explain_qc` uses [Ollama](https://ollama.com) only when `NGS_AI_ENABLED=true` (off by default) and the model responds within the timeout. Otherwise `answer` is a deterministic summary: one block per analysis with its FASTQ QC verdict (PASS / WARN / FAIL), one line per metric and the checks that did not pass, then the requested metrics that have no data. `citations` lists every fact behind it. Missing facts are reported as unavailable; the tool does not invent a value. Treat model-phrased answers as untrusted text: check the cited facts.

Ollama listens on `127.0.0.1` by default, which is what the API expects (`OLLAMA_BASE_URL=http://localhost:11434`). Do **not** expose Ollama on `0.0.0.0` on untrusted networks, since its API has no authentication.

### Analyze a file from MCP

Start the API with the directories the tool may read, for example `NGS_MCP_ANALYZE_ROOTS=/data/runs mvn spring-boot:run`, then call `analyze_file` with absolute paths. Pass the R1 and R2 FASTQ of one sample together for paired-end, in any order (the names must match apart from R1/R2); R1 also gets its own single-end analysis, so a pair returns two entries. Files are copied into storage during the call, so a multi-GB file takes a while before the tool answers. Analyses start as `QUEUED`; poll `get_analysis_context` until `status` is `DONE`, then query the metrics. Without the variable the tool is not listed (see `SECURITY.md`).

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

Each successful result carries the payload as `result.structuredContent` (a JSON object matching the tool's `outputSchema` in `tools/list`) and the same JSON as a string in `result.content[0].text`, for clients that ignore structured content. `list_metrics` wraps its list as `{"metrics": [...]}`. Failed calls return `isError: true` with a text message only.

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
