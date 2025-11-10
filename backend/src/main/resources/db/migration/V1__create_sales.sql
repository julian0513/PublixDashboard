CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sales (
                                     id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name varchar(120) NOT NULL,
    units integer NOT NULL CHECK (units >= 0),
    date date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
    );

-- prevent duplicates for the same product on the same day
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_product_date
    ON sales (product_name, date);

-- speed up queries
CREATE INDEX IF NOT EXISTS idx_sales_date ON sales (date);
CREATE INDEX IF NOT EXISTS idx_sales_product ON sales (product_name);
