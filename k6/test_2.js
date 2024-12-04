import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { randomItem } from './ramdomItem.js';

// Custom metrics
const errorRate = new Rate('errors');
const successRate = new Rate('success_rate');
const waitingTime = new Trend('waiting_time');

export const options = {
  stages: [
    { duration: '1m', target: 10 },    // Warm up with 10 users
    { duration: '2m', target: 20 },    // Ramp up to 20 users
    { duration: '5m', target: 20 },    // Stay at 20 for stability testing
    { duration: '2m', target: 30 },    // Stress test with 30 users
    { duration: '1m', target: 0 },     // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests should be below 500ms
    'http_req_duration{type:static}': ['p(95)<400'],  // Stricter threshold for static content
    errors: ['rate<0.01'],             // Less than 1% error rate
    success_rate: ['rate>0.95'],       // 95% success rate
    waiting_time: ['p(95)<400'],       // Connection waiting time threshold
  },
};

const BASE_URL = 'http://localhost:8080';

// Test data
const userIds = [1, 2, 3, 4, 5];
const postData = [
  { title: 'foo', body: 'bar', userId: 1 },
  { title: 'test', body: 'test body', userId: 2 }
];

// Helper function for response validation
function validateResponse(response, expectedStatus = 200) {
  const checks = {
    'status is correct': r => r.status === expectedStatus,
    'response time OK': r => r.timings.duration < 500,
    'content-type present': r => r.headers['Content-Type'] !== undefined,
  };

  const checkResult = check(response, checks);
  successRate.add(checkResult);
  errorRate.add(!checkResult);
  waitingTime.add(response.timings.waiting);

  return checkResult;
}

export default function () {
  group('Static Content Tests', function () {
    const staticResponse = http.get(`${BASE_URL}/get`);
    validateResponse(staticResponse);
    sleep(1);
  });

  group('API Integration Tests', function () {
    // GET requests
    group('GET Endpoints', function () {
      const responses = http.batch([
        ['GET', `${BASE_URL}/postman-echo/get?foo=bar`],
        ['GET', `${BASE_URL}/reqres/users/${randomItem(userIds)}`],
        ['GET', `${BASE_URL}/get?param=test`]
      ]);

      responses.forEach(response => validateResponse(response));
    });

    // POST requests
    group('POST Endpoints', function () {
      const payload = randomItem(postData);
      const postResponse = http.post(
        `${BASE_URL}/postman-echo/post`,
        JSON.stringify(payload),
        { headers: { 'Content-Type': 'application/json' } }
      );
      validateResponse(postResponse, 200);
    });

    // Error handling test
    group('Error Handling', function () {
      const notFoundResponse = http.get(`${BASE_URL}/status/404`);
      validateResponse(notFoundResponse, 404);

      const serverErrorResponse = http.get(`${BASE_URL}/status/500`);
      validateResponse(serverErrorResponse, 500);
    });
  });

  group('Performance Tests', function () {
    // Test with larger payload
    const largePayload = { data: 'x'.repeat(1000) };
    const largeResponse = http.post(
      `${BASE_URL}/postman-echo/post`,
      JSON.stringify(largePayload),
      { headers: { 'Content-Type': 'application/json' } }
    );
    validateResponse(largeResponse);

    // Rapid requests test
    const rapidResponses = http.batch([
      ['GET', `${BASE_URL}/get`],
      ['GET', `${BASE_URL}/get`],
      ['GET', `${BASE_URL}/get`]
    ]);
    rapidResponses.forEach(response => validateResponse(response));
  });

  sleep(random(1, 3)); // Random sleep between iterations
}

// Helper function for random sleep duration
function random(min, max) {
  return Math.random() * (max - min) + min;
}
