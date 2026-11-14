"""Plain-language explanations of the FASTQ QC checks, from the thresholds in qc.py."""

from __future__ import annotations

from . import qc

_RATIONALE = (
    "Fixed rules of thumb, carried over unchanged from the Java backend and checked against its output. "
    "They are not calibrated to an instrument, library type or species, so treat a WARN as a prompt to look, "
    "not as a verdict on the run."
)

_CHECKS = {
    "meanQuality": (
        "Mean Phred base quality over all bases.",
        f"PASS at {qc.MEAN_Q_PASS:g} or more, WARN from {qc.MEAN_Q_WARN:g}, FAIL below.",
    ),
    "gcContent": (
        "Percentage of G and C bases.",
        f"PASS within {qc.GC_PASS_MIN:g}-{qc.GC_PASS_MAX:g}%, WARN within {qc.GC_WARN_MIN:g}-{qc.GC_WARN_MAX:g}%, "
        "FAIL outside. Compare with the expected GC of the species.",
    ),
    "nContent": (
        "Percentage of bases called N (unknown).",
        f"PASS at {qc.N_PASS_MAX:g}% or less, WARN up to {qc.N_WARN_MAX:g}%, FAIL above.",
    ),
    "duplicationRate": (
        f"Percentage of duplicated reads among the first {qc.SAMPLE_LIMIT:,} reads.",
        f"PASS at {qc.DUP_PASS_MAX:g}% or less, WARN up to {qc.DUP_WARN_MAX:g}%, FAIL above.",
    ),
    "readCount": (
        "Number of reads.",
        f"PASS at {qc.READ_COUNT_PASS:,} or more, WARN below that. It never FAILs; very small files are flagged "
        "as possible fixtures or failed runs.",
    ),
    "perPositionQuality": (
        "Mean quality at each cycle (position in the read).",
        f"WARN if any position falls below Q{qc.PER_POSITION_Q_WARN:g}, or if the last 20% of cycles average more "
        f"than {qc.TRAILING_DROP_DELTA:g} Q below the rest. It never FAILs.",
    ),
    "overrepresented": (
        f"Most frequent sequence among the first {qc.SAMPLE_LIMIT:,} reads.",
        f"WARN if it makes up more than {qc.OVERREP_FRACTION_WARN * 100:g}% of the sampled reads. It never FAILs.",
    ),
    "adapterContent": (
        "Share of sampled reads containing a known adapter sequence (Illumina, TruSeq, Nextera, small RNA, polyA).",
        f"WARN if more than {qc.ADAPTER_FRACTION_WARN:g}% of the sampled reads have a hit. It never FAILs.",
    ),
}


def explain_check(check: str) -> dict:
    if check not in _CHECKS:
        raise ValueError(f"Unknown check '{check}'. Valid ids: {', '.join(_CHECKS)}")
    measures, thresholds = _CHECKS[check]
    return {"check": check, "measures": measures, "thresholds": thresholds, "rationale": _RATIONALE}
