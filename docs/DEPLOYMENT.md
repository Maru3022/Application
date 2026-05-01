# Deployment Guide

## Prerequisites

- Kubernetes cluster (1.28+)
- Helm 3
- kubectl configured
- Container registry access (GHCR)

## Step 1: Create Namespace and Secrets

```bash
kubectl create namespace healthlife

kubectl create secret generic healthlife-secrets \
  --from-literal=jwt-secret=$(openssl rand -base64 32) \
  -n healthlife

kubectl create secret generic healthlife-db-credentials \
  --from-literal=username=healthlife \
  --from-literal=password=$(openssl rand -base64 24) \
  -n healthlife
```

## Step 2: Deploy with Helm

```bash
helm upgrade --install healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=$(git rev-parse --short HEAD) \
  --wait
```

## Step 3: Verify Deployment

```bash
kubectl get pods -n healthlife
kubectl get svc -n healthlife
kubectl get hpa -n healthlife
```

## Step 4: Access Services

Port-forward gateway for local testing:

```bash
kubectl port-forward svc/gateway-service 8080:80 -n healthlife
```

## Rolling Updates

```bash
helm upgrade healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=1.1.0
```

## Rollback

```bash
helm rollback healthlife 1 -n healthlife
```

## Production Checklist

- [ ] Secrets rotated and stored in vault
- [ ] TLS certificates configured
- [ ] Resource limits tuned based on load tests
- [ ] NetworkPolicies applied
- [ ] Monitoring and alerting verified
- [ ] Backup strategy for PostgreSQL
- [ ] Disaster recovery runbook tested
