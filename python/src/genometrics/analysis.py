"""File-level analysis for the MCP tools: path checks, the result cache keyed by file hash, and the compact results."""

from __future__ import annotations

import hashlib
import json
import os
import sys
from pathlib import Path

from . import __version__, fastq, qc, vcf

_FASTQ_SUFFIXES = (".fastq", ".fq", ".fastq.gz", ".fq.gz")
_VCF_SUFFIXES = (".vcf", ".vcf.gz")


def cache_dir() -> Path:
    override = os.environ.get("GENOMETRICS_CACHE_DIR")
    if override:
        return Path(override)
    if sys.platform == "win32":
        base = Path(os.environ.get("LOCALAPPDATA") or Path.home() / "AppData" / "Local")
    elif sys.platform == "darwin":
        base = Path.home() / "Library" / "Caches"
    else:
        base = Path(os.environ.get("XDG_CACHE_HOME") or Path.home() / ".cache")
    return base / "genometrics"


def check_path(raw: str) -> Path:
    """An absolute, readable, regular file. Raises ValueError with a message the agent can act on."""
    path = Path(raw)
    if not path.is_absolute():
        raise ValueError(f"Path must be absolute: {raw}")
    if not path.is_file():
        raise ValueError(f"Not a regular file: {raw}")
    if not os.access(path, os.R_OK):
        raise ValueError(f"File is not readable: {raw}")
    return path


def kind_of(path: Path) -> str:
    name = path.name.lower()
    if name.endswith(_FASTQ_SUFFIXES):
        return "fastq"
    if name.endswith(_VCF_SUFFIXES):
        return "vcf"
    raise ValueError(f"Unsupported file type (expected .fastq, .fq or .vcf, plain or .gz): {path}")


def describe(path: Path) -> dict:
    """What was analyzed: path, size in bytes and SHA-256."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return {"path": str(path), "size_bytes": path.stat().st_size, "sha256": digest.hexdigest()}


def _cached(kind: str, files: list[dict], compute) -> dict:
    key = hashlib.sha256(json.dumps([__version__, kind, [f["sha256"] for f in files]]).encode()).hexdigest()
    entry = cache_dir() / f"{key}.json"
    try:
        result = json.loads(entry.read_text(encoding="utf-8"))
        return {**result, "analyzed": files, "cached": True}
    except (OSError, ValueError):
        pass
    result = compute()
    try:
        entry.parent.mkdir(parents=True, exist_ok=True)
        tmp = entry.with_suffix(f".{os.getpid()}.tmp")
        tmp.write_text(json.dumps(result), encoding="utf-8")
        tmp.replace(entry)
    except OSError:
        pass  # a read-only cache dir only costs speed
    return {**result, "analyzed": files, "cached": False}


def qc_fastq(paths: list[str]) -> dict:
    if len(paths) not in (1, 2):
        raise ValueError("Give one FASTQ file, or an R1/R2 pair")
    resolved = [check_path(p) for p in paths]
    for path in resolved:
        if kind_of(path) != "fastq":
            raise ValueError(f"Not a FASTQ file: {path}")
    if len(resolved) == 2:
        ends = [fastq.read_end(p.name) for p in resolved]
        if sorted(ends) != ["R1", "R2"]:
            raise ValueError("For a pair, one file name must mark R1 and the other R2 (e.g. _R1 / _R2)")
        resolved.sort(key=lambda p: fastq.read_end(p.name))
    files = [describe(p) for p in resolved]

    def compute() -> dict:
        metrics = fastq.parse(resolved[0]) if len(resolved) == 1 else fastq.parse_paired(*resolved)
        result = qc.evaluate(metrics)
        failing = [c for c in result["checks"] if c["status"] != "PASS"]
        recs = result["recommendations"]
        return {
            "verdict": result["overall"],
            "issues": [{"check": c["id"], "status": c["status"], "message": c["message"]} for c in failing],
            "next_step": recs[0]["detail"] if recs else "No action needed.",
            "recommendations": recs,
            "checks": result["checks"],
            "metrics": {
                "paired_end": bool(metrics["pairedEnd"]),
                **{f["key"]: f["value"] for f in fastq.metric_facts(metrics)},
            },
        }

    return _cached("qc_fastq", files, compute)


def summarize_vcf(path: str) -> dict:
    resolved = check_path(path)
    if kind_of(resolved) != "vcf":
        raise ValueError(f"Not a VCF file: {resolved}")
    files = [describe(resolved)]

    def compute() -> dict:
        m = vcf.parse(resolved)
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
        }

    return _cached("summarize_vcf", files, compute)


def compare(paths: list[str]) -> dict:
    """The metrics of each file side by side. Each FASTQ is analyzed on its own; a pair needs qc_fastq."""
    if len(paths) < 2:
        raise ValueError("Give at least two files")
    rows = []
    for raw in paths:
        path = check_path(raw)
        kind = kind_of(path)
        result = qc_fastq([raw]) if kind == "fastq" else summarize_vcf(raw)
        rows.append(
            {
                "path": str(path),
                "kind": kind,
                "verdict": result.get("verdict"),
                "metrics": result["metrics"],
                "sha256": result["analyzed"][0]["sha256"],
            }
        )
    return {"files": rows}


def report(paths: list[str]) -> str:
    """A Markdown report of one FASTQ (or R1/R2 pair) or one VCF, to hand to a colleague."""
    first = check_path(paths[0]) if paths else None
    if first is None:
        raise ValueError("Give at least one file")
    if kind_of(first) == "vcf":
        if len(paths) != 1:
            raise ValueError("A VCF report takes exactly one file")
        result = summarize_vcf(paths[0])
        title = f"VCF summary: {first.name}"
        body = _table(result["metrics"])
    else:
        result = qc_fastq(paths)
        title = f"FASTQ QC: {', '.join(Path(f['path']).name for f in result['analyzed'])}"
        checks = "\n".join(f"| {c['id']} | {c['status']} | {c['message']} |" for c in result["checks"])
        recs = "\n".join(f"- **{r['title']}** ({r['severity']}): {r['detail']}" for r in result["recommendations"])
        body = (
            f"**Verdict: {result['verdict']}**\n\n| Check | Status | Detail |\n|---|---|---|\n{checks}\n\n"
            f"### Next step\n\n{recs or result['next_step']}\n\n### Metrics\n\n{_table(result['metrics'])}"
        )
    files = "\n".join(
        f"- `{f['path']}`, {f['size_bytes']:,} bytes, SHA-256 `{f['sha256']}`" for f in result["analyzed"]
    )
    return (
        f"# {title}\n\n{body}\n\n### Files analyzed\n\n{files}\n\n"
        f"_GENOMETRICS {__version__}. Research use only; not a diagnostic result._\n"
    )


def _table(metrics: dict) -> str:
    rows = "\n".join(f"| {k} | {v:g} |" if isinstance(v, float) else f"| {k} | {v} |" for k, v in metrics.items())
    return f"| Metric | Value |\n|---|---|\n{rows}"
