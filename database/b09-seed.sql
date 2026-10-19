-- Du lieu gia lap phuc vu Buoi 9.
-- Co the chay lai: bo qua SKU B09 da ton tai.

INSERT INTO product (sku, name, unit, price)
SELECT
    'B09-P' || LPAD(CAST(n.X AS VARCHAR), 5, '0'),
    'San pham B09 ' || CAST(n.X AS VARCHAR),
    CASE MOD(n.X, 3)
        WHEN 0 THEN 'Cai'
        WHEN 1 THEN 'Hop'
        ELSE 'Thung'
    END,
    CAST(10000 + MOD(n.X, 200) * 5000 AS DECIMAL(19, 2))
FROM SYSTEM_RANGE(1, 5000) n
WHERE NOT EXISTS (
    SELECT 1
    FROM product p
    WHERE p.sku = 'B09-P' || LPAD(CAST(n.X AS VARCHAR), 5, '0')
);
