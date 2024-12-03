import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const routeHits = new Counter('route_hits');
const routeLatency = new Trend('route_latency');

export const options = {
  stages: [
    { duration: '1m', target: 10 },    // Ramp up to 10 users
    { duration: '2m', target: 20 },    // Ramp up to 20 users
    { duration: '5m', target: 20 },    // Stay at 20 users
    { duration: '2m', target: 40 },    // Ramp up to 40 users
    { duration: '5m', target: 40 },    // Stay at 40 users
    { duration: '2m', target: 0 },     // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests should be below 500ms
    errors: ['rate<0.01'],             // Less than 1% error rate
    'route_latency{route:data_go_kr}': ['p(95)<600'],
    'route_latency{route:example}': ['p(95)<400'],
    'route_latency{route:postman_echo}': ['p(95)<450'],
    'route_latency{route:reqres}': ['p(95)<500'],
    'route_latency{route:test}': ['p(95)<400'],
  },
};

const BASE_URL = 'http://localhost:8080';

// Test data for dynamic routes
const testIds = [1, 2, 3, 4, 5];
const testEndpoints = ['posts', 'comments', 'albums', 'photos', 'todos'];

const routes = {
  'data_go_kr': {
    method: 'GET',
    url: `${BASE_URL}/data_get`,
    expect: 200,
    weight: 1,
  },
  'example': {
    method: 'GET',
    url: `${BASE_URL}/get`,
    expect: 200,
    weight: 2,
  },
  'postman_echo': {
    method: 'GET',
    url: `${BASE_URL}/postman-echo/get`,
    expect: 200,
    weight: 2,
  },
  'reqres': {
    method: 'GET',
    url: `${BASE_URL}/reqres/users/__ID__`,
    expect: 200,
    weight: 2,
    dynamic: true,
    idList: testIds,
  },
  'test': {
    method: 'GET',
    url: `${BASE_URL}/test/__ENDPOINT__`,
    expect: 200,
    weight: 2,
    dynamic: true,
    endpointList: testEndpoints,
  },
};

function getRandomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

function processRoute(routeName, route) {
  let url = route.url;
  
  if (route.dynamic) {
    if (route.idList) {
      url = url.replace('__ID__', getRandomItem(route.idList));
    }
    if (route.endpointList) {
      url = url.replace('__ENDPOINT__', getRandomItem(route.endpointList));
    }
  }

  const response = http.request(route.method, url, null, {
    tags: { route: routeName },
  });

  const success = check(response, {
    [`${routeName} status is ${route.expect}`]: (r) => r.status === route.expect,
    [`${routeName} response time < 500ms`]: (r) => r.timings.duration < 500,
  });

  // Record metrics
  routeHits.add(1, { route: routeName });
  routeLatency.add(response.timings.duration, { route: routeName });
  
  if (!success) {
    errorRate.add(1, { route: routeName });
    console.error(`${routeName} failed: ${response.status} - ${response.body}`);
  }

  // Variable sleep time based on route weight
  sleep(1 / route.weight);
}

export default function () {
  for (const [routeName, route] of Object.entries(routes)) {
    processRoute(routeName, route);
  }
}
