/**
 * Smoke Test — minimal load to verify the system works at all.
 *
 * Run before every deployment to catch obvious breakage.
 * Duration: ~2 minutes | VUs: 3
 *
 * Pass criteria: 0 errors, all endpoints respond 200.
 */
import { sleep } from 'k6';
import { THRESHOLDS } from '../config.js';
import { login, getDashboard, addWater, getWaterToday, getProfile, healthCheck } from '../helpers.js';

export const options = {
  vus: 3,
  duration: '2m',
  thresholds: {
    ...THRESHOLDS,
    http_req_duration: ['p(95)<1000'], // tighter for smoke
    http_req_failed: ['rate<0.001'],
  },
};

export default function () {
  // 1. Health check (unauthenticated)
  healthCheck();
  sleep(0.5);

  // 2. Login
  const token = login();
  if (!token) return;
  sleep(0.5);

  // 3. Core user journey
  getProfile(token);
  sleep(0.3);

  getDashboard(token);
  sleep(0.3);

  addWater(token, 250);
  sleep(0.3);

  getWaterToday(token);
  sleep(1);
}
