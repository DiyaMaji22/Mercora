# ADR-002: Optimistic locking (not pessimistic) for retailer stock updates

## Status
Accepted

## Context
Retailers and admins can correct stock levels directly against PostgreSQL
(the `inventory` table's `available_stock` column). Two concurrent updates
to the same product need to be handled without corrupting the count.

## Decision
Use JPA's `@Version` optimistic locking (`Inventory.version`,
`Payment.version`) rather than pessimistic row locks (`SELECT ... FOR
UPDATE`).

## Consequences
**Positive:**
- A slow or stuck writer never blocks other writers — there's no lock to
  hold, so no risk of one hung transaction stalling every other retailer's
  update.
- Fits the deadlock-prevention strategy documented in
  `InventoryService`'s class Javadoc: the three subsystems (Redis Lua,
  PostgreSQL, Kafka) never hold a lock in one while waiting on another,
  which is the classic shape that produces distributed deadlocks.
- Conflicts are rare in practice (a single retailer updating their own
  product, occasionally racing an admin correction) so the cost of a retry
  is low.

**Negative:**
- Under genuinely high contention on the *same row* (not expected for this
  use case — retailer updates are low-frequency compared to checkout
  traffic, which never touches PostgreSQL synchronously at all), optimistic
  locking degrades to repeated retries rather than queuing. We bound this
  with `@Retryable(maxAttempts = 3)` and surface a 409 Conflict to the
  client if all retries are exhausted, rather than retrying indefinitely.
- Requires every write path to go through the entity's `save()` (so
  Hibernate can enforce the version check) — a raw `UPDATE` SQL statement
  would silently bypass it. We rely on code review / the repository
  pattern to enforce this rather than a database-level guarantee.
