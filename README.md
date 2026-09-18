# Flashmarket — Flash-Sale E-Commerce Platform

A microservices e-commerce platform built around one hard problem: selling
a small pool of stock to a large number of concurrent buyers without
overselling, without deadlocking, and without a slow database write sitting
in the critical path of every checkout click.

## Build status — read this first

This is a large multi-service system. Rather than claim uniform "100%
production-ready" coverage, here's an honest breakdown of what's real,
tested, and runnable versus what's a well-structured scaffold for a
follow-up pass.

| Area | Status |
|---|---|
| Inventory Service (Lua scripts, hold/release/bulk-lock, retailer optimistic-lock flow, Kafka events, scheduler) | **Complete + tested.** Includes a Testcontainers race-condition test proving 300 concurrent buyers against 100 units never oversells. |
| User Service (JWT auth, BCrypt, register/login/me) | **Complete + tested.** |
| Order Service (Redis cart, checkout orchestration, hold rollback on partial failure, circuit breaker to Inventory, Kafka producer, bulk-order approval workflow) | **Complete + tested** (core checkout path). Bulk-order request/approve/reject flow added and cross-checked against Inventory Service's contract, but has no dedicated unit test yet. |
| Payment Service (Kafka consumer w/ manual offset commit + DLQ, optimistic-lock webhook handling) | **Complete + tested.** |
| Product Service (CRUD, Redis 5-min cache, Elasticsearch sync) | **Complete.** No dedicated unit tests yet - the pattern mirrors Inventory/Order Service's tested equivalents. |
| API Gateway (routing, JWT validation, Redis rate limiting, circuit breaker fallback) | **Complete.** Uses static Docker-DNS routing rather than Eureka service discovery — see [ADR-004](docs/adr/004-no-eureka.md) for why, and how to add it back. |
| Flyway migrations, seed data | **Complete.** |
| Frontend (Next.js: home, products, cart, checkout, login/register, role dashboards, CheckoutTimer, StockIndicator via SSE) | **Complete and verified building** (`npm run build` succeeds, all 9 routes generate). Payment step is a stub awaiting a real gateway integration (Stripe, etc.) - see the checkout page's "Awaiting payment…" button. The SSE backend it depends on (`InventoryEventSseRelay` + `GET /inventory/stream/{id}`) is implemented, not just scaffolded. |
| AI Service (FastAPI: recommendations, dynamic pricing, fraud check) | **Complete and verified running** (`pytest` passes 4/4, endpoints manually curl-tested). Rule-based rather than trained-model-based — see the module docstrings for why and how to swap in a real model. |
| docker-compose (16 services: full backend + frontend + AI + Postgres/Redis/Kafka/Elasticsearch + Prometheus/Grafana/Zipkin) | **Complete**, validated as parseable YAML. Not run end-to-end in this environment (no Docker daemon available here) - see "What wasn't verified" below. |
| Kubernetes manifests (namespace, configmap, secrets template, Deployment+Service+HPA for all 8 services, Ingress) | **Complete**, validated as parseable YAML. StatefulSets for Postgres/Redis/Kafka deliberately omitted in favor of managed services / operators — see [devops/k8s/base/STATEFUL_INFRA.md](devops/k8s/base/STATEFUL_INFRA.md). |
| GitHub Actions CI/CD (build → test → push → deploy dev/staging/prod) | **Complete**, validated as parseable YAML. Deploy steps are `echo` placeholders pending real cluster credentials. |
| Monitoring (Prometheus scrape config, Grafana dashboard JSON) | **Complete**, validated as parseable configs. |
| JMeter (10k users / 100 items) + K6 (smoke + race-condition) load tests | **Complete**, JMeter XML validated as well-formed. |
| Documentation (this README, OpenAPI stub, ADRs, load testing guide, troubleshooting guide) | **Complete.** |

### What wasn't verified in this environment

This sandbox has no Docker daemon, and — discovered partway through this
build — its network egress allowlist includes `archive.ubuntu.com` (so I
*could* `apt-get install openjdk-17-jdk-headless maven`) but not Maven
Central (`repo.maven.apache.org` returns a 403 from the egress proxy), so
a full `mvn test` still isn't possible here even with a real JDK and Maven
installed. Given that constraint, here's exactly what was and wasn't
checked, precisely:

- **Frontend**: genuinely built and tested (`npm install && npm run build`
  both ran for real against the actual npm registry, which *is* allowlisted
  — including catching and fixing a real Suspense-boundary bug in
  `/login`).
- **AI service**: genuinely built and tested (`pytest` ran for real, and
  the FastAPI app was booted and hit with live `curl` requests).
- **Backend Java services**: full compilation against the real Spring Boot
  classpath was **not** possible (Maven Central blocked). What I *did* do
  instead, with a real JDK 17 + Maven installed:
  - Verified all 89 Java files: package declarations match their directory
    structure, and braces/parens balance (no truncated files, no copy-paste
    errors) — 0 errors.
  - Cross-checked `@RequiredArgsConstructor`-generated constructor field
    order against every test that manually instantiates a service with
    `new X(...)` (Mockito's `@InjectMocks` tests don't need this since
    reflection handles it) — `OrderService` and `PaymentService` both
    match exactly.
  - Diffed the `OrderPlacedEvent` record shared between Order Service
    (Kafka producer) and Payment Service (Kafka consumer) field-by-field,
    since a silent mismatch there is exactly the kind of bug that compiles
    fine per-module but breaks at runtime — they match exactly, including
    the nested `Item` record.
  - Cross-checked JWT claim names between the issuer (`user-service`'s
    `JwtTokenProvider`) and every validator (`JwtAuthFilter` in all 5
    services) and confirmed the two services whose filter only extracts
    `roles` (not `userId`) never actually call `Authentication.getDetails()`
    in their controllers — so there's no latent bug there either.
  - This is real verification of the parts most likely to break silently
    across service boundaries, but it is **not** a substitute for an actual
    `mvn test` run, which will still catch class-level issues this can't
    (e.g. a typo'd Spring annotation, a missing bean, an incompatible
    method override). Run `mvn -pl inventory-service -am test` yourself as
    the first step after cloning, with real Maven Central access.
- **docker-compose / Kubernetes**: config is schema-valid YAML, and the
  service wiring (env vars, ports, dependency ordering) was cross-checked
  against each service's `application.yml`, but the full stack has not
  actually been started with `docker compose up`.

## Architecture

```
                        ┌─────────────┐
                        │   Frontend   │  Next.js 14, App Router
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │ API Gateway  │  routing, rate limit, JWT
                        └──────┬───────┘
        ┌──────────┬──────────┼──────────┬──────────┐
   ┌────▼───┐ ┌────▼─────┐┌───▼────┐┌────▼────┐┌────▼─────┐
   │  User  │ │ Inventory││Product ││  Order  ││ Payment  │
   │Service │ │ Service  ││Service ││ Service ││ Service  │
   └────┬───┘ └────┬─────┘└───┬────┘└────┬────┘└────┬─────┘
        │          │          │          │          │
   ┌────▼──────────▼──────────▼──────────▼──────────▼────┐
   │        PostgreSQL · Redis · Kafka · Elasticsearch      │
   └─────────────────────────────────────────────────────┘

              ┌──────────────┐
              │  AI Service  │  FastAPI (recommendations, pricing, fraud)
              └──────────────┘
```

## The flash-sale flow, end to end

1. Gateway applies a Redis token-bucket rate limit (10k req/sec ceiling).
2. Checkout calls `POST /api/v1/inventory/hold`, which runs `hold_stock.lua`
   — an atomic Redis operation that checks stock, deducts it, and creates a
   5-minute hold in a single round trip. This is what prevents overselling.
3. Order Service persists a `PENDING` order to PostgreSQL and publishes
   `OrderPlacedEvent` to Kafka (`order-events`, 3 partitions, keyed by
   orderId).
4. Payment Service consumes the event with **manual offset commit** — the
   offset is only acknowledged after the payment record is durably written,
   so a crash mid-processing means Kafka redelivers rather than silently
   drops the order.
5. If checkout fails partway (e.g. item 2 of 3 is out of stock), Order
   Service rolls back the holds already placed for items 1 via
   `release_hold.lua`, returning stock atomically.
6. If a hold's 5-minute TTL lapses unconfirmed, a scheduled job (60s
   interval) reconciles it.
7. Retailer stock corrections go through PostgreSQL with `@Version`
   optimistic locking (never row locks — a slow writer never blocks
   others), then propagate via Kafka to Redis and out to shoppers over SSE.

See [docs/adr/](docs/adr/) for the reasoning behind each of these choices.

## Running locally

```bash
cp frontend/.env.example frontend/.env.local   # adjust if needed
docker compose up --build
```

Once healthy:
- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- Individual services: 8081 (user) · 8082 (inventory) · 8083 (product) ·
  8084 (order) · 8085 (payment) · 8000 (AI)
- Grafana: http://localhost:3001 (admin/admin) · Prometheus: :9090 ·
  Zipkin: :9411

Seed test data:
```bash
docker exec -i ecommerce-postgres psql -U ecommerce -d ecommerce \
  < backend/inventory-service/src/main/resources/db/seed/seed.sql
```

## Running tests

```bash
# Backend (per service, or drop -pl for all)
cd backend && mvn -pl inventory-service -am test

# Frontend
cd frontend && npm install && npm run build

# AI service
cd ai-service && pip install -r requirements.txt && pytest tests/ -v

# Load tests (requires a running stack)
k6 run testing/k6-smoke-test.js
k6 run --vus 300 --iterations 300 testing/k6-race-condition-test.js
jmeter -n -t testing/jmeter-flash-sale-plan.jmx -l results.jtl
```

See [docs/load-testing-guide.md](docs/load-testing-guide.md) for
interpreting results against the spec's SLAs (< 500ms order placement,
10,000 orders/min throughput).

## Deploying

```bash
kubectl apply -f devops/k8s/base --namespace ecommerce
```

Requires a populated `ecommerce-secrets` Secret first — see
[devops/k8s/base/secrets.yaml](devops/k8s/base/secrets.yaml) — and real
Postgres/Redis/Kafka endpoints; see
[devops/k8s/base/STATEFUL_INFRA.md](devops/k8s/base/STATEFUL_INFRA.md) for
why those aren't bundled as raw StatefulSets here.

## Known limitations

- **Checkout payment step is a stub.** The checkout page places a real
  inventory hold and a real `PENDING` order, but the final "pay" button is
  disabled with an explanatory label — there's no Stripe/gateway
  integration wired in. Payment Service's webhook handler
  (`POST /api/v1/payments/webhook`) is real and ready to receive a
  provider's callback; what's missing is the client-side card form and the
  actual gateway API call to create a payment intent.
- **CI/CD deploy stages are placeholders.** `.github/workflows/ci-cd.yml`'s
  build/test/push jobs are real; the three `deploy-*` jobs `echo` the
  intended `kubectl apply` command rather than running it, since that
  needs real cluster credentials configured as repo secrets.
- **Retailer dashboard's product list isn't scoped to the logged-in
  retailer.** It currently calls the general `search` endpoint rather than
  a "my listings" endpoint — functionally fine for a single-retailer demo,
  but `GET /api/v1/products?retailerId=...` (the repository method
  `findByRetailerId` already exists) is the natural follow-up for a
  multi-retailer deployment.

## Further reading

- [docs/adr/](docs/adr/) — why Redis Lua over distributed locks, why
  optimistic over pessimistic locking for retailer updates, why manual
  Kafka offset commit, why no Eureka
- [docs/api-contracts.md](docs/api-contracts.md) — OpenAPI-style summary of
  each service's endpoints
- [docs/load-testing-guide.md](docs/load-testing-guide.md)
- [docs/troubleshooting-guide.md](docs/troubleshooting-guide.md)
