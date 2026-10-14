SELECT w.id AS warehouse_id, w.code, i.quantity
FROM warehouse w
LEFT JOIN inventory i ON i.warehouse_id = w.id AND i.product_id = 2
WHERE w.id IN (3, 4)
ORDER BY w.id;

SELECT status, COUNT(*) AS receipt_count
FROM stock_transfer
WHERE LEFT(transfer_code, LENGTH('B06_SAME_4a4663e82a')) = 'B06_SAME_4a4663e82a'
GROUP BY status ORDER BY status;

SELECT COUNT(*) AS detail_count, COALESCE(SUM(d.quantity), 0) AS total_transferred
FROM transfer_detail d JOIN stock_transfer t ON t.id = d.transfer_id
WHERE LEFT(t.transfer_code, LENGTH('B06_SAME_4a4663e82a')) = 'B06_SAME_4a4663e82a'
AND t.status = 'COMPLETED';
