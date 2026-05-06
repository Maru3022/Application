# HealthLife Load Tests

k6-based load testing suite covering all critical user journeys.

## Prerequisites

```bash
# Install k6
# macOS
brew install k6

# Linux
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Windows
winget install k6
```

## Scenarios

| Scenario | VUs | Duration | Purpose |
|----------|-----|----------|---------|
| `smoke`  | 3   | 2m       | Verify system works — run before every deploy |
| `load`   | 500 | 30m      | Expected peak load — run weekly |
| `stress` | 2000| 22m      | Find breaking point — run before major releases |
| `spike`  | 5000| 7m       | Viral surge simulation — run quarterly |
| `soak`   | 300 | 2h10m    | Memory leak detection — run weekly |
| `auth`   | 200 | 9m       | Auth endpoint focused — run after auth changes |

## Running Tests

```bash
# Smoke test (fastest — run first)
BASE_URL=https://staging.healthlife.com k6 run k6/scenarios/smoke.js

# Load test
BASE_URL=https://staging.healthlife.com \
  TEST_USER_EMAIL=loadtest@healthlife.com \
  TEST_USER_PASSWORD=LoadTest@2025! \
  k6 run k6/scenarios/load.js

# With output to InfluxDB (for Grafana dashboard)
BASE_URL=https://staging.healthlife.com \
  k6 run --out influxdb=http://influxdb:8086/k6 k6/scenarios/load.js

# With HTML report
BASE_URL=https://staging.healthlife.com \
  k6 run --out json=results/load-$(date +%Y%m%d).json k6/scenarios/load.js
```

## Pass/Fail Criteria

| Metric | Threshold |
|--------|-----------|
| p(95) response time | < 2 000 ms |
| p(99) response time | < 5 000 ms |
| Error rate | < 1% |
| Check pass rate | > 95% |

## Pre-Release Checklist

Before every production release run in order:

1. `smoke.js` — must pass with 0 errors
2. `auth.js` — must pass (auth is critical path)
3. `load.js` — must pass all thresholds
4. `stress.js` — document the breaking point, ensure recovery works
5. `soak.js` — run overnight, check for memory growth in Grafana

## Interpreting Results

### Memory Leak (soak test)
Watch `jvm_memory_used_bytes{area="heap"}` in Grafana.
- **Healthy**: flat or sawtooth pattern (GC cycles)
- **Leak**: monotonically increasing over 2h

### Connection Pool Exhaustion
Watch `hikaricp_connections_active` and `hikaricp_connections_pending`.
- **Healthy**: active < pool size, pending = 0
- **Problem**: pending > 0 sustained → increase `maximum-pool-size`

### HPA Responsiveness (spike test)
Watch `kube_deployment_spec_replicas` vs `kube_deployment_status_ready_replicas`.
- Scale-out should begin within 60s of spike
- All replicas ready within 3 minutes

## CI Integration

Smoke tests run automatically in CI after every staging deploy.
See `.github/workflows/ci-cd.yml` — `deploy-staging` job.

For full load tests, trigger manually:
```bash
# From GitHub Actions
gh workflow run ci-cd.yml -f deploy_environment=staging
```
