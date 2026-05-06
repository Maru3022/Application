# HealthLife Operations Runbook

**On-call rotation:** See PagerDuty schedule.
**Escalation path:** On-call engineer → Tech Lead → CTO
**Slack channels:** `#healthlife-oncall` (critical), `#healthlife-alerts` (warnings)
**Grafana:** https://grafana.healthlife.com
**Prometheus:** http://prometheus.monitoring.svc:9090 (internal)

---

## Table of Contents

1. [Service Down](#service-down)
2. [Pod Crash Looping](#pod-crash-looping)
3. [High Error Rate](#high-error-rate)
4. [High Latency](#high-latency)
5. [HPA Max Replicas Reached](#hpa-max-replicas-reached)
6. [Deployment Replicas Mismatch](#deployment-replicas-mismatch)
7. [Connection Pool Exhaustion](#connection-pool-exhaustion)
8. [Slow Database Queries](#slow-database-queries)
9. [High Memory Usage / JVM Heap Near Limit](#high-memory-usage)
10. [JVM GC Pressure](#jvm-gc-pressure)
11. [CPU Throttling](#cpu-throttling)
12. [Redis Down](#redis-down)
13. [Redis High Memory](#redis-high-memory)
14. [Database Failover](#database-failover)
15. [Certificate Expiry](#certificate-expiry)
16. [Security Incident / Brute Force](#security-incident)
17. [Rate Limit Abuse](#rate-limit-abuse)
18. [SLO Budget Burn](#slo-budget-burn)
19. [Rollback Procedure](#rollback-procedure)
20. [Scaling Procedures](#scaling-procedures)
21. [Post-Incident Review Template](#post-incident-review-template)

---

## Service Down

**Alert:** `ServiceDown` (critical)
**Symptoms:** `up{job=~"healthlife-.*"} == 0` for > 2 minutes.

### Diagnosis
```bash
# Check pod status
kubectl get pods -n healthlife -l app=<service-name>

# Check recent events
kubectl describe pod <pod-name> -n healthlife

# Check logs (current + previous container)
kubectl logs <pod-name> -n healthlife --tail=100
kubectl logs <pod-name> -n healthlife --previous --tail=100

# Check if service endpoints exist
kubectl get endpoints <service-name> -n healthlife
```

### Resolution
1. If pods are `Pending` → check node resources: `kubectl top nodes`
2. If pods are `CrashLoopBackOff` → see [Pod Crash Looping](#pod-crash-looping)
3. If pods are `Running` but health check fails → check application logs for startup errors
4. If no pods exist → check deployment: `kubectl get deployment <service-name> -n healthlife`
5. If deployment has 0 replicas → scale up: `kubectl scale deployment <service-name> --replicas=2 -n healthlife`
6. If issue persists → [Rollback](#rollback-procedure)

---

## Pod Crash Looping

**Alert:** `PodCrashLooping` (critical)
**Symptoms:** Pod restarts > 0.5/min over 15 minutes.

### Diagnosis
```bash
kubectl logs <pod-name> -n healthlife --previous --tail=200
kubectl describe pod <pod-name> -n healthlife | grep -A 20 "Last State"
```

### Common Causes & Fixes

| Exit Code | Cause | Fix |
|-----------|-------|-----|
| 137 | OOMKilled | Increase memory limit in k8s manifest |
| 1 | Application error | Check logs for exception stack trace |
| 143 | SIGTERM timeout | Increase `terminationGracePeriodSeconds` |

```bash
# Check if OOMKilled
kubectl describe pod <pod-name> -n healthlife | grep -i "oom\|killed\|exit code"

# Check resource usage
kubectl top pod <pod-name> -n healthlife --containers

# Temporarily increase memory limit (then update manifest)
kubectl set resources deployment <service-name> -n healthlife \
  --limits=memory=1Gi --requests=memory=512Mi
```

---

## High Error Rate

**Alert:** `HighErrorRate` (critical, > 5%) / `ElevatedErrorRate` (warning, > 1%)

### Diagnosis
```bash
# Which endpoints are failing?
kubectl logs -l app=<service-name> -n healthlife --tail=500 | grep "ERROR\|5[0-9][0-9]"

# Check downstream dependencies
kubectl exec -it <pod-name> -n healthlife -- wget -qO- http://postgres:5432 2>&1
kubectl exec -it <pod-name> -n healthlife -- redis-cli -h redis ping
```

### Grafana Queries
```promql
# Top error endpoints
topk(10,
  sum(rate(http_server_requests_seconds_count{status=~"5..",namespace="healthlife"}[5m]))
  by (application, uri)
)
```

### Resolution
1. Identify the failing service from Grafana
2. Check if it's a dependency failure (DB, Redis, downstream service)
3. If DB connection issue → see [Connection Pool Exhaustion](#connection-pool-exhaustion)
4. If Redis issue → see [Redis Down](#redis-down)
5. If code bug → [Rollback](#rollback-procedure)

---

## High Latency

**Alert:** `HighLatencyP95` (warning, > 2s) / `HighLatencyP99Critical` (critical, > 5s)

### Diagnosis
```bash
# Check slow queries in logs
kubectl logs -l app=<service-name> -n healthlife --tail=500 | grep -i "slow\|timeout\|durationMs"

# Check DB query times
kubectl exec -it <postgres-pod> -n healthlife -- \
  psql -U healthlife -c "SELECT query, mean_exec_time, calls FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 20;"
```

### Grafana Queries
```promql
# P95 by endpoint
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{namespace="healthlife"}[5m]))
  by (le, application, uri)
)
```

### Resolution
1. Check if HPA is scaling — if at max replicas, see [HPA Max Replicas](#hpa-max-replicas-reached)
2. Check DB connection pool — see [Connection Pool Exhaustion](#connection-pool-exhaustion)
3. Check Redis cache hit rate — low hit rate means more DB queries
4. Check for slow DB queries and add indexes if needed
5. Temporarily scale up: `kubectl scale deployment <service-name> --replicas=4 -n healthlife`

---

## HPA Max Replicas Reached

**Alert:** `HpaMaxReplicasReached` (warning)

### Diagnosis
```bash
kubectl get hpa -n healthlife
kubectl describe hpa <service-name>-hpa -n healthlife
```

### Resolution
```bash
# Temporary: increase max replicas
kubectl patch hpa <service-name>-hpa -n healthlife \
  --patch '{"spec":{"maxReplicas":12}}'

# Permanent: update k8s/base/<service-name>.yaml and redeploy
# Then investigate WHY load is higher than expected
```

---

## Deployment Replicas Mismatch

**Alert:** `DeploymentReplicasMismatch` (warning)

### Diagnosis
```bash
kubectl get deployment <service-name> -n healthlife
kubectl describe deployment <service-name> -n healthlife | grep -A 5 "Conditions"
kubectl get events -n healthlife --sort-by='.lastTimestamp' | tail -20
```

### Resolution
1. Check if pods are failing to start (see [Pod Crash Looping](#pod-crash-looping))
2. Check if nodes have capacity: `kubectl describe nodes | grep -A 5 "Allocated resources"`
3. Check image pull errors: `kubectl get events -n healthlife | grep "Failed to pull"`

---

## Connection Pool Exhaustion

**Alert:** `HikariConnectionPoolExhausted` (warning) / `HikariConnectionPoolCritical` (critical)

### Diagnosis
```bash
# Check pending connections
kubectl logs -l app=<service-name> -n healthlife --tail=200 | grep -i "hikari\|connection\|pool"

# Check DB connections from PostgreSQL side
kubectl exec -it <postgres-pod> -n healthlife -- \
  psql -U healthlife -c "SELECT count(*), state, application_name FROM pg_stat_activity GROUP BY state, application_name;"
```

### Grafana Queries
```promql
hikaricp_connections_active{namespace="healthlife"}
hikaricp_connections_pending{namespace="healthlife"}
hikaricp_connections_max{namespace="healthlife"}
```

### Resolution
```bash
# Short-term: restart the affected service to release connections
kubectl rollout restart deployment/<service-name> -n healthlife

# Medium-term: increase pool size in application.yml
# hikari.maximum-pool-size: 30  (from 20)

# Long-term: add PgBouncer connection pooler in front of PostgreSQL
```

---

## Slow Database Queries

**Alert:** `SlowDatabaseQueries` (warning, P95 > 1s)

### Diagnosis
```bash
# Enable pg_stat_statements if not already enabled
kubectl exec -it <postgres-pod> -n healthlife -- \
  psql -U healthlife -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"

# Find slowest queries
kubectl exec -it <postgres-pod> -n healthlife -- \
  psql -U healthlife -c "
    SELECT
      left(query, 100) AS query,
      calls,
      round(mean_exec_time::numeric, 2) AS avg_ms,
      round(total_exec_time::numeric, 2) AS total_ms
    FROM pg_stat_statements
    ORDER BY mean_exec_time DESC
    LIMIT 20;"

# Check for missing indexes
kubectl exec -it <postgres-pod> -n healthlife -- \
  psql -U healthlife -c "
    SELECT schemaname, tablename, attname, n_distinct, correlation
    FROM pg_stats
    WHERE tablename IN ('sleep_entries','water_entries','food_log_entries','mood_entries')
    ORDER BY tablename, attname;"
```

### Resolution
1. Add missing index via Flyway migration
2. Rewrite N+1 queries (use bulk fetch)
3. Add Redis caching for frequently-read data

---

## High Memory Usage

**Alert:** `HighMemoryUsage` (warning, > 85%) / `MemoryNearLimit` (critical, > 95%)

### Diagnosis
```bash
kubectl top pods -n healthlife --sort-by=memory
kubectl exec -it <pod-name> -n healthlife -- \
  sh -c "cat /proc/meminfo | head -5"
```

### Grafana Queries
```promql
# JVM heap trend (memory leak indicator)
jvm_memory_used_bytes{area="heap", namespace="healthlife", application="<service>"}
```

### Resolution
```bash
# Immediate: increase memory limit
kubectl set resources deployment <service-name> -n healthlife \
  --limits=memory=1Gi

# If memory grows monotonically over time → memory leak
# Take heap dump:
kubectl exec -it <pod-name> -n healthlife -- \
  sh -c "jcmd 1 GC.heap_dump /tmp/heap.hprof && cat /tmp/heap.hprof" > heap.hprof
# Analyse with Eclipse MAT or VisualVM
```

---

## JVM GC Pressure

**Alert:** `JvmGcPressure` (warning, avg GC pause > 500ms)

### Diagnosis
```bash
kubectl logs <pod-name> -n healthlife --tail=200 | grep -i "gc\|pause"
```

### Grafana Queries
```promql
rate(jvm_gc_pause_seconds_sum{namespace="healthlife"}[5m])
/ rate(jvm_gc_pause_seconds_count{namespace="healthlife"}[5m])
```

### Resolution
1. Increase heap size: add `-Xmx` to `JAVA_OPTS` in Dockerfile
2. Switch to G1GC if using ZGC or vice versa
3. Reduce object allocation rate (profile with async-profiler)

---

## CPU Throttling

**Alert:** `HighCpuThrottling` (warning, > 50%)

### Diagnosis
```bash
kubectl top pods -n healthlife --sort-by=cpu
```

### Resolution
```bash
# Increase CPU limit
kubectl set resources deployment <service-name> -n healthlife \
  --limits=cpu=1000m --requests=cpu=200m

# Update k8s/base/<service-name>.yaml permanently
```

---

## Redis Down

**Alert:** `RedisDown` (critical)

### Impact
- Rate limiting disabled (fail-open — all requests pass)
- AI Coach cache miss on every request
- FCM device token lookups fail
- Analytics event storage fails

### Diagnosis
```bash
kubectl get pods -n healthlife -l app=redis
kubectl logs -l app=redis -n healthlife --tail=100
kubectl exec -it <redis-pod> -n healthlife -- redis-cli ping
```

### Resolution
```bash
# Restart Redis
kubectl rollout restart deployment/redis -n healthlife

# Check PVC
kubectl get pvc -n healthlife | grep redis
kubectl describe pvc redis-data -n healthlife

# If data is corrupted, restore from backup:
kubectl exec -it <redis-pod> -n healthlife -- redis-cli FLUSHALL
# Redis will rebuild from application writes
```

---

## Redis High Memory

**Alert:** `RedisHighMemoryUsage` (warning, > 85%)

### Diagnosis
```bash
kubectl exec -it <redis-pod> -n healthlife -- redis-cli INFO memory
kubectl exec -it <redis-pod> -n healthlife -- redis-cli INFO keyspace
# Find largest keys
kubectl exec -it <redis-pod> -n healthlife -- \
  redis-cli --bigkeys
```

### Resolution
```bash
# Check maxmemory policy (should be allkeys-lru)
kubectl exec -it <redis-pod> -n healthlife -- redis-cli CONFIG GET maxmemory-policy

# Increase maxmemory (update redis.yaml)
kubectl exec -it <redis-pod> -n healthlife -- \
  redis-cli CONFIG SET maxmemory 400mb
```

---

## Database Failover

### Symptoms
- Services returning 500/503
- `HikariCP connection timeout` in logs

### Diagnosis
```bash
kubectl exec -it <postgres-pod> -n healthlife -- pg_isready -U healthlife
kubectl get pvc -n healthlife | grep postgres
kubectl describe pvc postgres-data-postgres-0 -n healthlife
```

### Resolution
```bash
# Check PostgreSQL logs
kubectl logs postgres-0 -n healthlife --tail=200

# Check disk space
kubectl exec -it postgres-0 -n healthlife -- df -h /var/lib/postgresql/data

# If disk full — expand PVC (requires StorageClass that supports expansion)
kubectl patch pvc postgres-data-postgres-0 -n healthlife \
  --patch '{"spec":{"resources":{"requests":{"storage":"20Gi"}}}}'

# Restore from backup (last resort)
# See docs/DEPLOYMENT.md#database-restore
```

---

## Certificate Expiry

**Alert:** cert-manager fires `CertificateExpiringSoon` 30 days before expiry.

### Diagnosis
```bash
kubectl get certificate -n healthlife
kubectl describe certificate healthlife-tls -n healthlife
kubectl get certificaterequest -n healthlife
```

### Resolution
```bash
# cert-manager auto-renews 30 days before expiry.
# If auto-renewal failed, force renewal:
kubectl delete secret healthlife-tls -n healthlife
# cert-manager will re-issue within minutes

# Check cert-manager logs if renewal fails
kubectl logs -l app=cert-manager -n cert-manager --tail=100
```

---

## Security Incident

**Alert:** `HighAuthFailureRate` (warning, > 10 failures/sec)

### Immediate Response
```bash
# 1. Identify source IPs
kubectl logs -l app=gateway-service -n healthlife --tail=500 | \
  grep "401\|403" | awk '{print $1}' | sort | uniq -c | sort -rn | head -20

# 2. Block IP at ingress level (temporary)
kubectl annotate ingress gateway-ingress -n healthlife \
  nginx.ingress.kubernetes.io/server-snippet='deny <IP>;'

# 3. Rotate JWT secret if tokens may be compromised
# Update secret in Vault/AWS Secrets Manager
# Restart all services to pick up new secret (invalidates all existing tokens)
kubectl rollout restart deployment -n healthlife

# 4. Isolate affected service if needed
kubectl scale deployment <service-name> --replicas=0 -n healthlife
```

### Post-Incident
1. Collect logs and evidence before rotating secrets
2. File incident report (see [Post-Incident Review Template](#post-incident-review-template))
3. Review rate limiting configuration
4. Consider adding CAPTCHA to login endpoint

---

## Rate Limit Abuse

**Alert:** `RateLimitTriggeredFrequently` (warning, > 5 req/s returning 429)

### Diagnosis
```bash
# Find which users/IPs are being rate limited
kubectl logs -l app=gateway-service -n healthlife --tail=500 | grep "429"
```

### Resolution
1. If legitimate traffic → increase rate limit in `RateLimitFilter.java`
2. If abuse → block at WAF/CDN level (Cloudflare, AWS WAF)
3. If misconfigured client → contact the client team

---

## SLO Budget Burn

**Alert:** `SloAvailabilityBudgetBurning` (critical)

### What This Means
The 99.9% availability SLO allows 8.7 hours of downtime per year (43.8 min/month).
This alert fires when the current error rate would exhaust the monthly budget in < 2 days.

### Response
1. Immediately investigate the root cause (check other firing alerts)
2. If error rate > 5% → treat as P1 incident
3. After resolution, calculate remaining error budget for the month
4. If budget < 25% remaining → freeze non-critical deployments for the rest of the month

---

## Rollback Procedure

```bash
# List Helm release history
helm history healthlife -n healthlife

# Rollback to previous revision
helm rollback healthlife -n healthlife

# Rollback to specific revision
helm rollback healthlife <revision> -n healthlife

# Verify rollback
kubectl rollout status deployment/<service-name> -n healthlife
kubectl get pods -n healthlife

# If Helm rollback fails, use kubectl directly
kubectl rollout undo deployment/<service-name> -n healthlife
kubectl rollout status deployment/<service-name> -n healthlife
```

---

## Scaling Procedures

### Horizontal Scale (immediate)
```bash
# Scale a specific service
kubectl scale deployment <service-name> --replicas=4 -n healthlife

# Scale all services (e.g., before a marketing event)
for svc in gateway-service auth-service user-service health-data-service \
           mental-service nutrition-service social-service; do
  kubectl scale deployment $svc --replicas=4 -n healthlife
done
```

### Update HPA Limits (persistent)
```bash
kubectl patch hpa <service-name>-hpa -n healthlife \
  --patch '{"spec":{"minReplicas":3,"maxReplicas":15}}'
```

### Pre-Event Scaling Checklist
Before a marketing campaign or press feature:
- [ ] Scale gateway to 4+ replicas
- [ ] Scale auth-service to 4+ replicas
- [ ] Increase HPA maxReplicas to 15 for all services
- [ ] Verify Redis maxmemory is adequate
- [ ] Verify PostgreSQL connection pool can handle load
- [ ] Run spike test against staging
- [ ] Set up war room Slack channel

---

## Post-Incident Review Template

```markdown
## Incident Report — [DATE] [SERVICE] [SEVERITY]

### Summary
One-paragraph description of what happened.

### Timeline (UTC)
- HH:MM — Alert fired
- HH:MM — On-call engineer paged
- HH:MM — Root cause identified
- HH:MM — Mitigation applied
- HH:MM — Service restored
- HH:MM — Incident closed

### Root Cause
Detailed technical explanation.

### Impact
- Duration: X minutes
- Users affected: ~X (estimated from error rate × DAU)
- Error budget consumed: X% of monthly budget

### Resolution
What was done to fix it.

### Action Items
| Action | Owner | Due Date |
|--------|-------|----------|
| Add index on table X | @engineer | YYYY-MM-DD |
| Increase memory limit | @engineer | YYYY-MM-DD |
| Add alert for Y | @engineer | YYYY-MM-DD |

### What Went Well
- Alert fired promptly
- Rollback procedure worked

### What Could Be Improved
- Detection time was too slow
- Runbook was missing step X
```
