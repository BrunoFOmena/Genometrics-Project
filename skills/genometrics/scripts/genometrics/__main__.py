"""Command line for the GENOMETRICS skill: `python3 -m genometrics <command> ...`. Prints JSON on stdout."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

from . import explain, fastq, qc, vcf

FASTQ_SUFFIXES = (".fastq", ".fq", ".fastq.gz", ".fq.gz")
VCF_SUFFIXES = (".vcf", ".vcf.gz")


def die(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def file_info(raw: str) -> tuple[Path, dict]:
    """The path, its size and its SHA-256, so every result says which bytes it describes."""
    path = Path(raw)
    if not path.is_file():
        die(f"not a regular file: {raw}")
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return path, {"path": str(path.resolve()), "size_bytes": path.stat().st_size, "sha256": digest.hexdigest()}


def kind_of(path: Path) -> str:
    name = path.name.lower()
    if name.endswith(FASTQ_SUFFIXES):
        return "fastq"
    if name.endswith(VCF_SUFFIXES):
        return "vcf"
    die(f"unsupported file (expected .fastq, .fq or .vcf, plain or .gz): {path}")
    raise AssertionError  # unreachable: die() exits


def run_qc(raws: list[str]) -> dict:
    if len(raws) not in (1, 2):
        die("give one FASTQ file, or an R1/R2 pair")
    found = [file_info(raw) for raw in raws]
    for path, _ in found:
        if kind_of(path) != "fastq":
            die(f"not a FASTQ file: {path}")
    if len(found) == 2:
        found.sort(key=lambda item: fastq.read_end(item[0].name))
        if [fastq.read_end(path.name) for path, _ in found] != ["R1", "R2"]:
            die("for a pair, one file name must mark R1 and the other R2 (e.g. _R1 / _R2)")
    paths = [path for path, _ in found]
    metrics = fastq.parse(paths[0]) if len(paths) == 1 else fastq.parse_paired(*paths)
    result = qc.evaluate(metrics)
    recs = result["recommendations"]
    return {
        "verdict": result["overall"],
        "issues": [
            {"check": c["id"], "status": c["status"], "message": c["message"]}
            for c in result["checks"]
            if c["status"] != "PASS"
        ],
        "next_step": recs[0]["detail"] if recs else "No action needed.",
        "recommendations": recs,
        "checks": result["checks"],
        "metrics": {
            "paired_end": bool(metrics["pairedEnd"]),
            **{f["key"]: f["value"] for f in fastq.metric_facts(metrics)},
        },
        "analyzed": [info for _, info in found],
    }


def run_vcf(raw: str) -> dict:
    path, info = file_info(raw)
    if kind_of(path) != "vcf":
        die(f"not a VCF file: {path}")
    m = vcf.parse(path)
    return {
        "metrics": {
            "variant_count": m["variantCount"],
            "snp_count": m["snpCount"],
            "indel_count": m["indelCount"],
            "mnp_count": m["mnpCount"],
            "ts_tv_ratio": m["tsTvRatio"],
            "mean_variant_quality": m["meanQuality"],
            "mean_depth": m["meanDepth"],
            "pass_count": m["passCount"],
            "fail_count": m["failCount"],
        },
        "chromosomes": m["chromosomeDistribution"],
        "filters": m["filterDistribution"],
        "analyzed": [info],
    }


def run_compare(raws: list[str]) -> dict:
    if len(raws) < 2:
        die("give at least two files")
    rows = []
    for raw in raws:
        path, _ = file_info(raw)
        if kind_of(path) == "fastq":
            result = run_qc([raw])
            rows.append(
                {
                    "path": str(path.resolve()),
                    "kind": "fastq",
                    "verdict": result["verdict"],
                    "metrics": result["metrics"],
                }
            )
        else:
            result = run_vcf(raw)
            rows.append({"path": str(path.resolve()), "kind": "vcf", "metrics": result["metrics"]})
    return {"files": rows}


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        prog="genometrics", description="Local FASTQ and VCF quality control. Prints JSON."
    )
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("qc", help="QC of one FASTQ, or an R1/R2 pair").add_argument("files", nargs="+")
    sub.add_parser("vcf", help="Summary of one VCF").add_argument("file")
    sub.add_parser("compare", help="Metrics of several files side by side").add_argument("files", nargs="+")
    sub.add_parser("explain", help="What a QC check measures and its thresholds").add_argument("check")
    args = parser.parse_args(argv)
    try:
        if args.command == "qc":
            result = run_qc(args.files)
        elif args.command == "vcf":
            result = run_vcf(args.file)
        elif args.command == "compare":
            result = run_compare(args.files)
        else:
            result = explain.explain_check(args.check)
    except (ValueError, OSError) as exc:
        die(str(exc))
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
