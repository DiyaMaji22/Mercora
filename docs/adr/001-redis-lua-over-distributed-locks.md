# ADR-001: Redis Lua scripts instead of a distributed lock for stock holds

## Status
Accepted

## Context
The flash-sale checkout path needs to check available stock and reserve it
atomically, under heavy concurrent load (up to 10,000 req/sec at the
gateway). Two common approaches:

1. A distributed lock (e.g. Redlock, or a DB row lock) around a
   read-check-write sequence.
2. A single atomic operation that does the check-and-deduct server-side.

## Decision
We use Redis Lua scripts (`hold_stock.lua`, `release_hold.lua`,
`bulk_lock.lua`) executed via `EVALSHA`. Redis executes Lua scripts
single-threaded and atomically — no other command interleaves mid-script —
so the check-and-deduct happens as one indivisible unit without an explicit
lock/unlock pair at all.

## Consequences
**Positive:**
- No lock-acquisition latency or contention — the script either succeeds
  or fails in one round trip.
- No risk of a client crashing while holding a lock (no lock to leak).
- Naturally fast: this is exactly the workload Redis is built for.

**Negative:**
- Business logic now lives partly in Lua, which is a less common skill on
  most teams than application code — mitigated by keeping the scripts
  small, heavily commented, and covered by the race-condition test.
- Redis Cluster deployment requires the keys a single script touches
  (`stock:product:{id}` and `hold:user:{userId}:product:{id}`) to hash to
  the same slot. We call this out explicitly in
  `docker-compose.yml`'s Redis service comment — a real cluster deployment
  should use hash tags, e.g. `stock:{productId}` and
  `hold:{productId}:user:{userId}`, so both keys land on the same slot.
- Redis becomes a stronger consistency dependency than a pure cache would
  be. We accept this and treat PostgreSQL as the durable source of truth
  that Redis is reconciled against asynchronously (see ADR-002).
