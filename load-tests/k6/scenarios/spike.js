/**
 * Spike Test — sudden traffic surge.
 *
 * Simulates a viral moment: app gets featured in press, push notification
 * sent to all users simultaneously, or a marketing campaign goes live.
 *
 * Pattern: idle → instant 5000 VU spike → back to idle.
 * Tests HPA responsiveness and connection pool behaviour under sudden load.
 */
import { sleep } from 'k6';
import { login, getDashboard, healthCheck } from '../helpers.js';

export const options = {
  stages: [
    { duration: '1m', target: 10 },    // baseline
    { duration: '30s', target: 5000 }, // spike — instant surge
    { duration: '3m', target: 5000 },  // hold spike
    { duration: '1m', target: 10 },    // recovery
    { duration: '2m', target: 10 },    // verify recovery
  ],
  thresholds: {
    http_req_duration: ['p(95)<10000'], // very relaxed during spike
    http_req_failed: ['rate<0.20'],     // up to 20% errors acceptable during spike
    checks: ['rate>0.70'],
  },
};

export default function () {
  // During spike, prioritise health check + login (most critical paths)
  healthCheck();
  sleep(0.2);

  const token = login();
  if (!token) {
    sleep(3);
    return;
  }

  getDashboard(token);
  sleep(Math.random() * 2 + 0.5);
}
