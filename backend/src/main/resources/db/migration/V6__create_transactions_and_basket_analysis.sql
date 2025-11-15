-- V6: Create transactions and basket analysis tables
-- Purpose: Track what items are purchased together and analyze frequently bought together patterns

-- Transactions table: Represents a single purchase/transaction
CREATE TABLE IF NOT EXISTS transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_date date NOT NULL,
    transaction_time time NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Transaction items: What was bought in each transaction
CREATE TABLE IF NOT EXISTS transaction_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id uuid NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    product_name varchar(120) NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price decimal(10, 2) NOT NULL CHECK (unit_price >= 0),
    discount_percent decimal(5, 2) DEFAULT 0 CHECK (discount_percent >= 0 AND discount_percent <= 100),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Basket analysis: Pre-computed frequently bought together relationships
CREATE TABLE IF NOT EXISTS basket_analysis (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    primary_product varchar(120) NOT NULL,
    associated_product varchar(120) NOT NULL,
    co_occurrence_count integer NOT NULL DEFAULT 0 CHECK (co_occurrence_count >= 0),
    confidence_score decimal(5, 4) NOT NULL CHECK (confidence_score >= 0 AND confidence_score <= 1),
    support_score decimal(5, 4) NOT NULL CHECK (support_score >= 0 AND support_score <= 1),
    last_calculated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(primary_product, associated_product)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_date_time ON transactions(transaction_date, transaction_time);
CREATE INDEX IF NOT EXISTS idx_transaction_items_transaction_id ON transaction_items(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_items_product ON transaction_items(product_name);
CREATE INDEX IF NOT EXISTS idx_basket_analysis_primary ON basket_analysis(primary_product);
CREATE INDEX IF NOT EXISTS idx_basket_analysis_confidence ON basket_analysis(primary_product, confidence_score DESC);

