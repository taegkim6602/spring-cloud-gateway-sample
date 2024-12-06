import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

// Custom metrics
const errorRate = new Rate("errors");

// Test configuration
export const options = {
  scenarios: {
    gateway_load_test: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 1000 }, // Ramp up to 1000 users
        { duration: "10m", target: 1000 }, // Stay at 1000 for 4 minutes
        { duration: "30s", target: 0 }, // Ramp down to 0
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_duration: ["p(95)<10000"], // 95% of requests should be below 10s
    errors: ["rate<0.7"], // Error rate should be below 70%
  },
};

const BASE_URL = "http://localhost:80"; // Nginx load balancer endpoint

// Helper function to generate random numbers
function getRandomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// Main test function
export default function () {
  let currentEndpoint = "";
  let response;

  try {
    const random = Math.random();

    if (random < 0.2) {
      // Test data.go.kr route (20% of traffic)
      currentEndpoint = "data_go_kr";
      response = http.get(`${BASE_URL}/data_get`);
    } else if (random < 0.4) {
      // Test reqres route (20% of traffic)
      currentEndpoint = "reqres_users";
      response = http.get(`${BASE_URL}/reqres/users/${getRandomInt(1, 10)}`);
    } else if (random < 0.6) {
      // Test postman echo (20% of traffic)
      currentEndpoint = "postman_echo";
      response = http.get(`${BASE_URL}/postman-echo/get?test=1`);
    } else if (random < 0.8) {
      // Test example route (20% of traffic)
      currentEndpoint = "example_route";
      response = http.get(`${BASE_URL}/get`);
    } else {
      // Test test route (20% of traffic)
      currentEndpoint = "test_posts";
      response = http.get(`${BASE_URL}/test/posts/${getRandomInt(1, 100)}`);
    }

    // Basic checks for all endpoints
    const checkRes = check(response, {
      "status is 200": (r) => r.status === 200,
      "response time < 2000ms": (r) => r.timings.duration < 2000,
    });

    // Additional endpoint-specific checks
    if (checkRes && response.status === 200) {
      try {
        const jsonData = response.json();

        if (currentEndpoint === "data_go_kr") {
          check(jsonData, {
            "data_go_kr response has data": (obj) =>
              obj && obj.hasOwnProperty("data"),
          });
        } else if (currentEndpoint === "reqres_users") {
          check(jsonData, {
            "reqres response has user data": (obj) =>
              obj && obj.hasOwnProperty("data"),
          });
        }
      } catch (parseError) {
        console.log(
          `Failed to parse JSON for ${currentEndpoint}: ${parseError.message}`,
        );
        errorRate.add(1);
      }
    }

    // Record errors
    errorRate.add(!checkRes);

    // Add some randomized sleep time between requests (100-300ms)
    sleep(Math.random() * 0.2 + 0.1);
  } catch (err) {
    console.error(`Request failed for ${currentEndpoint}: ${err}`);
    errorRate.add(1);
  }
}
