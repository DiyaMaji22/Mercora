-- V2__indexes.sql

CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_products_retailer ON products(retailer_id);
CREATE INDEX IF NOT EXISTS idx_products_active ON products(is_active) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_inventory_product ON inventory(product_id);

CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id, order_created_at);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items(product_id);

CREATE INDEX IF NOT EXISTS idx_bulk_orders_user ON bulk_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_bulk_orders_status ON bulk_orders(status);

CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id, order_created_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_provider_ref ON payments(provider_reference)
    WHERE provider_reference IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email);
