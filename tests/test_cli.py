"""The CLI the skill calls, run as a subprocess the way Claude runs it."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "skills" / "genometrics" / "scripts"
DATASETS = ROOT / "datasets"


def cli(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, "-m", "genometrics", *args], cwd=SCRIPTS, capture_output=True, text=True, check=False
    )


def test_qc_pair_is_read_in_either_order():
    r1, r2 = DATASETS / "sample_R1.fastq", DATASETS / "sample_R2.fastq"
    result = cli("qc", str(r2), str(r1))
    assert result.returncode == 0, result.stderr
    data = json.loads(result.stdout)
    assert data["verdict"] in {"PASS", "WARN", "FAIL"}
    assert data["metrics"]["paired_end"] is True
    assert [f["path"] for f in data["analyzed"]] == [str(r1.resolve()), str(r2.resolve())]
    assert len(data["analyzed"][0]["sha256"]) == 64


def test_vcf_summary():
    data = json.loads(cli("vcf", str(DATASETS / "sample.vcf")).stdout)
    assert data["metrics"]["variant_count"] > 0
    assert data["analyzed"][0]["size_bytes"] > 0


def test_compare_mixes_fastq_and_vcf():
    data = json.loads(cli("compare", str(DATASETS / "sample.fastq"), str(DATASETS / "sample.vcf")).stdout)
    assert [f["kind"] for f in data["files"]] == ["fastq", "vcf"]


def test_explain_check():
    data = json.loads(cli("explain", "gcContent").stdout)
    assert "40" in data["thresholds"]


@pytest.mark.parametrize(
    ("args", "message"),
    [
        (("qc", "/nonexistent/x.fastq"), "not a regular file"),
        (("vcf", str(DATASETS / "sample.fastq")), "not a VCF file"),
        (("qc", str(DATASETS / "sample.vcf")), "not a FASTQ file"),
        (("qc", str(DATASETS / "sample.fastq"), str(DATASETS / "sample.fastq")), "R1 and the other R2"),
        (("explain", "nope"), "Unknown check"),
    ],
)
def test_errors_go_to_stderr_with_exit_code_1(args, message):
    result = cli(*args)
    assert result.returncode == 1
    assert result.stdout == ""
    assert message in result.stderr
