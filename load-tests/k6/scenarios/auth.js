/**
 * Auth Endpoint Load Test — focused on login/register/refresh.
 *
 * Auth is the most critical path: if login is slow, nothing else works.
 * Tests rate limiting, BCrypt performance, and JWT generation under load.
 *
 * Target: 200 concurrent auth operations.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THRESHOLDS } from '../config.js';
import { login } from '../helpers.js';

export const options = {
  stages: [
    { duration: '2m', target: 200 },
    { duration: '5m', target: 200 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    ...THRESHOLDS,
    // Auth must be fast — BCrypt(12) takes ~300ms, so p95 < 1s is realistic
    'http_req_duration{endpoint:login}': ['p(95)<1000'],
    'http_req_duration{endpoint:refresh}': ['p(95)<500'],
  },
};

export default function () {
  const vuId = __VU;
  const email = `loadtest+${vuId}@healthlife.com`;
  const password = 'LoadTest@2025!';

  // Register (first iteration only — subsequent iterations will get 409)
  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ email, password, displayName: `LoadUser${vuId}` }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'register' },
    },
  );
  check(registerRes, {
    'register 200 or 409': (r) => r.status === 200 || r.status === 409,
  });
  sleep(0.5);

  // Login
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    },
  );
  check(loginRes, { 'login 200': (r) => r.status === 200 });

  if (loginRes.status !== 200) {
    sleep(2);
    return;
  }

  const { accessToken, refreshToken } = JSON.parse(loginRes.body);
  sleep(1);

  // Refresh token
  const refreshRes = http.post(
    `${BASE_URL}/api/v1/auth/refresh`,
    JSON.stringify({ refreshToken }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'refresh' },
    },
  );
  check(refreshRes, { 'refresh 200': (r) => r.status === 200 });
  sleep(1);

  // Logout
  http.post(`${BASE_URL}/api/v1/auth/logout`, null, {
    headers: {
      'Content-Type': 'application/json',
      'X-Refresh-Token': refreshToken,
    },
    tags: { endpoint: 'logout' },
  });

  sleep(Math.random() * 2 + 1);
}
