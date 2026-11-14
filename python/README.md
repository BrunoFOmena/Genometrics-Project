# genometrics (Python core)

Pure-Python port of the GENOMETRICS analysis core ([ADR 0001](../docs/adr/0001-core-language.md)). No runtime dependencies, so `pip` works on Linux, macOS and Windows.

```python
from genometrics import fastq, qc

metrics = fastq.parse("reads.fastq.gz")  # or fastq.parse_paired(r1, r2)
result = qc.evaluate(metrics)  # {"overall": "WARN", "checks": [...], "recommendations": [...]}
```

Output keys match the Java backend's API, and the tests check every value against the frozen Java output in [`../datasets/golden/`](../datasets/golden/).

```bash
cd python
uv run pytest        # parity tests
uv run ruff check    # lint
```

## Speed

Pure Python, one core, 200,000 reads of 150 bp: about 48,000 reads/s (7 Mbases/s), plain or gzip. A 10-million-read file takes about 3.5 minutes. How to give a quick answer on large files is decided in #65.
