-- Optional, safe synthetic demo data for StockPilot.
-- Run manually AFTER Flyway migrations have created the schema, e.g.:
--   mysql -u stockpilot -p stockpilot < seed-demo-data.sql
--
-- Demo login (password is the same for both accounts): Password123!
--   Owner: owner@demo.stockpilot.test
--   Staff: staff@demo.stockpilot.test
--
-- The password hash below is a bcrypt hash of "Password123!" and is safe to publish -
-- it is demo-only and not used anywhere else.

INSERT INTO users (email, password_hash, full_name) VALUES
  ('owner@demo.stockpilot.test', '$2b$10$a.y3kLdRNFD0VZzeSHUEnemn9S5mLdk8fEgusy/ryuxsLhzwUe7uK', 'Dana Owner'),
  ('staff@demo.stockpilot.test', '$2b$10$a.y3kLdRNFD0VZzeSHUEnemn9S5mLdk8fEgusy/ryuxsLhzwUe7uK', 'Sam Staff');

INSERT INTO workspaces (name, slug) VALUES ('Demo Hardware Co', 'demo-hardware-co');

INSERT INTO workspace_memberships (workspace_id, user_id, role)
SELECT w.id, u.id, 'OWNER' FROM workspaces w, users u
WHERE w.slug = 'demo-hardware-co' AND u.email = 'owner@demo.stockpilot.test';

INSERT INTO workspace_memberships (workspace_id, user_id, role)
SELECT w.id, u.id, 'STAFF' FROM workspaces w, users u
WHERE w.slug = 'demo-hardware-co' AND u.email = 'staff@demo.stockpilot.test';

INSERT INTO products (workspace_id, name, sku, description, unit_price, stock_quantity, low_stock_threshold, active)
SELECT w.id, v.name, v.sku, v.description, v.unit_price, v.stock_quantity, v.low_stock_threshold, TRUE
FROM workspaces w
JOIN (
  SELECT 'Hex Bolt M8x40' AS name, 'BOLT-M8-40' AS sku, 'Zinc-plated steel hex bolt' AS description, 0.35 AS unit_price, 500 AS stock_quantity, 50 AS low_stock_threshold
  UNION ALL SELECT 'Cordless Drill 18V', 'DRILL-18V', 'Brushless cordless drill, battery included', 89.99, 12, 3
  UNION ALL SELECT 'Safety Goggles', 'SAFE-GOG-01', 'Impact-resistant safety goggles', 6.50, 8, 10
  UNION ALL SELECT 'Measuring Tape 5m', 'TAPE-5M', '5 meter retractable measuring tape', 4.25, 40, 5
  UNION ALL SELECT 'Work Gloves (Pair)', 'GLOVE-L', 'Large leather work gloves', 11.00, 0, 5
) v ON TRUE
WHERE w.slug = 'demo-hardware-co';
