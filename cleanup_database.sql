-- Database Cleanup Script
-- Run this script to delete all synthetic data (transactions, basket analyses, discounts)
-- 
-- Usage (if you have psql installed):
--   psql -h localhost -U app -d publixai -f cleanup_database.sql
--
-- Or copy and paste these commands into your PostgreSQL client

-- Delete in order to respect foreign key constraints
DELETE FROM transaction_item;
DELETE FROM transaction;
DELETE FROM basket_analysis;
DELETE FROM discount_effectiveness;
DELETE FROM discount;

-- Verify deletion
SELECT 
    (SELECT COUNT(*) FROM transaction_item) as transaction_items,
    (SELECT COUNT(*) FROM transaction) as transactions,
    (SELECT COUNT(*) FROM basket_analysis) as basket_analyses,
    (SELECT COUNT(*) FROM discount_effectiveness) as discount_effectiveness,
    (SELECT COUNT(*) FROM discount) as discounts;

