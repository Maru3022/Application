#!/usr/bin/env bash
# deploy-monitoring.sh — Install / upgrade the full monitoring stack.
#
# Usage:
#   GRAFANA_ADMIN_PASSWORD=secret \
#   SLACK_WEBHOOK_URL=https://hooks.slack.com/... \
#   PAGERDUTY_ROUTING_KEY=abc123 \
#   ./k8s/monitoring/deploy-monitoring.sh
#
# Prerequisites: helm, kubectl configured for the target cluster.

set -euo pipefail

NAMESPACE="monitoring"
RELEASE="prometheus"
VALUES_FILE="$(dirname "$0")/prometheus-stack-values.yaml"

if [[ -z "${GRAFANA_ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: GRAFANA_ADMIN_PASSWORD is required. Refusing to deploy Grafana with a default password."
  echo "Set it explicitly, e.g.:"
  echo "  GRAFANA_ADMIN_PASSWORD='strong-password' ./k8s/monitoring/deploy-monitoring.sh"
  exit 1
fi

echo "==> Adding Prometheus Community Helm repo..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

echo "==> Creating namespace ${NAMESPACE} (if not exists)..."
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "==> Installing / upgrading kube-prometheus-stack..."
helm upgrade --install "${RELEASE}" prometheus-community/kube-prometheus-stack \
  --namespace "${NAMESPACE}" \
  --values "${VALUES_FILE}" \
  --set "grafana.adminPassword=${GRAFANA_ADMIN_PASSWORD}" \
  --set "alertmanager.config.global.smtp_auth_password=${ALERTMANAGER_SMTP_PASSWORD:-}" \
  --set "alertmanager.config.receivers[2].slack_configs[0].api_url=${SLACK_WEBHOOK_URL:-}" \
  --set "alertmanager.config.receivers[2].pagerduty_configs[0].routing_key=${PAGERDUTY_ROUTING_KEY:-}" \
  --wait --timeout 10m

echo "==> Applying HealthLife ServiceMonitor and PrometheusRules..."
kubectl apply -f "$(dirname "$0")/servicemonitor.yaml"
kubectl apply -f "$(dirname "$0")/alertmanager-rules.yaml"

echo "==> Applying Grafana dashboards ConfigMap..."
kubectl apply -f "$(dirname "$0")/grafana-dashboards.yaml"

echo ""
echo "✅ Monitoring stack deployed successfully."
echo ""
echo "   Grafana:     kubectl port-forward svc/prometheus-grafana 3000:80 -n ${NAMESPACE}"
echo "                then open http://localhost:3000 (admin / \${GRAFANA_ADMIN_PASSWORD})"
echo ""
echo "   Prometheus:  kubectl port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090 -n ${NAMESPACE}"
echo "                then open http://localhost:9090"
echo ""
echo "   Alertmanager: kubectl port-forward svc/prometheus-kube-prometheus-alertmanager 9093:9093 -n ${NAMESPACE}"
