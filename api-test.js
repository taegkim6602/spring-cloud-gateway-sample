const k6 = require('k6');
const { check, sleep, group } = require('k6');
const { Rate, Trend } = require('k6/metrics');

let errorRate = new Rate('errors');
let successRate = new Rate('success_rate');
let waitingTime = new Trend('waiting_time');

module.exports.options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '2m', target: 20 },
    { duration: '5m', target: 20 },
    { duration: '2m', target: 30 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    'http_req_duration{type:static}': ['p(95)<400'],
    errors: ['rate<0.01'],
    success_rate: ['rate>0.95'],
    waiting_time: ['p(95)<400'],
  },
};

// Mock server implementation
let mockServer = {
  get: function(url) {
    let timings = {
      waiting: Math.random() * 100,
      duration: Math.random() * 200
    };

    if (url.includes('/status/404')) {
      return {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ error: 'Not Found' }),
        timings: timings
      };
    }
    if (url.includes('/status/500')) {
      return {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ error: 'Server Error' }),
        timings: timings
      };
    }
    return {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ data: 'Mock response' }),
      timings: timings
    };
  },
  post: function(url, body) {
    let timings = {
      waiting: Math.random() * 100,
      duration: Math.random() * 200
    };
    return {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        data: 'Mock POST response',
        receivedBody: JSON.parse(body)
      }),
      timings: timings
    };
  },
  batch: function(requests) {
    return requests.map(function(req) {
      let method = req[0];
      let url = req[1];
      return method === 'GET' ? mockServer.get(url) : mockServer.post(url);
    });
  }
};

// Test data
let userIds = [1, 2, 3, 4, 5];
let postData = [
  { title: 'foo', body: 'bar', userId: 1 },
  { title: 'test', body: 'test body', userId: 2 }
];

function validateResponse(response, expectedStatus) {
  if (expectedStatus === undefined) {
    expectedStatus = 200;
  }
  let checks = {
    'status is correct': function(r) { return r.status === expectedStatus; },
    'response time OK': function(r) { return r.timings.duration < 500; },
    'content-type present': function(r) { return r.headers['Content-Type'] !== undefined; }
  };
  let checkResult = check(response, checks);
  successRate.add(checkResult);
  errorRate.add(!checkResult);
  waitingTime.add(response.timings.waiting);
  return checkResult;
}

function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

module.exports.default = function() {
  group('Static Content Tests', function() {
    let staticResponse = mockServer.get('/get');
    validateResponse(staticResponse);
    sleep(1);
  });

  group('API Integration Tests', function() {
    group('GET Endpoints', function() {
      let responses = mockServer.batch([
        ['GET', '/postman-echo/get?foo=bar'],
        ['GET', '/reqres/users/' + randomItem(userIds)],
        ['GET', '/get?param=test']
      ]);
      responses.forEach(function(response) {
        validateResponse(response);
      });
    });

    group('POST Endpoints', function() {
      let payload = randomItem(postData);
      let postResponse = mockServer.post(
        '/postman-echo/post',
        JSON.stringify(payload)
      );
      validateResponse(postResponse, 200);
    });

    group('Error Handling', function() {
      let notFoundResponse = mockServer.get('/status/404');
      validateResponse(notFoundResponse, 404);
      let serverErrorResponse = mockServer.get('/status/500');
      validateResponse(serverErrorResponse, 500);
    });
  });

  group('Performance Tests', function() {
    let largePayload = { data: new Array(1000).join('x') };
    let largeResponse = mockServer.post(
      '/postman-echo/post',
      JSON.stringify(largePayload)
    );
    validateResponse(largeResponse);

    let rapidResponses = mockServer.batch([
      ['GET', '/get'],
      ['GET', '/get'],
      ['GET', '/get']
    ]);
    rapidResponses.forEach(function(response) {
      validateResponse(response);
    });
  });

  sleep(random(1, 3));
}

function random(min, max) {
  return Math.random() * (max - min) + min;
}
