# API Contracts

All requests go through the gateway at `http://localhost:8080` (or your
ingress host in production). Direct service ports are listed for local
debugging only.

## User Service (`:8081`)

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/auth/register` | none | `{email, password, fullName, role}` → `role` ∈ `ADMIN\|CUSTOMER\|RETAILER\|BULK_BUYER` |
| POST | `/api/v1/auth/login` | none | `{email, password}` |
| GET | `/api/v1/auth/me` | Bearer | — |

Both `register` and `login` return
`{accessToken, tokenType: "Bearer", expiresInSeconds, email, role}`.

## Product Service (`:8083`)

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/products` | Bearer, ADMIN\|RETAILER | `{sku, name, description, category, price}` |
| GET | `/api/v1/products/{id}` | none | — (cached, 5-min TTL) |
| PUT | `/api/v1/products/{id}` | Bearer, ADMIN\|RETAILER | partial `{name?, description?, category?, price?}` |
| DELETE | `/api/v1/products/{id}` | Bearer, ADMIN\|RETAILER | — (soft delete / deactivate) |
| GET | `/api/v1/products/search?q=&category=` | none | — |

## Inventory Service (`:8082`)

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/inventory/hold` | none* | `{productId, userId, quantity}` — atomic Lua checkout hold, 5-min TTL |
| POST | `/api/v1/inventory/bulk-lock` | none* | `{productId, userId, quantity}` — atomic Lua bulk lock, 2h TTL |
| POST | `/api/v1/inventory/release` | none* | `{productId, userId, quantity, returnStock}` |
| PUT | `/api/v1/inventory/{productId}/stock?quantity=N` | Bearer, ADMIN\|RETAILER | — retailer correction, optimistic lock |

\* Identity is carried in the request body rather than the JWT for these
three, since Order Service (not the end user's browser) is the direct
caller in the checkout flow. The gateway still requires a valid session to
reach `/release`/`/bulk-lock`'s callers upstream; `/hold` and `/release`
are on the gateway's public-path allowlist specifically so Order Service's
server-to-server calls don't need to forward a user's token.

## Order Service (`:8084`)

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/cart/items` | Bearer | `{productId, quantity}` |
| GET | `/api/v1/cart` | Bearer | — |
| DELETE | `/api/v1/cart/items/{productId}` | Bearer | — |
| POST | `/api/v1/orders` | Bearer | — places order from server-side cart |
| GET | `/api/v1/orders/{orderId}` | Bearer | — |
| GET | `/api/v1/orders/mine` | Bearer | — |
| POST | `/api/v1/orders/{orderId}/cancel` | Bearer | — |

`PlaceOrderResponse`: `{orderId, status, totalAmount, items[], createdAt, holdExpiresAt}`.

### Bulk orders (Order Service, `:8084`)

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/bulk-orders` | Bearer, BULK_BUYER\|ADMIN | `{productId, quantity}` — places a 2h Redis lock via Inventory Service, then records a `PENDING_APPROVAL` row |
| GET | `/api/v1/bulk-orders/mine` | Bearer, BULK_BUYER\|ADMIN | — |
| GET | `/api/v1/bulk-orders?status=PENDING_APPROVAL` | Bearer, ADMIN | — |
| POST | `/api/v1/bulk-orders/{id}/approve` | Bearer, ADMIN | — consumes the lock (stock stays deducted) |
| POST | `/api/v1/bulk-orders/{id}/reject` | Bearer, ADMIN | — returns the held stock |

## Payment Service (`:8085`)

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/payments/webhook` | none (would be signature-verified) | `{providerReference, status}` |
| GET | `/api/v1/payments/order/{orderId}` | Bearer | — |

## AI Service (`:8000`)

| Method | Path | Body |
|---|---|---|
| POST | `/recommendations` | `{user_id, limit?}` |
| POST | `/dynamic-price` | `{product_id, base_price, current_stock, views_last_hour?, purchases_last_hour?}` |
| POST | `/fraud-check` | `{order_id, user_id, amount, item_count, account_age_days, shipping_country?, billing_country?, orders_last_24h?}` |
| GET | `/health` | — |

Interactive Swagger UI is available at `http://localhost:8000/docs`
(FastAPI generates this automatically from the Pydantic models in
`app/models/schemas.py`).

## Error format

All Java services return errors as:
```json
{"timestamp": "...", "status": 409, "error": "Conflict", "message": "..."}
```
