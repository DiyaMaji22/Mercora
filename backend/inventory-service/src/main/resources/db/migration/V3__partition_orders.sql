-- V3__partition_orders.sql
-- orders is RANGE-partitioned by created_at (see V1). Pre-create partitions
-- for the current month and the following two months; a scheduled DBA job /
-- pg_partman (recommended for prod) should keep rolling this window forward.

CREATE TABLE IF NOT EXISTS orders_2026_08 PARTITION OF orders
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

CREATE TABLE IF NOT EXISTS orders_2026_09 PARTITION OF orders
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

CREATE TABLE IF NOT EXISTS orders_2026_10 PARTITION OF orders
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- Catch-all partition so inserts outside the pre-created window don't fail
-- outright while the rolling-partition job catches up.
CREATE TABLE IF NOT EXISTS orders_default PARTITION OF orders DEFAULT;
