# 🧬 GENOMETRICS

### Ask your sequencing data questions. Get answers you can trace.

**Local-first FASTQ and VCF quality control that your AI agent can talk to.** Drop in a file, ask in plain language, and get a verdict backed by real metrics, on your own machine.

![MCP](https://img.shields.io/badge/MCP-ready-6E56CF)
![Local-first](https://img.shields.io/badge/Local--first-your%20data%20stays%20put-success)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

> **Research use only.** GENOMETRICS is not a medical device and must not be used for diagnosis.

---

## Why GENOMETRICS

Checking a sequencing run usually means stitching together command-line tools, reading plots and remembering what each threshold means. GENOMETRICS turns that into a conversation:

> *"Is this FASTQ good enough to align?"*
> *"Compare the mean quality of these two samples."*
> *"How many SNPs and indels are in this VCF?"*

Your agent (Claude Code, Cursor, any MCP client) calls GENOMETRICS, which parses the files **on your machine**, computes the metrics and answers with the numbers it actually measured, cited by sample and analysis. When a metric is missing, the tools report it as unavailable instead of guessing.

## What you get

- **Instant FASTQ QC.** Read count and length, Phred quality, GC and N content, duplication, adapter contamination and over-represented sequences, with a clear **PASS / WARN / FAIL** verdict per check and actionable recommendations. Paired-end runs are detected automatically from `R1` / `R2`.
- **VCF at a glance.** Variant count, SNP and indel split, Ts/Tv ratio, mean quality and mean depth.
- **Answers with receipts.** Every value your agent reports comes from a stored metric with its sample, analysis and unit attached.
- **Side-by-side comparison.** Query and compare any metric across samples, projects and analyses.
- **Reports you can hand over.** Export **PDF**, **HTML**, **CSV** or **JSON**.
- **Private by design.** The API listens on `127.0.0.1` only, nothing is uploaded anywhere, and browser pages cannot reach it (see [`SECURITY.md`](SECURITY.md)).
- **Plugs into your tools.** MCP over HTTP or stdio, plus a ready-made agent skill and a REST API with Swagger.

## See it in action

Using the tiny demo file `datasets/sample.fastq`:

```text
You:    Run QC on datasets/sample.fastq and tell me if I can trust it.

Agent:  Overall: WARN
        - Mean quality 24.4 is below 28
        - GC content 38.7% is outside the 40-60% range
        - N content 0.83% and duplication 12.5% are acceptable
        - Only 8 reads: fine for a fixture, suspicious for a real run
```

The verdicts and numbers above are what GENOMETRICS returns for that file; the wording is your agent's.

## Quick start

You need **JDK 21**, **Maven 3.9+** and `curl`. No containers, no cloud account.

```bash
git clone https://github.com/BrunoFOmena/Genometrics-Project.git
cd Genometrics-Project
./scripts/demo.sh
```

The demo starts the API, analyzes the sample FASTQ and VCF files in [`datasets/`](datasets/) and checks that your agent can query them. When it ends, the API is ready at `http://localhost:8080` (stop it with `kill $(cat data/api.pid)`).

### Use it with your agent

**Claude Code, zero setup.** The repository ships a skill ([`.claude/skills/genometrics`](.claude/skills/genometrics/SKILL.md)) and an [`.mcp.json`](.mcp.json). Open the project and ask: *"run QC on datasets/sample.fastq"*. The skill starts the API on demand, analyzes your files and reads the results back.

**Any MCP client.**

```bash
claude mcp add --transport http genometrics http://localhost:8080/mcp
```

Cursor, stdio-only clients and tool examples are in [`docs/mcp.md`](docs/mcp.md).

### Analyze your own files

```bash
./.claude/skills/genometrics/scripts/analyze.sh path/to/reads_R1.fastq path/to/reads_R2.fastq
./.claude/skills/genometrics/scripts/analyze.sh path/to/variants.vcf
```

The script starts the API if needed, uploads the files (`.gz` works too), waits for the analysis and prints the QC verdict plus a link to the report. Then just ask your agent about it.

## What your agent can do

| MCP tool | What it does |
|----------|--------------|
| `list_metrics` | Lists every metric with its unit and description |
| `query_metrics` | Queries and compares stored metrics with filters, grouping and aggregation |
| `explain_qc` | Explains QC results with citations (phrased by a local [Ollama](https://ollama.com) model when `NGS_AI_ENABLED=true`, otherwise a readable summary of the facts with the QC verdict) |
| `get_analysis_context` | Returns one analysis with all of its metrics |

## What it is not

GENOMETRICS inspects and summarizes data you already have. It does not align reads, call variants or annotate them. Think of it as the fast, trustworthy first look before you invest hours in a pipeline.

## How it works

```text
 FASTQ / VCF files ──►  GENOMETRICS (on your machine)  ──►  MCP tools  ──►  your AI agent
                         streaming parsers, QC checks,
                         metric facts, reports
```

Built with Java 21 and Spring Boot, with H2 by default and optional PostgreSQL.

## Good to know

<details>
<summary><b>Turn on authentication</b></summary>

Auth is off by default because the API only listens on your own machine. Before exposing it on any network, enable it and set a strong secret (the API refuses to start with a missing, short or public-default secret):

```bash
export NGS_AUTH_DISABLED=false
export NGS_JWT_SECRET="your-32-byte-minimum-secret"
cd backend && mvn spring-boot:run
```

Then every `/api/**` call and `/mcp` need a token:

```bash
API=http://localhost:8080
TOKEN=$(curl -s -X POST $API/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"change-me-123","displayName":"Me"}' | jq -r .token)
# later: POST /api/auth/login with {"email","password"}
claude mcp add --transport http genometrics $API/mcp --header "Authorization: Bearer $TOKEN"
```

Requests with a foreign `Host` or `Origin` are rejected; to reach the API through another host name see [`SECURITY.md`](SECURITY.md).
</details>

<details>
<summary><b>Use PostgreSQL instead of H2</b></summary>

Point the `prod` profile at your own server:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ngs
export SPRING_DATASOURCE_USERNAME=ngs SPRING_DATASOURCE_PASSWORD=change-me
export NGS_AUTH_DISABLED=false NGS_JWT_SECRET="your-32-byte-minimum-secret"
cd backend && mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Uploads go to `NGS_STORAGE_LOCAL_DIR`. Copy `.env.example` for the main options.
</details>

<details>
<summary><b>Run the API and explore it by hand</b></summary>

```bash
cd backend && mvn spring-boot:run
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| MCP | http://localhost:8080/mcp |
| Swagger | http://localhost:8080/swagger-ui.html |

Reports for a sample: `/api/reports/{sampleId}` (JSON), `/csv`, `/fastq/pdf` and `/fastq/html`.
</details>

<details>
<summary><b>Run the tests</b></summary>

```bash
cd backend && mvn test     # unit and integration tests (in-memory H2)
./scripts/ci-check.sh      # everything CI runs, including the demo
```
</details>

## Contributing

Contributions are welcome. Start with the [contributing guide](CONTRIBUTING.md); see also the [Code of Conduct](CODE_OF_CONDUCT.md) and the [security policy](SECURITY.md). AI agents working on the code should read [`AGENTS.md`](AGENTS.md).

Never commit or upload real patient data to this repository, issues or PRs.

## License

[Apache License 2.0](LICENSE) © Bruno Omena

---

<p align="center">
  <sub>GENOMETRICS · by Bruno Omena · Local-first NGS analytics for the agent era</sub>
</p>
