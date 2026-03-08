/**
 * k6 Load Test for Payment Processing System
 *
 * Usage:
 *   1. Start the application: docker-compose up
 *   2. Install k6: https://k6.io/docs/getting-started/installation/
 *   3. Run: k6 run load-test.js
 *
 * Configuration:
 *   - VUs (virtual users): 50 concurrent users
 *   - Duration: 60 seconds
 *   - Thresholds: 95th percentile < 2s, error rate < 10%
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const purchaseErrors = new Counter('purchase_errors');
const purchaseSuccess = new Counter('purchase_success');
const errorRate = new Rate('error_rate');
const purchaseDuration = new Trend('purchase_duration', true);

export let options = {
  stages: [
    { duration: '10s', target: 10 },  // Ramp up
    { duration: '40s', target: 50 },  // Sustained load
    { duration: '10s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 95th percentile < 2s
    error_rate: ['rate<0.1'],            // Error rate < 10%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DEV_KEY = __ENV.DEV_KEY || 'dev-local-key';

function getToken() {
  let res = http.post(`${BASE_URL}/auth/token`,
    JSON.stringify({ developer_key: DEV_KEY }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status === 200) {
    return res.json('access_token');
  }
  return null;
}

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
    'X-Correlation-ID': `load-test-${__VU}-${__ITER}`,
    'Idempotency-Key': `load-${__VU}-${__ITER}-${Date.now()}`,
  };
}

export default function () {
  // Step 1: Get auth token
  let token = getToken();
  if (!token) {
    errorRate.add(1);
    purchaseErrors.add(1);
    return;
  }

  // Step 2: Purchase
  let purchaseRes = http.post(`${BASE_URL}/payments/purchase`,
    JSON.stringify({
      amount: (Math.random() * 100 + 1).toFixed(2),
      currency: 'USD',
      card: {
        number: '4111111111111111',
        expMonth: 12,
        expYear: 2030,
        cvv: '123'
      }
    }),
    { headers: authHeaders(token) }
  );

  let purchaseOk = check(purchaseRes, {
    'purchase status 201': (r) => r.status === 201,
    'purchase has orderId': (r) => r.json('orderId') !== undefined,
  });

  purchaseDuration.add(purchaseRes.timings.duration);

  if (purchaseOk) {
    purchaseSuccess.add(1);
    errorRate.add(0);
  } else {
    purchaseErrors.add(1);
    errorRate.add(1);
  }

  // Step 3: Health check (lightweight)
  let healthRes = http.get(`${BASE_URL}/payments/health`);
  check(healthRes, {
    'health status 200': (r) => r.status === 200,
  });

  sleep(0.5); // Pace requests
}

/**
 * Results Summary:
 * Run with: k6 run load-test.js
 * Or with custom URL: k6 run -e BASE_URL=http://my-server:8080 load-test.js
 *
 * Expected outputs:
 *   - purchase_success: total successful purchases
 *   - purchase_errors: total failed purchases
 *   - error_rate: percentage of failed requests
 *   - purchase_duration: latency distribution for purchase calls
 *   - http_req_duration: overall HTTP latency (p50, p90, p95, p99)
 */

