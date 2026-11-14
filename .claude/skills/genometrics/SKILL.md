---
name: genometrics
description: Run local quality control on FASTQ files and compute variant metrics on VCF files with GENOMETRICS, then query and compare the results. Use when the user asks to QC sequencing reads, check a FASTQ/VCF, summarize variants, or compare samples. Runs entirely on this machine (Java + Maven).
---

# GENOMETRICS: FASTQ QC and VCF metrics

GENOMETRICS is the API in this repository. It parses FASTQ/VCF files locally, stores metric facts, and exposes them over REST and MCP at `http://localhost:8080`. Nothing leaves the machine.

Requirements: JDK 21, Maven 3.9+, `curl`. Run commands from the repository root.

## 1. Analyze files

```bash
.claude/skills/genometrics/scripts/analyze.sh [-n sample-name] [-P project-id] file...
```

- Starts the API in the background if it is not running. The first start downloads Maven dependencies and can take a few minutes; later starts take seconds.
- Accepts FASTQ, VCF and FASTA, plain or `.gz`. Pass both mates (`_R1` / `_R2`) in one call to get a paired-end analysis.
- Each call creates one sample. Pass `-P <project-id>` from a previous run to keep samples in the same project; samples in different projects can still be compared.

Output:

```text
project=<id> sample=<id>
analysis=<id> qc=[{"key":"overall","status":"WARN",...},{"key":"meanQuality",...},...]
report=http://localhost:8080/api/reports/<sample-id>
```

- `qc` is the verdict for FASTQ analyses: the `overall` entry is PASS, WARN or FAIL, followed by one entry per check with its value and message. Quote these messages; do not re-derive thresholds.
- VCF analyses have no QC checks (`qc=[]`). Read the `report` URL for variant, SNP and indel counts, Ts/Tv, mean QUAL and mean depth.
- The `report` JSON also holds per-position quality, adapter hits and over-represented sequences. It is large; fetch it only when the user asks for that detail. `<report>/fastq/pdf`, `<report>/fastq/html` and `<report>/csv` are files to hand to the user.

## 2. Query and compare

If the `genometrics` MCP tools are connected, call them directly. Otherwise send the same call with curl:

```bash
curl -s http://localhost:8080/mcp -H 'Content-Type: application/json' -d '{
  "jsonrpc":"2.0","id":1,"method":"tools/call",
  "params":{"name":"query_metrics","arguments":{"metrics":["mean_quality","gc_content"],"dimensions":["sample","metric"],"aggregation":"AVG"}}}'
```

`result.content[0].text` is a JSON string with `columns` and `rows`.

| Tool | Arguments |
|------|-----------|
| `list_metrics` | none. Returns every metric key with unit and allowed aggregations |
| `query_metrics`, `compare_analyses` | `metrics` (required), `dimensions`, `filters`, `aggregation`, `limit` (max 1000) |
| `get_analysis_context` | `analysisId`. All metric facts of one analysis |
| `explain_qc` | `question`, optional `sampleIds`, `metrics`. Returns the matching facts with citations |

- Metric keys: `read_count`, `average_length`, `gc_content`, `n_content`, `mean_quality`, `duplication_rate` (FASTQ); `variant_count`, `snp_count`, `indel_count`, `mean_variant_quality`, `mean_depth`, `ts_tv_ratio`, `pass_count` (VCF).
- Dimensions: `project`, `sample`, `analysis`, `metric`, `status`, `fileType`.
- Aggregations: `NONE` (default, one row per fact), `AVG`, `SUM`, `MIN`, `MAX`. When aggregating more than one metric, always include `metric` in `dimensions`; otherwise different metrics are averaged together.
- Filters: `projectId`, `sampleIds`, `statuses`, `from`, `to` (ISO-8601 instants).

## Rules

- Report only values returned by the API. If a metric is missing, say it is not available.
- Research use only: this is not a diagnostic tool, and results must not be presented as clinical findings.
- Do not copy the user's sequencing data or results to external services.

## Housekeeping

- Stop the API: `kill $(cat data/api.pid)`. Log: `data/api.log`.
- Database and uploads live in `data/` (gitignored). Delete the folder to reset.
- If port 8080 is taken or auth is enabled (`NGS_AUTH_DISABLED=false`), the script fails; see `docs/mcp.md` for the token flow.
