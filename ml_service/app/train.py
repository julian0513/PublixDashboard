"""
SKLearn Training Model — Publix AI 
====================================

Purpose:
    Train scikit-learn models on October candy sales data with comprehensive features
    including calendar, discount, and basket analysis context.

Training Modes:
    • "seed": 2015-10-01 .. 2024-10-31 → history_seed.joblib (+ .meta.json)
        - Frozen baseline model for historical comparison
        - Uses fixed product universe from seed window
    • "live": 2015-10-01 .. today → model_live.joblib (+ .meta.json)
        - Updated model incorporating recent sales data
        - Includes new products that may have appeared after seed window

Model Architecture:
    - Preprocessing: OneHotEncoder (product_name) + passthrough (numeric features)
    - Model: GradientBoostingRegressor (stable, interpretable, handles non-linear relationships)
    - Features: Calendar + Discount + Basket Analysis (see features.py for details)

Target Variable:
    - **y**: Daily end-of-day units per (product, date)
    - Aggregated before feature generation so forecasts represent EOD totals

Data Flow:
    1. Load sales data from database (aggregated to daily totals)
    2. Extract features using make_features() (includes discount/basket enrichment)
    3. Time-split into train/validation/test sets (60/20/20)
    4. Train model and evaluate on validation/test sets
    5. Persist model and metadata atomically

Backward Compatibility:
    - Feature columns (CAT_COLS, NUM_COLS) imported from features.py
    - Ensures consistency between training and inference
    - Gracefully handles missing discount/basket data (defaults to safe values)

Metadata:
    - Includes reproducible feature lists, row counts, validation/test metrics
    - Frozen product universe for seed mode (prevents baseline bleed)
    - Library versions for reproducibility
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

# Database engine (shared with feature enrichment)
ENGINE = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=600)

# ----------------------------- Data Access -----------------------------

def _load_oct_history(end_date: date) -> pd.DataFrame:
    """
    Load October sales data from database.
    
    Loads all October rows from SEED_START through 'end_date' (inclusive),
    then aggregates to per-product, per-date totals for end-of-day training.
    
    This aggregation is critical: if multiple sales entries exist for the same
    product/date (e.g., intraday partials), they are summed to a final daily total.
    This ensures the model learns to predict daily EOD totals, not partials.
    
    Args:
        end_date: Latest date to include (inclusive)
        
    Returns:
        DataFrame with columns: ['product_name', 'date', 'units']
        - product_name: Product name (string)
        - date: Date (datetime)
        - units: Total units sold on that date (integer, aggregated)
        
    Raises:
        ValueError: If no October rows found in the requested window
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
    # This handles intraday partials and ensures we're predicting EOD totals.
    df_daily = (
        df.groupby(["product_name", "date"], as_index=False)["units"]
        .sum()
        .rename(columns={"units": "units"})
    )
    return df_daily

# ----------------------------- Splitting & Metrics -----------------------------

def _time_split(df_xy: pd.DataFrame) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """
    Time-ordered train/validation/test split (60/20/20).
    
    Splits data chronologically to respect temporal ordering. This prevents
    data leakage where future information could influence past predictions.
    
    For very small datasets (< 10 rows), collapses test set to avoid
    insufficient data issues.
    
    Args:
        df_xy: DataFrame with 'date' column, sorted by date
        
    Returns:
        Tuple of (train_df, valid_df, test_df)
        - train_df: 60% of data (earliest)
        - valid_df: 20% of data (middle)
        - test_df: 20% of data (latest, or empty if < 10 rows)
    """
    df_xy = df_xy.sort_values("date").reset_index(drop=True)
    n = len(df_xy)
    
    if n < 10:
        # Small dataset: use 80/20 split, no test set
        n_train = max(1, int(n * 0.8))
        train = df_xy.iloc[:n_train]
        valid = df_xy.iloc[n_train:]
        test = df_xy.iloc[0:0]  # Empty DataFrame
        return train, valid, test
    
    # Standard 60/20/20 split
    n_train = int(n * 0.60)
    n_valid = int(n * 0.20)
    train = df_xy.iloc[:n_train]
    valid = df_xy.iloc[n_train : n_train + n_valid]
    test = df_xy.iloc[n_train + n_valid :]
    return train, valid, test


@dataclass
class MetricReport:
    """
    Model performance metrics for a dataset.
    
    Attributes:
        rows: Number of samples evaluated
        mape: Mean Absolute Percentage Error (0.0-1.0, or 0.0-100.0 if > 1.0)
        mae: Mean Absolute Error (units)
        rmse: Root Mean Squared Error (units)
    """
    rows: int
    mape: float
    mae: float
    rmse: float


def _safe_mape(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """
    Calculate MAPE (Mean Absolute Percentage Error) with safe handling of zero targets.
    
    Standard MAPE can be undefined when y_true = 0. This function uses a denominator
    of max(|y_true|, 1.0) to ensure finite results even with zero sales.
    
    Args:
        y_true: True target values
        y_pred: Predicted values
        
    Returns:
        MAPE as float (0.0-1.0 range, or may exceed 1.0 for very poor predictions)
    """
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    denom = np.maximum(np.abs(y_true), 1.0)  # Avoid division by zero
    return float(np.mean(np.abs((y_true - y_pred) / denom)))


def _report(pipe: Pipeline, X: pd.DataFrame, y: pd.Series, name: str) -> MetricReport:
    """
    Evaluate model performance and print metrics.
    
    Args:
        pipe: Fitted sklearn Pipeline
        X: Feature matrix
        y: True target values
        name: Dataset name (e.g., "train", "valid", "test")
        
    Returns:
        MetricReport with performance metrics
    """
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
    Build sklearn Pipeline for candy sales prediction.
    
    Pipeline Components:
        1. Preprocessing (ColumnTransformer):
           - Categorical: OneHotEncoder on product_name (dense output)
           - Numeric: Passthrough (no transformation needed)
        2. Model: GradientBoostingRegressor
           - Handles non-linear relationships
           - Feature importance available for interpretability
           - Stable performance across different data distributions
           
    Returns:
        Unfitted sklearn Pipeline
        
    Note:
        OneHotEncoder uses handle_unknown="ignore" to gracefully handle
        new products not seen during training (returns all zeros for that product).
    """
    try:
        # sklearn >= 1.2 uses sparse_output
        ohe = OneHotEncoder(handle_unknown="ignore", sparse_output=False)
    except TypeError:
        # sklearn < 1.2 uses sparse
        ohe = OneHotEncoder(handle_unknown="ignore", sparse=False)
    
    # ColumnTransformer: apply OHE to categorical, passthrough numeric
    pre = ColumnTransformer([
        ("cat", ohe, CAT_COLS),
        ("num", "passthrough", NUM_COLS)
    ])
    
    # GradientBoostingRegressor: ensemble of decision trees
    model = GradientBoostingRegressor(random_state=RANDOM_STATE)
    
    return Pipeline([("prep", pre), ("model", model)])

# ----------------------------- Training Orchestration -----------------------------

@dataclass
class TrainSummary:
    """
    Training summary returned to caller.
    
    Attributes:
        status: "ok" if successful
        mode: Training mode used ("seed" or "live")
        rows: Dictionary with train/valid/test/total row counts
        metrics: Dictionary with validation and test metrics
        modelPath: Path to saved model file
        seedWindow: Dictionary with start/end dates of seed window
        trainedAt: ISO timestamp of training completion
        durationSec: Training duration in seconds
    """
    status: str
    mode: str
    rows: Dict[str, int]
    metrics: Dict[str, Dict[str, float]]
    modelPath: str
    seedWindow: Dict[str, str]
    trainedAt: str
    durationSec: float


def _atomic_write_joblib(obj, out_path: Path) -> None:
    """
    Atomically write joblib file (prevents corruption on interruption).
    
    Writes to temporary file first, then renames to final path. This ensures
    the final file is either complete or doesn't exist (no partial writes).
    
    Args:
        obj: Object to serialize (typically sklearn Pipeline)
        out_path: Final output path
    """
    tmp = out_path.with_suffix(out_path.suffix + ".tmp")
    joblib.dump(obj, tmp)
    os.replace(tmp, out_path)


def _atomic_write_json(payload: Dict, out_path: Path) -> None:
    """
    Atomically write JSON file (prevents corruption on interruption).
    
    Args:
        payload: Dictionary to serialize as JSON
        out_path: Final output path
    """
    tmp = out_path.with_suffix(out_path.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    os.replace(tmp, out_path)


def run_training(mode: str = "live") -> Dict[str, object]:
    """
    Train and persist model artifacts.
    
    Main training orchestration function. Loads data, extracts features (including
    discount and basket enrichment), trains model, evaluates performance, and saves
    model + metadata atomically.
    
    Args:
        mode: Training mode ("seed" or "live")
            - "seed": Train on frozen historical window (2015-2024)
            - "live": Train on all data through today (includes recent sales)
            
    Returns:
        Dictionary with training summary (compatible with TrainSummary dataclass)
        
    Raises:
        ValueError: If mode is invalid or insufficient training data
        
    Side Effects:
        - Writes model file (.joblib) and metadata (.meta.json) to THIS_DIR
        - Prints training progress and metrics to stdout
    """
    import time as _time
    start_ts = _time.time()

    # Validate mode
    mode = (mode or "live").lower().strip()
    if mode not in {"seed", "live"}:
        raise ValueError(f"Unsupported mode '{mode}'. Use 'seed' or 'live'.")

    # Determine date window and output paths
    if mode == "seed":
        end_date = SEED_END
        model_path, meta_path = SEED_MODEL_PATH, SEED_META_PATH
    else:
        end_date = date.today()
        model_path, meta_path = LIVE_MODEL_PATH, LIVE_META_PATH

    # ---- Step 1: Load Sales Data (aggregated to daily totals) ----
    print(f"[train] Loading October sales data (mode={mode}, end_date={end_date})...")
    df = _load_oct_history(end_date=end_date)
    print(f"[train] Loaded {len(df)} daily sales records")

    # ---- Step 2: Freeze Product Universe (for seed mode) ----
    # This prevents baseline bleed: seed model only predicts products seen in seed window
    product_names = sorted(df['product_name'].astype(str).str.strip().unique().tolist())
    print(f"[train] Product universe: {len(product_names)} products")

    # ---- Step 3: Extract Features (includes discount/basket enrichment) ----
    print(f"[train] Extracting features (calendar + discount + basket)...")
    X_all, y_all = make_features(df, engine=ENGINE)
    df_all = pd.concat([X_all, y_all.rename("units"), df[["date"]]], axis=1)
    print(f"[train] Feature matrix shape: {X_all.shape}")
    print(f"[train] Features: {len(CAT_COLS)} categorical, {len(NUM_COLS)} numeric")

    # ---- Step 4: Validate Feature Columns ----
    # Ensure all expected columns are present (critical for model compatibility)
    for col in CAT_COLS + NUM_COLS + ["units", "date"]:
        if col not in df_all.columns:
            raise ValueError(f"Feature column missing: {col}")

    # ---- Step 5: Time-Ordered Train/Validation/Test Split ----
    train_df, valid_df, test_df = _time_split(df_all)
    X_train, y_train = train_df[CAT_COLS + NUM_COLS], train_df["units"]
    X_valid, y_valid = valid_df[CAT_COLS + NUM_COLS], valid_df["units"]
    X_test, y_test = test_df[CAT_COLS + NUM_COLS], test_df["units"]
    
    print(f"[train] Data split: train={len(X_train)}, valid={len(X_valid)}, test={len(X_test)}")

    if len(X_train) == 0:
        raise ValueError("Insufficient data to train (no training rows).")

    # ---- Step 6: Build and Train Model ----
    pipe = _build_pipeline()
    print(f"[train] Fitting model (mode={mode}, rows={len(df_all)})...")
    pipe.fit(X_train, y_train)
    print(f"[train] Model training complete")

    # ---- Step 7: Evaluate Performance ----
    print(f"[train] Evaluating model performance...")
    valid_metrics = _report(pipe, X_valid, y_valid, "valid")
    test_metrics = _report(pipe, X_test, y_test, "test")

    # ---- Step 8: Persist Model and Metadata Atomically ----
    _atomic_write_joblib(pipe, model_path)
    print(f"[train] Saved model → {model_path}")

    # Build comprehensive metadata
    meta = {
        "mode": mode,
        "seedWindow": {"start": SEED_START.isoformat(), "end": SEED_END.isoformat()},
        "dataWindow": {"start": SEED_START.isoformat(), "end": end_date.isoformat()},
        "rows": {
            "train": len(X_train),
            "valid": len(X_valid),
            "test": len(X_test),
            "total": len(df_all)
        },
        "metrics": {
            "valid": asdict(valid_metrics),
            "test": asdict(test_metrics)
        },
        "features": {
            "categorical": CAT_COLS,
            "numeric": NUM_COLS,
            "numeric_calendar": [c for c in NUM_COLS if c in ["year", "month", "day", "dow", "doy", "is_weekend", "days_to_halloween"]],
            "numeric_discount": [c for c in NUM_COLS if "discount" in c],
            "numeric_basket": [c for c in NUM_COLS if "basket" in c]
        },
        "products": {
            "count": len(product_names),
            "names": product_names  # ← frozen set used at train time (for seed mode)
        },
        "randomState": RANDOM_STATE,
        "trainedAt": datetime.now().isoformat(timespec="seconds"),
        "library": {
            "sklearn": sklearn_version,
            "pandas": pd.__version__,
            "numpy": np.__version__
        },
        "database": {"urlEnvVar": "DATABASE_URL", "driver": "postgresql"},
        "target": "daily end-of-day units per (product, date)",
    }
    
    _atomic_write_json(meta, meta_path)
    print(f"[train] Saved metadata → {meta_path}")

    # ---- Step 9: Return Summary ----
    duration = time.time() - start_ts
    summary = asdict(TrainSummary(
        status="ok",
        mode=mode,
        rows={"train": len(X_train), "valid": len(X_valid), "test": len(X_test), "total": len(df_all)},
        metrics={"valid": asdict(valid_metrics), "test": asdict(test_metrics)},
        modelPath=str(model_path),
        seedWindow={"start": SEED_START.isoformat(), "end": SEED_END.isoformat()},
        trainedAt=meta["trainedAt"],
        durationSec=round(duration, 3),
    ))
    
    print(f"[train] Training complete in {duration:.2f} seconds")
    return summary

# ----------------------------- CLI -----------------------------

def main() -> None:
    """
    Command-line interface for training.
    
    Usage:
        python train.py               → live mode (default)
        python train.py --mode seed   → seed mode (frozen baseline)
        python train.py --mode live   → live mode (includes recent data)
    """
    parser = argparse.ArgumentParser(description="Train October candy sales model.")
    parser.add_argument(
        "--mode",
        choices=["seed", "live"],
        default="live",
        help="Training mode: 'seed' (frozen baseline) or 'live' (includes recent data)"
    )
    args = parser.parse_args()

    summary = run_training(mode=args.mode)
    print("[train] summary:", json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
