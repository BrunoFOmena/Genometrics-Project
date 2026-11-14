# Sample datasets

Tiny synthetic fixtures for local development. Do **not** commit large SRA/ENA dumps.

| File | Description |
|------|-------------|
| `sample.fastq` | 8 synthetic reads for FASTQ metrics |
| `sample.vcf` | 10 annotated variants for VCF metrics |
| `sample.fasta` | Tiny reference metadata fixture |

Load these files through the upload API after starting the stack, then query them with an MCP client (see the main README).
