-- V7: Create discount tracking and analysis tables
-- Purpose: Track discounts applied to products and analyze their impact on sales

-- Discounts table: Track discount promotions applied to products
CREATE TABLE IF NOT EXISTS discounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name varchar(120) NOT NULL,
    discount_percent decimal(5, 2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    start_date date NOT NULL,
    end_date date NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

-- Discount effectiveness: Track sales performance with/without discounts
CREATE TABLE IF NOT EXISTS discount_effectiveness (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name varchar(120) NOT NULL,
    discount_percent decimal(5, 2) NOT NULL,
    date date NOT NULL,
    units_sold integer NOT NULL DEFAULT 0 CHECK (units_sold >= 0),
    revenue decimal(12, 2) NOT NULL DEFAULT 0 CHECK (revenue >= 0),
    avg_unit_price decimal(10, 2) NOT NULL CHECK (avg_unit_price >= 0),
    sales_lift_percent decimal(8, 2), -- % increase compared to baseline
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(product_name, discount_percent, date)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_discounts_product ON discounts(product_name);
CREATE INDEX IF NOT EXISTS idx_discounts_date_range ON discounts(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_product ON discount_effectiveness(product_name);
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_discount ON discount_effectiveness(discount_percent);
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_date ON discount_effectiveness(date);
CREATE INDEX IF NOT EXISTS idx_discount_effectiveness_product_date ON discount_effectiveness(product_name, date);

