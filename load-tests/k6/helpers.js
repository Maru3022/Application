import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, TEST_USER, HEADERS } from './config.js';

/**
 * Authenticates a virtual user and returns the access token.
 * Fails the iteration if login does not return 200.
 */
export function login() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: TEST_USER.email, password: TEST_USER.password }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(res, {
    'login status 200': (r) => r.status === 200,
    'login has accessToken': (r) => {
      try {
        return JSON.parse(r.body).accessToken !== undefined;
      } catch {
        return false;
      }
    },
  });

  if (res.status !== 200) return null;
  return JSON.parse(res.body).accessToken;
}

/** GET /api/v1/health/dashboard */
export function getDashboard(token) {
  const res = http.get(`${BASE_URL}/api/v1/health/dashboard`, {
    headers: HEADERS(token),
  });
  check(res, { 'dashboard 200': (r) => r.status === 200 });
  return res;
}

/** POST /api/v1/health/water */
export function addWater(token, amountMl) {
  const res = http.post(
    `${BASE_URL}/api/v1/health/water`,
    JSON.stringify({ amountMl, recordedAt: new Date().toISOString() }),
    { headers: HEADERS(token) },
  );
  check(res, { 'add water 200': (r) => r.status === 200 });
  return res;
}

/** GET /api/v1/health/water/today */
export function getWaterToday(token) {
  const res = http.get(`${BASE_URL}/api/v1/health/water/today`, {
    headers: HEADERS(token),
  });
  check(res, { 'water today 200': (r) => r.status === 200 });
  return res;
}

/** POST /api/v1/mental/mood */
export function createMood(token, score) {
  const res = http.post(
    `${BASE_URL}/api/v1/mental/mood`,
    JSON.stringify({ moodScore: score, recordedAt: new Date().toISOString() }),
    { headers: HEADERS(token) },
  );
  check(res, { 'create mood 200': (r) => r.status === 200 });
  return res;
}

/** GET /api/v1/nutrition/food-log/today */
export function getFoodLogToday(token) {
  const res = http.get(`${BASE_URL}/api/v1/nutrition/food-log/today`, {
    headers: HEADERS(token),
  });
  check(res, { 'food log today 200': (r) => r.status === 200 });
  return res;
}

/** GET /api/v1/social/feed */
export function getSocialFeed(token) {
  const res = http.get(`${BASE_URL}/api/v1/social/feed`, {
    headers: HEADERS(token),
  });
  check(res, { 'social feed 200': (r) => r.status === 200 });
  return res;
}

/** GET /api/v1/users/me */
export function getProfile(token) {
  const res = http.get(`${BASE_URL}/api/v1/users/me`, {
    headers: HEADERS(token),
  });
  check(res, { 'profile 200': (r) => r.status === 200 });
  return res;
}

/** GET /actuator/health */
export function healthCheck() {
  const res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'health 200': (r) => r.status === 200 });
  return res;
}
