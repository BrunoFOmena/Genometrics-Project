"""Behavior the tiny golden fixtures cannot show: gzip, errors, sampling, mate detection, Java number formatting."""

from __future__ import annotations

import gzip
import re
from pathlib import Path

import pytest
from genometrics import fastq, qc
from genometrics.javafmt import fixed, grouped

DATASETS = Path(__file__).resolve().parents[1] / "datasets"


def test_gzip_gives_the_same_metrics(tmp_path):
    packed = tmp_path / "sample.fastq.gz"
    packed.write_bytes(gzip.compress((DATASETS / "sample.fastq").read_bytes()))

    assert fastq.parse(packed) == fastq.parse(DATASETS / "sample.fastq")


@pytest.mark.parametrize(
    ("content", "message"),
    [
        ("", "no reads"),
        ("ACGT\n", "expected '@' header"),
        ("@r1\nACGT\n+\n", "Truncated"),
        ("@r1\nACGT\n-\nIIII\n", "expected '+' line"),
    ],
)
def test_rejects_invalid_fastq(tmp_path, content, message):
    path = tmp_path / "bad.fastq"
    path.write_text(content)

    with pytest.raises(fastq.FastqError, match=re.escape(message)):
        fastq.parse(path)


def test_paired_files_must_have_the_same_read_count(tmp_path):
    r1, r2 = tmp_path / "s_R1.fastq", tmp_path / "s_R2.fastq"
    r1.write_text("@a\nACGT\n+\nIIII\n@b\nACGT\n+\nIIII\n")
    r2.write_text("@a\nACGT\n+\nIIII\n")

    with pytest.raises(fastq.FastqError, match="mismatch"):
        fastq.parse_paired(r1, r2)


def test_duplication_and_adapters_use_only_the_first_reads(tmp_path, monkeypatch):
    monkeypatch.setattr(fastq, "SAMPLE_LIMIT", 2)
    path = tmp_path / "s.fastq"
    path.write_text("".join(f"@r{i}\nAGATCGGAAGAGTT\n+\nIIIIIIIIIIIIII\n" for i in range(5)))

    metrics = fastq.parse(path)

    assert metrics["duplicationRate"] == 50.0
    assert metrics["adapterHits"]["readsSampled"] == 2
    assert metrics["adapterHits"]["adapters"][0]["name"] == "Illumina_Universal"


@pytest.mark.parametrize(
    ("name", "end"),
    [("x_R1.fastq.gz", "R1"), ("x_r2.fq", "R2"), ("x_1.fastq", "R1"), ("x.2.fq", "R2"), ("x.fastq", "SINGLE")],
)
def test_read_end_from_file_name(name, end):
    assert fastq.read_end(name) == end


def test_java_compatible_number_formatting():
    assert fixed(0.125, 2) == "0.13"  # HALF_UP on the decimal form; Python's format() gives 0.12
    assert fixed(2.675, 2) == "2.68"
    assert fixed(24.416666666666668, 1) == "24.4"
    assert grouped(1234567) == "1,234,567"


def test_trailing_quality_drop_recommends_a_trim_cycle(tmp_path):
    path = tmp_path / "s.fastq"
    path.write_text("".join(f"@r{i}\nACGTACGTAC\n+\nIIIIIIII##\n" for i in range(3)))

    result = qc.evaluate(fastq.parse(path))

    per_position = next(c for c in result["checks"] if c["id"] == "perPositionQuality")
    assert per_position["status"] == "WARN"
    assert any("cycle 9" in r["detail"] for r in result["recommendations"])
