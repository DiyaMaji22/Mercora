INSERT INTO users (id, email, password_hash, full_name, role) VALUES
  ('55555555-5555-5555-5555-555555555555', 'neon@shop.test', '$2a$10$replace.with.bcrypt.hash', 'Neon Styles', 'RETAILER'),
  ('66666666-6666-6666-6666-666666666666', 'retro@shop.test', '$2a$10$replace.with.bcrypt.hash', 'Retro Tech', 'RETAILER')
ON CONFLICT DO NOTHING;

INSERT INTO products (id, sku, name, description, category, price, retailer_id) VALUES
  ('55555555-0000-0000-0000-000000000001', 'NEON-JKT', 'Cyber-Jacket', 'A glowing neon jacket with built-in LEDs.', 'Apparel', 199.99, '55555555-5555-5555-5555-555555555555'),
  ('55555555-0000-0000-0000-000000000002', 'NEON-SNK', 'Cyber-Sneakers', 'High-top sneakers with customizable neon soles.', 'Footwear', 129.50, '55555555-5555-5555-5555-555555555555'),
  ('55555555-0000-0000-0000-000000000003', 'NEON-VSR', 'Cyber-Visor', 'A futuristic glowing visor for the ultimate cyberpunk look.', 'Accessories', 49.99, '55555555-5555-5555-5555-555555555555'),
  ('66666666-0000-0000-0000-000000000001', 'RETRO-CAS', 'Retro Cassette Player', 'A sleek 1980s style portable cassette player.', 'Electronics', 85.00, '66666666-6666-6666-6666-666666666666'),
  ('66666666-0000-0000-0000-000000000002', 'RETRO-ARC', 'Retro Arcade Cabinet', 'Miniature wooden arcade cabinet with classic games.', 'Gaming', 299.99, '66666666-6666-6666-6666-666666666666'),
  ('66666666-0000-0000-0000-000000000003', 'RETRO-CRT', 'Retro CRT Monitor', 'A classic vintage beige CRT computer monitor from the 1990s.', 'Electronics', 150.00, '66666666-6666-6666-6666-666666666666')
ON CONFLICT DO NOTHING;

INSERT INTO inventory (product_id, available_stock, reserved_stock) VALUES
  ('55555555-0000-0000-0000-000000000001', 50, 0),
  ('55555555-0000-0000-0000-000000000002', 200, 0),
  ('55555555-0000-0000-0000-000000000003', 75, 0),
  ('66666666-0000-0000-0000-000000000001', 30, 0),
  ('66666666-0000-0000-0000-000000000002', 10, 0),
  ('66666666-0000-0000-0000-000000000003', 5, 0)
ON CONFLICT DO NOTHING;
