# MCP over HTTP

GENOMETRICS exposes read-only metric tools to AI agents. The endpoint is JSON-RPC 2.0 on the API process:

```text
POST http://localhost:8080/mcp
```

This is not a stdio MCP server: clients need an HTTP MCP transport (or a small stdio bridge) pointed at that URL. With the default setup, authentication is off and the API uses the built-in dev user. If you set `NGS_AUTH_DISABLED=false`, send `Authorization: Bearer <jwt>` (see the README for how to get a token).

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
| `compare_analyses` | same shape as `query_metrics` | Same query, intended for side-by-side samples |
| `explain_qc` | `question` | Grounded answer plus citations. Optional: `projectId`, `sampleIds`, `metrics` |
| `get_analysis_context` | `analysisId` | One analysis and its metric facts |

`explain_qc` uses [Ollama](https://ollama.com) only when `NGS_AI_ENABLED=true` (off by default) and the model responds within the timeout. Otherwise the text is the catalogued facts. Missing facts are reported as unavailable; the tool does not invent a value. Treat model-phrased answers as untrusted text: check the cited facts.

With Docker, the API reaches Ollama on the host through `host.docker.internal`. Ollama listens on `127.0.0.1` by default, which the container cannot reach. Bind it to the Docker bridge address (for example `OLLAMA_HOST=172.17.0.1`) or firewall port 11434 to the Docker subnet; do **not** expose Ollama on `0.0.0.0` on untrusted networks, since its API has no authentication.

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

Claude Code and Cursor speak HTTP directly; a bridge is only needed for stdio-only clients. Use one, for example [`mcp-remote`](https://www.npmjs.com/package/mcp-remote), only if you trust that package, and pin a reviewed version:

```json
{
  "mcpServers": {
    "genometrics": {
      "command": "npx",
      "args": ["-y", "mcp-remote@0.15.2", "http://localhost:8080/mcp"]
    }
  }
}
```

Start the API first (`docker compose up -d` or `mvn spring-boot:run`).
