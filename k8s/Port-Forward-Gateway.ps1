# Forward local http://localhost:8080 -> gateway-service in kind (ClusterIP has no host port by default).
# Leave this window open while you use the browser or curl.
$ErrorActionPreference = "Stop"

$ctx = kubectl config current-context 2>$null
if ($ctx -ne "kind-healthlife") {
    Write-Host "[HealthLife] Switching kubectl context to kind-healthlife..."
    kubectl config use-context kind-healthlife
}

Write-Host ""
Write-Host "  Gateway port-forward:  http://localhost:8080  ->  svc/gateway-service:80"
Write-Host "  Health:                http://localhost:8080/internal/actuator/health"
Write-Host ""
Write-Host "  (Press Ctrl+C to stop. Closing this window stops the tunnel.)"
Write-Host ""

kubectl port-forward svc/gateway-service 8080:80 -n healthlife
