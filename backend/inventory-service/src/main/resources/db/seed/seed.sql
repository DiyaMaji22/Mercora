-- Seed data for local development / manual testing.
-- NOT a Flyway migration - run explicitly:
--   docker exec -i ecommerce-postgres psql -U ecommerce -d ecommerce < seed.sql

INSERT INTO users (id, email, password_hash, full_name, role) VALUES
  ('11111111-1111-1111-1111-111111111111', 'admin@shop.test', '$2a$10$replace.with.bcrypt.hash', 'Admin One', 'ADMIN'),
  ('22222222-2222-2222-2222-222222222222', 'retailer@shop.test', '$2a$10$replace.with.bcrypt.hash', 'Retailer One', 'RETAILER'),
  ('33333333-3333-3333-3333-333333333333', 'customer@shop.test', '$2a$10$replace.with.bcrypt.hash', 'Customer One', 'CUSTOMER'),
  ('44444444-4444-4444-4444-444444444444', 'bulk@shop.test', '$2a$10$replace.with.bcrypt.hash', 'Bulk Buyer One', 'BULK_BUYER')
ON CONFLICT DO NOTHING;

INSERT INTO products (id, sku, name, description, category, price, retailer_id) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', 'SKU-001', 'Wireless Headphones', 'Noise-cancelling over-ear headphones', 'Electronics', 89.99, '22222222-2222-2222-2222-222222222222'),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'SKU-002', 'Running Shoes', 'Lightweight trail running shoes', 'Footwear', 64.50, '22222222-2222-2222-2222-222222222222'),
  ('aaaaaaaa-0000-0000-0000-000000000003', 'SKU-003', 'Espresso Machine', 'Compact home espresso machine', 'Home', 249.00, '22222222-2222-2222-2222-222222222222')
ON CONFLICT DO NOTHING;

INSERT INTO inventory (product_id, available_stock, reserved_stock) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', 100, 0),
  ('aaaaaaaa-0000-0000-0000-000000000002', 5, 0),
  ('aaaaaaaa-0000-0000-0000-000000000003', 20, 0)
ON CONFLICT DO NOTHING;
