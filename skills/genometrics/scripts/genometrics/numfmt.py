"""Number formatting with HALF_UP rounding and thousands grouping, so QC messages are stable."""

from decimal import ROUND_HALF_UP, Decimal


def fixed(value: float, decimals: int) -> str:
    """N decimals, HALF_UP on the shortest decimal form (Python's own format rounds the binary value)."""
    quantum = Decimal(1).scaleb(-decimals)
    return str(Decimal(repr(float(value))).quantize(quantum, rounding=ROUND_HALF_UP))


def grouped(value: int) -> str:
    """Integer with comma thousands separators."""
    return f"{value:,}"
