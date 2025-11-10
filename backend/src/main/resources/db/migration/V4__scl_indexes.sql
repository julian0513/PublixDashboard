-- Partial index to speed up: findFirstByProductNameAndDateAndUndoneAtIsNullOrderByChangedAtDesc(...)
CREATE INDEX IF NOT EXISTS idx_scl_prod_date_time_pending
    ON public.sales_change_log (product_name, date, changed_at DESC)
    WHERE undone_at IS NULL;

-- Helpful for audit lookups by sale id
CREATE INDEX IF NOT EXISTS idx_scl_sale_id
    ON public.sales_change_log (sale_id);
