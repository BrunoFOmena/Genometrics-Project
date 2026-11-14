# Golden outputs

What the Java backend produces for the fixtures in `datasets/`, frozen as the acceptance tests of the Python port ([ADR 0001](../../docs/adr/0001-core-language.md), migration step 1).

| File | Input | Produced by |
|------|-------|-------------|
| `sample.fastq.json` | `sample.fastq` | `FastqParser.parseResult` |
| `sample_R1_R2.fastq.json` | `sample_R1.fastq` + `sample_R2.fastq` | `FastqParser.parsePairedResult` |
| `sample.vcf.json` | `sample.vcf` | `VcfParser.parseResult` |

`sample.fasta` has no golden file: FASTA uploads only record metadata.

## Format

Every file is a JSON object with sorted keys:

| Key | Content |
|-----|---------|
| `inputs` | Fixture file names, in the order they were parsed |
| `kind` | `fastq` or `vcf` |
| `metrics` | The full parse result, as the REST API returns it (nested objects, no ids or timestamps) |
| `metricFacts` | The facts the MCP tools query: `key`, `value`, `unit` (see `MetricCatalog`) |
| `qc` | FASTQ only: the QC verdict as the API returns it |
| `qcChecks` | FASTQ only: stored checks with `key`, `status`, `value`, `message`; the first is `overall` |
| `recommendations` | FASTQ only: `severity`, `title`, `detail` |

## Comparison rules for the Python port

- **Integers, booleans and strings:** exact, including QC statuses and check keys.
- **Floats:** relative tolerance `1e-9` (Java `double` arithmetic; summation order may differ).
- **Objects:** key order does not matter.
- **Arrays:** order matters, except `overrepresented` entries with the same `count`, which may come in any order.
- **Messages** (`qcChecks[].message`, `recommendations[]`): compared exactly for now. If the Python port rewords them on purpose, update the golden files in the same change.

## Keeping them honest

`GoldenContractTest` (backend) fails when the Java output drifts from these files. To change them on purpose:

```bash
cd backend && mvn test -Dtest=GoldenContractTest -Dgolden.update=true
```

Then review the diff and commit it together with the code change.

## Limits

The fixtures are tiny (8 reads, 10 variants). They pin down formulas, thresholds and formats, but not behavior that only shows on real data: the 50,000-read sampling limit for duplication and adapters, gzip input, reads longer than 150 bp, multi-sample VCFs. The Python port needs a larger synthetic fixture for those (migration step 2).
