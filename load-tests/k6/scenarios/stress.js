/**
 * Stress Test — find the breaking point.
 *
 * Gradually increases load beyond expected capacity to identify:
 *   - At what VU count does latency degrade?
 *   - At what VU count do errors appear?
 *   - Does the system recover after load drops?
 *
 * Stages: ramp to 2000 VUs (4× expected peak), then recover.
 *
 * NOTE: Run against staging only. This WILL cause degradation.
 */
import { sleep } from 'k6';
import { THRESHOLDS } from '../config.js';
import { login, getDashboard, addWater, getWaterToday } from '../helpers.js';

export const options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '5m', target: 500 },
    { duration: '5m', target: 1000 },
    { duration: '5m', target: 2000 },
    { duration: '5m', target: 2000 }, // hold at peak
    { duration: '5m', target: 0 },   // recovery
  ],
  thresholds: {
    // Relaxed thresholds — we expect degradation, we're measuring where
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.10'],
    checks: ['rate>0.80'],
  },
};

export default function () {
  const token = login();
  if (!token) {
    sleep(2);
    return;
  }

  getDashboard(token);
  sleep(1);
  addWater(token, 250);
  sleep(1);
  getWaterToday(token);
  sleep(Math.random() * 2 + 1);
}
