# ──────────────────────────────────────────────
# HealthLife Kubernetes cluster setup (Windows, 11 microservices + infra)
# ──────────────────────────────────────────────
param(
    # Opens a second PowerShell window that runs k8s/Port-Forward-Gateway.ps1 (required for http://localhost:8080).
    [switch]$StartGatewayPortForward
)

$ErrorActionPreference = "Stop"

function Log($msg) { Write-Host "`e[36m[HealthLife]`e[0m $msg" }
function Ok($msg)   { Write-Host "`e[32m[OK]`e[0m $msg" }
function Fail($msg) { Write-Host "`e[31m[FAIL]`e[0m $msg"; exit 1 }

# ── Prerequisites ─────────────────────────────
Log "Checking prerequisites..."
$tools = @("kind", "kubectl", "helm")
foreach ($t in $tools) {
    if (-not (Get-Command $t -ErrorAction SilentlyContinue)) {
        Fail "$t not found. Install it first (see docs/K8S-SETUP.md)"
    }
}
Ok "All prerequisites found"

# ── Create cluster ─────────────────────────────
Log "Creating kind cluster 'healthlife'..."
$clusters = kind get clusters 2>$null
if ($clusters -contains "healthlife") {
    Log "Cluster 'healthlife' already exists, skipping creation"
} else {
    kind create cluster --config k8s/kind-cluster.yaml --wait 120s
    Ok "Cluster created"
}

# ── Set kubectl context ────────────────────────
kubectl config use-context kind-healthlife
Ok "kubectl context set to kind-healthlife"

# ── Install NGINX Ingress Controller ──────────
Log "Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx `
    --for condition=ready pod `
    --selector app.kubernetes.io/component=controller `
    --timeout=120s 2>$null
Ok "Ingress controller installed"

# ── Label namespaces for NetworkPolicy ────────
Log "Labeling namespaces..."
kubectl label namespace ingress-nginx name=ingress-nginx --overwrite 2>$null
kubectl label namespace kube-system name=kube-system --overwrite 2>$null

# ── Create namespace ───────────────────────────
Log "Creating namespace..."
kubectl apply -f k8s/base/namespace.yaml
kubectl label namespace healthlife name=healthlife --overwrite
Ok "Namespace created"

# ── Create secrets ─────────────────────────────
Log "Creating secrets..."
$jwtSecret = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
kubectl create secret generic healthlife-secrets `
    --from-literal=jwt-secret=$jwtSecret `
    -n healthlife --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic healthlife-db-credentials `
    --from-literal=username=healthlife `
    --from-literal=password=healthlife_pass `
    -n healthlife --dry-run=client -o yaml | kubectl apply -f -
Ok "Secrets created"

# ── GHCR pull secret (required for private images unless using kind load) ──
$ghcrUser = $env:GHCR_USERNAME
if (-not $ghcrUser) { $ghcrUser = "Maru3022" }
if ($env:GHCR_TOKEN) {
    Log "Creating ghcr-secret from GHCR_TOKEN..."
    kubectl create secret docker-registry ghcr-secret `
        --namespace=healthlife `
        --docker-server=ghcr.io `
        --docker-username=$ghcrUser `
        --docker-password=$env:GHCR_TOKEN `
        --dry-run=client -o yaml | kubectl apply -f -
    Ok "ghcr-secret applied"
} else {
    Log "GHCR_TOKEN not set: if pods stay ImagePullBackOff, create ghcr-secret (see k8s/base/registry-secret.yaml)"
}

# ── Deploy infrastructure ─────────────────────
Log "Deploying PostgreSQL and Redis..."
kubectl apply -f k8s/infrastructure/postgres-init-cm.yaml
kubectl apply -f k8s/infrastructure/postgres.yaml
kubectl apply -f k8s/infrastructure/redis.yaml

Log "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n healthlife --timeout=120s 2>$null
if ($LASTEXITCODE -ne 0) {
    Log "PostgreSQL not ready yet, checking status..."
    kubectl get pods -n healthlife -l app=postgres
}

Log "Waiting for Redis to be ready..."
kubectl wait --for=condition=ready pod -l app=redis -n healthlife --timeout=60s 2>$null
if ($LASTEXITCODE -ne 0) {
    Log "Redis not ready yet, checking status..."
    kubectl get pods -n healthlife -l app=redis
}
Ok "Infrastructure ready"

# ── Deploy services with Helm ──────────────────
Log "Deploying HealthLife services with Helm..."
helm upgrade --install healthlife ./k8s/helm/healthlife `
    --namespace healthlife `
    --set image.tag=latest `
    --timeout 300s
if ($LASTEXITCODE -ne 0) {
    Log "Helm install may have timed out, checking pod status..."
    kubectl get pods -n healthlife
    Log "Some pods may still be pulling images. Run: kubectl get pods -n healthlife -w"
} else {
    Ok "Helm release installed"
}

# ── Wait for pods to be ready ──────────────────
Log "Waiting for all pods to become ready (this may take a few minutes)..."
kubectl wait --for=condition=ready pod -l app -n healthlife --timeout=600s 2>$null
if ($LASTEXITCODE -ne 0) {
    Log "Not all pods are ready yet. Check: kubectl get pods -n healthlife"
    kubectl get pods -n healthlife
} else {
    Ok "All pods are ready"
}

# ── Apply NetworkPolicies ─────────────────────
Log "Applying NetworkPolicies..."
kubectl apply -f k8s/base/network-policy.yaml
Ok "NetworkPolicies applied"

# ── Summary ───────────────────────────────────
Write-Host ""
Write-Host "═══════════════════════════════════════════════════"
Write-Host "`e[32m  HealthLife cluster is ready!`e[0m"
Write-Host "═══════════════════════════════════════════════════"
Write-Host ""
Write-Host "  Cluster:     kind-healthlife"
Write-Host "  Namespace:   healthlife"
Write-Host "  Services:    11 microservices"
Write-Host ""
Write-Host "  IMPORTANT — ClusterIP has no port on your PC until you port-forward."
Write-Host "  Without a running forward, the browser shows ERR_CONNECTION_REFUSED on :8080."
Write-Host ""
Write-Host "  Open gateway in browser:"
Write-Host "    .\k8s\Port-Forward-Gateway.ps1"
Write-Host "    (or re-run: .\k8s\Start-Cluster.ps1 -StartGatewayPortForward)"
Write-Host ""
Write-Host "  Quick commands:"
Write-Host "    kubectl get pods -n healthlife"
Write-Host "    kubectl get svc -n healthlife"
Write-Host "    kubectl port-forward svc/gateway-service 8080:80 -n healthlife"
Write-Host ""
Write-Host "  Access via port-forward:  http://localhost:8080"
Write-Host "  Access via ingress:       http://healthlife.local"
Write-Host "    (add '127.0.0.1 healthlife.local' to C:\Windows\System32\drivers\etc\hosts)"
Write-Host ""
Write-Host "  Delete cluster:  kind delete cluster --name healthlife"
Write-Host ""

if ($StartGatewayPortForward) {
    $pf = Join-Path $PSScriptRoot "Port-Forward-Gateway.ps1"
    Log "Starting gateway port-forward in a new window..."
    Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", $pf)
    Ok "New window opened — use http://localhost:8080 when it says Forwarding"
}
