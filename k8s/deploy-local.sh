#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# HealthLife — Local Kubernetes Deploy Script (Kind)
# Usage: ./k8s/deploy-local.sh [--clean]
# ============================================================

CLUSTER_NAME="healthlife"
NAMESPACE="healthlife"
K8S_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$K8S_DIR")"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
die()  { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ---- Dependency checks ----
for cmd in docker kind kubectl mvn; do
  command -v "$cmd" &>/dev/null || die "Required command not found: $cmd"
done

# ---- Optionally destroy existing cluster ----
if [[ "${1:-}" == "--clean" ]]; then
  warn "Deleting existing cluster..."
  kind delete cluster --name "$CLUSTER_NAME" 2>/dev/null || true
fi

# ---- Create Kind cluster if not exists ----
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  log "Creating Kind cluster: $CLUSTER_NAME"
  kind create cluster --name "$CLUSTER_NAME" --config "$K8S_DIR/kind-cluster.yaml"
else
  log "Kind cluster '$CLUSTER_NAME' already exists — skipping creation"
fi

kubectl cluster-info --context "kind-${CLUSTER_NAME}"

# ---- Install NGINX Ingress Controller ----
log "Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl rollout status deployment/ingress-nginx-controller -n ingress-nginx --timeout=120s

# ---- Build Maven project ----
log "Building Maven project (skipping tests)..."
cd "$ROOT_DIR"
mvn -B clean package -DskipTests --no-transfer-progress -q

# ---- Build Docker images and load into Kind ----
SERVICES=(auth-service user-service health-data-service mental-service nutrition-service ai-coach-service social-service notification-service analytics-service gateway-service)
IMAGE_PREFIX="ghcr.io/maru3022/application"

for svc in "${SERVICES[@]}"; do
  log "Building Docker image: $svc"
  docker build -t "${IMAGE_PREFIX}/${svc}:latest" "$ROOT_DIR/services/$svc" -q
  log "Loading $svc into Kind cluster..."
  kind load docker-image "${IMAGE_PREFIX}/${svc}:latest" --name "$CLUSTER_NAME"
done

# ---- Apply namespace ----
kubectl apply -f "$K8S_DIR/base/namespace.yaml"

# ---- Create Secrets (with default dev values) ----
log "Creating secrets..."
kubectl -n "$NAMESPACE" create secret generic healthlife-secrets \
  --from-literal=jwt-secret="HealthLifeLocalDevSecretKeyAtLeast256BitsLong2025ForHS256" \
  --from-literal=claude-api-key="${CLAUDE_API_KEY:-}" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" create secret generic healthlife-db-credentials \
  --from-literal=username="healthlife" \
  --from-literal=password="healthlife_pass" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" create secret generic healthlife-mail-credentials \
  --from-literal=username="" \
  --from-literal=password="" \
  --dry-run=client -o yaml | kubectl apply -f -

# ---- Apply infrastructure ----
log "Deploying infrastructure (Postgres, Redis)..."
kubectl apply -f "$K8S_DIR/infrastructure/postgres-init-cm.yaml"
kubectl apply -f "$K8S_DIR/infrastructure/postgres.yaml"
kubectl apply -f "$K8S_DIR/infrastructure/redis.yaml"

log "Waiting for Postgres to be ready..."
kubectl rollout status statefulset/postgres -n "$NAMESPACE" --timeout=120s
log "Waiting for Redis to be ready..."
kubectl rollout status deployment/redis -n "$NAMESPACE" --timeout=60s

# ---- Apply all services ----
log "Deploying microservices..."
for f in "$K8S_DIR"/base/*.yaml; do
  [[ "$f" == *namespace* ]] && continue
  kubectl apply -f "$f"
done

# ---- Wait for all deployments ----
log "Waiting for all deployments to be ready..."
kubectl get deployments -n "$NAMESPACE" -o name | while read -r dep; do
  kubectl rollout status "$dep" -n "$NAMESPACE" --timeout=180s
done

# ---- Add /etc/hosts entry ----
if ! grep -q "healthlife.local" /etc/hosts; then
  warn "Add this line to /etc/hosts (requires sudo):"
  warn "  127.0.0.1  healthlife.local"
  warn "Run: echo '127.0.0.1 healthlife.local' | sudo tee -a /etc/hosts"
fi

log ""
log "=========================================="
log " HealthLife is running!"
log " Gateway:  http://healthlife.local/api/v1"
log " Docs:     http://healthlife.local/swagger-ui.html"
log "=========================================="
