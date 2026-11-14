# GENOMETRICS

Local-first quality control for FASTQ files and summaries for VCF files. It runs on your machine: no server, no account, nothing leaves your computer. It is packaged as an agent skill for Claude Code.

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
