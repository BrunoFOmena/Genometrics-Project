"""Open plain or gzip (including bgzip) text files by content, not by extension."""

from __future__ import annotations

import gzip
from pathlib import Path
from typing import TextIO


def open_text(path: str | Path) -> TextIO:
    with open(path, "rb") as probe:
        gzipped = probe.read(2) == b"\x1f\x8b"
    if gzipped:
        return gzip.open(path, "rt", encoding="utf-8", newline="")
    return open(path, encoding="utf-8", newline="")


def lines(handle: TextIO):
    """Lines without their terminator (\\n or \\r\\n), like Java's BufferedReader.readLine."""
    return (line.rstrip("\n").rstrip("\r") for line in handle)
