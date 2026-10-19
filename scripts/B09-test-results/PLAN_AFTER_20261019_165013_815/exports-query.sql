SELECT r.receipt_date, p.sku,
       SUM(CAST(d.quantity AS BIGINT)) AS total_quantity
FROM export_receipt r
JOIN export_detail d ON d.receipt_id = r.id
JOIN product p ON p.id = d.product_id
WHERE r.status IN ('COMPLETED', 'APPROVED')
  AND r.receipt_date BETWEEN ? AND ?
GROUP BY r.receipt_date, p.id, p.sku
ORDER BY r.receipt_date, p.sku, p.id;
