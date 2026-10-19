-- DU LIEU GIA LAP CHO DO HIEU NANG BUOI 09, KHONG PHAI KET QUA TEST DUYET PHIEU.
-- Nap sau data.sql va b09-seed.sql. Chi them du lieu co tien to B09.
-- Khong tao index hay cache: can giu nen truoc toi uu.
-- Moi san pham: nhap 100, xuat 20, chuyen 10 A -> B, ton A=70 va B=10.
-- Cac actor B09-SEED-* la nhan lich su gia lap, khong phai tai khoan dang nhap.

-- Chi muc nay chi thuoc bang TAM phuc vu nap, khong tao index tren bang nghiep vu.
CREATE LOCAL TEMPORARY TABLE b09_seed_map (
    product_id BIGINT PRIMARY KEY,
    suffix VARCHAR(5) UNIQUE,
    sequence_number INT
);
INSERT INTO b09_seed_map (product_id, suffix, sequence_number)
SELECT id, SUBSTRING(sku, 6), CAST(SUBSTRING(sku, 6) AS INT)
FROM product
WHERE sku BETWEEN 'B09-P00001' AND 'B09-P05000' AND LENGTH(sku) = 10;

INSERT INTO warehouse (code, name, address)
SELECT 'B09-WH-A', 'Kho nguon B09', 'Dia chi gia lap A'
WHERE NOT EXISTS (SELECT 1 FROM warehouse WHERE code = 'B09-WH-A');

INSERT INTO warehouse (code, name, address)
SELECT 'B09-WH-B', 'Kho dich B09', 'Dia chi gia lap B'
WHERE NOT EXISTS (SELECT 1 FROM warehouse WHERE code = 'B09-WH-B');

INSERT INTO import_receipt
    (receipt_code, receipt_date, status, warehouse_id, created_by, approved_by)
SELECT 'B09-I' || p.suffix,
       DATEADD('DAY', MOD(p.sequence_number - 1, 15), DATE '2026-10-01'),
       'APPROVED', w.id, 'B09-SEED-CREATOR', 'B09-SEED-APPROVER'
FROM b09_seed_map p
JOIN warehouse w ON w.code = 'B09-WH-A'
WHERE 'B09-I' || p.suffix NOT IN (
    SELECT receipt_code FROM import_receipt WHERE receipt_code IS NOT NULL
);

INSERT INTO import_detail (receipt_id, product_id, quantity)
SELECT r.id, p.product_id, 100
FROM import_receipt r
JOIN b09_seed_map p ON p.suffix = SUBSTRING(r.receipt_code, 6)
WHERE r.receipt_code LIKE 'B09-I%'
AND r.id NOT IN (SELECT receipt_id FROM import_detail WHERE receipt_id IS NOT NULL);

INSERT INTO export_receipt
    (receipt_code, receipt_date, status, warehouse_id, created_by, approved_by)
SELECT 'B09-E' || p.suffix,
       DATEADD('DAY', MOD(p.sequence_number - 1, 15), DATE '2026-10-01'),
       'APPROVED', w.id, 'B09-SEED-CREATOR', 'B09-SEED-APPROVER'
FROM b09_seed_map p
JOIN warehouse w ON w.code = 'B09-WH-A'
WHERE 'B09-E' || p.suffix NOT IN (
    SELECT receipt_code FROM export_receipt WHERE receipt_code IS NOT NULL
);

INSERT INTO export_detail (receipt_id, product_id, quantity)
SELECT r.id, p.product_id, 20
FROM export_receipt r
JOIN b09_seed_map p ON p.suffix = SUBSTRING(r.receipt_code, 6)
WHERE r.receipt_code LIKE 'B09-E%'
AND r.id NOT IN (SELECT receipt_id FROM export_detail WHERE receipt_id IS NOT NULL);

INSERT INTO stock_transfer
    (transfer_code, transfer_date, status, from_warehouse_id, to_warehouse_id,
     created_by, approved_by)
SELECT 'B09-T' || p.suffix,
       DATEADD('DAY', MOD(p.sequence_number - 1, 15), DATE '2026-10-01'),
       'COMPLETED', a.id, b.id, 'B09-SEED-CREATOR', 'B09-SEED-APPROVER'
FROM b09_seed_map p
JOIN warehouse a ON a.code = 'B09-WH-A'
JOIN warehouse b ON b.code = 'B09-WH-B'
WHERE 'B09-T' || p.suffix NOT IN (
    SELECT transfer_code FROM stock_transfer WHERE transfer_code IS NOT NULL
);

INSERT INTO transfer_detail (transfer_id, product_id, quantity)
SELECT t.id, p.product_id, 10
FROM stock_transfer t
JOIN b09_seed_map p ON p.suffix = SUBSTRING(t.transfer_code, 6)
WHERE t.transfer_code LIKE 'B09-T%'
AND t.id NOT IN (SELECT transfer_id FROM transfer_detail WHERE transfer_id IS NOT NULL);

INSERT INTO inventory (warehouse_id, product_id, quantity, minimum_quantity)
SELECT w.id, p.product_id, 70, 5
FROM b09_seed_map p
JOIN warehouse w ON w.code = 'B09-WH-A'
WHERE (w.id, p.product_id) NOT IN (
    SELECT warehouse_id, product_id FROM inventory
    WHERE warehouse_id IS NOT NULL AND product_id IS NOT NULL
);

INSERT INTO inventory (warehouse_id, product_id, quantity, minimum_quantity)
SELECT w.id, p.product_id, 10, 5
FROM b09_seed_map p
JOIN warehouse w ON w.code = 'B09-WH-B'
WHERE (w.id, p.product_id) NOT IN (
    SELECT warehouse_id, product_id FROM inventory
    WHERE warehouse_id IS NOT NULL AND product_id IS NOT NULL
);

DROP TABLE b09_seed_map;
