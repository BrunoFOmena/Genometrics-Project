# MCP over HTTP

GENOMETRICS exposes the same read-only metric tools as the Explore API. The endpoint is JSON-RPC 2.0 on the API process:

```text
POST http://localhost:8080/mcp
```

The UI nginx proxies only `/api/`. Do not call `http://localhost:4200/mcp`.

This is not a stdio MCP server. Cursor and Claude Desktop need an HTTP MCP client (or a small stdio bridge) pointed at that URL. With the default Docker stack, authentication is off and the API uses the built-in dev user. If you set `NGS_AUTH_DISABLED=false`, send `Authorization: Bearer <jwt>`.

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

`explain_qc` uses Ollama only when `NGS_AI_ENABLED=true` and the model responds. Otherwise the text is the catalogued facts. Missing facts are reported as unavailable; the tool does not invent a value.

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

## Cursor

Add an HTTP MCP server (Cursor Settings → MCP). Example shape:

```json
{
  "mcpServers": {
    "genometrics": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

If your Cursor build only accepts stdio servers, run an HTTP-to-stdio bridge with that URL. Do not point it at port 4200.

## Claude Desktop

Claude Desktop's built-in config is stdio (`command` + `args`). Use a bridge, for example [`mcp-remote`](https://www.npmjs.com/package/mcp-remote), only if you trust that package:

```json
{
  "mcpServers": {
    "genometrics": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"]
    }
  }
}
```

Start the API first (`docker compose up -d` or `mvn spring-boot:run`). Tools stay read-only: they query facts and do not upload files or start analyses.
