# Mercora — Architecture Audit & Scalability Plan

**Verified against codebase at: `backend/` (Java 17, Spring Boot 3.2.5), `frontend/` (Next.js 14.2.35), `docker-compose.yml`**

---

## Part 1 — Gap Analysis (ranked by risk)

### GAP-1 · Java 17 blocks Virtual Threads [CRITICAL]
**What:** All 5 service Dockerfiles use `maven:3.9-eclipse-temurin-17` and `eclipse-temurin:17-jre-alpine`. The parent `pom.xml` pins `<java.version>17</java.version>` with matching `compiler.source`/`compiler.target`.

**Why it matters:** Virtual Threads (Project Loom, GA in Java 21, further optimised in Java 25 LTS) are the primary mechanism for handling 250k concurrent requests on a blocking JDBC/servlet stack without exhausting OS threads. Tomcat on Java 17 defaults to a shared thread pool bounded well below 250k; every blocked JDBC call holds an OS thread.

**Trade-off:** Java 25 LTS is not yet released (current LTS is 21; 25 is targeted for Sep 2025). Use 21 now; migrate to 25 when GA and the `eclipse-temurin:25-jre-alpine` image is available.

**Risk if skipped:** The entire 250k-request target is impossible without this change. The system will saturate Tomcat threads under moderate flash-sale load.

---

### GAP-2 · Synchronous `.block()` on Tomcat threads in the hot checkout path [CRITICAL]
**What:** Verified in `order-service/src/main/java/.../order/client/InventoryClient.java` and `ProductPriceClient.java`. Both use `WebClient.block()` to issue blocking HTTP calls to `inventory-service` and `product-service` respectively. These calls happen inside `OrderService.placeOrder()` which runs on a Tomcat request thread.

**Why it matters:** On Java 17 with OS threads, each in-flight `placeOrder` request freezes a Tomcat thread for the full RTT to two downstream services. Under flash-sale concurrency, 250k simultaneous calls = 250k blocked OS threads = OOM or thread starvation crash.

**Trade-off:** Upgrading to Java 21+ with `spring.threads.virtual.enabled=true` makes these `.block()` calls yield their carrier thread rather than parking an OS thread, which eliminates the crisis. No code change is required for the calls themselves under that model. Alternatively, re-architecting `placeOrder` to emit a Kafka event and return `202 Accepted` is the fully async path but requires an idempotent, compensating flow.

**Risk if skipped:** Even with virtual threads, `.block()` inside a `@Transactional` boundary on the same thread is a correctness risk if the carrier thread pool is exhausted by pinning (e.g., synchronised blocks in JDBC drivers).

---

### GAP-3 · MVC `SseEmitter` holds a Tomcat thread per connected client [HIGH]
**What:** Confirmed in `inventory-service/src/main/java/.../inventory/controller/InventoryController.java` (returns `SseEmitter`) and `InventoryEventBroadcaster.java` (stores `SseEmitter` instances in a `ConcurrentHashMap`).

**Why it matters:** Each `/api/v1/inventory/stream/{productId}` subscriber occupies a Tomcat thread for up to 30 minutes (configured timeout: `30 * 60 * 1000L` ms in `InventoryEventBroadcaster`). If the product detail page of a viral flash-sale item attracts 50k concurrent viewers, 50k Tomcat threads are held open, completely crowding out checkout traffic on the same service.

**Trade-off:** Migrating to a WebFlux/Netty SSE endpoint (`Flux<ServerSentEvent>`) eliminates this. A single Netty event-loop thread can multiplex thousands of open SSE connections. The broadcaster changes from `SseEmitter` to a `Sinks.Many<ServerSentEvent>` per product.

**Risk if skipped:** SSE subscribers will starve the thread pool before checkout volume reaches meaningful scale.

---

### GAP-4 · HikariCP pool too small relative to concurrency target [HIGH]
**What:** Verified across all `application.yml` files:
- `user-service`: `maximum-pool-size: ${DB_POOL_SIZE:20}`
- `inventory-service`: `maximum-pool-size: ${DB_POOL_SIZE:20}`
- `product-service`: `maximum-pool-size: ${DB_POOL_SIZE:20}`
- `payment-service`: `maximum-pool-size: ${DB_POOL_SIZE:20}`
- `order-service`: `maximum-pool-size: ${DB_POOL_SIZE:30}`

Summed: 110 maximum DB connections from 5 services sharing one Postgres instance. PostgreSQL's default `max_connections` is 100. The system will fail to acquire connections before hitting the configured pool ceiling.

**Why it matters:** JDBC connection acquisition blocks. With virtual threads, a blocked JDBC call yields its carrier thread but still waits in HikariCP's queue. If queue depth is exhausted, the request throws `SQLTransientConnectionException` with a 30-second timeout (configured in inventory-service).

**Trade-off:** Simply raising `maximum-pool-size` past Postgres's `max_connections` causes Postgres to refuse connections. The correct fix is a connection multiplexer (PgBouncer in transaction-pooling mode) between the services and Postgres. PgBouncer exposes a large frontend pool to the services while maintaining a small backend pool against Postgres.

**Risk if skipped:** Postgres crashes under connection saturation before 250k requests is approached.

---

### GAP-5 · API Gateway rate limiter caps throughput below the stated target [HIGH]
**What:** Verified in `api-gateway/src/main/resources/application.yml`:
```yaml
redis-rate-limiter.replenishRate: 10000
redis-rate-limiter.burstCapacity: 20000
```
Applied to every route. The burst ceiling of 20,000 req/s is a hard wall. Any traffic exceeding it receives HTTP 429 at the gateway before reaching a service.

**Why it matters:** The goal is 250k concurrent requests. Depending on request duration, throughput rate and concurrency are related but not equal — however, a 20k/s ceiling will saturate in well under one second at peak flash-sale load.

**Risk if skipped:** The system appears to fail at the gateway, masking whether backend improvements are working.

---

### GAP-6 · Single-node Kafka with replication factor 1 [HIGH]
**What:** `docker-compose.yml` configures one Kafka broker with `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1` and `KAFKA_AUTO_CREATE_TOPICS_ENABLE: true`.

**Why it matters:** A single Kafka broker is a single point of failure for the entire event-driven pipeline: `OrderPlacedEvent` (order → payment), `InventoryUpdateEvent` (inventory → SSE relay). If the broker restarts mid-flash-sale, all in-flight order and inventory events are lost.

**Trade-off:** In local dev, acceptable. In production, minimum 3 brokers with replication factor 3 and `min.insync.replicas=2`.

**Risk if skipped:** Silent event loss during Kafka restarts/upgrades corrupts the order→payment flow with no dead-letter recovery.

---

### GAP-7 · Single-node Redis with no AOF/RDB persistence [MEDIUM]
**What:** `docker-compose.yml` uses `redis:7-alpine` with no persistence flags. The `InventoryRedisConfig` in inventory-service uses `READFROM.REPLICA_PREFERRED` which has no effect on standalone mode (it silently degrades to master reads). All stock hold state (`stock:product:*`, `hold:user:*:product:*`, `bulk:lock:*`) is volatile.

**Why it matters:** A Redis restart during a flash sale clears all active holds, allowing oversell after recovery when Postgres is used to re-initialise stock.

**Risk if skipped:** Oversell is possible after any Redis failure. The Lua atomic guarantees are intact during operation but offer no crash recovery.

---

### GAP-8 · JWT secret defaults to a hardcoded dev value [MEDIUM]
**What:** Every service `application.yml` defaults to `change-this-dev-only-secret-min-32-bytes-long!`. The `docker-compose.yml` uses `${JWT_SECRET:-change-this-dev-only-secret-min-32-bytes-long!}`. If `JWT_SECRET` is not set in the production environment, this secret ships.

**Why it matters:** Tokens signed with a known secret can be forged by anyone. The gateway's `JwtAuthGlobalFilter` and each service's `JwtAuthFilter` both use this key.

**Risk if skipped:** Complete auth bypass in any deployment where the environment variable is not explicitly set.

---

### GAP-9 · `product-service` search falls back to `findAll()` [MEDIUM]
**What:** Fixed earlier in this session — `ProductService.search()` now calls `productRepository.findAll()` when no query/category is given. This is a full table scan returning all active products on every page load of `/products`.

**Why it matters:** As the product catalogue grows, this becomes a progressively more expensive unbounded query. At 250k concurrent users browsing the catalogue, this saturates the product-service DB connection pool instantly.

**Trade-off:** Elasticsearch (already in the stack) is the correct solution. Seeds should sync to ES via `ProductService.syncToSearchIndex()` which already exists. Products added via raw SQL bypass this sync. The fix is either a startup indexer that bootstraps ES from Postgres, or a paginated default query with a sane limit.

---

### GAP-10 · No refresh token / token revocation [LOW]
**What:** `user-service/AuthService.java` issues only an access token with a configurable TTL (`JWT_EXPIRATION_SECONDS`, default 3600s). There is no refresh token, no token revocation list, and no rotation mechanism.

**Why it matters:** A compromised token is valid for its full TTL with no recourse. At 1-hour TTL, a stolen token gives 60 minutes of unauthorized access.

---

### GAP-11 · Frontend Node dependencies are not on latest stable versions [LOW]
**What:** Confirmed in `frontend/package.json`:
- `next`: `14.2.35` (Next.js 15.x is current)
- `react` / `react-dom`: `^18.3.1` (React 19 is current stable)
- `zustand`: `^4.5.4` (5.x is current)
- `axios`: `^1.7.2`
- `tailwindcss`: `^3.4.4` (4.x is current)
- `typescript`: `^5.5.3`
- `eslint`: `^8.57.0` (9.x is current)
- `@types/node`: `^20.14.9`

**Risk if skipped:** Security advisories in older packages; missing performance improvements in Next.js 15's enhanced React Server Components compiler and Turbopack.

---

## Part 2 — Target Architecture

```
                          ┌──────────────────────────────────────────┐
                          │           Load Balancer / CDN             │
                          │    (static assets cached at edge)        │
                          └──────────────────┬───────────────────────┘
                                             │
                          ┌──────────────────▼───────────────────────┐
                          │          API Gateway (Netty/WebFlux)      │
                          │  JWT edge validation · Redis rate limiter │
                          │  Circuit breaker (inventory-service)      │
                          └──┬───────┬───────┬───────┬───────┬───────┘
                             │       │       │       │       │
              ┌──────────────▼─┐ ┌───▼───┐ ┌▼─────┐ ┌▼────┐ ┌▼──────────┐
              │  user-service  │ │product│ │order │ │pay  │ │inventory  │
              │  (Tomcat + VT) │ │service│ │svc   │ │svc  │ │service    │
              │  BCrypt ·  JWT │ │(Tomcat│ │(Tomcat│ │(Tomcat│(Tomcat + VT│
              │  Postgres JDBC │ │+ VT)  │ │+ VT) │ │+ VT)│ │Lua/Redis  │
              └────────┬───────┘ └───┬───┘ └──┬───┘ └──┬──┘ │SSE→WebFlux│
                       │             │         │        │    └─────┬─────┘
                       │         ┌───▼──┐  ┌───▼──┐    │          │
                       │         │Redis │  │Redis │    │     ┌────▼────┐
                       │         │Cache │  │Cart  │    │     │  Redis  │
                       │         │(5min │  │(Hash)│    │     │ Cluster │
                       │         │ TTL) │  └──────┘    │     │stock/   │
                       │         └──────┘               │     │hold keys│
                       │                                │     └─────────┘
                    ┌──▼────────────────────────────────▼──┐
                    │         PgBouncer (transaction mode)  │
                    │  frontend pool: 1000  backend: 80     │
                    └──────────────────┬────────────────────┘
                                       │
                              ┌────────▼────────┐
                              │   PostgreSQL 15  │
                              │  (primary + 1    │
                              │   read replica)  │
                              └─────────────────┘

                    ┌─────────────────────────────────────┐
                    │              Apache Kafka             │
                    │  3 brokers · RF=3 · MIR=2            │
                    │  Topics: order-events inventory-events│
                    └─────────────────────────────────────┘
```

**Interaction contracts:**
- Gateway → services: HTTP/1.1. JWT claims forwarded as `X-User-Id`, `X-User-Email` headers.
- `order-service` → `inventory-service`: WebClient POST (blocking under virtual threads; no reactive chain).
- `order-service` → `product-service`: WebClient GET for price lookup (same pattern).
- `inventory-service`: Lua scripts are the single writer to Redis stock/hold keys. Kafka publishes events post-write. SSE relay listens on Kafka and pushes to WebFlux subscribers.
- `payment-service`: Pure Kafka consumer on `order-events`. Writes INITIATED record immediately, processes payment asynchronously.

---

## Part 3 — Migration Strategy

### Phase 1 — Java 21 + Virtual Threads (highest leverage, low risk)

**Files to change:**

`backend/pom.xml`:
```xml
<java.version>21</java.version>
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

Every `backend/*/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
FROM eclipse-temurin:21-jre-alpine
```

Every `backend/*/src/main/resources/application.yml` (all 5 services):
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Rollback:** Revert Dockerfile and pom.xml. Virtual threads are opt-in; disabling them requires only setting `spring.threads.virtual.enabled=false`.

**Validation:** Run `docker exec <service> jcmd 1 Thread.print | grep "VirtualThread"` after startup. Under load, OS thread count should remain bounded while JVM thread count climbs.

**Note on Java 25 LTS:** Java 25 is currently pre-release. Migrate to 25 when `eclipse-temurin:25-jre-alpine` is published and the Spring Boot 3.x compatibility matrix confirms support. The code changes are identical to the Java 21 step above.

---

### Phase 2 — SSE refactor: MVC SseEmitter → WebFlux Sink

**Scope:** `inventory-service` only.

The existing `InventoryEventBroadcaster` maintains a `Map<UUID, List<SseEmitter>>`. Replace with a `Map<UUID, Sinks.Many<ServerSentEvent<InventoryUpdateEvent>>>`. The `InventoryController.stream()` method returns `Flux<ServerSentEvent>` instead of `SseEmitter`.

This is an isolated change within `inventory-service`. Spring MVC and WebFlux can coexist on the same Tomcat when `spring-boot-starter-webflux` is added alongside `spring-boot-starter-web` — which is already the case in `inventory-service/pom.xml` (WebFlux is present for its reactor types). However, the SSE endpoint must be registered as a reactive endpoint.

**Trade-off:** If MVC and WebFlux coexist and a reactive controller is registered, Spring Boot's auto-configuration may switch the web application type to reactive, breaking the existing MVC endpoints. The cleanest path is to keep the MVC controller for all write endpoints and create a `@RestController` that is explicitly reactive only for the SSE endpoint using `RouterFunction<ServerResponse>`.

**Rollback:** Revert `InventoryEventBroadcaster` and `InventoryController.stream()`.

---

### Phase 3 — PgBouncer

Add to `docker-compose.yml`:
```yaml
pgbouncer:
  image: bitnami/pgbouncer:latest
  environment:
    POSTGRESQL_HOST: postgres
    POSTGRESQL_PORT: 5432
    POSTGRESQL_USERNAME: ecommerce
    POSTGRESQL_PASSWORD: ecommerce
    POSTGRESQL_DATABASE: ecommerce
    PGBOUNCER_POOL_MODE: transaction
    PGBOUNCER_MAX_CLIENT_CONN: 1000
    PGBOUNCER_DEFAULT_POOL_SIZE: 80
  depends_on:
    postgres:
      condition: service_healthy
```

Update all service `DB_URL` environment variables from `postgres:5432` to `pgbouncer:5432`.

**Trade-off:** Transaction-pooling mode means Postgres session-level features (advisory locks, `SET LOCAL`, `LISTEN/NOTIFY`) do not work across transactions. This system uses `@Version` optimistic locking and Lua-based Redis atomics — neither uses Postgres session features. Safe to use transaction mode.

**Rollback:** Point `DB_URL` back to `postgres:5432`.

---

### Phase 4 — Gateway rate limiter ceiling

In `api-gateway/src/main/resources/application.yml`, raise rate limits to the expected peak:
```yaml
redis-rate-limiter.replenishRate: 250000
redis-rate-limiter.burstCapacity: 300000
```

Or, more precisely, tune per-route based on actual service SLOs rather than a single global ceiling.

**Rollback:** Revert the YAML values.

---

### Phase 5 — Kafka production hardening

- Replace single Zookeeper + Kafka broker with a 3-broker cluster using KRaft mode (Zookeeper-free, available in Confluent 7.4+).
- Set `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3`.
- Configure producers with `acks=all` and `enable.idempotence=true` in all services.
- Add dead-letter topics for `order-events` and `inventory-events`.

**Rollback:** N/A in production; maintain separate dev and prod compose files.

---

### Phase 6 — Redis persistence and cluster

- Enable `appendonly yes` + `appendfsync everysec` on the Redis service.
- For production: replace `RedisStandaloneConfiguration` with `RedisClusterConfiguration` in all three `RedisConfig.java` files. Update Lua key naming to use hash tags (e.g., `{productId}:stock`, `{productId}:hold:{userId}`) to ensure script-touched keys land on the same cluster slot.

---

### Phase 7 — Frontend dependency upgrades

Target versions (latest stable at audit date):
```json
{
  "next": "15.x",
  "react": "^19.0.0",
  "react-dom": "^19.0.0",
  "zustand": "^5.0.0",
  "axios": "^1.x (latest patch)",
  "tailwindcss": "^4.0.0",
  "typescript": "^5.x (latest)",
  "eslint": "^9.x",
  "@types/node": "^22.x",
  "@types/react": "^19.x",
  "@types/react-dom": "^19.x"
}
```

**Migration notes:**
- Next.js 15 changes the `params` prop to be async in Server Components and adds Turbopack by default. Audit all `params` usages in `app/` directory.
- React 19 deprecates some legacy APIs. Run `npx codemod react-19` before upgrading.
- Tailwind 4 is a complete configuration rewrite — CSS-first config replaces `tailwind.config.js`.
- Zustand 5 introduces breaking changes in store creation API.
- ESLint 9 uses a flat config format (`eslint.config.js`), replacing `.eslintrc`.

---

## Part 4 — Operational Plan

### SLOs

| Endpoint | P99 Latency | Error Rate |
|---|---|---|
| `POST /api/v1/inventory/hold` | < 50ms | < 0.1% |
| `POST /api/v1/orders/checkout` | < 500ms | < 0.5% |
| `GET /api/v1/products/search` | < 100ms | < 0.1% |
| `GET /api/v1/inventory/stream/*` | connection established < 200ms | < 0.01% |

### Capacity assumptions

- **Peak concurrent users:** 250,000
- **Read/write ratio:** 90% reads (product browse, stock check), 10% writes (holds, orders)
- **Peak checkout rate during flash sale:** ~5,000 orders/second
- **Redis throughput required:** ~5,000 Lua executions/second per product (within single-node Redis's 100k ops/sec headroom)
- **Postgres write TPS required:** ~5,000 INSERT/s on `orders` + `order_items` (requires PgBouncer + possible Postgres read replica for reads)

### Scaling triggers

- **CPU > 70% sustained for 2 minutes** on any service → scale out (horizontal pod autoscaling in Kubernetes; increase replica count in Docker Swarm).
- **HikariCP `pool.WaitTime` P99 > 500ms** → add PgBouncer replicas or increase backend pool size.
- **Redis memory > 70% of allocated** → expand cluster or purge expired keys.
- **Kafka consumer lag > 10,000 messages** on `order-events` → scale payment-service consumers.

### Failure modes and mitigations

| Failure | Detection | Mitigation |
|---|---|---|
| Redis crash | Health check; hold requests return 5xx | Redis AOF recovery restores hold state; expired holds trigger scheduler-based cleanup |
| `inventory-service` down | Circuit breaker trips (configured in `order-service/application.yml`: 50% failure rate, 20-call window) | Gateway returns `503` from `/fallback/inventory`; checkout is blocked |
| Kafka broker failure (single-node) | Consumer lag alarm | Single-node: payment events lost; production (Phase 5): automatic leader re-election within 30s |
| Postgres primary failure | HikariCP timeout; `SQLTransientConnectionException` | With PgBouncer + read replica: read traffic continues; writes queue until failover |
| JWT secret not set | First login attempt fails with 500 | Pre-deployment check in CI: assert `JWT_SECRET` env var is set and length ≥ 32 bytes |

### Observability

Already in the stack:
- Prometheus scraping all services via `/actuator/prometheus` (confirmed in all `application.yml` files).
- Grafana on port 3001.
- Zipkin on port 9411 for distributed tracing.

Missing:
- **Structured logging**: Services use Logback default format. Add `logstash-logback-encoder` to emit JSON logs and ship to a log aggregator (Loki, Elasticsearch).
- **JVM Virtual Thread metrics**: After Phase 1, expose `jvm.thread.daemon` and `jvm.thread.peak` via Micrometer to track carrier thread pool pressure.
- **Kafka consumer lag metrics**: Add `kafka-lag-exporter` sidecar or use Confluent's JMX metrics to alert on consumer group lag.
