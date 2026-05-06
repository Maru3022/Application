/**
 * Load Test — expected production traffic.
 *
 * Simulates a realistic mix of user actions at the expected peak load.
 * Target: 500 concurrent users (≈ 10k DAU with typical session patterns).
 *
 * Stages:
 *   0→500 VUs over 5 min  (ramp-up)
 *   500 VUs for 20 min    (sustained load)
 *   500→0 VUs over 5 min  (ramp-down)
 *
 * Pass criteria:
 *   - p(95) < 2s
 *   - error rate < 1%
 *   - checks pass rate > 95%
 */
import { sleep } from 'k6';
import { THRESHOLDS } from '../config.js';
import {
  login,
  getDashboard,
  addWater,
  getWaterToday,
  createMood,
  getFoodLogToday,
  getSocialFeed,
  getProfile,
} from '../helpers.js';

export const options = {
  stages: [
    { duration: '5m', target: 500 },
    { duration: '20m', target: 500 },
    { duration: '5m', target: 0 },
  ],
  thresholds: THRESHOLDS,
};

export default function () {
  const token = login();
  if (!token) return;

  // Simulate realistic user session — weighted by feature usage
  const action = Math.random();

  if (action < 0.30) {
    // 30% — dashboard view (most common)
    getDashboard(token);
    sleep(2);
    getWaterToday(token);
  } else if (action < 0.50) {
    // 20% — log water intake
    addWater(token, Math.floor(Math.random() * 300) + 150);
    sleep(1);
    getWaterToday(token);
  } else if (action < 0.65) {
    // 15% — mood tracking
    createMood(token, Math.floor(Math.random() * 10) + 1);
  } else if (action < 0.78) {
    // 13% — nutrition log
    getFoodLogToday(token);
  } else if (action < 0.90) {
    // 12% — social feed
    getSocialFeed(token);
  } else {
    // 10% — profile view
    getProfile(token);
  }

  sleep(Math.random() * 3 + 1); // think time 1-4s
}
