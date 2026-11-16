# GENOMETRICS

[![CI](https://github.com/BrunoFOmena/Genometrics-Project/actions/workflows/ci.yml/badge.svg)](https://github.com/BrunoFOmena/Genometrics-Project/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Local-first quality control for FASTQ files and summaries for VCF files, packaged as an agent skill for Claude Code.

Sequencing data is sensitive and often cannot be uploaded to a cloud service. GENOMETRICS runs entirely on your machine: no server, no account, no dependencies beyond Python. Nothing leaves your computer. Instead of reading a wall of numbers, you ask in plain language and get a PASS/WARN/FAIL verdict with the metric behind each check.

## Install

Needs Python 3.10 or newer. In Claude Code, enter each command on its own line (pasting both at once fails):

```text
/plugin marketplace add BrunoFOmena/Genometrics-Project
```

```text
/plugin install genometrics@genometrics
```

Then run `/reload-plugins`; the skill is available after the reload.

Or copy [`skills/genometrics/`](skills/genometrics/) into `~/.claude/skills/`.

## Use

Ask in plain language, with the path of your file:

- "QC this file: /data/sample.fastq.gz"
- "QC this pair: /data/s1_R1.fastq.gz and /data/s1_R2.fastq.gz"
- "Summarize /data/calls.vcf.gz"
- "Compare these two files"
- "Explain the gcContent check"

Or run the CLI directly:

```bash
PYTHONPATH=skills/genometrics/scripts python3 -m genometrics qc datasets/sample.fastq
```

## Examples

Real answers from Claude Code with the plugin installed, on the fixtures in [`datasets/`](datasets/). The numbers come from the tool; Claude quotes them and ends every answer with a research-use note. The CLI itself prints JSON.

### QC of one file

> QC this file: datasets/sample.fastq

```text
Verdict: WARN. 5 checks gave a warning and 3 passed.

| Check              | Status | Message                                      |
|--------------------|--------|----------------------------------------------|
| meanQuality        | WARN   | Mean quality 24.4 below 28                   |
| gcContent          | WARN   | GC 38.7% outside 40-60%                      |
| readCount          | WARN   | Read count 8 is very low (fixture or failed run?) |
| perPositionQuality | WARN   | Quality drops below 20 at some positions     |
| overrepresented    | WARN   | Top sequence appears in 25.0% of sampled reads |
| nContent           | PASS   | N content 0.83% is low                       |
| duplicationRate    | PASS   | Duplication 12.5% is acceptable              |
| adapterContent     | PASS   | Adapter contamination within normal range    |

Metrics: 8 reads, average length 60.1, GC 38.7%, N 0.83%, mean quality 24.4, duplication 12.5%.
Next step (from the tool): "Consider quality trimming; inspect the per-position quality chart."

Research use only: these results are not a diagnosis and are not clinical findings.
```

### Explain a check

> Explain the gcContent check

```text
What it measures: the percentage of G and C bases in the reads.

Thresholds:
- PASS: within 40-60%
- WARN: within 35-65%
- FAIL: outside 35-65%

Your sample: GC was 38.7%. That is outside the 40-60% pass range but inside the
35-65% warn range, so the check returned WARN.

Research use only: these results are not a diagnosis and are not clinical findings.
```

### QC of an R1/R2 pair

> QC this pair: sample_R1.fastq and sample_R2.fastq

```text
Verdict: WARN. 5 checks gave a warning and 3 passed. The tool reported paired_end: true
and analyzed the two mates together.

Metrics: read count 8, average length 59.4, GC 36.6%, N 0.00%, mean quality 21.3, duplication 12.5%.
Files checked: sample_R1.fastq (537 bytes) and sample_R2.fastq (530 bytes), each with its SHA-256.

Research use only: these results are not a diagnosis and are not clinical findings.
```

## Layout

```text
skills/genometrics/     the skill: SKILL.md and scripts/ (the Python core, no dependencies)
.claude-plugin/         marketplace entry for the skill
datasets/               small FASTQ, VCF and FASTA samples, and the golden outputs of the reference implementation
tests/                  pytest: behavior, golden parity and the CLI
docs/adr/               architecture decisions
```

## Develop

```bash
pip install pytest ruff
ruff check && ruff format --check
pytest
```

The golden files in [`datasets/golden/`](datasets/golden/) hold the expected output for the fixtures; the tests check the core against them.

Research use only. Not a diagnostic tool.

[Apache-2.0](LICENSE)
