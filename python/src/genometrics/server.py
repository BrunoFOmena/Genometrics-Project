"""Stdio MCP server: `genometrics-mcp`. Tools are read-only and answer synchronously."""

from __future__ import annotations

import functools
from collections.abc import Callable
from typing import Annotated, Any, TypedDict

from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations
from pydantic import Field

from . import __version__, analysis, explain

mcp = MCPServer("genometrics", version=__version__)
_READ_ONLY = ToolAnnotations(readOnlyHint=True, idempotentHint=True, openWorldHint=False)

Paths = Annotated[list[str], Field(description="Absolute paths of regular, readable files")]


def _tool(fn: Callable) -> Callable:
    """Register a tool; input and parse errors (ValueError) reach the agent as readable tool errors."""

    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return fn(*args, **kwargs)
        except ValueError as exc:
            raise ToolError(str(exc)) from exc

    return mcp.tool(annotations=_READ_ONLY, structured_output=fn.__annotations__["return"] != "str")(wrapper)


class FastqQc(TypedDict):
    verdict: str
    issues: list[dict[str, Any]]
    next_step: str
    recommendations: list[dict[str, Any]]
    checks: list[dict[str, Any]]
    metrics: dict[str, Any]
    analyzed: list[dict[str, Any]]
    cached: bool


class VcfSummary(TypedDict):
    metrics: dict[str, Any]
    chromosomes: dict[str, int]
    filters: dict[str, int]
    analyzed: list[dict[str, Any]]
    cached: bool


class Comparison(TypedDict):
    files: list[dict[str, Any]]


class CheckExplanation(TypedDict):
    check: str
    measures: str
    thresholds: str
    rationale: str


@_tool
def qc_fastq(paths: Paths) -> FastqQc:
    """Quality control of one FASTQ file or an R1/R2 pair (plain or .gz). Answers synchronously.

    Returns the verdict (PASS, WARN or FAIL), the checks that did not pass, the next step, all checks with their
    messages, and the main metrics. Quote the messages; do not re-derive thresholds. Results are cached by file
    hash, so asking again is instant. A 10-million-read file takes minutes the first time.

    Example: {"paths": ["/data/s1_R1.fastq.gz", "/data/s1_R2.fastq.gz"]}
    """
    return analysis.qc_fastq(paths)  # type: ignore[return-value]


@_tool
def summarize_vcf(
    path: Annotated[str, Field(description="Absolute path of a .vcf or .vcf.gz file")],
) -> VcfSummary:
    """Summary of a VCF file: variant, SNP and indel counts, Ts/Tv, mean QUAL and depth, PASS/FAIL counts.

    Results are cached by file hash. Example: {"path": "/data/calls.vcf.gz"}
    """
    return analysis.summarize_vcf(path)  # type: ignore[return-value]


@_tool
def compare(
    paths: Annotated[list[str], Field(description="Two or more absolute paths (FASTQ or VCF)", min_length=2)],
) -> Comparison:
    """The same metrics for several files side by side (each FASTQ on its own, not as a pair).

    Example: {"paths": ["/data/a.fastq.gz", "/data/b.fastq.gz"]}
    """
    return analysis.compare(paths)  # type: ignore[return-value]


@_tool
def explain_check(
    check: Annotated[str, Field(description="A check id from qc_fastq, e.g. meanQuality", examples=["gcContent"])],
) -> CheckExplanation:
    """What a FASTQ QC check measures, its PASS/WARN/FAIL thresholds, and how they were chosen.

    Valid ids: meanQuality, gcContent, nContent, duplicationRate, readCount, perPositionQuality, overrepresented,
    adapterContent.
    """
    return explain.explain_check(check)  # type: ignore[return-value]


@_tool
def report(paths: Paths) -> str:
    """A Markdown report for one VCF, one FASTQ, or an R1/R2 pair, to hand to a colleague.

    Includes the verdict, checks, next step, metrics and the path, size and SHA-256 of each file analyzed.
    """
    return analysis.report(paths)


def main() -> None:
    mcp.run("stdio")


if __name__ == "__main__":
    main()
