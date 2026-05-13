# Kubernetes Cluster Setup Guide

## Architecture

```
kind-healthlife (3 nodes)
├── control-plane  (ingress-ready, ports 80/443 mapped)
├── worker-1
└── worker-2

Namespace: healthlife
├── Infrastructure
│   ├── PostgreSQL (StatefulSet, 7 databases)
│   └── Redis (Deployment)
├── Microservices (10 pods via Helm)
│   ├── gateway-service     :8080  (needs Redis)
│   ├── auth-service        :8081  (needs DB + Redis)
│   ├── user-service        :8082  (needs DB)
│   ├── health-data-service :8083  (needs DB)
│   ├── mental-service      :8084  (needs DB)
│   ├── nutrition-service   :8085  (needs DB)
│   ├── ai-coach-service    :8086  (needs DB)
│   ├── social-service      :8087  (needs DB)
│   ├── notification-service:8088
│   └── analytics-service   :8089  (needs Redis)
├── HPA (per service, 2-8 replicas)
├── PDB (per service, minAvailable: 1)
└── NetworkPolicies (default-deny + allow-internal)
```

## Prerequisites

| Tool  | Version  | Install |
|-------|----------|---------|
| kind  | 0.20+    | `choco install kind` or https://kind.sigs.k8s.io |
| kubectl | 1.28+  | `choco install kubernetes-cli` |
| helm  | 3.12+    | `choco install kubernetes-helm` |
| Docker | 20+     | Docker Desktop for Windows |

## Quick Start

### Windows (PowerShell)

```powershell
# From project root
.\k8s\Start-Cluster.ps1
```

### Linux / macOS

```bash
# From project root
chmod +x k8s/start-cluster.sh
./k8s/start-cluster.sh
```

## Manual Step-by-Step

### 1. Create the kind cluster

```bash
kind create cluster --config k8s/kind-cluster.yaml --wait 120s
kubectl config use-context kind-healthlife
```

### 2. Install NGINX Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for condition=ready pod \
  --selector app.kubernetes.io/component=controller \
  --timeout=120s
```

### 3. Label namespaces (for NetworkPolicy)

```bash
kubectl label namespace ingress-nginx name=ingress-nginx
kubectl label namespace kube-system name=kube-system
```

### 4. Create namespace and secrets

```bash
kubectl apply -f k8s/base/namespace.yaml
kubectl label namespace healthlife name=healthlife

# JWT secret
kubectl create secret generic healthlife-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  -n healthlife

# DB credentials
kubectl create secret generic healthlife-db-credentials \
  --from-literal=username=healthlife \
  --from-literal=password=healthlife_pass \
  -n healthlife
```

### 5. Deploy infrastructure (PostgreSQL + Redis)

```bash
kubectl apply -f k8s/infrastructure/postgres-init-cm.yaml
kubectl apply -f k8s/infrastructure/postgres.yaml
kubectl apply -f k8s/infrastructure/redis.yaml

# Wait for readiness
kubectl wait --for=condition=ready pod -l app=postgres -n healthlife --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n healthlife --timeout=60s
```

PostgreSQL creates 7 databases automatically via init script:
`healthlife_auth`, `healthlife_user`, `healthlife_healthdata`, `healthlife_mental`, `healthlife_nutrition`, `healthlife_aicoach`, `healthlife_social`

### 6. Deploy microservices with Helm

```bash
helm upgrade --install healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=latest \
  --wait --timeout 300s
```

To deploy a specific image tag:

```bash
helm upgrade --install healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=sha-abc1234
```

### 7. Apply NetworkPolicies

```bash
kubectl apply -f k8s/base/network-policy.yaml
```

## Access the Application

Without a port-forward or ingress, **nothing listens on your PC’s port 8080** — the browser will show `ERR_CONNECTION_REFUSED`. On Windows you can run `.\k8s\Port-Forward-Gateway.ps1` from the repo root (leave the window open).

### Port-forward (quick test)

```bash
kubectl port-forward svc/gateway-service 8080:80 -n healthlife
```

Access: http://localhost:8080

### Ingress (production-like)

Add to your hosts file (`C:\Windows\System32\drivers\etc\hosts` on Windows, `/etc/hosts` on Linux):

```
127.0.0.1 api.healthlife.com
```

Access: http://api.healthlife.com (or https with cert-manager)

## Useful Commands

```bash
# Check all pods
kubectl get pods -n healthlife

# Watch pod startup
kubectl get pods -n healthlife -w

# Check services
kubectl get svc -n healthlife

# Check HPA
kubectl get hpa -n healthlife

# View pod logs
kubectl logs -f deployment/gateway-service -n healthlife

# Describe a pod
kubectl describe pod <pod-name> -n healthlife

# Connect to PostgreSQL
kubectl exec -it deployment/postgres -n healthlife -- psql -U healthlife -d healthlife_auth

# Connect to Redis
kubectl exec -it deployment/redis -n healthlife -- redis-cli
```

## Scaling

```bash
# Scale a specific service manually
kubectl scale deployment auth-service --replicas=4 -n healthlife

# HPA auto-scales based on CPU (2-8 replicas per service)
kubectl get hpa -n healthlife -w
```

## Rolling Updates

```bash
helm upgrade healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=new-tag \
  --wait
```

## Rollback

```bash
# List releases
helm history healthlife -n healthlife

# Rollback to previous
helm rollback healthlife 1 -n healthlife
```

## Teardown

```bash
# Delete just the release
helm uninstall healthlife -n healthlife

# Delete the entire cluster
kind delete cluster --name healthlife
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Pods `ImagePullBackOff` | Check GHCR access: `kubectl describe pod <name> -n healthlife` |
| Pods `CrashLoopBackOff` | Check logs: `kubectl logs <pod> -n healthlife` |
| PostgreSQL not ready | `kubectl describe pod -l app=postgres -n healthlife` |
| Ingress not working | Verify ingress-nginx pods: `kubectl get pods -n ingress-nginx` |
| NetworkPolicy blocking | Temporarily remove: `kubectl delete networkpolicy healthlife-default-deny -n healthlife` |
| kind cluster won't start | Ensure Docker Desktop is running with 8GB+ RAM |

## Resource Requirements

| Component | CPU | Memory |
|-----------|-----|--------|
| PostgreSQL | 100m-500m | 256Mi-512Mi |
| Redis | 50m-200m | 64Mi-128Mi |
| Each microservice | 100m-500m | 256Mi-512Mi |
| **Total (2 replicas each)** | ~3 vCPU | ~6 GiB |

Docker Desktop should be configured with at least **8 GB RAM** and **4 CPUs**.
