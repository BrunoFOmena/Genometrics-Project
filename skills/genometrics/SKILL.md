---
name: genometrics
description: Run local quality control on FASTQ files (single-end or R1/R2 pairs) and summarize VCF files, then compare files or explain a QC check. Use when the user asks to QC sequencing reads, check a FASTQ or VCF, summarize variants, or compare samples. Runs entirely on this machine with Python 3.10+ and no dependencies.
license: Apache-2.0
---

# GENOMETRICS: FASTQ QC and VCF summaries

Run the bundled CLI. It prints JSON on stdout and nothing leaves the machine. `<skill>` is the folder that contains this SKILL.md.

```bash
PYTHONPATH=<skill>/scripts python3 -m genometrics qc <file> [<file>]   # one FASTQ, or an R1/R2 pair
PYTHONPATH=<skill>/scripts python3 -m genometrics vcf <file>           # VCF summary
PYTHONPATH=<skill>/scripts python3 -m genometrics compare <file> <file> ...   # FASTQ and VCF side by side
PYTHONPATH=<skill>/scripts python3 -m genometrics explain <check>      # gcContent, meanQuality, ...
```

- Pass both mates of a paired-end sample in one `qc` call; the names must mark R1 and R2 (`_R1` / `_R2`).
- `qc` returns `verdict` (PASS, WARN or FAIL), `issues`, `next_step`, `checks` (each with its message), `metrics` and `analyzed` (path, size and SHA-256 of each input).
- `vcf` returns variant, SNP and indel counts, Ts/Tv, mean QUAL and depth, and PASS/FAIL counts.
- Errors go to stderr with exit code 1. Show the message to the user.
- A very large FASTQ (millions of reads) takes minutes; it is read in full.

## Rules

- Quote the verdict and check messages from the output. Do not re-derive thresholds; use `explain` to explain one.
- Report only values the command returned. If a metric is missing, say it is not available.
- The thresholds are rules of thumb from the Java reference implementation, not calibrated to an instrument or species.
- Research use only: not a diagnostic tool; never present results as clinical findings.
- Do not copy the user's sequencing data or results to external services.
