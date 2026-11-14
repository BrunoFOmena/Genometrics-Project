"""Number formatting that matches Java's String.format(Locale.ROOT, ...), so QC messages are identical."""

from decimal import ROUND_HALF_UP, Decimal


def fixed(value: float, decimals: int) -> str:
    """Like Java "%.Nf": HALF_UP rounding of the shortest decimal form (Python's own format rounds the binary value)."""
    quantum = Decimal(1).scaleb(-decimals)
    return str(Decimal(repr(float(value))).quantize(quantum, rounding=ROUND_HALF_UP))


def grouped(value: int) -> str:
    """Like Java "%,d" with Locale.ROOT."""
    return f"{value:,}"
