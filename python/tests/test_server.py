"""The stdio server driven by a real MCP client, in a subprocess, the way an agent uses it."""

from __future__ import annotations

import sys
from pathlib import Path

import anyio
import pytest
from mcp.client.client import Client
from mcp.client.stdio import StdioServerParameters

DATASETS = Path(__file__).resolve().parents[2] / "datasets"
TOOLS = {"qc_fastq", "summarize_vcf", "compare", "explain_check", "report"}


@pytest.fixture
def params(tmp_path):
    return StdioServerParameters(
        command=sys.executable,
        args=["-m", "genometrics.server"],
        env={"GENOMETRICS_CACHE_DIR": str(tmp_path / "cache")},
    )


def run(params, fn):
    async def main():
        async with Client(params) as client:
            return await fn(client)

    return anyio.run(main)


def test_tools_are_read_only_and_have_schemas(params):
    tools = run(params, lambda c: c.list_tools()).tools
    assert {t.name for t in tools} == TOOLS
    for tool in tools:
        assert tool.annotations.read_only_hint is True
        assert tool.input_schema["properties"]
        assert tool.description
    assert all(t.output_schema for t in tools if t.name != "report")


def test_qc_fastq_answers_then_hits_the_cache(params):
    async def go(client):
        args = {"paths": [str(DATASETS / "sample.fastq")]}
        return await client.call_tool("qc_fastq", args), await client.call_tool("qc_fastq", args)

    first, second = run(params, go)
    a, b = first.structured_content, second.structured_content
    assert a["verdict"] in {"PASS", "WARN", "FAIL"} and a["cached"] is False and b["cached"] is True
    assert a["analyzed"][0]["sha256"] == b["analyzed"][0]["sha256"]
    assert {**a, "cached": True} == b


def test_pair_vcf_compare_explain_and_report(params):
    async def go(client):
        pair = [str(DATASETS / "sample_R2.fastq"), str(DATASETS / "sample_R1.fastq")]  # any order
        vcf = str(DATASETS / "sample.vcf")
        return (
            await client.call_tool("qc_fastq", {"paths": pair}),
            await client.call_tool("summarize_vcf", {"path": vcf}),
            await client.call_tool("compare", {"paths": [pair[1], vcf]}),
            await client.call_tool("explain_check", {"check": "gcContent"}),
            await client.call_tool("report", {"paths": [vcf]}),
        )

    pair, vcf, compare, explain, report = run(params, go)
    assert pair.structured_content["metrics"]["paired_end"] is True
    assert vcf.structured_content["metrics"]["variant_count"] > 0
    assert [f["kind"] for f in compare.structured_content["files"]] == ["fastq", "vcf"]
    assert "40" in explain.structured_content["thresholds"]
    assert report.content[0].text.startswith("# VCF summary: sample.vcf")


@pytest.mark.parametrize(
    ("tool", "args", "message"),
    [
        ("qc_fastq", {"paths": ["relative.fastq"]}, "must be absolute"),
        ("qc_fastq", {"paths": ["/nonexistent/x.fastq"]}, "Not a regular file"),
        ("summarize_vcf", {"path": str(DATASETS / "sample.fastq")}, "Not a VCF"),
        ("explain_check", {"check": "nope"}, "Unknown check"),
    ],
)
def test_bad_input_is_an_error_the_agent_can_read(params, tool, args, message):
    result = run(params, lambda c: c.call_tool(tool, args))
    assert result.is_error and message in result.content[0].text
