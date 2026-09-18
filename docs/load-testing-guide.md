# Load Testing Guide

## What we're validating

The spec sets two hard numbers:
- **< 500ms** for order placement (p95/p99, not average)
- **10,000 orders/min** sustained throughput

And one correctness property that matters more than either number: **zero
oversells**, regardless of load.

## Test order

Run these roughly in this order — each builds confidence before the next,
heavier one:

### 1. Smoke test (`k6-smoke-test.js`)
Confirms the full register → cart → checkout path works at all, under
light load (10 VUs, 30s). Run this first after any deploy.

```bash
k6 run --vus 10 --duration 30s testing/k6-smoke-test.js
```

Look for: `http_req_failed` rate near 0%, `http_req_duration` p95 well
under 500ms (light load should be nowhere near the ceiling).

### 2. Race-condition test (`k6-race-condition-test.js`)
Hits Inventory Service's `/hold` endpoint directly with 300 concurrent
virtual users against a product seeded with 5 units of stock (or point
`PRODUCT_ID` at whatever pool size you're testing).

```bash
k6 run --vus 300 --iterations 300 testing/k6-race-condition-test.js
```

Look for: `hold_success` counter exactly equals the seeded stock quantity;
`hold_conflict` (409s) accounts for the rest; **zero 5xx responses** — a
5xx here would mean the Lua script itself errored, which should never
happen regardless of contention.

This is the network-level counterpart to the JUnit
`InventoryLuaScriptRaceConditionTest` (which proves the same property
in-process via Testcontainers) — running both matters because network
timing can surface races that an in-process test can't.

### 3. Full flash-sale plan (`jmeter-flash-sale-plan.jmx`)
10,000 threads ramped over 60 seconds, each hitting `/inventory/hold`
against a pool of 100 products (uniformly distributed), asserting both
"status is 200 or 409" and "response under 500ms" per request.

```bash
jmeter -n -t testing/jmeter-flash-sale-plan.jmx \
  -Jbase_url_host=localhost -Jbase_url_port=8080 \
  -l results.jtl -e -o report/
```

Open `report/index.html` for the dashboard. Look for:
- **Error %**: should be 0% for non-2xx/4xx (5xx = bug); 409s are expected
  and correct once stock for a given product is exhausted.
- **p95/p99 response time**: should stay under 500ms even as thread count
  ramps up. If it climbs steadily rather than plateauing, that's a signal
  the system is saturating somewhere (check Grafana — HikariCP pool
  exhaustion and Redis connection pool exhaustion are the two most likely
  culprits at this scale).
- **Throughput (requests/min)**: should sustain at or above 10,000/min once
  ramp-up completes.

## Where to look when a run doesn't hit the SLA

1. **Grafana → "Request rate by service"**: confirm the load is actually
   reaching Inventory Service and isn't being rejected earlier at the
   gateway's rate limiter (10k req/sec ceiling — if your test exceeds this,
   you'll see 429s, which is the gateway working as designed, not a bug).
2. **Grafana → "HikariCP active connections"**: if Order Service's pool is
   pegged at its max (30 by default — see `order-service/application.yml`),
   raise `DB_POOL_SIZE` or reduce concurrent request volume.
3. **Grafana → "Inventory circuit breaker state"**: if Order Service's
   circuit breaker to Inventory Service has tripped OPEN, checkout calls
   are failing fast via `InventoryClient.holdStockFallback` rather than
   queuing — check Inventory Service's own health/logs for what's actually
   slow or erroring there.
4. **Kafka consumer lag panel**: a growing lag on `order-events` means
   Payment Service can't keep up with order volume — this doesn't block
   the checkout SLA directly (Kafka publish is fire-and-forget from Order
   Service's perspective) but is worth watching if payments start visibly
   trailing orders.

## A note on realistic numbers in this environment

These tools were validated for *syntax* (the `.jmx` parses as well-formed
XML, the k6 scripts import and reference the expected APIs) but not run
against a live stack in this sandbox, which has no Docker daemon. Treat the
above as the procedure to run once you have the stack up via
`docker compose up`, not as results already gathered.
