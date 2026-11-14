"""VCF behavior the small golden fixture cannot show."""

from __future__ import annotations

import gzip
from pathlib import Path

import pytest
from genometrics import vcf

DATASETS = Path(__file__).resolve().parents[1] / "datasets"
HEADER = "##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"


def write(tmp_path, body, header=HEADER):
    path = tmp_path / "t.vcf"
    path.write_text(header + body, encoding="utf-8")
    return path


def test_bgzip_or_gzip_gives_the_same_summary(tmp_path):
    packed = tmp_path / "sample.vcf.gz"
    packed.write_bytes(gzip.compress((DATASETS / "sample.vcf").read_bytes()))

    assert vcf.parse(packed) == vcf.parse(DATASETS / "sample.vcf")


def test_rejects_short_rows_and_empty_files(tmp_path):
    with pytest.raises(vcf.VcfError, match="at least 8 columns"):
        vcf.parse(write(tmp_path, "chr1\t1\t.\tA\tG\t50\tPASS\n"))
    with pytest.raises(vcf.VcfError, match="no variants"):
        vcf.parse(write(tmp_path, ""))


def test_trailing_empty_columns_are_dropped_like_java(tmp_path):
    with pytest.raises(vcf.VcfError, match="at least 8 columns"):
        vcf.parse(write(tmp_path, "chr1\t1\t.\tA\tG\t50\tPASS\t\t\n"))


def test_classifies_snp_indel_mnp_and_ts_tv(tmp_path):
    body = (
        "chr1\t1\t.\tA\tG\t50\tPASS\tDP=10\n"  # transition
        "chr1\t2\t.\tA\tC\t50\tPASS\tDP=20\n"  # transversion
        "chr2\t3\t.\tAC\tA\t.\tLowQual\t.\n"  # deletion of 1
        "chr2\t4\t.\tAC\tGT\t50\t.\t.\n"  # MNP
        f"chr2\t5\t.\tA\t{'A' * 30}\t250\tPASS\tDP=500\n"  # insertion over the bin range, QUAL and DP overflow
    )

    m = vcf.parse(write(tmp_path, body))

    assert (m["snpCount"], m["indelCount"], m["mnpCount"]) == (2, 2, 1)
    assert (m["transitions"], m["transversions"], m["tsTvRatio"]) == (1, 1, 1.0)
    assert (m["passCount"], m["failCount"]) == (4, 1)
    assert m["indelLengthDistribution"]["counts"][-1] == 1
    assert m["indelLengthDistribution"]["counts"][1 + 20 - 1] == 1
    assert m["qualityHistogram"]["counts"][-1] == 1
    assert m["depthHistogram"]["counts"][-1] == 1
    assert m["meanDepth"] == pytest.approx((10 + 20 + 500) / 3)


def test_ts_tv_ratio_without_transversions_is_the_transition_count(tmp_path):
    m = vcf.parse(write(tmp_path, "chr1\t1\t.\tC\tT\t50\tPASS\t.\nchr1\t2\t.\tG\tA\t50\tPASS\t.\n"))

    assert m["tsTvRatio"] == 2.0
    assert m["tsTvByChromosome"]["chr1"] == {"ts": 2, "tv": 0, "tsTvRatio": 2.0}


def test_info_values_parse_like_java(tmp_path):
    body = (
        "chr1\t1\t.\tA\tG\tNaN\tPASS\tdp=7;AF=0.25,0.5;GENE=TP53\n"  # keys are case-insensitive; first value wins
        "chr1\t2\t.\tA\tG\tabc\tPASS\tDP=1_000;AF=x\n"  # Java rejects 1_000 and x
    )

    m = vcf.parse(write(tmp_path, body))

    assert m["meanDepth"] == 7.0
    assert m["alleleFrequency"] == {"count": 1, "mean": 0.25, "min": 0.25, "max": 0.25, "values": [0.25]}
    assert m["geneDistribution"] == {"TP53": 1}
    assert m["qualityHistogram"]["counts"][0] == 1  # NaN lands in the first bin, as in Java


def test_header_caps_and_dedupes_samples(tmp_path):
    names = "\t".join(f"S{i}" for i in range(25)) + "\tS0"
    header = (
        "##fileformat=VCFv4.2\n##reference=hg38\n##contig=<ID=chr1,length=10>\n##INFO=<ID=DP,Number=1>\n"
        f"#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t{names}\n"
    )

    h = vcf.parse(write(tmp_path, "chr1\t1\t.\tA\tG\t50\tPASS\t.\tGT\n", header))["header"]

    assert h["reference"] == "hg38"
    assert h["contigs"] == ["chr1"] and h["infoIds"] == ["DP"]
    assert len(h["samples"]) == 20 and h["samples"][0] == "S0"
