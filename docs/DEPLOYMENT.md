# Deployment Guide

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| kubectl | 1.28+ | https://kubernetes.io/docs/tasks/tools/ |
| Helm | 3.14+ | https://helm.sh/docs/intro/install/ |
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| k6 | latest | `brew install k6` / `winget install k6` |

---

## Local Development (Docker Compose)

```bash
# Start PostgreSQL (7 DBs), Redis, Kafka
docker compose -f infrastructure/docker-compose.yml up -d

# Build shared modules
mvn install -DskipTests -B -pl shared/common-exceptions,shared/common-dto,shared/common-security -am

# Run any service
cd services/auth-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Kubernetes — Local Cluster (kind)

### 1. Create cluster

```bash
kind create cluster --config k8s/kind-cluster.yaml
kubectl cluster-info --context kind-healthlife
```

### 2. Create namespace and secrets

```bash
kubectl create namespace healthlife

# Generate strong secrets
JWT_SECRET=$(openssl rand -base64 64)
DB_PASSWORD=$(openssl rand -base64 24)

kubectl create secret generic healthlife-secrets \
  --from-literal=jwt-secret="${JWT_SECRET}" \
  --from-literal=claude-api-key="" \
  --from-literal=firebase-service-account-json="" \
  -n healthlife

kubectl create secret generic healthlife-db-credentials \
  --from-literal=username=healthlife \
  --from-literal=password="${DB_PASSWORD}" \
  -n healthlife

kubectl create secret generic healthlife-mail-credentials \
  --from-literal=username="" \
  --from-literal=password="" \
  -n healthlife
```

### 3. Deploy infrastructure

```bash
kubectl apply -f k8s/infrastructure/ -n healthlife

# Wait for PostgreSQL and Redis to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n healthlife --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n healthlife --timeout=60s
```

### 4. Build and load images

```bash
# Build all service JARs
mvn package -DskipTests -B

# Build Docker images
for svc in gateway-service auth-service user-service health-data-service \
           mental-service nutrition-service ai-coach-service social-service \
           notification-service analytics-service; do
  docker build -t ghcr.io/maru3022/application/${svc}:latest services/${svc}/
  kind load docker-image ghcr.io/maru3022/application/${svc}:latest
done
```

### 5. Deploy application

```bash
helm upgrade --install healthlife k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=latest \
  --set image.pullPolicy=Never \
  --wait --timeout 5m
```

### 6. Verify

```bash
kubectl get pods -n healthlife
kubectl get hpa -n healthlife
kubectl get pdb -n healthlife

# Port-forward gateway for testing
kubectl port-forward svc/gateway-service 8080:80 -n healthlife

# Test health endpoint
curl http://localhost:8080/actuator/health
```

---

## Kubernetes — Production (AWS EKS)

### Prerequisites

- AWS account with EKS permissions
- `aws` CLI configured
- `eksctl` installed

### 1. Create EKS cluster

```bash
eksctl create cluster \
  --name healthlife-prod \
  --region us-east-1 \
  --nodegroup-name standard \
  --node-type m5.xlarge \
  --nodes 3 \
  --nodes-min 3 \
  --nodes-max 10 \
  --managed
```

### 2. Install cluster add-ons

```bash
# cert-manager (TLS)
helm repo add jetstack https://charts.jetstack.io
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set installCRDs=true

# nginx ingress
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace

# External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets --create-namespace

# VPA (optional — for resource recommendations)
helm repo add fairwinds-stable https://charts.fairwinds.com/stable
helm upgrade --install vpa fairwinds-stable/vpa --namespace kube-system
```

### 3. Configure secrets in AWS Secrets Manager

```bash
# Create secrets in AWS Secrets Manager
aws secretsmanager create-secret \
  --name healthlife/production/jwt-secret \
  --secret-string "$(openssl rand -base64 64)"

aws secretsmanager create-secret \
  --name healthlife/production/db-password \
  --secret-string "$(openssl rand -base64 32)"

aws secretsmanager create-secret \
  --name healthlife/production/claude-api-key \
  --secret-string "your-claude-api-key"

aws secretsmanager create-secret \
  --name healthlife/production/firebase-service-account-json \
  --secret-string "$(cat firebase-service-account.json)"
```

### 4. Apply External Secrets

```bash
# Update the IAM role ARN in k8s/base/external-secrets.yaml first
kubectl create namespace healthlife
kubectl apply -f k8s/base/external-secrets.yaml
```

### 5. Configure DNS and TLS

```bash
# Get the ingress LoadBalancer hostname
kubectl get svc ingress-nginx-controller -n ingress-nginx

# Create DNS A record pointing healthlife.com → LoadBalancer IP

# Create ClusterIssuer for Let's Encrypt
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ops@healthlife.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
EOF
```

### 6. Deploy application

```bash
helm upgrade --install healthlife k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=sha-$(git rev-parse --short HEAD) \
  --set ingress.host=healthlife.com \
  --set cors.allowedOrigins="https://healthlife.com,https://app.healthlife.com" \
  --values k8s/helm/healthlife/values.yaml \
  --wait --timeout 10m
```

### 7. Deploy monitoring

```bash
GRAFANA_ADMIN_PASSWORD="$(openssl rand -base64 16)" \
SLACK_WEBHOOK_URL="https://hooks.slack.com/your-webhook" \
PAGERDUTY_ROUTING_KEY="your-pagerduty-key" \
./k8s/monitoring/deploy-monitoring.sh
```

### 8. Run smoke test

```bash
BASE_URL=https://healthlife.com \
TEST_USER_EMAIL=loadtest@healthlife.com \
TEST_USER_PASSWORD=LoadTest@2025! \
k6 run load-tests/k6/scenarios/smoke.js
```

---

## Rolling Updates

```bash
# Update image tag (triggers rolling update)
helm upgrade healthlife k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=sha-$(git rev-parse --short HEAD) \
  --reuse-values \
  --wait --timeout 5m
```

## Rollback

```bash
# List revisions
helm history healthlife -n healthlife

# Rollback to previous revision
helm rollback healthlife -n healthlife

# Rollback to specific revision
helm rollback healthlife 3 -n healthlife

# Verify
kubectl rollout status deployment/gateway-service -n healthlife
```

## Database Restore

```bash
# Restore from pg_dump backup
kubectl exec -it postgres-0 -n healthlife -- \
  psql -U healthlife healthlife_auth < backup/healthlife_auth_20250101.sql
```

---

## Production Checklist

### Before first deploy
- [ ] Secrets created in AWS Secrets Manager (not in git)
- [ ] External Secrets Operator configured and syncing
- [ ] DNS records pointing to LoadBalancer
- [ ] TLS certificate issued by cert-manager
- [ ] CORS_ALLOWED_ORIGINS set to production domain
- [ ] Firebase service account configured (for push notifications)
- [ ] Claude API key configured (for AI Coach)
- [ ] SMTP credentials configured (for email)

### Before every release
- [ ] `mvn spotless:check` passes
- [ ] All tests pass: `mvn test -B`
- [ ] k6 smoke test passes against staging
- [ ] k6 load test passes against staging (weekly)
- [ ] OWASP + Trivy scans pass in CI
- [ ] Helm lint passes

### After deploy
- [ ] All pods Running: `kubectl get pods -n healthlife`
- [ ] HPA not at max replicas: `kubectl get hpa -n healthlife`
- [ ] No alerts firing in Grafana
- [ ] Error rate < 1% in Grafana Service Overview dashboard
- [ ] P95 latency < 2s in Grafana

---

## Useful Commands

```bash
# Watch pod status
kubectl get pods -n healthlife -w

# Tail logs for a service
kubectl logs -l app=gateway-service -n healthlife -f --tail=100

# Check resource usage
kubectl top pods -n healthlife --sort-by=memory

# Scale a service manually
kubectl scale deployment auth-service --replicas=4 -n healthlife

# Get all HPA status
kubectl get hpa -n healthlife

# Describe a failing pod
kubectl describe pod <pod-name> -n healthlife

# Execute into a pod
kubectl exec -it <pod-name> -n healthlife -- sh

# Check Flyway migration status
kubectl exec -it <pod-name> -n healthlife -- \
  sh -c "java -jar app.jar --spring.flyway.validate-on-migrate=true"
```
