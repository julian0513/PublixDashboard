-- keep pgcrypto available (harmless if already installed)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- only set default if the table already exists (so V2 won't fail on a fresh DB)
DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'sales_change_log'
        ) THEN
            ALTER TABLE sales_change_log
                ALTER COLUMN id SET DEFAULT gen_random_uuid();
        END IF;
    END $$;
