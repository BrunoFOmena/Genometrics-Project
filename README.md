# 🧬 GENOMETRICS — NGS QC & Metrics for AI agents

**Local-first FASTQ/VCF analytics exposed as an MCP server, so AI agents can query grounded genomics metrics on your own machine.**

Built by **Bruno Omena** · modular monolith designed to run on a **16 GB RAM laptop**.

![Status](https://img.shields.io/badge/Status-Active-success)
![MCP](https://img.shields.io/badge/MCP-JSON--RPC%20over%20HTTP-6E56CF)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Database](https://img.shields.io/badge/Database-H2%20%7C%20PostgreSQL-336791?logo=postgresql&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

---

## 📖 About the Project

**GENOMETRICS** focuses on **Next-Generation Sequencing (NGS)** quality control and variant metrics. Instead of a dashboard, the interface is your **AI agent** (Claude Code, Cursor, any MCP client): you load FASTQ/VCF files through the REST API, GENOMETRICS parses them locally with streaming Java parsers, and the agent queries the resulting metric facts through MCP tools.

Everything runs on your machine: the API, MCP endpoint and Postgres listen on `127.0.0.1` only, so data stays local unless you send it somewhere.

> **Research use only.** GENOMETRICS is not a medical device and must not be used for diagnosis.

---

## ✨ Features

### 🤖 MCP server (`/mcp`)
| Tool | What it does |
|------|--------------|
| `list_metrics` | Metric catalog (keys, units, descriptions) |
| `query_metrics` | Query grounded metric facts with filters and aggregation |
| `compare_analyses` | Compare metrics across samples |
| `explain_qc` | Explain QC results with citations to metric facts (phrased by a local [Ollama](https://ollama.com) model when `NGS_AI_ENABLED=true`, otherwise the grounded facts) |
| `get_analysis_context` | One analysis with all its metric facts |

### 📁 Projects & Samples (REST)
- Create **projects** and **samples**; upload **FASTQ** (single and paired-end) and **VCF** files (up to 2 GB).
- Async analysis with status `QUEUED` → `RUNNING` → `DONE`.

### 🔬 FASTQ QC
- **Phred**, **GC content**, **duplication**, **N-content**, **adapter** detection, **over-represented** sequences.
- **Paired-end** support with automatic mate detection (`R1`/`R2`).
- **PASS / WARN / FAIL** evaluation with recommendations.

### 🧪 VCF Metrics
- Variant counts by type, quality and chromosomal distribution.

### 📄 Reports
- Metrics as **JSON**, **CSV** and **PDF**/**HTML** (FASTQ QC report) via `/api/reports/...`.
- Swagger UI for REST API exploration.

### 🔐 Authentication *(off by default for local use)*
- Optional JWT login (Spring Security); `NGS_AUTH_DISABLED=true` by default.

---

## 🛠️ Tech Stack

| Technology | Role |
|------------|------|
| **Java 21** + **Spring Boot 3.3** | REST API, MCP endpoint, security, JPA |
| **FastqParser / VcfParser** | In-process streaming parsers |
| **SpringDoc OpenAPI** | Swagger documentation |
| **JWT (JJWT)** | Optional authentication |
| **H2** / **PostgreSQL** | Local default / Compose database |
| **GitHub Actions** | CI (`backend`, `issue-link`) |
| **GHCR** | API Docker image on push to `main` |
| **Docker Compose** | Postgres + API in one command |

---

## 🚀 How to Run

### Prerequisites

- **JDK 21** and **Maven 3.9+**, or **Docker** (Compose)
- `curl` and `jq` for the examples below

### Option A: Docker Compose (Postgres + API)

```bash
git clone https://github.com/BrunoFOmena/Genometrics-Project.git
cd Genometrics-Project
docker compose up --build -d
```

### Option B: Maven (H2, no Docker)

```bash
cd backend
mvn spring-boot:run
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| MCP | http://localhost:8080/mcp |
| Swagger | http://localhost:8080/swagger-ui.html |
| PostgreSQL (Compose only) | localhost:5432 (`ngs` / `ngs`) |

### Load data

From the repository root:

```bash
API=http://localhost:8080
PROJECT=$(curl -s -X POST $API/api/projects -H 'Content-Type: application/json' -d '{"name":"Demo"}' | jq -r .id)
SAMPLE=$(curl -s -X POST $API/api/projects/$PROJECT/samples -H 'Content-Type: application/json' -d '{"name":"S1"}' | jq -r .id)
curl -s -F file=@datasets/sample.fastq $API/api/samples/$SAMPLE/files
curl -s -F file=@datasets/sample.vcf $API/api/samples/$SAMPLE/files
```

Fixtures live in [`datasets/`](datasets/) (FASTQ single/paired-end, VCF). Reports per sample: `curl -o qc.pdf $API/api/reports/$SAMPLE/fastq/pdf` (also `/csv`, `/fastq/html`, JSON at `/api/reports/$SAMPLE`).

### Connect your agent

GENOMETRICS speaks MCP (JSON-RPC 2.0) over HTTP at `http://localhost:8080/mcp`. Example for Claude Code:

```bash
claude mcp add --transport http genometrics http://localhost:8080/mcp
```

Other clients (Cursor, stdio bridges), tool arguments and examples: [`docs/mcp.md`](docs/mcp.md).

Check it from the shell:

```bash
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

### Configuration

Copy `.env.example` → `.env` to change JWT and auth. Before exposing the API on any network, enable auth and set a strong secret:

```bash
export NGS_AUTH_DISABLED=false
export NGS_JWT_SECRET="your-32-byte-minimum-secret"
docker compose up --build -d
```

With auth enabled, every `/api/**` call and `/mcp` need a token:

```bash
TOKEN=$(curl -s -X POST $API/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"change-me-123","displayName":"Me"}' | jq -r .token)
# later: POST /api/auth/login with {"email","password"}
claude mcp add --transport http genometrics http://localhost:8080/mcp --header "Authorization: Bearer $TOKEN"
```

Add `-H "Authorization: Bearer $TOKEN"` to the curl calls above.

Environment variables (`prod` profile inside the API container):

- `SPRING_DATASOURCE_URL` (default in compose: `jdbc:postgresql://postgres:5432/ngs`)
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
- `NGS_JWT_SECRET` (required when auth is enabled)
- `NGS_STORAGE_LOCAL_DIR` (default in compose: `/data/uploads`, persisted in volume `uploads_data`)

### Pull from GHCR (after merge to `main`)

```bash
export GENOMETRICS_API_IMAGE=ghcr.io/<owner>/genometrics-project/api:latest
cd docker
docker compose pull
docker compose up -d
```

### Postgres only (optional)

```bash
cd docker
docker compose up -d postgres
cd ../backend
SERVER_ADDRESS=127.0.0.1 mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

---

## 🧪 Tests

```bash
cd backend && mvn test     # in-memory H2
./scripts/ci-check.sh      # same checks as CI
```

Optional Postgres smoke test with Docker: `RUN_TESTCONTAINERS=true mvn -Dtest=PostgresContainerIT test`

---

## 🔀 Git & CI

**`main`** is the official branch. PRs target **`main`**; only the maintainer merges. See [`CONTRIBUTING.md`](CONTRIBUTING.md).

```bash
git fetch origin && git checkout main && git pull
git checkout -b feature/<name>
git push -u origin HEAD
# Open PR → main · CI · maintainer merges
```

| Event | Tests | GHCR image |
|-------|-------|------------|
| PR → `main` | ✅ | ❌ |
| Push to `feature/*`, `fix/*`, `hotfix/*` | ✅ | ❌ |
| Push to `main` | ✅ | ✅ |

Protect **`main`**: require PR, checks `backend` / `issue-link`, restrict merge to maintainer.

Every PR closes a prioritized issue (`P0` / `P1` / `P2`) with `Closes #<n>`. See [`CONTRIBUTING.md`](CONTRIBUTING.md#issues-priorities-and-prs).

AI agents: see [`AGENTS.md`](AGENTS.md) for project context.

---

## 🤝 Contributing & community

- [Contributing guide](CONTRIBUTING.md) · [Code of Conduct](CODE_OF_CONDUCT.md) · [Security policy](SECURITY.md)
- **Research use only.** GENOMETRICS is not a medical device and must not be used for diagnosis.
- Never commit or upload real patient data to this repository, issues or PRs.

## 📜 License

[Apache License 2.0](LICENSE) © Bruno Omena

---

## 💻 Hardware Notes

| Resource | Guidance |
|----------|----------|
| **RAM** | API heap ~1 GB; Postgres Compose ≤ 512 MB |
| **Disk** | Use fixtures in `datasets/`; prune `data/uploads` periodically |
| **GPU** | Not used |

---

<p align="center">
  <sub>GENOMETRICS · Bruno Omena · Local-first NGS analytics</sub>
</p>
