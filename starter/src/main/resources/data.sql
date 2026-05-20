INSERT INTO skus (sku_id, description) VALUES
    ('SKU-RED-MUG',  'Red ceramic mug 12oz'),
    ('SKU-BLUE-PEN', 'Blue ballpoint pen');

INSERT INTO bin_stock (bin_id, sku_id, quantity_on_hand, quantity_reserved, received_at) VALUES
    ('A-12-3', 'SKU-RED-MUG',  5, 0, DATE '2026-05-01'),
    ('A-12-4', 'SKU-RED-MUG',  2, 0, DATE '2026-05-10'),
    ('B-04-1', 'SKU-BLUE-PEN', 7, 0, DATE '2026-04-20'),
    ('C-09-2', 'SKU-BLUE-PEN', 4, 0, DATE '2026-05-05');
