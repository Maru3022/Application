/**
 * Soak Test — sustained load over extended period.
 *
 * Runs at 300 VUs for 2 hours to detect:
 *   - Memory leaks (heap grows over time)
 *   - Connection pool exhaustion
 *   - Database connection leaks
 *   - Redis key accumulation
 *   - Log file growth / disk exhaustion
 *
 * Run weekly in staging before production releases.
 * Duration: 2h 10m total
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
    { duration: '5m', target: 300 },   // ramp up
    { duration: '2h', target: 300 },   // sustained soak
    { duration: '5m', target: 0 },     // ramp down
  ],
  thresholds: {
    ...THRESHOLDS,
    // Latency must not degrade over time (memory leak indicator)
    http_req_duration: ['p(95)<2000', 'p(99)<4000'],
  },
};

export default function () {
  const token = login();
  if (!token) {
    sleep(5);
    return;
  }

  // Full user journey — covers all services
  getProfile(token);
  sleep(1);

  getDashboard(token);
  sleep(2);

  addWater(token, Math.floor(Math.random() * 400) + 100);
  sleep(1);

  createMood(token, Math.floor(Math.random() * 10) + 1);
  sleep(1);

  getFoodLogToday(token);
  sleep(1);

  getSocialFeed(token);
  sleep(Math.random() * 5 + 3); // longer think time for soak
}
