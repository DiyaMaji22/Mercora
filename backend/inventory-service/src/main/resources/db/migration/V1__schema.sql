-- V1__schema.sql
-- Core schema shared conceptually across services. Each microservice owns
-- and migrates only the tables it's responsible for; this file documents
-- the full picture and inventory-service applies the tables it needs.
-- (In a stricter per-service-DB deployment, split this per service schema.)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER', 'RETAILER', 'BULK_BUYER')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    retailer_id UUID REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL UNIQUE REFERENCES products(id),
    available_stock INT NOT NULL DEFAULT 0 CHECK (available_stock >= 0),
    reserved_stock INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED','FULFILLED')),
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL,
    order_created_at TIMESTAMPTZ NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL,
    FOREIGN KEY (order_id, order_created_at) REFERENCES orders(id, created_at)
);

CREATE TABLE IF NOT EXISTS bulk_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','EXPIRED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at TIMESTAMPTZ,
    decided_by UUID REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL,
    order_created_at TIMESTAMPTZ NOT NULL,
    provider_reference VARCHAR(255),
    status VARCHAR(32) NOT NULL CHECK (status IN ('INITIATED','SUCCEEDED','FAILED','REFUNDED')),
    amount NUMERIC(12,2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (order_id, order_created_at) REFERENCES orders(id, created_at)
);
