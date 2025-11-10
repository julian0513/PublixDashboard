from __future__ import annotations

"""
SKLearn Training Model — Publix AI 
---------------------
- Trains a scikit-learn model on October candy sales using a stable pipeline:
  OneHotEncoder(handle_unknown='ignore', dense) + GradientBoostingRegressor.
- Two explicit modes:
    • "seed": 2015-10-01 .. 2024-10-31 → history_seed.joblib (+ .meta.json)
    • "live": 2015-10-01 .. today      → model_live.joblib   (+ .meta.json)
- Writes artifacts atomically next to the module and returns a structured summary.
- Metadata includes reproducible feature lists, row counts, validation/test metrics,
  and the frozen **product universe** used for seed training.
  
Back-compat / Contract
----------------------
- Feature columns (CAT_COLS, NUM_COLS) are imported from features.py to avoid drift
  between training and inference.
- Target **y** is **daily end-of-day units per (product, date)** — we aggregate
  before feature generation so forecasts naturally represent EOD totals.
"""

import argparse
import json
import os
import time
from dataclasses import asdict, dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Dict, Tuple

import joblib
import numpy as np
import pandas as pd
from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sklearn import __version__ as sklearn_version
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

# Shared feature builder + canonical feature lists
from features import make_features, CAT_COLS, NUM_COLS  # -> (X: DataFrame, y: Series)

# ----------------------------- Environment & Paths -----------------------------

load_dotenv()

APP_TZ = os.getenv("TZ", "America/New_York")
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://ml_ro:change-me@localhost:5432/publixai")

THIS_DIR = Path(__file__).resolve().parent
SEED_MODEL_PATH = THIS_DIR / "history_seed.joblib"
SEED_META_PATH = THIS_DIR / "history_seed.meta.json"
LIVE_MODEL_PATH = THIS_DIR / "model_live.joblib"
LIVE_META_PATH = THIS_DIR / "model_live.meta.json"

SEED_START = date(2015, 10, 1)
SEED_END = date(2024, 10, 31)
RANDOM_STATE = 42

ENGINE = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=600)

# ----------------------------- Data Access -----------------------------

def _load_oct_history(end_date: date) -> pd.DataFrame:
    """
    Load October rows from SEED_START through 'end_date' (inclusive),
    then **aggregate to per-product, per-date totals** for EOD training.
    """
    sql = text("""
               SELECT
                   product_name,
                   units::int   AS units,
                   date::date   AS date
               FROM public.sales
               WHERE EXTRACT(MONTH FROM date) = 10
                 AND date >= :seed_start
                 AND date <= :end_date
               ORDER BY date, product_name
               """)
    df = pd.read_sql_query(sql, ENGINE, params={"seed_start": SEED_START, "end_date": end_date})
    if df.empty:
        raise ValueError("No October rows available in the requested window.")

    # Canonicalize + types
    df["date"] = pd.to_datetime(df["date"], errors="raise", utc=False)
    df["product_name"] = df["product_name"].astype(str).str.strip()
    df["units"] = pd.to_numeric(df["units"], errors="raise").astype(int)

    # --------- EOD aggregation (critical) ----------
    # If multiple rows exist for the same product/date, sum them to a final daily total.
    df_daily = (
        df.groupby(["product_name", "date"], as_index=False)["units"]
        .sum()
        .rename(columns={"units": "units"})
    )
    return df_daily

# ----------------------------- Splitting & Metrics -----------------------------

def _time_split(df_xy: pd.DataFrame):
    """Time-ordered split (60/20/20). Collapses test for small samples."""
    df_xy = df_xy.sort_values("date").reset_index(drop=True)
    n = len(df_xy)
    if n < 10:
        n_train = max(1, int(n * 0.8))
        train = df_xy.iloc[:n_train]
        valid = df_xy.iloc[n_train:]
        test = df_xy.iloc[0:0]
        return train, valid, test
    n_train = int(n * 0.60)
    n_valid = int(n * 0.20)
    train = df_xy.iloc[:n_train]
    valid = df_xy.iloc[n_train : n_train + n_valid]
    test = df_xy.iloc[n_train + n_valid :]
    return train, valid, test

@dataclass
class MetricReport:
    rows: int
    mape: float
    mae: float
    rmse: float

def _safe_mape(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """Finite MAPE even with zero targets."""
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    denom = np.maximum(np.abs(y_true), 1.0)
    return float(np.mean(np.abs((y_true - y_pred) / denom)))

def _report(pipe: Pipeline, X: pd.DataFrame, y: pd.Series, name: str) -> MetricReport:
    n = int(len(y))
    if n == 0:
        print(f"{name:>5}  rows={0:4d}  MAPE=0.000  MAE=0.000  RMSE=0.000")
        return MetricReport(rows=0, mape=0.0, mae=0.0, rmse=0.0)
    y_hat = pipe.predict(X)
    mape = _safe_mape(y, y_hat)
    mae = float(mean_absolute_error(y, y_hat))
    rmse = float(np.sqrt(mean_squared_error(y, y_hat)))
    print(f"{name:>5}  rows={n:4d}  MAPE={mape:.3f}  MAE={mae:.3f}  RMSE={rmse:.3f}")
    return MetricReport(rows=n, mape=mape, mae=mae, rmse=rmse)

# ----------------------------- Model Building -----------------------------

def _build_pipeline() -> Pipeline:
    """
    Preprocessing + model:
    - OHE on product name (dense) + passthrough numeric features
    - GradientBoostingRegressor baseline (stable, interpretable)
    """
    try:
        ohe = OneHotEncoder(handle_unknown="ignore", sparse_output=False)  # sklearn>=1.2
    except TypeError:
        ohe = OneHotEncoder(handle_unknown="ignore", sparse=False)         # sklearn<1.2
    pre = ColumnTransformer([("cat", ohe, CAT_COLS), ("num", "passthrough", NUM_COLS)])
    model = GradientBoostingRegressor(random_state=RANDOM_STATE)
    return Pipeline([("prep", pre), ("model", model)])

# ----------------------------- Training Orchestration -----------------------------

@dataclass
class TrainSummary:
    status: str
    mode: str
    rows: Dict[str, int]
    metrics: Dict[str, Dict[str, float]]
    modelPath: str
    seedWindow: Dict[str, str]
    trainedAt: str
    durationSec: float

def _atomic_write_joblib(obj, out_path: Path) -> None:
    tmp = out_path.with_suffix(out_path.suffix + ".tmp")
    joblib.dump(obj, tmp)
    os.replace(tmp, out_path)

def _atomic_write_json(payload: Dict, out_path: Path) -> None:
    tmp = out_path.with_suffix(out_path.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    os.replace(tmp, out_path)

def run_training(mode: str = "live") -> Dict[str, object]:
    """
    Train and persist artifacts.
    - Target `y` is **daily EOD units** per (product, date).
    """
    import time as _time
    start_ts = _time.time()

    mode = (mode or "live").lower().strip()
    if mode not in {"seed", "live"}:
        raise ValueError(f"Unsupported mode '{mode}'. Use 'seed' or 'live'.")

    if mode == "seed":
        end_date = SEED_END
        model_path, meta_path = SEED_MODEL_PATH, SEED_META_PATH
    else:
        end_date = date.today()
        model_path, meta_path = LIVE_MODEL_PATH, LIVE_META_PATH

    # Load data (already aggregated to daily totals)
    df = _load_oct_history(end_date=end_date)

    # Freeze the product universe used during training (prevents baseline bleed)
    product_names = sorted(df['product_name'].astype(str).str.strip().unique().tolist())

    # Features
    X_all, y_all = make_features(df)
    df_all = pd.concat([X_all, y_all.rename("units"), df[["date"]]], axis=1)

    # Column checks
    for col in CAT_COLS + NUM_COLS + ["units", "date"]:
        if col not in df_all.columns:
            raise ValueError(f"Feature column missing: {col}")

    # Split
    train_df, valid_df, test_df = _time_split(df_all)
    X_train, y_train = train_df[CAT_COLS + NUM_COLS], train_df["units"]
    X_valid, y_valid = valid_df[CAT_COLS + NUM_COLS], valid_df["units"]
    X_test, y_test = test_df[CAT_COLS + NUM_COLS], test_df["units"]

    if len(X_train) == 0:
        raise ValueError("Insufficient data to train (no training rows).")

    # Fit
    pipe = _build_pipeline()
    print(f"[train] Fitting model (mode={mode}, rows={len(df_all)}) …")
    pipe.fit(X_train, y_train)

    # Metrics (finite)
    valid_metrics = _report(pipe, X_valid, y_valid, "valid")
    test_metrics = _report(pipe, X_test, y_test, "test")

    # Save model + meta atomically
    _atomic_write_joblib(pipe, model_path)
    print(f"[train] Saved model → {model_path}")

    meta = {
        "mode": mode,
        "seedWindow": {"start": SEED_START.isoformat(), "end": SEED_END.isoformat()},
        "dataWindow": {"start": SEED_START.isoformat(), "end": end_date.isoformat()},
        "rows": {"train": len(X_train), "valid": len(X_valid), "test": len(X_test), "total": len(df_all)},
        "metrics": {"valid": asdict(valid_metrics), "test": asdict(test_metrics)},
        "features": {"categorical": CAT_COLS, "numeric": NUM_COLS},
        "products": {"count": len(product_names), "names": product_names},  # ← frozen set used at train time
        "randomState": RANDOM_STATE,
        "trainedAt": datetime.now().isoformat(timespec="seconds"),
        "library": {"sklearn": sklearn_version, "pandas": pd.__version__, "numpy": np.__version__},
        "database": {"urlEnvVar": "DATABASE_URL", "driver": "postgresql"},
        "target": "daily end-of-day units per (product, date)",
    }
    _atomic_write_json(meta, meta_path)
    print(f"[train] Saved metadata → {meta_path}")

    duration = time.time() - start_ts
    return asdict(TrainSummary(
        status="ok",
        mode=mode,
        rows={"train": len(X_train), "valid": len(X_valid), "test": len(X_test), "total": len(df_all)},
        metrics={"valid": asdict(valid_metrics), "test": asdict(test_metrics)},
        modelPath=str(model_path),
        seedWindow={"start": SEED_START.isoformat(), "end": SEED_END.isoformat()},
        trainedAt=meta["trainedAt"],
        durationSec=round(duration, 3),
    ))

# ----------------------------- CLI -----------------------------

def main() -> None:
    """
    Usage:
      python train.py               → live mode
      python train.py --mode seed   → seed mode
      python train.py --mode live   → live mode
    """
    parser = argparse.ArgumentParser(description="Train October candy sales model.")
    parser.add_argument("--mode", choices=["seed", "live"], default="live", help="Training mode")
    args = parser.parse_args()

    summary = run_training(mode=args.mode)
    print("[train] summary:", json.dumps(summary, indent=2))

if __name__ == "__main__":
    main()
