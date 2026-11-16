# GENOMETRICS

[![CI](https://github.com/BrunoFOmena/Genometrics-Project/actions/workflows/ci.yml/badge.svg)](https://github.com/BrunoFOmena/Genometrics-Project/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Local-first quality control for FASTQ files and summaries for VCF files, packaged as an agent skill for Claude Code.

Sequencing data is sensitive and often cannot be uploaded to a cloud service. GENOMETRICS runs entirely on your machine: no server, no account, no dependencies beyond Python. Nothing leaves your computer. Instead of reading a wall of numbers, you ask in plain language and get a PASS/WARN/FAIL verdict with the metric behind each check.
## Install

Needs Python 3.10 or newer. In Claude Code:

```text
/plugin marketplace add BrunoFOmena/Genometrics-Project
/plugin install genometrics@genometrics
```

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

## Example

Ask Claude Code "QC this file: datasets/sample.fastq", or run the CLI. The output is JSON; this is an abridged result:

```json
{
  "verdict": "WARN",
  "checks": [
    { "id": "meanQuality", "status": "WARN", "message": "Mean quality 24.4 below 28" },
    { "id": "gcContent", "status": "WARN", "message": "GC 38.7% outside 40–60%" },
    { "id": "nContent", "status": "PASS", "message": "N content 0.83% is low" },
    { "id": "duplicationRate", "status": "PASS", "message": "Duplication 12.5% is acceptable" },
    { "id": "readCount", "status": "WARN", "message": "Read count 8 is very low (fixture or failed run?)" }
  ],
  "metrics": { "read_count": 8.0, "average_length": 60.125, "gc_content": 38.67, "mean_quality": 24.42 }
}
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

The golden files in [`datasets/golden/`](datasets/golden/) hold the output of the Java reference implementation, removed in this version; the tests check the Python core against them. See [ADR 0001](docs/adr/0001-core-language.md).

Research use only. Not a diagnostic tool.

[Apache-2.0](LICENSE)
