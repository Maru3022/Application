# HealthLife Kubernetes Deployment Commands

## Prerequisites

- k3s cluster with Traefik (default)
- kubectl configured to point to k3s
- Helm 3+ installed
- cert-manager installed with ClusterIssuer `letsencrypt-prod`

## 1. Create Namespace

```bash
kubectl apply -f k8s/base/namespace.yaml
```

## 2. Create Secrets

**⚠️ Never commit real secrets to git! Replace placeholder values.**

```bash
# JWT secret (minimum 64 characters for HS256)
kubectl create secret generic healthlife-secrets \
  --namespace=healthlife \
  --from-literal=jwt-secret='YOUR_JWT_SECRET_MIN_64_CHARS'

# Database credentials
kubectl create secret generic healthlife-db-credentials \
  --namespace=healthlife \
  --from-literal=username=healthlife \
  --from-literal=password=YOUR_DB_PASSWORD

# AI Coach API key
kubectl create secret generic healthlife-secrets \
  --namespace=healthlife \
  --from-literal=deepseek-api-key='YOUR_DEEPSEEK_API_KEY'

# Stripe secrets
kubectl create secret generic healthlife-secrets \
  --namespace=healthlife \
  --from-literal=stripe-secret-key='sk_live_...' \
  --from-literal=stripe-webhook-secret='whsec_...' \
  --from-literal=stripe-price-pro='price_...' \
  --from-literal=stripe-price-premium='price_...' \
  --from-literal=stripe-price-family='price_...'

# OAuth secrets
kubectl create secret generic healthlife-secrets \
  --namespace=healthlife \
  --from-literal=oauth-google-client-id='YOUR_GOOGLE_CLIENT_ID' \
  --from-literal=oauth-apple-audience='YOUR_APPLE_AUDIENCE'

# Mail credentials
kubectl create secret generic healthlife-mail-credentials \
  --namespace=healthlife \
  --from-literal=username='YOUR_SMTP_USERNAME' \
  --from-literal=password='YOUR_SMTP_PASSWORD'

# Firebase service account (base64 encoded)
kubectl create secret generic healthlife-secrets \
  --namespace=healthlife \
  --from-file=firebase-service-account-json=path/to/firebase-service-account.json
```

## 3. Install PostgreSQL

```bash
# Primary database (auth)
helm upgrade --install postgres bitnami/postgresql \
  --namespace=healthlife \
  --set auth.username=healthlife \
  --set auth.password=YOUR_DB_PASSWORD \
  --set auth.database=healthlife_auth \
  --set primary.persistence.size=10Gi \
  --set fullnameOverride=postgres \
  --wait

# Create additional databases
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_user;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_healthdata;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_mental;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_nutrition;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_social;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_payment;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_aicoach;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_notification;"
kubectl exec -n healthlife postgres-0 -- psql -U healthlife -d healthlife_auth -c "CREATE DATABASE healthlife_analytics;"
```

## 4. Install Redis

```bash
helm upgrade --install redis bitnami/redis \
  --namespace=healthlife \
  --set auth.enabled=false \
  --set master.persistence.size=5Gi \
  --set fullnameOverride=redis \
  --wait
```

## 5. Deploy HealthLife Services

```bash
# Apply all base manifests
kubectl apply -f k8s/base/

# Deploy via Helm (recommended)
helm upgrade --install healthlife k8s/helm/healthlife \
  --namespace=healthlife \
  --values k8s/helm/healthlife/values.yaml \
  --set image.tag=latest \
  --wait
```

## 6. Deploy Monitoring

```bash
# Create monitoring namespace
kubectl create namespace monitoring

# Install Prometheus + Grafana
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace=monitoring \
  --values k8s/monitoring/prometheus-stack-values.yaml \
  --wait
```

## 7. Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n healthlife

# Check services
kubectl get services -n healthlife

# Check ingresses
kubectl get ingress -n healthlife
kubectl get ingress -n monitoring

# Test API endpoint
curl -k https://api.healthlife.app/api/v1/auth/health
```

## 8. Access URLs

- **API**: https://api.healthlife.app
- **App**: https://app.healthlife.app
- **Admin**: https://admin.healthlife.app
- **Monitoring**: https://monitoring.healthlife.app (Grafana)

## 9. Cleanup

```bash
# Remove all HealthLife resources
helm uninstall healthlife --namespace=healthlife
helm uninstall prometheus --namespace=monitoring
helm uninstall postgres --namespace=healthlife
helm uninstall redis --namespace=healthlife
kubectl delete namespace healthlife
kubectl delete namespace monitoring
```
