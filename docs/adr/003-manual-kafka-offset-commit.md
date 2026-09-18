# ADR-003: Manual Kafka offset commit for order-events consumption

## Status
Accepted

## Context
Payment Service consumes `OrderPlacedEvent` from `order-events` to
initiate payment processing. If Kafka auto-commits offsets on a fixed
interval (the default), a consumer crash between "offset committed" and
"payment record written" silently loses that order — there's no signal
that anything went wrong, and no redelivery.

## Decision
`enable-auto-commit: false`, `AckMode.MANUAL_IMMEDIATE`. The offset is only
acknowledged (`ack.acknowledge()`) after `PaymentService.initiate()` has
durably persisted the payment record. Errors are routed through
`DefaultErrorHandler` with a fixed 3-retry backoff, then to
`order-events.DLQ` via `DeadLetterPublishingRecoverer`.

## Consequences
**Positive:**
- No order can be silently dropped by a consumer crash — worst case, a
  restart replays the event and `initiate()` runs again (see Negative
  below for the dedup caveat).
- Failed events are inspectable in the DLQ topic rather than lost, with the
  original headers/exception context DeadLetterPublishingRecoverer attaches
  by default.

**Negative:**
- Redelivery on crash-after-process-before-ack can create a duplicate
  `INITIATED` payment row for the same order if the consumer dies in that
  exact window. This is called out directly in `OrderEventConsumer`'s
  Javadoc as "harmless in practice" for this event (an extra INITIATED
  row that never transitions further is inert), but a stricter deployment
  should add an idempotency key (e.g. upsert on `order_id` with a unique
  constraint) rather than relying on that being an acceptable trade-off
  indefinitely.
- Manual commit is slightly more throughput-limiting than auto-commit under
  very high message rates, since each partition's commit is a real
  round-trip rather than a background batch. Given Payment Service's
  workload (one event per order, not one per stock unit) this is not
  expected to be the bottleneck — Inventory Service's Redis Lua path is.
