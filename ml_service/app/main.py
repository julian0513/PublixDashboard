from __future__ import annotations

"""
FastAPI ML Service — Publix AI (production-ready)

Guarantees
----------
- All forecasts are **end-of-day totals** by store close (22:00 ET by default).
- Seed forecasts are **frozen** to the historical window and product universe.
- Intraday blends model prior with time-scaled partials and never goes below observed.

Endpoints
---------
POST /ml/train?mode=seed|live
GET  /ml/forecast?start&end&mode=seed|live&top_k
GET  /ml/forecast_intraday?date_str&as_of?&open_hour?&close_hour?&top_k&mode
GET  /health
GET  /ml/forecast_historical  (QA-only math baseline; frozen)

Security
--------
All operational endpoints require X-ML-Secret.
"""

import os
from pathlib import Path
from datetime import date, datetime
from typing import Dict, List, Optional, Tuple

import joblib
import numpy as np
import pandas as pd
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import create_engine, text
from zoneinfo import ZoneInfo

# --------------------------- Environment & Paths ---------------------------

load_dotenv()

APP_TZ = os.getenv("TZ", "America/New_York")
ML_SECRET = os.getenv("ML_SECRET", "dev-secret")
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://ml_ro:change-me@localhost:5432/publixai")

STORE_OPEN_HOUR = int(os.getenv("STORE_OPEN_HOUR", "8"))
STORE_CLOSE_HOUR = int(os.getenv("STORE_CLOSE_HOUR", "22"))
INTRADAY_WEIGHT_ACCEL = float(os.getenv("INTRADAY_WEIGHT_ACCEL", "1.25"))
INTRADAY_MIN_FRAC = float(os.getenv("INTRADAY_MIN_FRAC", "0.000001"))

ENGINE = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=600)

THIS_DIR = Path(__file__).resolve().parent
SEED_MODEL_PATH = THIS_DIR / "history_seed.joblib"
LIVE_MODEL_PATH = THIS_DIR / "model_live.joblib"
SEED_META_PATH = THIS_DIR / "history_seed.meta.json"
LIVE_META_PATH = THIS_DIR / "model_live.meta.json"

# Shared training constants / feature builder
import train
from features import calendar_grid

SEED_START = train.SEED_START
SEED_END = train.SEED_END

# Feature columns expected by the pipeline
CAT_COLS = ["product_name"]
NUM_COLS = ["year", "month", "day", "dow", "doy", "is_weekend", "days_to_halloween"]

# Hot-loaded models
_models: Dict[str, Optional[object]] = {"seed": None, "live": None}

# --------------------------- Schemas ---------------------------

class ForecastItem(BaseModel):
    productName: str
    predictedUnits: float
    confidence: Optional[float] = None  # 0..1

class ForecastResponse(BaseModel):
    status: str
    modeRequested: str
    modeUsed: str
    dateRange: Dict[str, str]
    topK: int
    totalProducts: int
    items: List[ForecastItem]
    modelPath: Optional[str] = None
    trainedAt: Optional[str] = None

# --------------------------- Auth & Utils ---------------------------

def _auth(secret: str) -> None:
    if secret != ML_SECRET:
        raise HTTPException(status_code=401, detail="Unauthorized")

def _parse_date(s: str) -> date:
    try:
        return datetime.strptime(s, "%Y-%m-%d").date()
    except Exception:
        raise HTTPException(status_code=400, detail=f"Invalid date: {s}. Use YYYY-MM-DD.")

def _now_tz() -> datetime:
    return datetime.now(ZoneInfo(APP_TZ))

def _load_model(path: Path) -> Optional[object]:
    if not path.exists():
        return None
    try:
        return joblib.load(path)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to load model: {e}")

def _load_meta(path: Path) -> Optional[dict]:
    j = path.with_suffix(".meta.json")
    if not j.exists():
        return None
    try:
        import json
        with open(j, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return None

def _valid_confidence_from_meta(meta: Optional[dict]) -> Optional[float]:
    """confidence ≈ max(0, 1 - MAPE) where MAPE can be 0..1 or 0..100."""
    try:
        m = float(meta["metrics"]["valid"]["mape"])
        if m > 1.0:
            m /= 100.0
        return max(0.0, min(1.0, 1.0 - m))
    except Exception:
        return None

# --------------------------- Database Helpers ---------------------------

def _distinct_products(end_date: date) -> List[str]:
    """
    Distinct product names from October rows within [SEED_START, end_date].
    - For seed forecasts, pass end_date=SEED_END (frozen window).
    - For live forecasts, pass the requested end (includes new items).
    """
    sql = text("""
               SELECT DISTINCT product_name
               FROM public.sales
               WHERE EXTRACT(MONTH FROM date) = 10
                 AND date >= :start AND date <= :end
               ORDER BY 1
               """)
    df = pd.read_sql_query(sql, ENGINE, params={"start": SEED_START, "end": end_date})
    names = (
        df["product_name"]
        .astype(str).str.strip().replace({"": np.nan}).dropna().unique().tolist()
    )
    if not names:
        raise HTTPException(status_code=404, detail="No products found for October in the selected window.")
    return names


def _latest_created_at(d: date) -> Optional[datetime]:
    """
    Return the most recent created_at for calendar date `d` in APP_TZ,
    as a tz-aware datetime. None if no rows for that date.
    """
    sql = text("""
               SELECT MAX(timezone(:tz, created_at)) AS latest_local
               FROM public.sales
               WHERE date = :d
               """)
    df = pd.read_sql_query(sql, ENGINE, params={"tz": APP_TZ, "d": d})
    if df.empty or pd.isna(df.loc[0, "latest_local"]):
        return None
    latest = pd.to_datetime(df.loc[0, "latest_local"])
    return latest.to_pydatetime().replace(tzinfo=ZoneInfo(APP_TZ))



def _partials_for_date(d: date, as_of: Optional[datetime] = None) -> pd.DataFrame:
    """
    Sum partial units for a specific calendar date from public.sales.
    If `as_of` is provided, compare created_at in APP_TZ against the cutoff.

    Returns: columns ["product_name", "partial_units"].
    """
    if as_of is None:
        sql = text("""
                   SELECT product_name, SUM(units)::float AS partial_units
                   FROM public.sales
                   WHERE date = :d
                   GROUP BY product_name
                   """)
        params = {"d": d}
    else:
        # Normalize cutoff to naive local datetime in APP_TZ
        as_of_local = as_of
        if as_of_local.tzinfo is not None:
            as_of_local = as_of_local.astimezone(ZoneInfo(APP_TZ)).replace(tzinfo=None)
        sql = text("""
                   SELECT product_name, SUM(units)::float AS partial_units
                   FROM public.sales
                   WHERE date = :d
                     AND timezone(:tz, created_at) <= :as_of_local
                   GROUP BY product_name
                   """)
        params = {"d": d, "tz": APP_TZ, "as_of_local": as_of_local}

    df = pd.read_sql_query(sql, ENGINE, params=params)
    if df.empty:
        return pd.DataFrame(columns=["product_name", "partial_units"])
    df["product_name"] = df["product_name"].astype(str).str.strip()
    return df[["product_name", "partial_units"]]

# --------------------------- Model Selection & Products ---------------------------

def _ensure_models_loaded() -> None:
    if _models["seed"] is None:
        _models["seed"] = _load_model(SEED_MODEL_PATH)
    if _models["live"] is None:
        _models["live"] = _load_model(LIVE_MODEL_PATH)

def _seed_products_from_model(model: object) -> Optional[List[str]]:
    """
    Fallback freeze: read product vocabulary from the fitted OneHotEncoder
    inside the persisted seed pipeline. This avoids DB reads and prevents
    any bleed even if metadata is missing.
    """
    try:
        prep = model.named_steps.get("prep")
        cat = prep.named_transformers_["cat"]
        cats = cat.categories_[0]  # single categorical: product_name
        return [str(x) for x in cats.tolist()]
    except Exception:
        return None

def _select_model(mode: str) -> Tuple[str, Optional[object], Optional[Path], Optional[dict]]:
    """
    - mode=live → prefer live; fallback to seed if missing
    - mode=seed → require seed
    """
    _ensure_models_loaded()
    if mode == "live":
        model = _models["live"] or _models["seed"]
        if model is None:
            raise HTTPException(status_code=404, detail="No trained model found. Train seed/live model first.")
        mode_used = "live" if _models["live"] is not None else "seed"
        path = LIVE_MODEL_PATH if mode_used == "live" else SEED_MODEL_PATH
        return mode_used, model, path, _load_meta(path)

    model = _models["seed"]
    if model is None:
        raise HTTPException(status_code=404, detail="Seed model not found. Train with /ml/train?mode=seed first.")
    return "seed", model, SEED_MODEL_PATH, _load_meta(SEED_MODEL_PATH)

def _predict_sum_by_product(model: object, start: date, end: date, products: List[str]) -> pd.DataFrame:
    """
    Build calendar grid (products × dates), generate features, predict **daily EOD totals**,
    then **sum by product** over [start, end].
    """
    try:
        df_inf = calendar_grid(start=start, end=end, products=products)
    except TypeError:
        df_inf = calendar_grid(products=products, start=start, end=end)

    expected = CAT_COLS + NUM_COLS
    missing = [c for c in expected if c not in df_inf.columns]
    if missing:
        raise HTTPException(status_code=500, detail=f"Inference features missing: {missing}")

    X = df_inf[expected]
    try:
        y_hat = model.predict(X)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Model prediction failed: {e}")

    y_hat = np.maximum(0.0, np.asarray(y_hat, dtype=float))  # clip negatives
    out = (
        pd.DataFrame({"product_name": df_inf["product_name"], "y_hat": y_hat})
        .groupby("product_name", as_index=False)["y_hat"].sum()
        .rename(columns={"y_hat": "predicted_units"})
        .sort_values("predicted_units", ascending=False)
        .reset_index(drop=True)
    )
    return out

# --------------------------- Time-of-Day Helper ---------------------------

def _day_fraction(now: datetime, open_hour: int, close_hour: int) -> float:
    """
    Fraction of the retail day elapsed between [open_hour, close_hour] in APP_TZ.
    < open → 0.0, > close → 1.0, else linear fraction.
    """
    start = now.replace(hour=open_hour, minute=0, second=0, microsecond=0)
    end = now.replace(hour=close_hour, minute=0, second=0, microsecond=0)
    if now <= start:
        return 0.0
    if now >= end:
        return 1.0
    return (now - start).total_seconds() / max(1.0, (end - start).total_seconds())

# --------------------------- FastAPI App & Endpoints ---------------------------

app = FastAPI(title="Publix AI ML Service", version="1.0.1")

@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "ok", "time": datetime.now().isoformat(timespec="seconds")}

@app.post("/ml/train")
def ml_train(
        mode: str = Query("live", pattern="^(seed|live)$"),
        x_ml_secret: str = Header(..., alias="X-ML-Secret"),
):
    """
    Train a model:
      seed → 2015-10-01 .. 2024-10-31 (frozen baseline)
      live → 2015-10-01 .. today (includes new rows)
    """
    _auth(x_ml_secret)
    summary = train.run_training(mode=mode)

    # hot-reload
    if mode == "seed":
        _models["seed"] = _load_model(SEED_MODEL_PATH)
    else:
        _models["live"] = _load_model(LIVE_MODEL_PATH)

    return summary

@app.get("/ml/forecast", response_model=ForecastResponse)
def ml_forecast(
        start: str = Query(..., description="YYYY-MM-DD"),
        end: str = Query(..., description="YYYY-MM-DD"),
        top_k: int = Query(10, ge=1, le=100),
        mode: str = Query("seed", pattern="^(seed|live)$"),
        x_ml_secret: str = Header(..., alias="X-ML-Secret"),
):
    """
    Predict **EOD totals** per product over [start, end] (sum of daily EODs).
    Seed mode is frozen to the original product universe (no bleed).
    """
    _auth(x_ml_secret)
    start_d, end_d = _parse_date(start), _parse_date(end)
    if end_d < start_d:
        raise HTTPException(status_code=400, detail="end must be on or after start.")

    mode_used, model, model_path, meta = _select_model(mode)

    # 🔒 Freeze product universe for seed
    if mode_used == "seed":
        products: Optional[List[str]] = None
        # 1) Prefer metadata (if present)
        if meta and isinstance(meta.get("products"), dict):
            names = meta["products"].get("names")
            if isinstance(names, list) and names:
                products = [str(x) for x in names]
        # 2) Else read from fitted OneHotEncoder in the saved pipeline
        if not products:
            products = _seed_products_from_model(model)
        # 3) Else last resort: DB up to SEED_END (still frozen window)
        if not products:
            products = _distinct_products(end_date=SEED_END)
    else:
        products = _distinct_products(end_date=end_d)

    df_pred = _predict_sum_by_product(model, start_d, end_d, products)

    base_conf = _valid_confidence_from_meta(meta)
    items = [
        ForecastItem(
            productName=str(r["product_name"]),
            predictedUnits=float(r["predicted_units"]),
            confidence=(None if base_conf is None else round(base_conf, 3)),
        )
        for _, r in df_pred.head(top_k).iterrows()
    ]

    return ForecastResponse(
        status="ok",
        modeRequested=mode,
        modeUsed=mode_used,
        dateRange={"start": start_d.isoformat(), "end": end_d.isoformat()},
        topK=top_k,
        totalProducts=int(len(df_pred)),
        items=items,
        modelPath=(None if model_path is None else str(model_path)),
        trainedAt=(None if not meta else meta.get("trainedAt")),
    )

@app.get("/ml/forecast_intraday", response_model=ForecastResponse)
def ml_forecast_intraday(
        date_str: Optional[str] = Query(None, description="YYYY-MM-DD (defaults to today in APP_TZ)"),
        top_k: int = Query(10, ge=1, le=100),
        mode: str = Query("live", pattern="^(seed|live)$"),
        open_hour: int = Query(STORE_OPEN_HOUR, ge=0, le=23),
        close_hour: int = Query(STORE_CLOSE_HOUR, ge=0, le=23),
        as_of: Optional[str] = Query(None, description="Override 'now' (ISO). Defaults to last sale time for that date."),
        x_ml_secret: str = Header(..., alias="X-ML-Secret"),
):
    """
    Intraday **EOD** forecast for a single date.

    NOW DEFINITION (critical):
      - If `as_of` is omitted → we use the **latest created_at** stored for that date.
      - If there are no rows yet for that date → we use store opening time (open_hour).
      - If `as_of` is provided → we use exactly that timestamp.
    This makes 'now' equal to the time of the user's most recent sale entry.
    """
    _auth(x_ml_secret)
    tz = ZoneInfo(APP_TZ)
    target = _parse_date(date_str) if date_str else _now_tz().date()

    # Resolve "now" = sale entry time (NOT console/local time)
    if as_of:
        try:
            dt = datetime.fromisoformat(as_of)
            now = dt if dt.tzinfo else dt.replace(tzinfo=tz)
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid 'as_of' timestamp. Use ISO format.")
    else:
        now = _latest_created_at(target)
        if now is None:
            # No entries yet today → treat as just opened (minimal fraction)
            now = datetime(target.year, target.month, target.day, open_hour, 0, 0, tzinfo=tz)

    # Fraction of day elapsed based on that sale timestamp
    frac = _day_fraction(now=now, open_hour=open_hour, close_hour=close_hour)
    frac = max(0.0, min(1.0, float(frac)))
    eff_frac = max(frac, INTRADAY_MIN_FRAC)  # numeric floor to avoid divide-by-zero early

    # Model + meta
    mode_used, model, model_path, meta = _select_model(mode)
    base_conf = _valid_confidence_from_meta(meta)

    # Product universe (live includes new items; seed frozen)
    products = _distinct_products(end_date=(SEED_END if mode_used == "seed" else target))

    # Model prior (EOD) for target date
    df_model = _predict_sum_by_product(model, start=target, end=target, products=products).rename(
        columns={"predicted_units": "model_pred"}
    )

    # Partials as of that exact sale time
    df_partials = _partials_for_date(target, as_of=now)  # product_name, partial_units

    # Union + blend
    all_names = set(df_model["product_name"].tolist()) | set(df_partials["product_name"].tolist())
    if not all_names:
        return ForecastResponse(
            status="ok",
            modeRequested=f"{mode}-intraday",
            modeUsed=f"{mode_used}-intraday",
            dateRange={"start": target.isoformat(), "end": target.isoformat()},
            topK=top_k,
            totalProducts=0,
            items=[],
            modelPath=(None if model_path is None else str(model_path)),
            trainedAt=(None if not meta else meta.get("trainedAt")),
        )

    df = pd.DataFrame({"product_name": sorted(all_names)})
    df = df.merge(df_model, on="product_name", how="left").merge(df_partials, on="product_name", how="left")
    df["model_pred"] = df["model_pred"].fillna(0.0)
    df["partial_units"] = df["partial_units"].fillna(0.0)

    # Extrapolate to EOD from sale time and blend (never below observed)
    extrapolated = df["partial_units"].values / eff_frac
    w = min(1.0, max(0.0, frac * INTRADAY_WEIGHT_ACCEL))  # early → model, late → partials
    df["eod_units"] = (1.0 - w) * df["model_pred"].values + w * extrapolated
    df["eod_units"] = np.maximum(df["eod_units"].values, df["partial_units"].values)

    # Confidence scaled by model contribution
    if base_conf is None:
        df["confidence"] = np.nan
    else:
        eps = 1e-9
        model_share = ((1.0 - w) * df["model_pred"].values) / np.maximum(df["eod_units"].values, eps)
        df["confidence"] = np.clip(base_conf * model_share, 0.0, 1.0)

    df_out = df[["product_name", "eod_units", "confidence"]].sort_values("eod_units", ascending=False)
    items = [
        ForecastItem(
            productName=str(r.product_name),
            predictedUnits=float(r.eod_units),
            confidence=(None if pd.isna(r.confidence) else float(np.round(r.confidence, 3))),
        )
        for r in df_out.head(top_k).itertuples(index=False)
    ]

    return ForecastResponse(
        status="ok",
        modeRequested=f"{mode}-intraday",
        modeUsed=f"{mode_used}-intraday",
        dateRange={"start": target.isoformat(), "end": target.isoformat()},
        topK=top_k,
        totalProducts=int(len(df_out)),
        items=items,
        modelPath=(None if model_path is None else str(model_path)),
        trainedAt=(None if not meta else meta.get("trainedAt")),
    )


# --------------------------- QA-only baseline (frozen) ---------------------------

def _historical_baseline_sum(start_d: date, end_d: date) -> pd.DataFrame:
    """
    Simple math baseline for QA:
      - Average per-day units per product over the frozen seed window (to SEED_END),
      - Multiply by the number of days in the requested target range [start_d, end_d].
    """
    n_days = (end_d - start_d).days + 1
    sql = text("""
               WITH oct AS (
                   SELECT product_name, date::date AS d, units::int AS u
                   FROM public.sales
                   WHERE EXTRACT(MONTH FROM date) = 10
                     AND date >= :start_seed AND date <= :end_seed
                   )
               SELECT product_name, AVG(u) AS avg_units
               FROM oct
               GROUP BY product_name
               """)
    df = pd.read_sql_query(sql, ENGINE, params={"start_seed": SEED_START, "end_seed": SEED_END})
    if df.empty:
        raise HTTPException(status_code=404, detail="No October rows for historical baseline (seed window).")
    df["predicted_units"] = df["avg_units"].astype(float) * float(n_days)
    return df[["product_name", "predicted_units"]].sort_values("predicted_units", ascending=False)

@app.get("/ml/forecast_historical", response_model=ForecastResponse)
def ml_forecast_historical(
        start: str = Query(..., description="YYYY-MM-DD"),
        end: str = Query(..., description="YYYY-MM-DD"),
        top_k: int = Query(10, ge=1, le=100),
        x_ml_secret: str = Header(..., alias="X-ML-Secret"),
):
    """QA-only: math baseline frozen to the seed window."""
    _auth(x_ml_secret)
    start_d, end_d = _parse_date(start), _parse_date(end)
    if end_d < start_d:
        raise HTTPException(status_code=400, detail="end must be on or after start.")

    df_pred = _historical_baseline_sum(start_d, end_d)
    items = [
        ForecastItem(productName=str(r["product_name"]), predictedUnits=float(r["predicted_units"]), confidence=None)
        for _, r in df_pred.head(top_k).iterrows()
    ]
    return ForecastResponse(
        status="ok",
        modeRequested="historical",
        modeUsed="historical",
        dateRange={"start": start_d.isoformat(), "end": end_d.isoformat()},
        topK=top_k,
        totalProducts=int(len(df_pred)),
        items=items,
        modelPath=None,
        trainedAt=None,
    )
