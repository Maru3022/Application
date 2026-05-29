#!/usr/bin/env bash
# Forward http://localhost:8080 -> gateway-service (kind ClusterIP is not on the host until you forward).
set -euo pipefail

ctx="$(kubectl config current-context 2>/dev/null || true)"
if [[ "$ctx" != "kind-healthlife" ]]; then
  echo "[HealthLife] Switching kubectl context to kind-healthlife..."
  kubectl config use-context kind-healthlife
fi

echo ""
echo "  Gateway port-forward:  http://localhost:8080  ->  svc/gateway-service:80"
echo "  Health:                http://localhost:8080/internal/actuator/health"
echo ""
echo "  (Press Ctrl+C to stop.)"
echo ""

exec kubectl port-forward svc/gateway-service 8080:80 -n healthlife
