"""Streaming FASTQ metrics: a port of the Java FastqParser, FastqMateDetector and AdapterCatalog.

One pass over the reads. Output keys match the Java backend's API map (camelCase) so results can be compared with the
golden files in datasets/golden/.
"""

from __future__ import annotations

import gzip
import re
from collections.abc import Iterator
from pathlib import Path

SAMPLE_LIMIT = 50_000  # reads sampled for duplication, over-represented sequences and adapters
HEATMAP_MAX_POSITION = 150
HEATMAP_Q_BINS = 41

ADAPTERS: list[tuple[str, str]] = [
    ("Illumina_Universal", "AGATCGGAAGAG"),
    ("Illumina_Index", "GATCGGAAGAGC"),
    ("TruSeq_Universal", "AATGATACGGCGACCACCGAGATCTACAC"),
    ("TruSeq_Index", "CAAGCAGAAGACGGCATACGAGAT"),
    ("Nextera_Transposase", "CTGTCTCTTATACACATCT"),
    ("Nextera_Read1", "TCGTCGGCAGCGTCAGATGTGTATAAGAGACAG"),
    ("Nextera_Read2", "GTCTCGTGGGCTCGGAGATGTGTATAAGAGACAG"),
    ("SmallRNA_3p", "TGGAATTCTCGGGTGCCAAGG"),
    ("SmallRNA_5p", "GTTCAGAGTTCTACAGTCCGACGATC"),
    ("PolyA", "AAAAAAAAAAAA"),
]

_R1 = re.compile(r"(_R1|\.1\.|_1\.)", re.IGNORECASE)
_R2 = re.compile(r"(_R2|\.2\.|_2\.)", re.IGNORECASE)


class FastqError(ValueError):
    """The file is not valid FASTQ."""


def read_end(filename: str) -> str:
    """ "R1", "R2" or "SINGLE", from the file name (same rules as the Java mate detector)."""
    if _R2.search(filename):
        return "R2"
    if _R1.search(filename):
        return "R1"
    return "SINGLE"


def parse(path: str | Path) -> dict:
    """Metrics for a single-end FASTQ (plain or gzip)."""
    acc = _Accumulator()
    for seq, qual in _records(path):
        acc.add(seq, qual)
    if acc.read_count == 0:
        raise FastqError("FASTQ contains no reads")
    return acc.metrics(paired_end=False, r1=0, r2=0)


def parse_paired(r1_path: str | Path, r2_path: str | Path) -> dict:
    """Metrics for an R1/R2 pair; both mates count, and the files must hold the same number of reads."""
    acc = _Accumulator()
    r1_count = r2_count = 0
    r1, r2 = _records(r1_path), _records(r2_path)
    while True:
        rec1, rec2 = next(r1, None), next(r2, None)
        if rec1 is None and rec2 is None:
            break
        if rec1 is None or rec2 is None:
            raise FastqError(f"Paired FASTQ read count mismatch: R1={r1_count}, R2={r2_count}")
        acc.add(*rec1)
        r1_count += 1
        acc.add(*rec2)
        r2_count += 1
    if r1_count == 0:
        raise FastqError("FASTQ contains no reads")
    return acc.metrics(paired_end=True, r1=r1_count, r2=r2_count)


def metric_facts(metrics: dict) -> list[dict]:
    """The facts the MCP tools query, with the keys and units of the Java MetricCatalog."""
    return [
        {"key": "read_count", "value": float(metrics["readCount"]), "unit": "reads"},
        {"key": "average_length", "value": float(metrics["avgLength"]), "unit": "bp"},
        {"key": "gc_content", "value": float(metrics["gcContent"]), "unit": "%"},
        {"key": "n_content", "value": float(metrics["nContent"]), "unit": "%"},
        {"key": "mean_quality", "value": float(metrics["meanQuality"]), "unit": "Phred"},
        {"key": "duplication_rate", "value": float(metrics["duplicationRate"]), "unit": "%"},
    ]


def _open_text(path: str | Path):
    with open(path, "rb") as probe:
        gzipped = probe.read(2) == b"\x1f\x8b"
    if gzipped:
        return gzip.open(path, "rt", encoding="utf-8", newline="")
    return open(path, encoding="utf-8", newline="")


def _records(path: str | Path) -> Iterator[tuple[str, str]]:
    """(sequence, quality) per record; blank lines between records are skipped, as in the Java reader."""
    with _open_text(path) as handle:
        lines = (line.rstrip("\n").rstrip("\r") for line in handle)
        for header in lines:
            if not header.strip():
                continue
            if not header.startswith("@"):
                raise FastqError("Invalid FASTQ: expected '@' header")
            seq, plus, qual = next(lines, None), next(lines, None), next(lines, None)
            if seq is None or plus is None or qual is None:
                raise FastqError("Truncated FASTQ record")
            if not plus.startswith("+"):
                raise FastqError("Invalid FASTQ: expected '+' line")
            yield seq, qual


class _Accumulator:
    def __init__(self) -> None:
        self.read_count = 0
        self.total_length = 0
        self.min_length: int | None = None
        self.max_length = 0
        self.bases = {"A": 0, "T": 0, "C": 0, "G": 0}
        self.total_bases = 0
        self.quality_sum = 0
        self.quality_count = 0
        self.length_dist: dict[int, int] = {}
        self.pos_sum: list[int] = []
        self.pos_count: list[int] = []
        self.heatmap = [[0] * HEATMAP_Q_BINS for _ in range(HEATMAP_MAX_POSITION)]
        self.sequence_counts: dict[str, int] = {}
        self.duplicates = 0
        self.sampled = 0
        self.adapter_sampled = 0
        self.reads_with_adapter = 0
        self.adapter_counts: dict[str, int] = {}
        self.adapter_positions: dict[str, dict[int, int]] = {}

    def add(self, seq: str, qual: str) -> None:
        length = len(seq)
        self.read_count += 1
        self.total_length += length
        self.min_length = length if self.min_length is None else min(self.min_length, length)
        self.max_length = max(self.max_length, length)
        self.length_dist[length] = self.length_dist.get(length, 0) + 1

        upper = seq.upper()
        for base in "ATCG":
            self.bases[base] += upper.count(base)
        self.total_bases += length

        q_len = min(length, len(qual))
        if len(self.pos_sum) < q_len:
            grow = q_len - len(self.pos_sum)
            self.pos_sum.extend([0] * grow)
            self.pos_count.extend([0] * grow)
        pos_sum, pos_count, heatmap = self.pos_sum, self.pos_count, self.heatmap
        for i, char in enumerate(qual[:q_len]):
            phred = ord(char) - 33
            pos_sum[i] += phred
            pos_count[i] += 1
            if i < HEATMAP_MAX_POSITION:
                heatmap[i][max(0, min(HEATMAP_Q_BINS - 1, phred))] += 1
        self.quality_sum += sum(ord(char) for char in qual[:q_len]) - 33 * q_len
        self.quality_count += q_len

        if self.sampled < SAMPLE_LIMIT:
            self.sampled += 1
            if seq in self.sequence_counts:
                self.duplicates += 1
            self.sequence_counts[seq] = self.sequence_counts.get(seq, 0) + 1

        if self.adapter_sampled < SAMPLE_LIMIT:
            self.adapter_sampled += 1
            hit = False
            for name, adapter in ADAPTERS:
                index = upper.find(adapter)
                if index >= 0:
                    hit = True
                    self.adapter_counts[name] = self.adapter_counts.get(name, 0) + 1
                    positions = self.adapter_positions.setdefault(name, {})
                    positions[index + 1] = positions.get(index + 1, 0) + 1
            if hit:
                self.reads_with_adapter += 1

    def metrics(self, paired_end: bool, r1: int, r2: int) -> dict:
        n, total = self.read_count, self.total_bases
        a, t, c, g = (self.bases[b] for b in "ATCG")
        other = total - a - t - c - g
        mean_quality = self.quality_sum / self.quality_count if self.quality_count else 0.0
        overrepresented = sorted(
            ({"sequence": s, "count": k} for s, k in self.sequence_counts.items() if k > 1),
            key=lambda row: -row["count"],
        )[:10]
        return {
            "readCount": n,
            "avgLength": self.total_length / n if n else 0.0,
            "minLength": self.min_length or 0,
            "maxLength": self.max_length,
            "gcContent": (g + c) * 100 / total if total else 0.0,
            "atContent": (a + t) * 100 / total if total else 0.0,
            "nContent": other * 100 / total if total else 0.0,
            "meanQuality": mean_quality,
            "duplicationRate": self.duplicates * 100 / self.sampled if self.sampled else 0.0,
            "pairedEnd": paired_end,
            "readCountR1": r1,
            "readCountR2": r2,
            "lengthDistribution": {str(k): v for k, v in sorted(self.length_dist.items())},
            "baseComposition": {"A": a, "T": t, "C": c, "G": g, "N": other},
            "perPositionQuality": {
                str(i + 1): self.pos_sum[i] / self.pos_count[i] for i in range(len(self.pos_sum)) if self.pos_count[i]
            },
            "overrepresented": overrepresented,
            "phredSummary": {"mean": mean_quality, "encoding": "Phred+33"},
            "adapterHits": self._adapter_hits(),
            "qualityHeatmap": self._heatmap(),
        }

    def _adapter_hits(self) -> dict:
        sampled = self.adapter_sampled
        return {
            "readsSampled": sampled,
            "readsWithHit": self.reads_with_adapter,
            "fraction": self.reads_with_adapter * 100 / sampled if sampled else 0.0,
            "adapters": [
                {
                    "name": name,
                    "sequence": sequence,
                    "count": self.adapter_counts[name],
                    "positions": {str(p): k for p, k in sorted(self.adapter_positions[name].items())},
                }
                for name, sequence in ADAPTERS
                if self.adapter_counts.get(name)
            ],
        }

    def _heatmap(self) -> dict:
        data, max_position = [], 0
        for p, row in enumerate(self.heatmap):
            for q, count in enumerate(row):
                if count:
                    max_position = p + 1
                    data.append([p + 1, q, count])
        return {"maxPosition": max_position, "bins": HEATMAP_Q_BINS, "data": data}
