# Troubleshooting Guide

## docker-compose won't start / services keep restarting

**Check startup order first.** Services depend on Postgres/Redis/Kafka
being *healthy*, not just started — `docker-compose.yml` uses
`condition: service_healthy` for this, but healthchecks can still take
30-60s (especially Kafka). Run `docker compose ps` and confirm dependencies
show `healthy` before assuming a downstream service is actually broken.

**Flyway migration failures on inventory-service startup.** Only
inventory-service runs Flyway (`spring.flyway.enabled: true`); every other
service has it disabled and just validates against the existing schema
(`ddl-auto: validate`). If you see `ddl-auto: validate` failures on
user-service/order-service/etc. at startup, it almost always means
inventory-service hasn't finished migrating yet — check its logs first,
and make sure it started (and is healthy) before the others in a manual
`docker compose up` sequence.

## "relation does not exist" errors from a service other than inventory-service

Same root cause as above — that service started before inventory-service
finished applying V1-V3 migrations. In Compose this shouldn't happen given
the `depends_on: condition: service_healthy` chains, but if you're running
services individually outside Compose, start inventory-service first and
wait for its `/actuator/health` to report UP.

## Checkout returns 409 CONFLICT even though stock looks fine in the DB

Remember: **Redis, not PostgreSQL, is the live stock counter during
checkout.** PostgreSQL's `inventory.available_stock` is the durable
record, reconciled from Redis asynchronously via Kafka — it is not what
`/inventory/hold` reads. Check the actual Redis key:

```bash
docker exec -it ecommerce-redis redis-cli GET "stock:product:<productId>"
```

If that's 0 or missing, either it was never seeded, or an earlier hold
consumed it and hasn't been reconciled back to Postgres yet (expected — the
Kafka event might just not have landed yet under fast repeated testing).

## A hold seems "stuck" — stock never returns after a test order

The Redis key `hold:user:{userId}:product:{productId}` has a 5-minute TTL
and disappears on its own; check `TTL` on that key directly. If you're
testing rapidly and want to force-clear it:

```bash
docker exec -it ecommerce-redis redis-cli DEL "hold:user:<userId>:product:<productId>"
```

Note this does **not** return the deducted stock — only
`release_hold.lua` with `mode=RETURN` does that (see `POST
/api/v1/inventory/release` with `returnStock: true`, which is what the
checkout page's "Cancel hold" button calls, and what the order-cancel
endpoint calls internally).

## Kafka consumer lag keeps growing on `order-events`

Check Payment Service logs for repeated retries/DLQ publishes — a growing
lag usually means `OrderEventConsumer.onOrderPlaced` is throwing
(commonly: a transient DB connection issue) and every message is going
through the full 3-retry backoff before either succeeding or landing in
`order-events.DLQ`. Messages in the DLQ need manual reprocessing — there's
no automatic replay in this build; a natural follow-up is a scheduled job
or manual tool that re-publishes DLQ messages back to `order-events` after
the underlying issue is fixed.

## Frontend shows stock but it never updates live (StockIndicator stuck)

`StockIndicator` connects to `GET /api/v1/inventory/stream/{productId}`, an
SSE endpoint on Inventory Service (`InventoryController.stream`,
public/no-auth) that's fed by `InventoryEventSseRelay`, a Kafka listener
consuming the same `inventory-events` topic the retailer-update flow
publishes to. If updates aren't arriving:

1. Confirm the browser's connection actually opened — check the Network
   tab for a pending `stream/{productId}` request with type `eventsource`;
   if it 404s, you're likely hitting the gateway without the
   `/api/v1/inventory/stream` path in its public-path allowlist (see
   `JwtAuthGlobalFilter.PUBLIC_PATHS`) or without that route configured in
   `api-gateway/application.yml`.
2. Confirm `InventoryEventSseRelay` is actually consuming — check
   inventory-service logs for consumer group registration
   (`inventory-sse-relay-<random-uuid>`, one per instance, by design — see
   its Javadoc for why a fresh group ID per instance is intentional here).
3. Trigger a retailer stock update (`PUT /api/v1/inventory/{id}/stock`) and
   confirm an `InventoryUpdateEvent` lands on the `inventory-events` topic
   at all (`docker exec -it ecommerce-kafka kafka-console-consumer
   --bootstrap-server localhost:9092 --topic inventory-events
   --from-beginning`) before assuming the SSE leg is the problem.

## `mvn test` fails immediately with "no such module" or similar

Make sure you're running Maven from inside `backend/`, and that the parent
`pom.xml`'s `<modules>` list matches the services present — if you've
deleted or renamed a service directory without updating
`backend/pom.xml`, the reactor build fails at the aggregation step before
any tests run.

## JWT validation fails across services in a fresh environment

All services validate JWTs with the same HMAC secret
(`jwt.secret` / `JWT_SECRET` env var) — **User Service issues the token,
every other service (and the gateway) must be configured with the exact
same secret to validate it.** `docker-compose.yml` sets this from a single
`${JWT_SECRET:-...}` default so they stay in sync automatically in local
dev; if you're overriding it per-service in Kubernetes, double check
`ecommerce-secrets`' `JWT_SECRET` is referenced identically by every
Deployment's `envFrom`.
