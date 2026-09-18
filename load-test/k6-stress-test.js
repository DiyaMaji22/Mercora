import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 500 },  // Ramp up to 500 concurrent users
    { duration: '30s', target: 500 },  // Stay at 500
    { duration: '10s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<500'], // 99% of requests must complete below 500ms
  },
};

export default function () {
  // Simulate catalog browsing
  const searchRes = http.get('http://localhost:8080/api/v1/products/search');
  check(searchRes, {
    'search status is 200': (r) => r.status === 200,
    'search returned products': (r) => JSON.parse(r.body).length > 0,
  });
  
  sleep(1);
}
