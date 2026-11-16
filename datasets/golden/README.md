# Golden outputs

The expected output for the fixtures in `datasets/`, used as acceptance tests for the core.

| File | Input | Function |
|------|-------|----------|
| `sample.fastq.json` | `sample.fastq` | `fastq.parse` |
| `sample_R1_R2.fastq.json` | `sample_R1.fastq` + `sample_R2.fastq` | `fastq.parse_paired` |
| `sample.vcf.json` | `sample.vcf` | `vcf.parse` |

`sample.fasta` has no golden file: the skill has no FASTA parser.

## Format

Every file is a JSON object with sorted keys:

| Key | Content |
|-----|---------|
| `inputs` | Fixture file names, in the order they were parsed |
| `kind` | `fastq` or `vcf` |
| `metrics` | The full parse result, as the parser returns it (nested objects) |
| `metricFacts` | The headline metrics: `key`, `value`, `unit` |
| `qc` | FASTQ only: the QC verdict as the API returns it |
| `qcChecks` | FASTQ only: stored checks with `key`, `status`, `value`, `message`; the first is `overall` |
| `recommendations` | FASTQ only: `severity`, `title`, `detail` |

## Comparison rules

- **Integers, booleans and strings:** exact, including QC statuses and check keys.
- **Floats:** relative tolerance `1e-9` (summation order may differ).
- **Objects:** key order does not matter.
- **Arrays:** order matters, except `overrepresented` entries with the same `count`, which may come in any order.
- **Messages** (`qcChecks[].message`, `recommendations[]`): compared exactly for now. If the core rewords them on purpose, update the golden files in the same change.

## Keeping them honest

`tests/test_golden.py` fails when the Python core drifts from these files. Change them only with a deliberate contract change that is reviewed like any other.

Then review the diff and commit it together with the code change.

Deliberate change (#97): the parsers now reject a FASTQ record whose quality length differs from its sequence length, and a VCF row with a non-numeric POS or QUAL. The fixtures had a few such reads, so they were corrected (quality padded or trimmed to the sequence length) and the FASTQ golden files regenerated from the Python core. Only mean quality (24.42, 21.31) and a position 61 entry changed; no status or message did.

## Limits

The fixtures are tiny (8 reads, 10 variants). They pin down formulas, thresholds and formats, but not behavior that only shows on real data: the 50,000-read sampling limit for duplication and adapters, gzip input, reads longer than 150 bp, multi-sample VCFs. A larger synthetic fixture would be needed for those.
