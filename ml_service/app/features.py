from __future__ import annotations
from typing import Iterable, List, Sequence, Tuple, Union
from datetime import date as _date

import numpy as np
import pandas as pd

# ---- Feature columns (shared by training & inference) ----------------------
CAT_COLS: List[str] = ["product_name"]
NUM_COLS: List[str] = ["year", "month", "day", "dow", "doy", "is_weekend", "days_to_halloween"]

__all__ = ["make_features", "calendar_grid", "CAT_COLS", "NUM_COLS"]


def _to_local_date_range(start: Union[str, _date], end: Union[str, _date]) -> pd.DatetimeIndex:
    """Return an inclusive daily date range in local time (no tz math)."""
    dr = pd.date_range(start=start, end=end, freq="D")
    if dr.empty:
        raise ValueError("Empty date range: ensure end is on/after start.")
    return dr


def _clean_products(products: Iterable[str]) -> List[str]:
    """Trim, dedupe, and drop empty product names."""
    if products is None:
        return []
    vals = pd.Series(list(products), dtype="object").astype(str).str.strip()
    vals = vals[vals.ne("")].drop_duplicates()
    return vals.tolist()


def make_features(df: pd.DataFrame) -> Tuple[pd.DataFrame, pd.Series]:
    """
    Returns (X, y) where X has categorical + numeric columns and y is integer units.
    Required columns in df: ['product_name','date','units'].
    """
    if not {"product_name", "date", "units"}.issubset(df.columns):
        missing = [c for c in ["product_name", "date", "units"] if c not in df.columns]
        raise ValueError(f"make_features: missing columns: {missing}")

    df = df.copy()

    # normalize inputs
    df["product_name"] = df["product_name"].astype(str).str.strip()
    df["date"] = pd.to_datetime(df["date"])  # local-naive ok
    # Ensure integer units, clip negatives (guards against dirty inputs)
    df["units"] = pd.to_numeric(df["units"], errors="coerce").fillna(0).astype(int).clip(lower=0)

    # calendar features
    df["year"] = df["date"].dt.year
    df["month"] = df["date"].dt.month
    df["day"] = df["date"].dt.day
    df["dow"] = df["date"].dt.weekday            # 0=Mon … 6=Sun
    df["doy"] = df["date"].dt.dayofyear
    df["is_weekend"] = (df["dow"] >= 5).astype(int)

    # proximity to Halloween (Oct 31 of same year)
    halloweens = pd.to_datetime(df["date"].dt.year.astype(str) + "-10-31")
    df["days_to_halloween"] = (halloweens - df["date"]).dt.days.astype(int)

    # columns for model
    X = df[CAT_COLS + NUM_COLS].copy()
    y = df["units"].copy()  # int
    return X, y


def calendar_grid(
        products: Sequence[str] = None,
        start: Union[str, _date] = None,
        end: Union[str, _date] = None,
        **kwargs,
) -> pd.DataFrame:
    """
    Build a cartesian (products × dates) grid for inference with the SAME feature columns
    used in training. Accepts either positional or keyword args and validates inputs.

    Examples:
      calendar_grid(products=["Snickers","Twix"], start="2025-10-01", end="2025-10-31")
      calendar_grid(start="2025-10-01", end="2025-10-31", products=["Snickers","Twix"])
    """
    # Support callers that pass args in different orders via kwargs
    products = kwargs.get("products", products)
    start = kwargs.get("start", start)
    end = kwargs.get("end", end)

    names = _clean_products(products)
    if not names:
        raise ValueError("calendar_grid: products must be a non-empty sequence of names.")
    dates = _to_local_date_range(start, end)

    grid = (
        pd.MultiIndex.from_product([pd.Index(names, name="product_name"), dates], names=["product_name", "date"])
        .to_frame(index=False)
    )

    # derive features (identical to make_features)
    grid["year"] = grid["date"].dt.year
    grid["month"] = grid["date"].dt.month
    grid["day"] = grid["date"].dt.day
    grid["dow"] = grid["date"].dt.weekday        # 0=Mon … 6=Sun
    grid["doy"] = grid["date"].dt.dayofyear
    grid["is_weekend"] = (grid["dow"] >= 5).astype(int)
    halloweens = pd.to_datetime(grid["date"].dt.year.astype(str) + "-10-31")
    grid["days_to_halloween"] = (halloweens - grid["date"]).dt.days.astype(int)

    # Ensure column order expected by pipeline is present
    return grid[["product_name", "date"] + NUM_COLS]
