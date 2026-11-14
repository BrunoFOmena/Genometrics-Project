"""Parity with the Java backend: every value in datasets/golden/*.json (rules in datasets/golden/README.md)."""

from __future__ import annotations

import json
import math
from pathlib import Path

import pytest
from genometrics import fastq, qc, vcf

DATASETS = Path(__file__).resolve().parents[1] / "datasets"
GOLDEN = DATASETS / "golden"


def assert_same(actual, expected, path="$"):
    """Exact for ints, bools and strings; relative 1e-9 for floats; key order ignored; array order kept."""
    if isinstance(expected, dict):
        assert isinstance(actual, dict), f"{path}: expected object, got {actual!r}"
        assert set(actual) == set(expected), f"{path}: keys {sorted(set(actual) ^ set(expected))} differ"
        for key in expected:
            assert_same(actual[key], expected[key], f"{path}.{key}")
    elif isinstance(expected, list):
        assert isinstance(actual, list) and len(actual) == len(expected), (
            f"{path}: length {len(actual)} != {len(expected)}"
        )
        for i, (a, e) in enumerate(zip(actual, expected, strict=True)):
            assert_same(a, e, f"{path}[{i}]")
    elif isinstance(expected, bool) or expected is None or isinstance(expected, str):
        assert actual == expected, f"{path}: {actual!r} != {expected!r}"
    elif isinstance(expected, (int, float)):
        assert isinstance(actual, (int, float)) and not isinstance(actual, bool), f"{path}: {actual!r} is not a number"
        assert math.isclose(actual, expected, rel_tol=1e-9, abs_tol=1e-12), f"{path}: {actual!r} != {expected!r}"
    else:
        raise AssertionError(f"{path}: unexpected golden value {expected!r}")


CASES = {
    "sample.fastq.json": lambda: fastq.parse(DATASETS / "sample.fastq"),
    "sample_R1_R2.fastq.json": lambda: fastq.parse_paired(DATASETS / "sample_R1.fastq", DATASETS / "sample_R2.fastq"),
}


@pytest.mark.parametrize("golden_file", sorted(CASES))
def test_matches_the_java_golden_output(golden_file):
    golden = json.loads((GOLDEN / golden_file).read_text(encoding="utf-8"))
    metrics = CASES[golden_file]()
    result = qc.evaluate(metrics)

    assert golden["kind"] == "fastq"
    assert_same(metrics, golden["metrics"], "$.metrics")
    assert_same({"overall": result["overall"], "checks": result["checks"]}, golden["qc"], "$.qc")
    assert_same(result["recommendations"], golden["recommendations"], "$.recommendations")
    assert_same(fastq.metric_facts(metrics), golden["metricFacts"], "$.metricFacts")
    assert_same(qc.stored_checks(result), golden["qcChecks"], "$.qcChecks")


def test_vcf_matches_the_java_golden_output():
    golden = json.loads((GOLDEN / "sample.vcf.json").read_text(encoding="utf-8"))
    metrics = vcf.parse(DATASETS / "sample.vcf")

    assert golden["kind"] == "vcf"
    assert_same(metrics, golden["metrics"], "$.metrics")
    assert_same(vcf.metric_facts(metrics), golden["metricFacts"], "$.metricFacts")


def test_every_golden_file_is_covered():
    covered = set(CASES) | {"sample.vcf.json"}
    assert {p.name for p in GOLDEN.glob("*.json")} == covered
