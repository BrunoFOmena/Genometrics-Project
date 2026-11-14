"""FASTQ quality-control verdict: a port of the Java FastqQcEvaluator and FastqQcThresholds.

Same checks, thresholds, messages and recommendations, so results match the golden files.
"""

from __future__ import annotations

import math

from .fastq import SAMPLE_LIMIT
from .javafmt import fixed, grouped

MEAN_Q_PASS, MEAN_Q_WARN = 28.0, 20.0
GC_PASS_MIN, GC_PASS_MAX = 40.0, 60.0
GC_WARN_MIN, GC_WARN_MAX = 35.0, 65.0
N_PASS_MAX, N_WARN_MAX = 1.0, 5.0
DUP_PASS_MAX, DUP_WARN_MAX = 20.0, 50.0
READ_COUNT_PASS, READ_COUNT_WARN = 100_000, 10_000
PER_POSITION_Q_WARN = 20.0
TRAILING_DROP_DELTA = 10.0
OVERREP_FRACTION_WARN = 0.05
ADAPTER_FRACTION_WARN = 5.0

_RECOMMENDATIONS = {
    "duplicationRate": (
        "High duplication",
        "Review PCR cycles and library prep; consider deduplication before alignment.",
    ),
    "meanQuality": ("Low mean quality", "Consider quality trimming; inspect the per-position quality chart."),
    "nContent": ("Elevated N bases", "Check instrument cycles or sample degradation."),
    "gcContent": ("Unusual GC content", "Verify species reference and library prep; compare with expected genome GC%."),
    "readCount": ("Low read count", "Confirm sequencing yield; low counts may indicate failed run or test fixture."),
    "overrepresented": (
        "Possible adapter contamination",
        "Inspect the Overrepresented sequences table; consider adapter trimming.",
    ),
    "adapterContent": (
        "Adapter contamination detected",
        "Review adapter hits in detailed stats; trim adapters before alignment.",
    ),
}


def evaluate(metrics: dict) -> dict:
    """{"overall", "checks": [{id, status, value, message}], "recommendations": [{severity, title, detail}]}."""
    per_position = {int(k): float(v) for k, v in metrics.get("perPositionQuality", {}).items()}
    checks = [
        _mean_quality(metrics["meanQuality"]),
        _gc(metrics["gcContent"]),
        _n_content(metrics["nContent"]),
        _duplication(metrics["duplicationRate"]),
        _read_count(metrics["readCount"]),
        _per_position(per_position),
        _overrepresented(metrics.get("overrepresented", []), metrics["readCount"]),
        _adapters(metrics.get("adapterHits", {})),
    ]
    statuses = {c["status"] for c in checks}
    overall = "FAIL" if "FAIL" in statuses else "WARN" if "WARN" in statuses else "PASS"
    trim_cycle = next((p for p in sorted(per_position) if per_position[p] < PER_POSITION_Q_WARN), None)
    return {"overall": overall, "checks": checks, "recommendations": _recommendations(checks, trim_cycle)}


def stored_checks(result: dict) -> list[dict]:
    """The checks as stored for agents: an "overall" row first, then one row per check."""
    rows = [{"key": "overall", "status": result["overall"], "value": None, "message": "Overall FASTQ quality control"}]
    rows += [
        {"key": c["id"], "status": c["status"], "value": c["value"], "message": c["message"]} for c in result["checks"]
    ]
    return rows


def _check(check_id: str, status: str, value: float, message: str) -> dict:
    return {"id": check_id, "status": status, "value": value, "message": message}


def _mean_quality(q: float) -> dict:
    if q >= MEAN_Q_PASS:
        return _check("meanQuality", "PASS", q, f"Mean quality {fixed(q, 1)} is acceptable")
    if q >= MEAN_Q_WARN:
        return _check("meanQuality", "WARN", q, f"Mean quality {fixed(q, 1)} below {fixed(MEAN_Q_PASS, 0)}")
    return _check("meanQuality", "FAIL", q, f"Mean quality {fixed(q, 1)} below {fixed(MEAN_Q_WARN, 0)}")


def _gc(gc: float) -> dict:
    if GC_PASS_MIN <= gc <= GC_PASS_MAX:
        return _check("gcContent", "PASS", gc, f"GC {fixed(gc, 1)}% within 40–60%")
    if GC_WARN_MIN <= gc <= GC_WARN_MAX:
        return _check("gcContent", "WARN", gc, f"GC {fixed(gc, 1)}% outside 40–60%")
    return _check("gcContent", "FAIL", gc, f"GC {fixed(gc, 1)}% outside 35–65%")


def _n_content(n: float) -> dict:
    if n <= N_PASS_MAX:
        return _check("nContent", "PASS", n, f"N content {fixed(n, 2)}% is low")
    if n <= N_WARN_MAX:
        return _check("nContent", "WARN", n, f"N content {fixed(n, 2)}% elevated")
    return _check("nContent", "FAIL", n, f"N content {fixed(n, 2)}% above {fixed(N_WARN_MAX, 0)}%")


def _duplication(d: float) -> dict:
    if d <= DUP_PASS_MAX:
        return _check("duplicationRate", "PASS", d, f"Duplication {fixed(d, 1)}% is acceptable")
    if d <= DUP_WARN_MAX:
        return _check("duplicationRate", "WARN", d, f"Duplication {fixed(d, 1)}% elevated")
    return _check("duplicationRate", "FAIL", d, f"Duplication {fixed(d, 1)}% above {fixed(DUP_WARN_MAX, 0)}%")


def _read_count(n: int) -> dict:
    if n >= READ_COUNT_PASS:
        return _check("readCount", "PASS", float(n), f"Read count {grouped(n)} is sufficient")
    if n >= READ_COUNT_WARN:
        return _check("readCount", "WARN", float(n), f"Read count {grouped(n)} below 100k")
    return _check("readCount", "WARN", float(n), f"Read count {grouped(n)} is very low (fixture or failed run?)")


def _per_position(per_position: dict[int, float]) -> dict:
    if not per_position:
        return _check("perPositionQuality", "PASS", 0.0, "No per-position quality data")
    values = list(per_position.values())
    status, message = "PASS", "Per-position quality stable"
    if any(q < PER_POSITION_Q_WARN for q in values):
        status, message = "WARN", f"Quality drops below {fixed(PER_POSITION_Q_WARN, 0)} at some positions"
    positions = sorted(per_position)
    n = len(positions)
    if n >= 5:
        tail_start = math.floor(n * 0.8)
        head_avg = sum(per_position[p] for p in positions[:tail_start]) / tail_start
        tail_avg = sum(per_position[p] for p in positions[tail_start:]) / (n - tail_start)
        if head_avg - tail_avg > TRAILING_DROP_DELTA:
            status = "WARN"
            message = f"Trailing cycles avg Q {fixed(tail_avg, 1)} vs head {fixed(head_avg, 1)}"
    return _check("perPositionQuality", status, min(values), message)


def _overrepresented(rows: list[dict], read_count: int) -> dict:
    if not rows:
        return _check("overrepresented", "PASS", 0.0, "No overrepresented sequences")
    top = max(int(row.get("count", 0)) for row in rows)
    sample = min(SAMPLE_LIMIT, read_count)
    fraction = top / sample if sample else 0.0
    if fraction > OVERREP_FRACTION_WARN:
        return _check(
            "overrepresented",
            "WARN",
            fraction * 100,
            f"Top sequence appears in {fixed(fraction * 100, 1)}% of sampled reads",
        )
    return _check("overrepresented", "PASS", fraction * 100, "Overrepresented sequences within normal range")


def _adapters(hits: dict) -> dict:
    fraction = float(hits.get("fraction", 0.0))
    with_hit = int(hits.get("readsWithHit", 0))
    if fraction > ADAPTER_FRACTION_WARN:
        return _check(
            "adapterContent",
            "WARN",
            fraction,
            f"Adapter hits in {fixed(fraction, 1)}% of sampled reads ({with_hit} hits)",
        )
    return _check("adapterContent", "PASS", fraction, "Adapter contamination within normal range")


def _recommendations(checks: list[dict], trim_cycle: int | None) -> list[dict]:
    recs = []
    for check in checks:
        if check["status"] == "PASS":
            continue
        if check["id"] == "perPositionQuality":
            if trim_cycle is not None:
                title = "Quality drop at 3' end"
                detail = (
                    f"Consider 3' trimming to cycle {trim_cycle} where quality falls below {int(PER_POSITION_Q_WARN)}."
                )
            else:
                title, detail = (
                    "Per-position quality issue",
                    "Inspect the quality-by-position chart for cycles to trim.",
                )
        elif check["id"] in _RECOMMENDATIONS:
            title, detail = _RECOMMENDATIONS[check["id"]]
        else:
            continue
        recs.append({"severity": check["status"], "title": title, "detail": detail})
    if not recs and any(c["status"] != "PASS" for c in checks):
        recs.append({"severity": "WARN", "title": "Review QC flags", "detail": "Inspect flagged metrics above."})
    return recs
