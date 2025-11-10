-- V5: Allow multiple entries per product per day (for intraday partials)
--     and add indexes to speed up "as_of" queries.

-- 1) Drop the unique constraint that prevents multiple entries per day.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE schemaname = 'public' AND indexname = 'uq_sales_product_date'
  ) THEN
DROP INDEX public.uq_sales_product_date;
END IF;
END $$;

-- 2) Replace with non-unique composite index to keep lookups fast by product+date.
CREATE INDEX IF NOT EXISTS idx_sales_product_date
    ON public.sales (product_name, date);

-- 3) Intraday needs fast "sum up to a time" access; created_at should be in the index.
--    These two indexes cover the common filters:
--      - WHERE date = ? AND created_at <= ?
--      - WHERE created_at BETWEEN ? AND ?
CREATE INDEX IF NOT EXISTS idx_sales_date_created_at
    ON public.sales (date, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sales_created_at
    ON public.sales (created_at);
