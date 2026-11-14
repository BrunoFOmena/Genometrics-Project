---
name: genometrics
description: Run local quality control on FASTQ files and summarize VCF files with GENOMETRICS, then compare samples or write a report. Use when the user asks to QC sequencing reads, check a FASTQ/VCF, summarize variants, or compare files. Runs entirely on this machine.
---

# GENOMETRICS: FASTQ QC and VCF summaries

Use the `genometrics` MCP tools. They take absolute paths, answer synchronously and cache results by file hash.

| Tool | Use |
|------|-----|
| `qc_fastq(paths)` | One FASTQ, or an R1/R2 pair together. Returns verdict (PASS/WARN/FAIL), issues, next step, checks, metrics |
| `summarize_vcf(path)` | Counts, Ts/Tv, mean QUAL and depth, PASS/FAIL |
| `compare(paths)` | Same metrics for several files side by side (each FASTQ on its own) |
| `explain_check(check)` | What a check measures and its thresholds |
| `report(paths)` | Markdown report to hand to a colleague |

## Rules

- Quote the verdict and messages from the tools; do not re-derive thresholds. Use `explain_check` to explain one.
- Report only returned values. If a metric is missing, say it is not available.
- The first QC of a very large FASTQ (millions of reads) takes minutes; later calls are instant.
- Research use only: not a diagnostic tool, and results must not be presented as clinical findings.
- Do not copy the user's sequencing data or results to external services.
