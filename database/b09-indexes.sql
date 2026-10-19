-- Chi muc cho loc trang thai/ngay va noi phieu-chi tiet cua ba bao cao.
-- Khong thay doi du lieu, khong thay doi cau SQL bao cao.
CREATE INDEX IF NOT EXISTS idx_b09_import_status_date
    ON import_receipt (status, receipt_date, id);
CREATE INDEX IF NOT EXISTS idx_b09_import_detail_receipt
    ON import_detail (receipt_id, product_id, quantity);
CREATE INDEX IF NOT EXISTS idx_b09_export_status_date
    ON export_receipt (status, receipt_date, id);
CREATE INDEX IF NOT EXISTS idx_b09_export_detail_receipt
    ON export_detail (receipt_id, product_id, quantity);
CREATE INDEX IF NOT EXISTS idx_b09_transfer_status_date
    ON stock_transfer (status, transfer_date, id);
CREATE INDEX IF NOT EXISTS idx_b09_transfer_detail_transfer
    ON transfer_detail (transfer_id, product_id, quantity);
