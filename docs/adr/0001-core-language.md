# ADR 0001: Core language

- **Status:** Accepted, 2026-11-14
- **Decided by:** Bruno Omena (maintainer)
- **Issue:** #30

## Decision

GENOMETRICS moves to a **Python core with an MCP server**. A UI, if it comes back, is an optional viewer on the same HTTP API and never owns logic. The Java backend stays as the reference implementation until the Python core reaches parity, then it is retired.

This is option 1 of #30. Not chosen:

- **Keep Java, add a thin Python MCP wrapper (option 2):** users would run and install two runtimes (JVM and Python) for one tool, and every new capability would still be written in Java.
- **Hybrid (option 3):** the same two-runtime cost as option 2, plus a permanent boundary between the two.

## Why

- The genomics ecosystem is Python-first: pysam, cyvcf2, scikit-allel, DuckDB, Parquet. The roadmap (#31 Parquet + DuckDB, #34 annotation, #35 insights reports) lives there.
- Researchers and agent tooling can read, extend and install Python far more easily than a Spring Boot service. The official MCP SDK is available in Python.
- One runtime to install: the skill, the demo and CI no longer need a JVM and Maven.

## Costs we accept

- **A rewrite.** About 4,200 lines of Java (parsing, QC, analysis, upload, reports, MCP, AI, auth) and 85 tests have to be re-created or retired.
- **Speed.** Pure Python is slow on large FASTQ. The reader must be chosen by benchmark (a native-backed reader or vectorized parsing), not assumed.
- **Native dependencies.** pysam and cyvcf2 wrap htslib and have limited or no native Windows support; Windows users would go through WSL. Java ran on Windows as a single jar.
- **Two codebases during the migration.** Mitigated by a frozen contract and parity tests (below).

## What must not change (the contract)

The migration is judged against behavior that already exists and is tested:

- The **MCP tools** and their arguments: `list_metrics`, `query_metrics`, `explain_qc`, `get_analysis_context` (`compare_analyses` was removed in #54; others may change under #49 to #54; the contract is whatever is on `main` when each step starts).
- The **metric keys and units** in `MetricCatalog`, and the **QC thresholds and verdicts** in `FastqQcThresholds` / `FastqQcEvaluator`.
- The **fixtures** in `datasets/` and the grounded-answer cases in `datasets/evals/grounded-questions.json`.
- The **local-first guarantees**: loopback only, Host/Origin guard, optional bearer token, research-use-only notice.

## Migration plan

Strangler, not big-bang. The `backend` CI job and its required check stay green until the cutover.

| Java today | Python target | Notes |
|------------|---------------|-------|
| `FastqParser`, `FastqMateDetector`, `AdapterCatalog` | `core/fastq.py` | One streaming pass: read count and length stats, GC / AT / N, mean Phred, per-position quality, 150 x 41 quality heatmap, length and base distributions, adapter hits, top 10 over-represented sequences, duplication on the first 50,000 reads. Paired-end by `R1` / `R2`. |
| `FastqQcEvaluator`, `FastqQcThresholds` | `core/qc.py` | Same thresholds, same PASS / WARN / FAIL per check, same recommendations. |
| `VcfParser` | `core/vcf.py` (cyvcf2 or pysam) | Counts by SNP / indel / MNP, Ts/Tv (and per chromosome), mean QUAL and DP, FILTER, chromosome, allele frequency and indel-length distributions, histograms, header. |
| `AnalysisService`, `MetricFactWriter`, JPA entities | `core/analysis.py` + the data layer of #31 | `analyze(path)` returns results; jobs go QUEUED, RUNNING, DONE or FAILED in a process or thread pool; facts are written to Parquet and DuckDB keyed by file hash (idempotent re-runs). Replaces H2 (PostgreSQL support was removed in #46). |
| `McpController`, `McpStdioRunner` | `mcp-server/` | Official `mcp` SDK. stdio first, then Streamable HTTP. Same Host/Origin guard on HTTP. |
| REST controllers, Swagger, JWT | `mcp-server/` HTTP app | Only what the skill, demo and a future UI need: projects, samples, upload, analyses, reports. Optional bearer token. |
| `ReportService`, `FastqReportService` | `core/reports.py` | HTML and CSV first; the PDF library is chosen in its own issue. |

### Steps

**Amended 2026-11-14: vertical slice.** Instead of porting every component before publishing, ship a small Python MCP server that people can install in one line, then grow it. The previous step list is replaced by:

1. **Freeze the contract** (#62, done): golden files in `datasets/golden/` with the Java output for every fixture.
2. **v0.1, FASTQ** (#63): `python/` package (`uv`, `pytest`, `ruff`, CI job `python`), pure-Python FASTQ parser and QC evaluator with golden parity. No native dependencies, so `pip` works on Windows too.
3. **v0.1, VCF** (#64): pure-Python VCF summary with golden parity.
4. **v0.1, MCP server** (#65): stdio only, installed with `uvx`, **synchronous** tools designed for the user (`qc_fastq`, `summarize_vcf`, `compare`, `explain_check`), results cached by file hash, a report to hand over. No HTTP, REST, auth, projects or samples in v0.1.
5. **Validate and switch** (#66): compare our numbers with FastQC / fastp / bcftools on public datasets; move the demo, skill, CI and docs to Python; the ruleset's required checks change in the same step.
6. **Retire Java** (#67).

The Parquet + DuckDB data layer (#31) is no longer on the v0.1 path: a JSON cache per file hash is enough until cohort queries are needed.

The contract changes accordingly: **metric values, QC verdicts, messages and recommendations stay identical to the golden files**; the MCP tool names and shapes are redesigned in #65 (the Java tools are not ported 1:1).

### Measured so far

Pure-Python FASTQ reader (#63), one core, 200,000 reads of 150 bp: about 48,000 reads/s (7 Mbases/s), plain or gzip. A 10-million-read file takes about 3.5 minutes, so "answer in seconds" needs a decision in #65: sample the first reads for a quick verdict, add an optional native reader, or both.

### Done when

- Every golden fixture and eval passes against the Python core.
- `uvx genometrics-mcp` (one line) gives a QC answer, with Python only.
- CI is green on the new jobs and the ruleset points at them.

## Open questions

- FASTQ speed on large files: quick verdict from the first reads, an optional native reader, or both (see "Measured so far").
- REST framework (FastAPI or something smaller) and whether Swagger-style docs are kept.
- Windows policy: WSL only, or a pure-Python VCF fallback.
- PDF report library.
- Whether and when a UI returns (not part of v0.1).

## Until the cutover

New features keep going into the Java backend only when they are bug fixes or contract changes that the Python port will need to match. Everything else waits for the Python core, so work is not written twice.
