"""VCF summary: a port of the Java VcfParser. Pure Python, plain or bgzip/gzip input.

Output keys match the Java backend's API (camelCase) so results can be compared with datasets/golden/sample.vcf.json.
"""

from __future__ import annotations

import math
import re
from pathlib import Path

from .textio import lines, open_text

TRANSITIONS = {"AG", "GA", "CT", "TC"}
INDEL_DELTA_MAX = 20
QUAL_MAX = 100
DP_MAX = 200
BIN_WIDTH = 5
MAX_CONTIGS = 50
MAX_TAG_IDS = 40
MAX_SAMPLES = 20

_JAVA_INT = re.compile(r"[+-]?\d+")


class VcfError(ValueError):
    """The file is not a valid VCF."""


def parse(path: str | Path) -> dict:
    """Summary of a VCF: counts by type, Ts/Tv, QUAL and DP, distributions, histograms and header facts."""
    variant_count = snp = indel = mnp = 0
    qual_sum = 0.0
    qual_count = 0
    dp_sum = 0.0
    dp_count = 0
    transitions = transversions = 0
    passed = failed = 0
    chrom_dist: dict[str, int] = {}
    filter_dist: dict[str, int] = {}
    gene_dist: dict[str, int] = {}
    ts_tv_by_chrom: dict[str, list[int]] = {}
    allele_freqs: list[float] = []
    indel_bins = [0] * (INDEL_DELTA_MAX * 2 + 1)
    indel_low = indel_high = 0
    qual_hist = _Histogram(QUAL_MAX)
    dp_hist = _Histogram(DP_MAX)
    header = _Header()

    with open_text(path) as handle:
        for line in lines(handle):
            if not line.strip():
                continue
            if line.startswith("##"):
                header.meta(line)
                continue
            if line.startswith("#CHROM"):
                header.columns(line)
                continue
            if line.startswith("#"):
                continue
            cols = _java_split(line, "\t")
            if len(cols) < 8:
                raise VcfError("Invalid VCF row: expected at least 8 columns")
            chrom, ref, alt_field, qual_text, filt, info = (
                cols[0],
                cols[3].upper(),
                cols[4].upper(),
                cols[5],
                cols[6],
                cols[7],
            )

            variant_count += 1
            chrom_dist[chrom] = chrom_dist.get(chrom, 0) + 1
            filter_dist[filt] = filter_dist.get(filt, 0) + 1
            if filt.upper() == "PASS" or filt == ".":
                passed += 1
            else:
                failed += 1

            if qual_text != ".":
                qual = _java_double(qual_text)
                if qual is not None:
                    qual_sum += qual  # sequential sum, as in Java
                    qual_count += 1
                    qual_hist.add(qual)

            dp = _int_info(info, "DP")
            if dp is not None:
                dp_sum += dp
                dp_count += 1
                dp_hist.add(dp)
            af = _float_info(info, "AF")
            if af is not None:
                allele_freqs.append(af)
            gene = _info(info, "GENE")
            if gene is not None:
                gene_dist[gene] = gene_dist.get(gene, 0) + 1

            first_alt = alt_field.split(",")[0]
            if len(ref) == 1 and len(first_alt) == 1:
                snp += 1
                is_ts = (ref + first_alt) in TRANSITIONS
                if is_ts:
                    transitions += 1
                else:
                    transversions += 1
                counts = ts_tv_by_chrom.setdefault(chrom, [0, 0])
                counts[0 if is_ts else 1] += 1
            elif len(ref) != len(first_alt):
                indel += 1
                delta = len(first_alt) - len(ref)
                if delta < -INDEL_DELTA_MAX:
                    indel_low += 1
                elif delta > INDEL_DELTA_MAX:
                    indel_high += 1
                else:
                    indel_bins[delta + INDEL_DELTA_MAX] += 1
            else:
                mnp += 1

    if variant_count == 0:
        raise VcfError("VCF contains no variants")

    if allele_freqs:
        af_summary = {
            "count": len(allele_freqs),
            "mean": sum(allele_freqs) / len(allele_freqs),
            "min": min(allele_freqs),
            "max": max(allele_freqs),
            "values": allele_freqs[:100],
        }
    else:
        af_summary = {"count": 0}

    return {
        "variantCount": variant_count,
        "snpCount": snp,
        "indelCount": indel,
        "mnpCount": mnp,
        "meanQuality": qual_sum / qual_count if qual_count else 0.0,
        "meanDepth": dp_sum / dp_count if dp_count else 0.0,
        "transitions": transitions,
        "transversions": transversions,
        "tsTvRatio": _ratio(transitions, transversions),
        "passCount": passed,
        "failCount": failed,
        "chromosomeDistribution": dict(sorted(chrom_dist.items())),
        "alleleFrequency": af_summary,
        "geneDistribution": dict(sorted(gene_dist.items())),
        "filterDistribution": dict(sorted(filter_dist.items())),
        "indelLengthDistribution": {
            "labels": ["≤-21", *(str(d) for d in range(-INDEL_DELTA_MAX, INDEL_DELTA_MAX + 1)), "≥21"],
            "counts": [indel_low, *indel_bins, indel_high],
        },
        "tsTvByChromosome": {
            chrom: {"ts": ts, "tv": tv, "tsTvRatio": _ratio(ts, tv)}
            for chrom, (ts, tv) in sorted(ts_tv_by_chrom.items())
        },
        "header": header.as_dict(),
        "qualityHistogram": qual_hist.as_dict(),
        "depthHistogram": dp_hist.as_dict(),
    }


def metric_facts(metrics: dict) -> list[dict]:
    """The facts the MCP tools query, with the keys and units of the Java MetricCatalog."""
    return [
        {"key": "variant_count", "value": float(metrics["variantCount"]), "unit": "variants"},
        {"key": "snp_count", "value": float(metrics["snpCount"]), "unit": "variants"},
        {"key": "indel_count", "value": float(metrics["indelCount"]), "unit": "variants"},
        {"key": "mean_variant_quality", "value": float(metrics["meanQuality"]), "unit": "Phred"},
        {"key": "mean_depth", "value": float(metrics["meanDepth"]), "unit": "x"},
        {"key": "ts_tv_ratio", "value": float(metrics["tsTvRatio"]), "unit": "ratio"},
        {"key": "pass_count", "value": float(metrics["passCount"]), "unit": "variants"},
    ]


def _ratio(ts: int, tv: int) -> float:
    return float(ts) if tv == 0 else ts / tv


def _java_split(text: str, sep: str) -> list[str]:
    """Java's String.split drops trailing empty strings."""
    parts = text.split(sep)
    while len(parts) > 1 and parts[-1] == "":
        parts.pop()
    return parts


def _java_double(text: str) -> float | None:
    try:
        return float(text.strip())
    except ValueError:
        return None


def _info(info: str, key: str) -> str | None:
    for part in info.split(";"):
        kv = part.split("=", 1)
        if len(kv) == 2 and kv[0].lower() == key.lower():
            return kv[1]
    return None


def _int_info(info: str, key: str) -> int | None:
    value = _info(info, key)
    if value is None:
        return None
    first = value.split(",")[0]
    return int(first) if _JAVA_INT.fullmatch(first) else None


def _float_info(info: str, key: str) -> float | None:
    value = _info(info, key)
    return None if value is None else _java_double(value.split(",")[0])


class _Histogram:
    def __init__(self, max_inclusive: int) -> None:
        self.max_inclusive = max_inclusive
        self.bins = [0] * (max_inclusive // BIN_WIDTH)
        self.overflow = 0

    def add(self, value: float) -> None:
        if value > self.max_inclusive:
            self.overflow += 1
            return
        if value < 0:
            return
        index = 0 if math.isnan(value) else int(value / BIN_WIDTH)  # Java casts NaN to 0
        self.bins[min(index, len(self.bins) - 1)] += 1

    def as_dict(self) -> dict:
        labels = [f"{i * BIN_WIDTH}–{(i + 1) * BIN_WIDTH}" for i in range(len(self.bins))]
        return {
            "binWidth": BIN_WIDTH,
            "labels": [*labels, f">{self.max_inclusive}"],
            "counts": [*self.bins, self.overflow],
        }


class _Header:
    def __init__(self) -> None:
        self.fileformat = self.reference = self.source = None
        self.contigs: list[str] = []
        self.info_ids: list[str] = []
        self.format_ids: list[str] = []
        self.samples: list[str] = []
        self.contig_count = self.info_count = self.format_count = 0

    def meta(self, line: str) -> None:
        if line.startswith("##fileformat="):
            self.fileformat = _after_equals(line)
        elif line.startswith("##reference="):
            self.reference = _after_equals(line)
        elif line.startswith("##source="):
            self.source = _after_equals(line)
        elif line.startswith("##contig="):
            self.contig_count += 1
            _add_capped(self.contigs, _angle_id(line), MAX_CONTIGS)
        elif line.startswith("##INFO="):
            self.info_count += 1
            _add_capped(self.info_ids, _angle_id(line), MAX_TAG_IDS)
        elif line.startswith("##FORMAT="):
            self.format_count += 1
            _add_capped(self.format_ids, _angle_id(line), MAX_TAG_IDS)

    def columns(self, line: str) -> None:
        for name in _java_split(line, "\t")[9:]:
            if len(self.samples) >= MAX_SAMPLES:
                break
            name = name.strip()
            if name and name not in self.samples:
                self.samples.append(name)

    def as_dict(self) -> dict:
        return {
            "fileformat": self.fileformat,
            "reference": self.reference,
            "source": self.source,
            "contigs": self.contigs,
            "contigCount": self.contig_count,
            "infoIds": self.info_ids,
            "infoCount": self.info_count,
            "formatIds": self.format_ids,
            "formatCount": self.format_count,
            "samples": self.samples,
        }


def _after_equals(line: str) -> str | None:
    eq = line.find("=")
    if eq < 0 or eq == len(line) - 1:
        return None
    return line[eq + 1 :].strip() or None


def _angle_id(line: str) -> str | None:
    start = line.find("ID=")
    if start < 0:
        return None
    start += 3
    end = start
    while end < len(line) and line[end] not in ",> ":
        end += 1
    return line[start:end] if end > start else None


def _add_capped(items: list[str], value: str | None, cap: int) -> None:
    if value and value.strip() and len(items) < cap and value not in items:
        items.append(value)
