"""
Feature Engineering Module — Publix AI ML Service
==================================================

Purpose:
    Centralized feature engineering for candy sales prediction models.
    Handles calendar features, discount features, and basket analysis features.

Key Functions:
    - make_features(): Extract features from sales data (training)
    - calendar_grid(): Build inference grid with features (prediction)
    - _enrich_with_discount_features(): Add discount context
    - _enrich_with_basket_features(): Add basket analysis context

Feature Categories:
    - Categorical: product_name
    - Numeric (Calendar): year, month, day, dow, doy, is_weekend, days_to_halloween
    - Numeric (Discount): has_active_discount, discount_percent, avg_discount_effectiveness
    - Numeric (Basket): basket_association_count, top_basket_confidence, avg_basket_confidence

Data Dependencies:
    - Requires database connection to discounts, discount_effectiveness, and basket_analysis tables
    - Gracefully handles missing data (products without discounts/baskets get default values)

Backward Compatibility:
    - If discount/basket data is unavailable, features default to safe values (0.0, False)
    - Model can still train/predict with calendar features only
"""

from __future__ import annotations
from typing import Iterable, List, Optional, Sequence, Tuple, Union
from datetime import date as _date

import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text

# ---- Feature Column Definitions (shared by training & inference) ----
# These must match exactly between training and inference to avoid feature mismatch errors.

# Categorical features (one-hot encoded)
CAT_COLS: List[str] = ["product_name"]

# Numeric features (passed through to model)
# Calendar features (always available)
NUM_COLS_CALENDAR: List[str] = [
    "year",
    "month",
    "day",
    "dow",              # Day of week (0=Monday, 6=Sunday)
    "doy",              # Day of year (1-365/366)
    "is_weekend",       # Binary: 1 if Saturday/Sunday, else 0
    "days_to_halloween" # Days until Oct 31 of same year
]

# Discount features (enriched from database)
NUM_COLS_DISCOUNT: List[str] = [
    "has_active_discount",      # Binary: 1 if product has active discount on this date, else 0
    "discount_percent",          # Numeric: discount percentage (0.0-100.0), 0.0 if no discount
    "avg_discount_effectiveness" # Numeric: historical average sales lift % for this product/discount combo
]

# Basket analysis features (enriched from database)
NUM_COLS_BASKET: List[str] = [
    "basket_association_count",  # Count of items frequently bought with this product
    "top_basket_confidence",     # Highest confidence score among basket associations (0.0-1.0)
    "avg_basket_confidence"      # Average confidence score of all basket associations (0.0-1.0)
]

# Combined numeric features (order matters for model compatibility)
NUM_COLS: List[str] = NUM_COLS_CALENDAR + NUM_COLS_DISCOUNT + NUM_COLS_BASKET

__all__ = ["make_features", "calendar_grid", "CAT_COLS", "NUM_COLS", "NUM_COLS_CALENDAR", 
           "NUM_COLS_DISCOUNT", "NUM_COLS_BASKET"]


# ---- Utility Functions ----

def _to_local_date_range(start: Union[str, _date], end: Union[str, _date]) -> pd.DatetimeIndex:
    """
    Generate an inclusive daily date range in local time (no timezone math).
    
    Args:
        start: Start date (inclusive)
        end: End date (inclusive)
        
    Returns:
        DatetimeIndex with daily frequency
        
    Raises:
        ValueError: If date range is empty (end < start)
    """
    dr = pd.date_range(start=start, end=end, freq="D")
    if dr.empty:
        raise ValueError("Empty date range: ensure end is on or after start.")
    return dr


def _clean_products(products: Iterable[str]) -> List[str]:
    """
    Normalize product names: trim whitespace, deduplicate, remove empty strings.
    
    Args:
        products: Iterable of product name strings
        
    Returns:
        List of cleaned, unique product names
    """
    if products is None:
        return []
    vals = pd.Series(list(products), dtype="object").astype(str).str.strip()
    vals = vals[vals.ne("")].drop_duplicates()
    return vals.tolist()


# ---- Database Connection (lazy initialization) ----

_ENGINE: Optional[object] = None

def _get_engine():
    """
    Lazy-load database engine from environment variable.
    Uses same connection pattern as train.py for consistency.
    
    Returns:
        SQLAlchemy engine instance
        
    Note:
        Falls back gracefully if DATABASE_URL is not set or connection fails.
        This allows the module to work in environments without database access.
    """
    global _ENGINE
    if _ENGINE is None:
        import os
        from dotenv import load_dotenv
        load_dotenv()
        database_url = os.getenv("DATABASE_URL", "postgresql://ml_ro:change-me@localhost:5432/publixai")
        try:
            from sqlalchemy import create_engine
            _ENGINE = create_engine(database_url, pool_pre_ping=True, pool_recycle=600)
        except Exception as e:
            # Log warning but don't fail - features will use defaults
            print(f"[features] Warning: Could not initialize database connection: {e}")
            print("[features] Discount and basket features will default to zero values.")
            _ENGINE = False  # Sentinel to prevent retry
    return _ENGINE if _ENGINE is not False else None


# ---- Discount Feature Enrichment ----

def _enrich_with_discount_features(df: pd.DataFrame, engine: Optional[object]) -> pd.DataFrame:
    """
    Enrich dataframe with discount-related features from database.
    
    Features Added:
        - has_active_discount: 1 if product has active discount on date, else 0
        - discount_percent: Discount percentage (0.0-100.0), 0.0 if no discount
        - avg_discount_effectiveness: Historical average sales lift % for this product/discount combo
        
    Args:
        df: DataFrame with columns ['product_name', 'date']
        engine: SQLAlchemy engine (None if unavailable)
        
    Returns:
        DataFrame with discount features added (defaults to 0.0/False if data unavailable)
    """
    # Initialize default values (safe fallbacks)
    df["has_active_discount"] = 0
    df["discount_percent"] = 0.0
    df["avg_discount_effectiveness"] = 0.0
    
    if engine is None:
        return df
    
    try:
        # Query active discounts for the date range
        min_date = df["date"].min()
        max_date = df["date"].max()
        
        sql_discounts = text("""
            SELECT 
                d.product_name,
                d.discount_percent,
                d.start_date,
                d.end_date
            FROM public.discounts d
            WHERE d.start_date <= :max_date
              AND d.end_date >= :min_date
        """)
        
        df_discounts = pd.read_sql_query(
            sql_discounts, 
            engine, 
            params={"min_date": min_date, "max_date": max_date}
        )
        
        if not df_discounts.empty:
            # Convert dates to datetime for comparison
            df_discounts["start_date"] = pd.to_datetime(df_discounts["start_date"])
            df_discounts["end_date"] = pd.to_datetime(df_discounts["end_date"])
            df["date_dt"] = pd.to_datetime(df["date"])

            # Vectorized approach: merge dataframes instead of row-by-row processing
            # This is much faster for large datasets

            # Create a temporary dataframe with index preserved
            df_temp = df[["product_name", "date_dt"]].copy()
            df_temp["original_index"] = df.index

            # Cross join all (product, date) with all discounts, then filter
            df_temp["key"] = 1
            df_discounts["key"] = 1
            df_cross = df_temp.merge(df_discounts, on="key", how="outer").drop("key", axis=1)

            # Filter for active discounts (vectorized)
            df_active = df_cross[
                (df_cross["product_name_x"] == df_cross["product_name_y"]) &
                (df_cross["start_date"] <= df_cross["date_dt"]) &
                (df_cross["end_date"] >= df_cross["date_dt"])
            ].copy()

            # Group by original index and find the highest discount percent per (product, date)
            if not df_active.empty:
                active_discounts = df_active.groupby("original_index").agg({
                    "discount_percent": "max"
                }).reset_index()

                # Update the main dataframe
                df["has_active_discount"] = 0
                df["discount_percent"] = 0.0

                for _, row in active_discounts.iterrows():
                    idx = int(row["original_index"])
                    df.loc[idx, "has_active_discount"] = 1
                    df.loc[idx, "discount_percent"] = float(row["discount_percent"])
            
            # Query historical discount effectiveness for avg_discount_effectiveness
            # This gives us the average sales lift for each product/discount combination
            # Get unique products
            products_list = df["product_name"].unique().tolist()
            if products_list:
                # Build IN clause with proper parameterization
                placeholders = ",".join([f":p{i}" for i in range(len(products_list))])
                sql_effectiveness = text(f"""
                    SELECT 
                        product_name,
                        discount_percent,
                        AVG(sales_lift_percent) AS avg_lift
                    FROM public.discount_effectiveness
                    WHERE product_name IN ({placeholders})
                      AND discount_percent > 0
                    GROUP BY product_name, discount_percent
                """)
                
                params = {f"p{i}": p for i, p in enumerate(products_list)}
                df_effectiveness = pd.read_sql_query(
                    sql_effectiveness,
                    engine,
                    params=params
                )
                
                if not df_effectiveness.empty:
                    # Merge effectiveness data
                    df_merged = df.merge(
                        df_effectiveness,
                        on=["product_name", "discount_percent"],
                        how="left"
                    )
                    df["avg_discount_effectiveness"] = df_merged["avg_lift"].fillna(0.0)
            
            # Clean up temporary column
            df.drop(columns=["date_dt"], inplace=True, errors="ignore")
            
    except Exception as e:
        # Log but don't fail - use default values
        print(f"[features] Warning: Could not load discount features: {e}")
        print("[features] Using default discount values (0.0, False)")
    
    return df


# ---- Basket Analysis Feature Enrichment ----

def _enrich_with_basket_features(df: pd.DataFrame, engine: Optional[object]) -> pd.DataFrame:
    """
    Enrich dataframe with basket analysis features from database.
    
    Features Added:
        - basket_association_count: Number of items frequently bought with this product
        - top_basket_confidence: Highest confidence score among associations (0.0-1.0)
        - avg_basket_confidence: Average confidence score of all associations (0.0-1.0)
        
    Args:
        df: DataFrame with columns ['product_name', 'date']
        engine: SQLAlchemy engine (None if unavailable)
        
    Returns:
        DataFrame with basket features added (defaults to 0.0 if data unavailable)
    """
    # Initialize default values (safe fallbacks)
    df["basket_association_count"] = 0
    df["top_basket_confidence"] = 0.0
    df["avg_basket_confidence"] = 0.0
    
    if engine is None:
        return df
    
    try:
        # Query basket analysis for all products in the dataframe
        products_list = df["product_name"].unique().tolist()
        if not products_list:
            return df
        
        # Build IN clause with proper parameterization
        placeholders = ",".join([f":p{i}" for i in range(len(products_list))])
        sql_basket = text(f"""
            SELECT 
                primary_product AS product_name,
                COUNT(*) AS association_count,
                MAX(confidence_score) AS top_confidence,
                AVG(confidence_score) AS avg_confidence
            FROM public.basket_analysis
            WHERE primary_product IN ({placeholders})
            GROUP BY primary_product
        """)
        
        params = {f"p{i}": p for i, p in enumerate(products_list)}
        df_basket = pd.read_sql_query(
            sql_basket,
            engine,
            params=params
        )
        
        if not df_basket.empty:
            # Merge basket data
            df_merged = df.merge(
                df_basket,
                on="product_name",
                how="left"
            )
            
            # Fill missing values with defaults (products without basket data)
            df["basket_association_count"] = df_merged["association_count"].fillna(0).astype(int)
            df["top_basket_confidence"] = df_merged["top_confidence"].fillna(0.0).astype(float)
            df["avg_basket_confidence"] = df_merged["avg_confidence"].fillna(0.0).astype(float)
            
    except Exception as e:
        # Log but don't fail - use default values
        print(f"[features] Warning: Could not load basket features: {e}")
        print("[features] Using default basket values (0.0)")
    
    return df


# ---- Main Feature Engineering Functions ----

def make_features(df: pd.DataFrame, engine: Optional[object] = None) -> Tuple[pd.DataFrame, pd.Series]:
    """
    Extract features from sales data for model training.
    
    This is the primary feature engineering function used during training.
    It extracts calendar features and enriches with discount/basket data from the database.
    
    Required Input Columns:
        - product_name: Product name (string)
        - date: Date of sale (datetime or date-like)
        - units: Number of units sold (numeric)
        
    Output:
        Tuple of (X, y) where:
            - X: DataFrame with feature columns (CAT_COLS + NUM_COLS)
            - y: Series of target values (units sold)
    
    Feature Engineering Steps:
        1. Normalize and validate input columns
        2. Extract calendar features (year, month, day, etc.)
        3. Enrich with discount features from database
        4. Enrich with basket analysis features from database
        5. Return feature matrix X and target vector y
        
    Args:
        df: Input DataFrame with sales data
        engine: Optional SQLAlchemy engine (auto-detected if None)
        
    Returns:
        Tuple of (feature DataFrame, target Series)
        
    Raises:
        ValueError: If required columns are missing
        
    Example:
        >>> df = pd.DataFrame({
        ...     'product_name': ['Reese\'s', 'M&M\'s'],
        ...     'date': ['2024-10-15', '2024-10-16'],
        ...     'units': [50, 45]
        ... })
        >>> X, y = make_features(df)
        >>> assert 'product_name' in X.columns
        >>> assert 'year' in X.columns
        >>> assert 'has_active_discount' in X.columns
        >>> assert 'basket_association_count' in X.columns
    """
    # Validate required columns
    if not {"product_name", "date", "units"}.issubset(df.columns):
        missing = [c for c in ["product_name", "date", "units"] if c not in df.columns]
        raise ValueError(f"make_features: missing required columns: {missing}")
    
    # Create working copy to avoid mutating input
    df = df.copy()
    
    # ---- Step 1: Normalize Input Data ----
    df["product_name"] = df["product_name"].astype(str).str.strip()
    df["date"] = pd.to_datetime(df["date"], errors="raise", utc=False)
    # Ensure integer units, clip negatives (guards against dirty inputs)
    df["units"] = pd.to_numeric(df["units"], errors="coerce").fillna(0).astype(int).clip(lower=0)
    
    # ---- Step 2: Extract Calendar Features ----
    # These are always available and don't require database access
    df["year"] = df["date"].dt.year
    df["month"] = df["date"].dt.month
    df["day"] = df["date"].dt.day
    df["dow"] = df["date"].dt.weekday  # 0=Monday, 6=Sunday
    df["doy"] = df["date"].dt.dayofyear
    df["is_weekend"] = (df["dow"] >= 5).astype(int)
    
    # Proximity to Halloween (Oct 31 of same year)
    halloweens = pd.to_datetime(df["date"].dt.year.astype(str) + "-10-31")
    df["days_to_halloween"] = (halloweens - df["date"]).dt.days.astype(int)
    
    # ---- Step 3: Enrich with Discount Features ----
    # Get database engine if not provided
    if engine is None:
        engine = _get_engine()
    
    df = _enrich_with_discount_features(df, engine)
    
    # ---- Step 4: Enrich with Basket Analysis Features ----
    df = _enrich_with_basket_features(df, engine)
    
    # ---- Step 5: Extract Feature Matrix and Target ----
    # Ensure all expected columns are present (with defaults if enrichment failed)
    for col in NUM_COLS_DISCOUNT + NUM_COLS_BASKET:
        if col not in df.columns:
            # Set safe defaults if enrichment didn't add the column
            if col.startswith("has_"):
                df[col] = 0
            else:
                df[col] = 0.0
    
    # Extract feature columns in correct order
    X = df[CAT_COLS + NUM_COLS].copy()
    y = df["units"].copy()  # Target: integer units
    
    return X, y


def calendar_grid(
    products: Sequence[str] = None,
    start: Union[str, _date] = None,
    end: Union[str, _date] = None,
    engine: Optional[object] = None,
    **kwargs,
) -> pd.DataFrame:
    """
    Build a cartesian (products × dates) grid for model inference.
    
    This function creates a feature matrix for prediction by generating all combinations
    of products and dates, then enriching with the same features used during training.
    This ensures feature consistency between training and inference.
    
    Args:
        products: Sequence of product names (required)
        start: Start date (inclusive, required)
        end: End date (inclusive, required)
        engine: Optional SQLAlchemy engine (auto-detected if None)
        **kwargs: Alternative way to pass arguments (for backward compatibility)
        
    Returns:
        DataFrame with columns: ['product_name', 'date'] + NUM_COLS
        (All feature columns needed for model prediction)
        
    Raises:
        ValueError: If products is empty or date range is invalid
        
    Example:
        >>> grid = calendar_grid(
        ...     products=["Reese's", "M&M's"],
        ...     start="2025-10-01",
        ...     end="2025-10-31"
        ... )
        >>> assert len(grid) == 2 * 31  # 2 products × 31 days
        >>> assert 'year' in grid.columns
        >>> assert 'has_active_discount' in grid.columns
    """
    # Support callers that pass args in different orders via kwargs
    products = kwargs.get("products", products)
    start = kwargs.get("start", start)
    end = kwargs.get("end", end)
    
    # Validate inputs
    names = _clean_products(products)
    if not names:
        raise ValueError("calendar_grid: products must be a non-empty sequence of names.")
    
    dates = _to_local_date_range(start, end)
    
    # ---- Step 1: Create Cartesian Product (products × dates) ----
    grid = (
        pd.MultiIndex.from_product(
            [pd.Index(names, name="product_name"), dates],
            names=["product_name", "date"]
        )
        .to_frame(index=False)
    )
    
    # ---- Step 2: Extract Calendar Features (identical to make_features) ----
    grid["year"] = grid["date"].dt.year
    grid["month"] = grid["date"].dt.month
    grid["day"] = grid["date"].dt.day
    grid["dow"] = grid["date"].dt.weekday  # 0=Monday, 6=Sunday
    grid["doy"] = grid["date"].dt.dayofyear
    grid["is_weekend"] = (grid["dow"] >= 5).astype(int)
    
    # Proximity to Halloween
    halloweens = pd.to_datetime(grid["date"].dt.year.astype(str) + "-10-31")
    grid["days_to_halloween"] = (halloweens - grid["date"]).dt.days.astype(int)
    
    # ---- Step 3: Enrich with Discount Features ----
    if engine is None:
        engine = _get_engine()
    
    grid = _enrich_with_discount_features(grid, engine)
    
    # ---- Step 4: Enrich with Basket Analysis Features ----
    grid = _enrich_with_basket_features(grid, engine)
    
    # ---- Step 5: Ensure All Required Columns Are Present ----
    # Add defaults for any missing enrichment columns
    for col in NUM_COLS_DISCOUNT + NUM_COLS_BASKET:
        if col not in grid.columns:
            if col.startswith("has_"):
                grid[col] = 0
            else:
                grid[col] = 0.0
    
    # Return columns in expected order (product_name, date, then all numeric features)
    return grid[["product_name", "date"] + NUM_COLS]
