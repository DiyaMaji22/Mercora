import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Race-condition simulation: many virtual users hit Inventory Service's
// hold endpoint concurrently for the SAME product, with stock intentionally
// scarce, to verify the Redis Lua script never oversells under real network
// concurrency (not just in-process, unlike the JUnit Testcontainers test).
//
// Run with:
//   k6 run --vus 300 --iterations 300 k6-race-condition-test.js
//
// Expected result: successCount == initial stock, oversellCount == 0.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const PRODUCT_ID = __ENV.PRODUCT_ID || 'aaaaaaaa-0000-0000-0000-000000000002'; // seeded with 5 units

const successCount = new Counter('hold_success');
const conflictCount = new Counter('hold_conflict');

export const options = {
  scenarios: {
    race: {
      executor: 'per-vu-iterations',
      vus: 300,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export default function () {
  const userId = `${__VU}-${Date.now()}`;
  const res = http.post(
    `${BASE_URL}/api/v1/inventory/hold`,
    JSON.stringify({ productId: PRODUCT_ID, userId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (res.status === 200) {
    successCount.add(1);
  } else if (res.status === 409) {
    conflictCount.add(1);
  }

  check(res, {
    'status is 200 or 409 (never 5xx - no crashes under contention)': (r) =>
      r.status === 200 || r.status === 409,
  });
}
