#!/bin/bash
# HealthLife Kubernetes Deployment Script
# Automates steps 6.1-6.6 from deployment guide
# 
# Usage: ./scripts/deploy.sh
# Prerequisites: kubectl, helm, k3s cluster with Traefik

set -euo pipefail

# Configuration
NAMESPACE="healthlife"
MONITORING_NAMESPACE="monitoring"
POSTGRES_PASSWORD="${DB_PASSWORD:-}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 32)}"
GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warn() {
    echo -e "${YELLOW}[WARN] $1${NC}"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}"
    exit 1
}

info() {
    echo -e "${BLUE}[INFO] $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."
    
    command -v kubectl >/dev/null 2>&1 || error "kubectl is not installed"
    command -v helm >/dev/null 2>&1 || error "helm is not installed"
    
    kubectl cluster-info >/dev/null 2>&1 || error "Cannot connect to Kubernetes cluster"
    [[ -n "$POSTGRES_PASSWORD" ]] || error "DB_PASSWORD is required (do not use default DB credentials in deployment)"
    [[ -n "$GRAFANA_ADMIN_PASSWORD" ]] || error "GRAFANA_ADMIN_PASSWORD is required"
    
    # Check if Traefik is the default ingress
    kubectl get ingressclass traefik >/dev/null 2>&1 || warn "Traefik IngressClass not found - ensure k3s with Traefik"
    
    log "Prerequisites check passed"
}

# 6.1 Create namespace
create_namespace() {
    log "Creating namespace: $NAMESPACE"
    kubectl apply -f k8s/base/namespace.yaml
    kubectl create namespace $MONITORING_NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
}

# 6.2 Create secrets (with placeholders)
create_secrets() {
    log "Creating secrets (using placeholder values - REPLACE IN PRODUCTION)"
    
    # JWT secret
    kubectl create secret generic healthlife-secrets \
        --namespace=$NAMESPACE \
        --from-literal=jwt-secret="$JWT_SECRET" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Database credentials
    kubectl create secret generic healthlife-db-credentials \
        --namespace=$NAMESPACE \
        --from-literal=username=healthlife \
        --from-literal=password="$POSTGRES_PASSWORD" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Mail credentials (placeholder)
    kubectl create secret generic healthlife-mail-credentials \
        --namespace=$NAMESPACE \
        --from-literal=username="smtp@example.com" \
        --from-literal=password="SMTP_PASSWORD_PLACEHOLDER" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    info "⚠️  Replace placeholder secrets with real values in production!"
}

# 6.3 Install PostgreSQL
install_postgresql() {
    log "Installing PostgreSQL..."
    
    helm upgrade --install postgres bitnami/postgresql \
        --namespace=$NAMESPACE \
        --set auth.username=healthlife \
        --set auth.password="$POSTGRES_PASSWORD" \
        --set auth.database=healthlife_auth \
        --set primary.persistence.size=10Gi \
        --set fullnameOverride=postgres \
        --wait
    
    # Create additional databases
    log "Creating additional databases..."
    local databases=("healthlife_user" "healthlife_healthdata" "healthlife_mental" 
                    "healthlife_nutrition" "healthlife_social" "healthlife_payment"
                    "healthlife_aicoach" "healthlife_notification" "healthlife_analytics")
    
    for db in "${databases[@]}"; do
        kubectl exec -n $NAMESPACE postgres-0 -- psql -U healthlife -d healthlife_auth \
            -c "CREATE DATABASE $db;" 2>/dev/null || true
    done
    
    log "PostgreSQL and databases created"
}

# 6.4 Install Redis
install_redis() {
    log "Installing Redis..."
    
    helm upgrade --install redis bitnami/redis \
        --namespace=$NAMESPACE \
        --set auth.enabled=false \
        --set master.persistence.size=5Gi \
        --set fullnameOverride=redis \
        --wait
    
    log "Redis installed"
}

# 6.5 Deploy HealthLife services
deploy_services() {
    log "Deploying HealthLife services..."
    
    # Apply base manifests
    kubectl apply -f k8s/base/
    
    # Deploy via Helm
    helm upgrade --install healthlife k8s/helm/healthlife \
        --namespace=$NAMESPACE \
        --values k8s/helm/healthlife/values.yaml \
        --set image.tag=latest \
        --wait
    
    log "HealthLife services deployed"
}

# 6.6 Deploy monitoring
deploy_monitoring() {
    log "Deploying monitoring stack..."
    
    # Add Helm repo
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo update
    
    # Deploy Prometheus + Grafana
    helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
        --namespace=$MONITORING_NAMESPACE \
        --values k8s/monitoring/prometheus-stack-values.yaml \
        --set grafana.adminPassword="$GRAFANA_ADMIN_PASSWORD" \
        --wait
    
    log "Monitoring deployed"
}

# Wait for pods to be ready
wait_for_ready() {
    log "Waiting for all pods to be ready..."
    
    # Wait for HealthLife pods
    kubectl wait --for=condition=ready pod -l app=healthlife -n $NAMESPACE --timeout=300s
    kubectl wait --for=condition=ready pod -l app=healthlife -n $NAMESPACE --timeout=300s
    
    # Wait for monitoring pods
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=prometheus -n $MONITORING_NAMESPACE --timeout=300s
    
    log "All pods are ready"
}

# Show deployment status
show_status() {
    log "Deployment completed! 🎉"
    echo ""
    info "=== Access URLs ==="
    info "API: https://api.healthlife.app"
    info "App: https://app.healthlife.app"
    info "Admin: https://admin.healthlife.app"
    info "Monitoring: https://monitoring.healthlife.app"
    echo ""
    info "=== Grafana Credentials ==="
    info "Username: admin"
    info "Password: $GRAFANA_ADMIN_PASSWORD"
    echo ""
    info "=== Commands to check status ==="
    info "kubectl get pods -n $NAMESPACE"
    info "kubectl get ingress -n $NAMESPACE"
    info "kubectl get ingress -n $MONITORING_NAMESPACE"
}

# Main execution
main() {
    log "Starting HealthLife deployment..."
    echo ""
    
    check_prerequisites
    create_namespace
    create_secrets
    install_postgresql
    install_redis
    deploy_services
    deploy_monitoring
    wait_for_ready
    show_status
    
    log "Deployment script completed successfully!"
}

# Handle script interruption
trap 'error "Script interrupted"; exit 1' INT TERM

# Run main function
main "$@"
