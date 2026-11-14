# genometrics-mcp (Python core)

## Use it from an agent

```bash
claude mcp add genometrics -- uvx genometrics-mcp
```

Then ask "QC this file". The server speaks MCP over stdio: no port, no account, nothing leaves the machine. Tools (all read-only, synchronous, absolute paths only):

| Tool | Returns |
|------|---------|
| `qc_fastq(paths)` | One FASTQ or an R1/R2 pair: verdict, issues, next step, checks, metrics |
| `summarize_vcf(path)` | Counts, Ts/Tv, mean QUAL and depth, PASS/FAIL |
| `compare(paths)` | The same metrics for several files side by side |
| `explain_check(check)` | What a QC check measures and its thresholds |
| `report(paths)` | A Markdown report to hand to a colleague |

Every result lists the files analyzed (path, size, SHA-256) and is cached by file hash in the user cache directory (`~/.cache/genometrics` on Linux; override with `GENOMETRICS_CACHE_DIR`), so asking again is instant.

## Library

Pure-Python port of the GENOMETRICS analysis core ([ADR 0001](../docs/adr/0001-core-language.md)). No runtime dependencies, so `pip` works on Linux, macOS and Windows.

```python
from genometrics import fastq, qc, vcf

metrics = fastq.parse("reads.fastq.gz")  # or fastq.parse_paired(r1, r2)
result = qc.evaluate(metrics)  # {"overall": "WARN", "checks": [...], "recommendations": [...]}
summary = vcf.parse("calls.vcf.gz")  # counts, Ts/Tv, QUAL and DP, distributions, header
```

Output keys match the Java backend's API, and the tests check every value against the frozen Java output in [`../datasets/golden/`](../datasets/golden/).

```bash
cd python
uv run pytest        # parity tests
uv run ruff check    # lint
```

## Speed

Pure Python, one core, 200,000 reads of 150 bp: about 48,000 reads/s (7 Mbases/s), plain or gzip. A 10-million-read file takes about 3.5 minutes. The first QC of a large file therefore takes minutes (results are cached afterwards); `qc_fastq` says so in its description.
