# Sample datasets

Tiny synthetic fixtures for local development. Do **not** commit large SRA/ENA dumps.

| File | Description |
|------|-------------|
| `sample.fastq` | 8 synthetic reads for FASTQ metrics |
| `sample.vcf` | 10 annotated variants for VCF metrics |
| `sample.fasta` | Tiny reference metadata fixture |

[`golden/`](golden/) holds the output of the removed Java backend for these fixtures, frozen as the contract for the Python port, and `evals/grounded-questions.json` holds the grounded-answer cases.

Run the CLI on them, as shown in the main README.
