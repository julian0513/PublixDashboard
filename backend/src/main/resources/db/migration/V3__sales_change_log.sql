CREATE TABLE IF NOT EXISTS public.sales_change_log (
                                                       id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id       uuid NOT NULL REFERENCES public.sales(id) ON DELETE CASCADE,
    product_name  text NOT NULL,
    date          date NOT NULL,
    old_units     int  NOT NULL,
    new_units     int  NOT NULL,
    changed_at    timestamptz NOT NULL DEFAULT now(),
    undone_at     timestamptz
    );

CREATE INDEX IF NOT EXISTS idx_scl_prod_date_time
    ON public.sales_change_log (product_name, date, changed_at DESC);
