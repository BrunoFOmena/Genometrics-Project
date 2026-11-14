# Sample datasets

Tiny synthetic fixtures for local development. Do **not** commit large SRA/ENA dumps.

| File | Description |
|------|-------------|
| `sample.fastq` | 8 synthetic reads for FASTQ metrics |
| `sample.vcf` | 10 annotated variants for VCF metrics |
| `sample.fasta` | Tiny reference metadata fixture |

[`golden/`](golden/) holds the backend output for these fixtures, frozen as the contract for the Python port, and `evals/grounded-questions.json` holds the grounded-answer cases.

Load them all with `./scripts/demo.sh`, or through the upload API, then query them with an MCP client (see the main README).
