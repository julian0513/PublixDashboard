-- 1) row count for seed period (should be 3100)
SELECT COUNT(*) AS rows_total
FROM sales
WHERE date BETWEEN '2015-10-01' AND '2024-10-31';

-- 2) every product has 31 days per year (should return 0 rows)
SELECT product_name, EXTRACT(YEAR FROM date) AS yr, COUNT(*) AS rows_per_year
FROM sales
WHERE date BETWEEN '2015-10-01' AND '2024-10-31'
GROUP BY 1,2
HAVING COUNT(*) <> 31;

-- 3) each October day has all 10 products (should return 0 rows)
WITH days AS (
    SELECT d::date AS d
    FROM generate_series('2015-10-01'::date,'2024-10-31'::date,'1 day') AS d
    WHERE EXTRACT(MONTH FROM d)=10
)
SELECT d AS date_missing, COUNT(s.product_name) AS products_that_day
FROM days
         LEFT JOIN sales s ON s.date = days.d
GROUP BY d
HAVING COUNT(s.product_name) <> 10
ORDER BY d;

-- 4) halloween spike sanity (just to eyeball; 10 rows for each year)
SELECT date, product_name, units
FROM sales
WHERE EXTRACT(MONTH FROM date)=10
  AND EXTRACT(DAY   FROM date) IN (31,24,17)
ORDER BY date, units DESC;

-- 5) type/constraint quick scan
SELECT COUNT(*) AS negative_units FROM sales WHERE units < 0;
SELECT COUNT(*) AS null_dates      FROM sales WHERE date IS NULL;
