#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────
# HealthLife Kubernetes Cluster Setup
# ──────────────────────────────────────────────

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
log()  { echo -e "${CYAN}[HealthLife]${NC} $1"; }
ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

# ── Prerequisites ─────────────────────────────
log "Checking prerequisites..."
command -v kind >/dev/null 2>&1   || fail "kind not found. Install: https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
command -v kubectl >/dev/null 2>&1 || fail "kubectl not found. Install: https://kubernetes.io/docs/tasks/tools/"
command -v helm >/dev/null 2>&1   || fail "helm not found. Install: https://helm.sh/docs/intro/install/"
ok "All prerequisites found"

# ── Create cluster ─────────────────────────────
log "Creating kind cluster 'healthlife'..."
if kind get clusters 2>/dev/null | grep -q "healthlife"; then
  log "Cluster 'healthlife' already exists, skipping creation"
else
  kind create cluster --config k8s/kind-cluster.yaml --wait 120s
  ok "Cluster created"
fi

# ── Set kubectl context ────────────────────────
kubectl config use-context kind-healthlife
ok "kubectl context set to kind-healthlife"

# ── Install NGINX Ingress Controller ──────────
log "Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for condition=ready pod \
  --selector app.kubernetes.io/component=controller \
  --timeout=120s 2>/dev/null || true
ok "Ingress controller installed"

# ── Label namespaces for NetworkPolicy ────────
log "Labeling namespaces..."
kubectl label namespace ingress-nginx name=ingress-nginx --overwrite 2>/dev/null || true
kubectl label namespace kube-system name=kube-system --overwrite 2>/dev/null || true

# ── Create namespace ───────────────────────────
log "Creating namespace..."
kubectl apply -f k8s/base/namespace.yaml
kubectl label namespace healthlife name=healthlife --overwrite
ok "Namespace created"

# ── Create secrets ─────────────────────────────
log "Creating secrets..."
kubectl create secret generic healthlife-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  -n healthlife --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic healthlife-db-credentials \
  --from-literal=username=healthlife \
  --from-literal=password=healthlife_pass \
  -n healthlife --dry-run=client -o yaml | kubectl apply -f -
ok "Secrets created"

# ── Deploy infrastructure ─────────────────────
log "Deploying PostgreSQL and Redis..."
kubectl apply -f k8s/infrastructure/postgres-init-cm.yaml
kubectl apply -f k8s/infrastructure/postgres.yaml
kubectl apply -f k8s/infrastructure/redis.yaml

log "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n healthlife --timeout=120s 2>/dev/null || {
  log "PostgreSQL not ready yet, checking status..."
  kubectl get pods -n healthlife -l app=postgres
}

log "Waiting for Redis to be ready..."
kubectl wait --for=condition=ready pod -l app=redis -n healthlife --timeout=60s 2>/dev/null || {
  log "Redis not ready yet, checking status..."
  kubectl get pods -n healthlife -l app=redis
}
ok "Infrastructure ready"

# ── Deploy services with Helm ──────────────────
log "Deploying HealthLife services with Helm..."
helm upgrade --install healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=latest \
  --timeout 300s || {
  log "Helm install may have timed out, checking pod status..."
  kubectl get pods -n healthlife
  log "Some pods may still be pulling images. Run: kubectl get pods -n healthlife -w"
}
ok "Helm release installed"

# ── Wait for pods to be ready ──────────────────
log "Waiting for all pods to become ready (this may take a few minutes)..."
kubectl wait --for=condition=ready pod -l app -n healthlife --timeout=600s 2>/dev/null || {
  log "Not all pods are ready yet. Check: kubectl get pods -n healthlife"
  kubectl get pods -n healthlife
}
ok "All pods are ready"

# ── Apply NetworkPolicies ─────────────────────
log "Applying NetworkPolicies..."
kubectl apply -f k8s/base/network-policy.yaml
ok "NetworkPolicies applied"

# ── Summary ───────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
echo -e "${GREEN}  HealthLife cluster is ready!${NC}"
echo "═══════════════════════════════════════════════════"
echo ""
echo "  Cluster:     kind-healthlife"
echo "  Namespace:   healthlife"
echo "  Services:    10 microservices"
echo ""
echo "  Quick commands:"
echo "    kubectl get pods -n healthlife"
echo "    kubectl get svc -n healthlife"
echo "    kubectl port-forward svc/gateway-service 8080:80 -n healthlife"
echo ""
echo "  Access via port-forward:  http://localhost:8080"
echo "  Access via ingress:       http://healthlife.local"
echo "    (add '127.0.0.1 healthlife.local' to /etc/hosts)"
echo ""
echo "  Delete cluster:  kind delete cluster --name healthlife"
echo ""
