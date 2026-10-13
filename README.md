# GENOMETRICS

**Local-first QC for FASTQ and VCF, with an analyst that only answers from metrics already calculated.**

[MIT License](LICENSE) · Built by **Bruno Omena**

Sequencing files stay on your machine. The analyst does not invent a Phred score, a variant count, or a depth: it queries catalogued facts, cites them, and — if you run [Ollama](https://ollama.com) — phrases the answer from that evidence. This is file QC and metric review, not an alignment or variant-calling pipeline. It is a research and quality-control tool, not a clinical diagnostic device.

![Status](https://img.shields.io/badge/Status-Active-success)
![License](https://img.shields.io/badge/License-MIT-blue)
![Angular](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)

## Why it matters

| Promise | How |
|---------|-----|
| No invented metrics | Questions run through the same query API as the charts |
| Explainable QC | Every numeric claim can point at an analysis, sample, metric, value, and unit |
| Sensitive data stays local | Parsers, Postgres, and the optional model run on your computer |

## Quick start

**Prerequisite:** [Docker](https://docs.docker.com/get-docker/) with Compose v2.

```bash
git clone https://github.com/BrunoFOmena/Genometrics-Project.git
cd Genometrics-Project
docker compose up --build -d
```

Open http://localhost:4200 (no login). Then:

1. Create a project and a sample.
2. Upload [`datasets/sample.fastq`](datasets/sample.fastq) or [`datasets/sample.vcf`](datasets/sample.vcf).
3. Open **Panel** when the job is `DONE`.
4. Open **Explore**, run a query, and ask a question. The answer cites the stored facts.

| Service | URL |
|---------|-----|
| App | http://localhost:4200 |
| API / Swagger | http://localhost:8080/swagger-ui.html |
| MCP (direct; nginx does not proxy it) | http://localhost:8080/mcp |

The first image build can take several minutes. Auth is off by default (`NGS_AUTH_DISABLED=true`). Published ports bind to `127.0.0.1` only.

### Optional local model

Docker turns the analyst on and points it at Ollama on the host. Without Ollama, Explore still returns the catalogued facts.

```bash
ollama serve
ollama pull llama3.2
```

Override with `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, or `NGS_AI_ENABLED=false` (see [`.env.example`](.env.example)).

## What you can do

### Explore and Analyst

**Explore** queries the metric catalog (quality, GC, depth, variant counts, and the rest already computed for a file). **Analyst** answers in that same context. With Ollama it writes a short explanation. Without it, or if the model is down, the reply is the grounded fact list — it does not fill in missing numbers.

### Panel, projects, and files

- Projects and samples, FASTQ (including paired-end) and VCF uploads.
- Streaming parsers, async jobs (`QUEUED` → `RUNNING` → `DONE`), PASS / WARN / FAIL on FASTQ QC.
- Named charts on **Panel**. JSON, CSV, and PDF export for FASTQ QC.

Compare and History remain available as routes. They are not the main workspace.

### Agents (MCP)

The API exposes the same read tools over HTTP JSON-RPC at `POST /mcp`. It is not a stdio MCP server. Setup for Cursor and Claude Desktop: [`docs/mcp.md`](docs/mcp.md).

Tools: `list_metrics`, `query_metrics`, `compare_analyses`, `explain_qc`, `get_analysis_context`.

### Evals

[`datasets/evals/grounded-questions.json`](datasets/evals/grounded-questions.json) and `GroundedAnalystEvalTest` check that missing facts are not invented and that answers cite the analysis behind a number.

## Stack

| Area | Technology |
|------|------------|
| UI | Angular 19, PrimeNG, ECharts |
| API | Java 21, Spring Boot 3 |
| Data | H2 in local dev, PostgreSQL in Docker |
| Optional model | Ollama (`llama3.2` by default) |
| CI | GitHub Actions; images on push to `prod` |

## Develop on the host

JDK 21, Maven 3.9+, Node 22. Host runs keep `NGS_AI_ENABLED` off unless you set it.

```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm start
```

| Service | URL |
|---------|-----|
| UI | http://localhost:4200 |
| API | http://localhost:8080 |

Postgres only, while the API runs on the host:

```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

For a shared deploy, set a real `NGS_JWT_SECRET` (32+ bytes) and `NGS_AUTH_DISABLED=false`.

Images after a merge to `prod`: `ghcr.io/<owner>/genometrics-project/api` and `.../frontend` (`:latest` and `:${GITHUB_SHA}`).

## Tests

```bash
./scripts/ci-check.sh
```

Or separately: `cd backend && mvn test`, `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`, `cd frontend && npm run e2e`.

## Git

**`prod`** is the official branch. Pull requests target `prod`; only the maintainer merges. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

```bash
git fetch origin && git checkout prod && git pull
git checkout -b feature/<name>
```

| Event | Tests | GHCR image |
|-------|-------|------------|
| PR → `prod` | yes | no |
| Push to `feature/*`, `fix/*`, `hotfix/*` | yes | no |
| Push to `prod` | yes | yes |

## Hardware

API heap about 1 GB. Postgres in Compose is limited to 512 MB. GPU is not required; Ollama can use a CPU model.
