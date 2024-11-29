import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Ramp up to 20 users
    { duration: '1m', target: 20 },  // Stay at 20 users for 1 minute
    { duration: '30s', target: 0 },  // Ramp down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    errors: ['rate<0.01'],            // Less than 1% of requests should fail
  },
};

const BASE_URL = 'http://localhost:8080'; // Using localhost since k6 runs outside Docker

export default function () {
  const requests = {
    'httpbin': {
      method: 'GET',
      url: `${BASE_URL}/get`,
      expect: 200,
    },
    'postman-echo': {
      method: 'GET',
      url: `${BASE_URL}/postman-echo/get`,
      expect: 200,
    },
    'reqres': {
      method: 'GET',
      url: `${BASE_URL}/reqres/users/2`,
      expect: 200,
    }
  };

  for (const [name, request] of Object.entries(requests)) {
    const response = http.request(request.method, request.url);
    
    const success = check(response, {
      [`${name} status is ${request.expect}`]: (r) => r.status === request.expect,
      [`${name} response time < 500ms`]: (r) => r.timings.duration < 500,
    });

    if (!success) {
      errorRate.add(1);
    }

    sleep(1);
  }
}
