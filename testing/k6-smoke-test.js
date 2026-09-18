import http from 'k6/http';
import { check, sleep } from 'k6';

// Smoke test: verifies the core flash-sale purchase path (register -> login
// -> hold stock -> place order) works end-to-end under light load before
// running the heavier JMeter concurrency plan. Run with:
//   k6 run --vus 10 --duration 30s k6-smoke-test.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || 'aaaaaaaa-0000-0000-0000-000000000001';

export const options = {
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 500ms order threshold from the spec
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const email = `loadtest-${__VU}-${__ITER}-${Date.now()}@example.com`;

  // 1. Register
  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ email, password: 'password123', fullName: 'Load Test', role: 'CUSTOMER' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(registerRes, { 'register succeeded': (r) => r.status === 201 });

  const token = registerRes.json('accessToken');
  const authHeaders = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };

  // 2. Add to cart
  const addToCartRes = http.post(
    `${BASE_URL}/api/v1/cart/items`,
    JSON.stringify({ productId: PRODUCT_ID, quantity: 1 }),
    authHeaders
  );
  check(addToCartRes, { 'add to cart succeeded': (r) => r.status === 204 });

  sleep(0.5);

  // 3. Place order (this is the hot path: Redis Lua hold + Postgres write + Kafka publish)
  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, null, authHeaders);
  check(orderRes, {
    'order placed or correctly rejected for stock': (r) => r.status === 201 || r.status === 409,
  });

  sleep(1);
}
