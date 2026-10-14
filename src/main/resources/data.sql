INSERT INTO warehouse (code, name, address)
SELECT 'WH01', 'Kho Hà Nội', 'Hà Nội'
WHERE NOT EXISTS (
    SELECT 1 FROM warehouse WHERE code = 'WH01'
);

INSERT INTO product (name, price, sku, unit)
SELECT 'Laptop Dell', 15000000, 'DELL01', 'Cái'
WHERE NOT EXISTS (
    SELECT 1 FROM product WHERE sku = 'DELL01'
);

INSERT INTO suppliers (name)
SELECT 'Nhà cung cấp ABC'
WHERE NOT EXISTS (
    SELECT 1 FROM suppliers WHERE name = 'Nhà cung cấp ABC'
);