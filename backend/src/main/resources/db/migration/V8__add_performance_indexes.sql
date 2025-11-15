-- V8: Add performance indexes for frequently queried columns
-- Purpose: Optimize query performance for basket analysis and discount lookups

-- Index for basket_analysis primary_product lookups (most common query)
CREATE INDEX IF NOT EXISTS idx_basket_analysis_primary_product ON basket_analysis(primary_product);

-- Index for basket_analysis co_occurrence_count (for sorting/filtering)
CREATE INDEX IF NOT EXISTS idx_basket_analysis_co_occurrence ON basket_analysis(co_occurrence_count DESC);

-- Composite index for primary_product + co_occurrence_count (optimizes filtered queries)
CREATE INDEX IF NOT EXISTS idx_basket_analysis_product_co_occ ON basket_analysis(primary_product, co_occurrence_count DESC);

-- Index for discount_effectiveness product_name lookups
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_product_name ON discount_effectiveness(product_name);

-- Index for discount_effectiveness sales_lift_percent (for optimal discount calculation)
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_sales_lift ON discount_effectiveness(sales_lift_percent DESC);

-- Composite index for product_name + discount_percent + sales_lift_percent
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_product_discount_lift ON discount_effectiveness(product_name, discount_percent, sales_lift_percent DESC);

-- Index for discounts product_name lookups
CREATE INDEX IF NOT EXISTS idx_discounts_product_name ON discounts(product_name);

-- Index for discounts date range queries (for year-by-year)
CREATE INDEX IF NOT EXISTS idx_discounts_date_range ON discounts(start_date, end_date);

-- Index for transaction_items product_name (for basket analysis recalculation)
CREATE INDEX IF NOT EXISTS idx_transaction_items_product_name ON transaction_items(product_name);

-- Index for sales table product_name and date (for yearly analysis)
CREATE INDEX IF NOT EXISTS idx_sales_product_date ON sales(product_name, date);

