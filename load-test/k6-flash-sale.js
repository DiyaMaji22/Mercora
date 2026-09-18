import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '10s', target: 2000 }, // Ramp up to 2000 concurrent users
    { duration: '30s', target: 2000 }, // Hold steady at 2000 users
    { duration: '10s', target: 0 },    // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(99)<250'], // 99% of requests must complete within 250ms
    http_req_failed: ['rate<0.01'],  // Less than 1% errors allowed
  },
};

export default function () {
  // Simulate a flash sale by blasting the inventory hold endpoint with random user IDs
  const payload = JSON.stringify({
    productId: "aaaaaaaa-0000-0000-0000-000000000001", // Demo SKU-001
    userId: uuidv4(),
    quantity: 1
  });

  const headers = { 'Content-Type': 'application/json' };

  const res = http.post('http://localhost:8080/api/v1/inventory/hold', payload, { headers });
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'hold successful or handled properly': (r) => {
       try {
         const body = JSON.parse(r.body);
         // Once the 10,000 unit stock is exhausted, it gracefully returns INSUFFICIENT_STOCK
         return body.success === true || body.code === 'INSUFFICIENT_STOCK';
       } catch (e) { 
         return false; 
       }
    }
  });

  // Small delay to simulate realistic think time and prevent saturating the local testing machine's CPU
  sleep(0.1); 
}
