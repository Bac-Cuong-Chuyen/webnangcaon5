SELECT
    source.code AS from_code,
    destination.code AS to_code,
    SUM(CAST(d.quantity AS BIGINT)) AS total_quantity
FROM stock_transfer t
JOIN transfer_detail d ON d.transfer_id = t.id
JOIN warehouse source ON source.id = t.from_warehouse_id
JOIN warehouse destination ON destination.id = t.to_warehouse_id
WHERE t.status = 'COMPLETED'
  AND t.transfer_date BETWEEN ? AND ?
GROUP BY source.id, source.code,
         destination.id, destination.code
ORDER BY source.code, destination.code,
         source.id, destination.id;
