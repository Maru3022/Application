/**
 * Shared configuration for all k6 load test scenarios.
 *
 * Usage:
 *   BASE_URL=https://staging.healthlife.com k6 run scenarios/smoke.js
 *   BASE_URL=https://staging.healthlife.com k6 run scenarios/load.js
 *   BASE_URL=https://staging.healthlife.com k6 run scenarios/stress.js
 *   BASE_URL=https://staging.healthlife.com k6 run scenarios/spike.js
 *   BASE_URL=https://staging.healthlife.com k6 run scenarios/soak.js
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Shared thresholds applied to every scenario unless overridden.
export const THRESHOLDS = {
  // 95% of requests must complete within 2 seconds
  http_req_duration: ['p(95)<2000', 'p(99)<5000'],
  // Error rate must stay below 1%
  http_req_failed: ['rate<0.01'],
  // At least 95% of checks must pass
  checks: ['rate>0.95'],
};

// Test user credentials — override via env vars in CI
export const TEST_USER = {
  email: __ENV.TEST_USER_EMAIL || 'loadtest@healthlife.com',
  password: __ENV.TEST_USER_PASSWORD || 'LoadTest@2025!',
};

export const HEADERS = (token) => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${token}`,
});
