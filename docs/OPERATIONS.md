# Operations Runbook

## Pod Crash Looping

### Symptoms
- Pod status `CrashLoopBackOff`
- High restart count

### Diagnosis
```bash
kubectl logs <pod> -n healthlife --previous
kubectl describe pod <pod> -n healthlife
```

### Resolution
1. Check application logs for startup errors
2. Verify database connectivity
3. Check resource limits (OOMKilled)
4. Rollback to previous version if needed:
   ```bash
   helm rollback healthlife <revision> -n healthlife
   ```

## Database Failover

### Symptoms
- Services returning 500/503
- Connection timeout errors

### Diagnosis
```bash
kubectl exec -it <postgres-pod> -- pg_isready
```

### Resolution
1. Verify PostgreSQL pod health
2. Check PVC storage capacity
3. Restore from backup if necessary
4. Update connection strings if primary changed

## High Latency

### Symptoms
- P95 latency > 2s
- HPA scaling up frequently

### Diagnosis
```bash
kubectl top pods -n healthlife
kubectl logs <pod> -n healthlife | grep "durationMs"
```

### Resolution
1. Check database query performance
2. Verify Redis cache hit rates
3. Scale affected service horizontally
4. Review slow query logs

## Certificate Expiry

### Symptoms
- TLS handshake failures
- Clients unable to connect

### Diagnosis
```bash
kubectl get certificate -n healthlife
kubectl describe certificate healthlife-tls -n healthlife
```

### Resolution
1. Verify cert-manager is running
2. Check Let's Encrypt rate limits
3. Force renewal:
   ```bash
   kubectl delete secret healthlife-tls -n healthlife
   ```

## Security Incident Response

1. Isolate affected service:
   ```bash
   kubectl scale deployment <service> --replicas=0 -n healthlife
   ```
2. Collect logs and evidence
3. Rotate compromised secrets
4. Apply patched image
5. Post-incident review
