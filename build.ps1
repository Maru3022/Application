# HealthLife Build Automation Script
param(
    [Parameter()]
    [ValidateSet("build", "test", "verify", "format-check", "format-apply", "docker-build", "k8s-deploy", "k8s-undeploy", "local-up", "local-down", "security-scan", "help")]
    [string]$Target = "help"
)

function Show-Help {
    Write-Host "HealthLife Build Automation" -ForegroundColor Cyan
    Write-Host "Usage: .\build.ps1 -Target <command>" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Green
    Write-Host "  build        Compile all modules"
    Write-Host "  test         Run unit tests"
    Write-Host "  verify       Run full verification (tests, integration tests, coverage)"
    Write-Host "  format-check Check code formatting with Spotless"
    Write-Host "  format-apply Apply code formatting with Spotless"
    Write-Host "  docker-build Build all Docker images locally"
    Write-Host "  k8s-deploy   Deploy to local Kubernetes"
    Write-Host "  k8s-undeploy Remove from local Kubernetes"
    Write-Host "  local-up     Start local infrastructure (Postgres, Redis, Kafka)"
    Write-Host "  local-down   Stop local infrastructure"
    Write-Host "  security-scan Run OWASP dependency check"
}

switch ($Target) {
    "build" {
        mvn clean compile -B
    }
    "test" {
        mvn test -B
    }
    "verify" {
        mvn clean verify -B
    }
    "format-check" {
        mvn spotless:check -B
    }
    "format-apply" {
        mvn spotless:apply -B
    }
    "docker-build" {
        Get-ChildItem -Path "services" -Directory | ForEach-Object {
            $name = $_.Name
            Write-Host "Building $name..."
            docker build -t "healthlife/$name`:latest" $_.FullName
        }
    }
    "k8s-deploy" {
        kubectl apply -f k8s/base/namespace.yaml
        kubectl apply -f k8s/base/
    }
    "k8s-undeploy" {
        kubectl delete -f k8s/base/ --ignore-not-found=true
    }
    "local-up" {
        docker-compose -f infrastructure/docker-compose.yml up -d
    }
    "local-down" {
        docker-compose -f infrastructure/docker-compose.yml down
    }
    "security-scan" {
        mvn org.owasp:dependency-check-maven:check -B
    }
    default {
        Show-Help
    }
}
