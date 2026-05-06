# HealthLife — Health & Wellness Platform

Production-ready, cloud-native microservices platform for health and wellness management.
Built on **Spring Boot 3.2 / Java 21 / Kubernetes**.

[![CI/CD](https://github.com/maru3022/application/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/maru3022/application/actions/workflows/ci-cd.yml)
[![Load Tests](https://github.com/maru3022/application/actions/workflows/load-tests.yml/badge.svg)](https://github.com/maru3022/application/actions/workflows/load-tests.yml)

---

## Table of Contents

1. [Architecture](#architecture)
2. [Services](#services)
3. [Technology Stack](#technology-stack)
4. [Quick Start (Local)](#quick-start-local)
5. [Running on Kubernetes](#running-on-kubernetes)
6. [Monitoring](#monitoring)
7. [Load Testing](#load-testing)
8. [Environment Variables](#environment-variables)
9. [API Documentation](#api-documentation)
10. [Security](#security)
11. [Project Structure](#project-structure)
12. [Contributing](#contributing)

---

## Architecture

HealthLife is a **database-per-service** microservices system. All external traffic enters through
the Gateway, which handles JWT validation, rate limiting (per-user + per-IP), circuit breaking,
and security headers.

```
Mobile App (React Native / Expo)
        │
        ▼ HTTPS
┌───────────────────┐
│   Gateway :8080   │  ← rate limit, circuit breaker, security headers
└─────────┬─────────┘
          │ routes /api/v1/{service}/**
    ┌─────┴──────────────────────────────────────────────┐
    │                                                    │
    ▼                                                    ▼
Auth :8081          User :8082       Health :8083    Mental :8084
Nutrition :8085     AI Coach :8086   Social :8087
Notification :8088  Analytics :8089
    │                    │
    ▼                    ▼
PostgreSQL (per-service DB)        Redis (shared)
```

Full architecture details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## Services

| Service | Port | Responsibility |
|---------|------|----------------|
| **gateway-service** | 8080 | Reverse proxy, rate limiting (300 req/min/user), circuit breaker, security headers |
| **auth-service** | 8081 | JWT auth (HS256), MFA (TOTP), email verification, password reset, refresh tokens |
| **user-service** | 8082 | Profile, goals, subscription, GDPR data export |
| **health-data-service** | 8083 | Sleep, weight, water, activity, symptoms, menstrual cycle, dashboard |
| **mental-service** | 8084 | Mood tracking, journal, stress, meditations, breathing sessions |
| **nutrition-service** | 8085 | Food log, food search/barcode, custom foods, nutrition analysis |
| **ai-coach-service** | 8086 | Claude AI insights, daily/weekly reports, energy/symptom predictions |
| **social-service** | 8087 | Posts, likes, challenges, leaderboards, friendships |
| **notification-service** | 8088 | Email (SMTP) + Firebase push notifications, FCM device token management |
| **analytics-service** | 8089 | Event tracking (Redis List, 30-day TTL, 1000 events/key) |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security, JWT (jjwt 0.12.5), BCrypt(12) |
| Data | PostgreSQL 16, Redis 7, Spring Data JPA, Flyway |
| Resilience | Resilience4j (circuit breaker, retry, rate limiter) |
| Observability | Micrometer, Prometheus, Zipkin, kube-prometheus-stack |
| Logging | SLF4J + Logback JSON (logstash-logback-encoder) |
| Testing | JUnit 5, AssertJ, Testcontainers, H2 |
| Load Testing | k6 (6 scenarios: smoke/load/stress/spike/soak/auth) |
| Build | Maven 3.9+, Spotless (Palantir Java Format) |
| Containers | Docker, Eclipse Temurin JRE 21 Alpine, tini |
| Orchestration | Kubernetes 1.28+, Helm 3 |
| CI/CD | GitHub Actions (9-stage pipeline) |
| Mobile | React Native, Expo 52, Zustand, Axios |
| Push | Firebase Admin SDK (FCM) |
| AI | Anthropic Claude 3.5 Sonnet |

---

## Quick Start (Local)

### Prerequisites

- JDK 21
- Maven 3.9+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

This starts PostgreSQL (7 databases), Redis, and Kafka.

### 2. Build shared modules

```bash
mvn install -DskipTests -B -pl shared/common-exceptions,shared/common-dto,shared/common-security -am
```

### 3. Run a service

```bash
cd services/auth-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Repeat for any service you need. Each service reads its config from `application.yml`
with `localhost` defaults for all dependencies.

### 4. Run all tests

```bash
mvn test -B
```

### 5. Format code

```bash
mvn spotless:apply -B
```

### API Docs (Swagger UI)

| Service | URL |
|---------|-----|
| Auth | http://localhost:8081/swagger-ui.html |
| User | http://localhost:8082/swagger-ui.html |
| Health Data | http://localhost:8083/swagger-ui.html |
| Mental | http://localhost:8084/swagger-ui.html |
| Nutrition | http://localhost:8085/swagger-ui.html |
| AI Coach | http://localhost:8086/swagger-ui.html |
| Social | http://localhost:8087/swagger-ui.html |
| Notification | http://localhost:8088/swagger-ui.html |
| Analytics | http://localhost:8089/swagger-ui.html |

---

## Running on Kubernetes

Full guide: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

### Quick deploy (kind / minikube)

```bash
# 1. Create cluster
kind create cluster --config k8s/kind-cluster.yaml

# 2. Create namespace and secrets
kubectl create namespace healthlife
kubectl create secret generic healthlife-secrets \
  --from-literal=jwt-secret=$(openssl rand -base64 64) \
  --from-literal=claude-api-key="" \
  --from-literal=firebase-service-account-json="" \
  -n healthlife
kubectl create secret generic healthlife-db-credentials \
  --from-literal=username=healthlife \
  --from-literal=password=$(openssl rand -base64 24) \
  -n healthlife
kubectl create secret generic healthlife-mail-credentials \
  --from-literal=username="" \
  --from-literal=password="" \
  -n healthlife

# 3. Deploy infrastructure (PostgreSQL + Redis)
kubectl apply -f k8s/infrastructure/ -n healthlife

# 4. Deploy application
helm upgrade --install healthlife k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=latest \
  --wait --timeout 5m

# 5. Verify
kubectl get pods -n healthlife
kubectl get hpa -n healthlife
```

### Production deploy (with External Secrets)

For production, use External Secrets Operator instead of plain Kubernetes Secrets:

```bash
# Install ESO
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace

# Apply ESO manifests (pulls secrets from AWS Secrets Manager)
kubectl apply -f k8s/base/external-secrets.yaml

# Deploy application
helm upgrade --install healthlife k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=sha-$(git rev-parse --short HEAD) \
  --values k8s/helm/healthlife/values.yaml \
  --wait --timeout 10m
```

---

## Monitoring

Full guide: [docs/OPERATIONS.md](docs/OPERATIONS.md)

### Deploy monitoring stack

```bash
GRAFANA_ADMIN_PASSWORD=your-password \
SLACK_WEBHOOK_URL=https://hooks.slack.com/... \
PAGERDUTY_ROUTING_KEY=your-key \
./k8s/monitoring/deploy-monitoring.sh
```

This installs **kube-prometheus-stack** (Prometheus + Alertmanager + Grafana) with:

- **20 alert rules** across 7 groups (availability, latency, errors, resources, DB, Redis, JVM, security, SLO)
- **2 Grafana dashboards** — Service Overview + SLO Dashboard
- **Alertmanager routing** — Slack warnings, Slack + PagerDuty for critical
- **SLO tracking** — 99.9% availability, 95% of requests < 2s

### Access dashboards

```bash
# Grafana
kubectl port-forward svc/prometheus-grafana 3000:80 -n monitoring
# open http://localhost:3000 (admin / your-password)

# Prometheus
kubectl port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090 -n monitoring

# Alertmanager
kubectl port-forward svc/prometheus-kube-prometheus-alertmanager 9093:9093 -n monitoring
```

### Key metrics endpoints

Every service exposes:
- `/actuator/health/liveness` — Kubernetes liveness probe
- `/actuator/health/readiness` — Kubernetes readiness probe
- `/actuator/prometheus` — Prometheus metrics scrape endpoint

---

## Load Testing

Full guide: [load-tests/README.md](load-tests/README.md)

### Install k6

```bash
# macOS
brew install k6

# Windows
winget install k6

# Linux — see load-tests/README.md
```

### Run scenarios

```bash
# Smoke test — run before every deploy (2 min, 3 VUs)
BASE_URL=https://staging.healthlife.com k6 run load-tests/k6/scenarios/smoke.js

# Load test — expected peak (30 min, 500 VUs)
BASE_URL=https://staging.healthlife.com k6 run load-tests/k6/scenarios/load.js

# Stress test — find breaking point (ramp to 2000 VUs)
BASE_URL=https://staging.healthlife.com k6 run load-tests/k6/scenarios/stress.js

# Spike test — viral surge (instant 5000 VUs)
BASE_URL=https://staging.healthlife.com k6 run load-tests/k6/scenarios/spike.js

# Soak test — memory leak detection (2h, 300 VUs)
BASE_URL=https://staging.healthlife.com k6 run load-tests/k6/scenarios/soak.js
```

### Pass/fail thresholds

| Metric | Threshold |
|--------|-----------|
| p(95) response time | < 2 000 ms |
| p(99) response time | < 5 000 ms |
| Error rate | < 1% |
| Check pass rate | > 95% |

Load tests also run automatically every **Sunday 02:00 UTC** via GitHub Actions.

---

## Environment Variables

### Required for all services

| Variable | Description |
|----------|-------------|
| `JWT_SECRET` | HS256 signing key — min 32 chars, generate with `openssl rand -base64 64` |
| `SPRING_PROFILES_ACTIVE` | `production` in K8s, `local` for local dev |

### Database services (auth, user, health-data, mental, nutrition, ai-coach, social)

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:5432/<dbname>` |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |

### Redis services (gateway, auth, notification, analytics)

| Variable | Description |
|----------|-------------|
| `SPRING_DATA_REDIS_HOST` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | Redis port (default `6379`) |

### Optional

| Variable | Service | Description |
|----------|---------|-------------|
| `CLAUDE_API_KEY` | ai-coach | Anthropic API key for AI insights |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | notification | Firebase service account JSON for push notifications |
| `MAIL_USERNAME` | auth, notification | SMTP username |
| `MAIL_PASSWORD` | auth, notification | SMTP password |
| `CORS_ALLOWED_ORIGINS` | all | Comma-separated allowed origins (default: `http://localhost:3000,http://localhost:8081`) |
| `AI_EXECUTOR_CORE_THREADS` | ai-coach | Thread pool size for Claude API calls (default: `20`) |

---

## API Documentation

### Authentication flow

```
POST /api/v1/auth/register     — register new user
POST /api/v1/auth/login        — login, returns accessToken + refreshToken
POST /api/v1/auth/refresh      — refresh access token
POST /api/v1/auth/logout       — invalidate refresh token
POST /api/v1/auth/mfa/setup    — enable TOTP MFA
POST /api/v1/auth/mfa/verify   — verify MFA code and complete login
GET  /api/v1/auth/verify-email/{token}  — verify email address
POST /api/v1/auth/password/reset        — request password reset
POST /api/v1/auth/password/reset/confirm — confirm password reset
```

### Key endpoints

```
# User
GET  /api/v1/users/me                — get profile (includes email from JWT)
PUT  /api/v1/users/me                — update profile
GET  /api/v1/users/me/data-export    — GDPR Article 20 data export
GET  /api/v1/users/me/goals          — get health goals
PUT  /api/v1/users/me/goals          — update health goals

# Health Data
GET  /api/v1/health/dashboard        — aggregated dashboard (water, steps, sleep, weight)
GET  /api/v1/health/sleep/stats      — sleep statistics (avg duration, quality, goal %)
POST /api/v1/health/water            — log water intake
GET  /api/v1/health/water/today      — total water today (ml)
POST /api/v1/health/activity/sync    — sync activity data
GET  /api/v1/health/activity/today   — today's activity

# Mental
POST /api/v1/mental/mood             — log mood entry
GET  /api/v1/mental/mood/history     — mood history
POST /api/v1/mental/journal          — create journal entry
GET  /api/v1/mental/meditations      — list meditations (filter by category)

# Nutrition
GET  /api/v1/nutrition/food-log/today — today's food log
GET  /api/v1/nutrition/analysis       — today's macro totals
GET  /api/v1/nutrition/goals          — daily nutrition targets
GET  /api/v1/nutrition/foods/search?q= — search food database

# AI Coach
POST /api/v1/ai/chat                 — chat with AI coach
GET  /api/v1/ai/insights/daily       — daily health insight (cached 24h)
GET  /api/v1/ai/insights/weekly      — weekly health report
GET  /api/v1/ai/recommendations      — personalised recommendations

# Social
GET  /api/v1/social/feed             — social feed (friends + self)
GET  /api/v1/social/challenges       — list challenges
POST /api/v1/social/challenges/{id}/join — join a challenge
GET  /api/v1/social/challenges/{id}/leaderboard — challenge leaderboard

# Notifications
POST /api/v1/notifications/device-token  — register FCM device token
DELETE /api/v1/notifications/device-token — remove FCM device token
```

---

## Security

Full details: [docs/SECURITY.md](docs/SECURITY.md)

### Key security features

- **JWT** — HS256, 15-min access tokens, 7-day refresh tokens, key length validated (≥ 32 bytes)
- **MFA** — TOTP via Google Authenticator (RFC 6238)
- **Rate limiting** — 300 req/min per authenticated user, 100 req/min per IP (Redis atomic INCR)
- **Path traversal protection** — Gateway rejects `../` sequences before forwarding
- **Email relay protection** — Notification endpoints require `ROLE_ADMIN`
- **CORS** — Configurable via `CORS_ALLOWED_ORIGINS` env var
- **Security headers** — CSP, HSTS, X-Frame-Options, X-XSS-Protection
- **Container security** — non-root (UID 1000), read-only root filesystem, all capabilities dropped
- **Secrets** — External Secrets Operator manifests for production (AWS Secrets Manager)
- **Dependency scanning** — OWASP Dependency Check (fail on CVSS ≥ 7) + Trivy in CI/CD

---

## Project Structure

```
.
├── services/                    # 10 microservices
│   ├── gateway-service/         # Port 8080 — API gateway
│   ├── auth-service/            # Port 8081 — Authentication
│   ├── user-service/            # Port 8082 — User profiles + GDPR
│   ├── health-data-service/     # Port 8083 — Health metrics
│   ├── mental-service/          # Port 8084 — Mental wellness
│   ├── nutrition-service/       # Port 8085 — Nutrition tracking
│   ├── ai-coach-service/        # Port 8086 — AI insights (Claude)
│   ├── social-service/          # Port 8087 — Social features
│   ├── notification-service/    # Port 8088 — Email + Firebase push
│   └── analytics-service/       # Port 8089 — Event analytics
│
├── shared/                      # Shared libraries
│   ├── common-dto/              # All DTOs (auth, health, mental, nutrition, social, user, ai)
│   ├── common-security/         # JWT, security config, CORS, rate limiting
│   └── common-exceptions/       # Custom exceptions
│
├── mobile/HealthLife/           # React Native / Expo mobile app
│   └── src/
│       ├── api/                 # Axios API clients (auth, health, services)
│       ├── screens/             # Auth + main screens
│       ├── store/               # Zustand auth store
│       └── constants/           # API URLs, theme, storage keys
│
├── k8s/
│   ├── base/                    # Kubernetes manifests (Deployment, Service, HPA, PDB, VPA)
│   │   ├── external-secrets.yaml  # External Secrets Operator for production
│   │   └── secrets.yaml           # Placeholder secrets for local/staging
│   ├── helm/healthlife/         # Helm chart
│   ├── infrastructure/          # PostgreSQL + Redis StatefulSets
│   ├── monitoring/              # Prometheus stack, alert rules, Grafana dashboards
│   │   ├── prometheus-stack-values.yaml  # kube-prometheus-stack Helm values
│   │   ├── alertmanager-rules.yaml       # 20 production alert rules
│   │   ├── grafana-dashboards.yaml       # Service Overview + SLO dashboards
│   │   ├── servicemonitor.yaml           # Prometheus ServiceMonitor
│   │   └── deploy-monitoring.sh          # One-command monitoring deploy
│   └── overlays/production/     # Kustomize production overlays
│
├── load-tests/                  # k6 load testing suite
│   ├── k6/
│   │   ├── config.js            # Shared config and thresholds
│   │   ├── helpers.js           # Reusable API call helpers
│   │   └── scenarios/
│   │       ├── smoke.js         # 3 VUs / 2m — run before every deploy
│   │       ├── load.js          # 500 VUs / 30m — expected peak load
│   │       ├── stress.js        # Ramp to 2000 VUs — find breaking point
│   │       ├── spike.js         # Instant 5000 VUs — viral surge
│   │       ├── soak.js          # 300 VUs / 2h — memory leak detection
│   │       └── auth.js          # 200 VUs — auth endpoint focused
│   └── README.md
│
├── docs/
│   ├── ARCHITECTURE.md          # C4 diagrams, data flow, technology decisions
│   ├── DEPLOYMENT.md            # Step-by-step deployment guide
│   ├── OPERATIONS.md            # Production runbook (21 incident scenarios)
│   ├── SECURITY.md              # Threat model, security practices
│   ├── K8S-SETUP.md             # Kubernetes cluster setup
│   └── K8S-RUN.md               # Running on Kubernetes
│
├── infrastructure/
│   └── docker-compose.yml       # Local dev: PostgreSQL, Redis, Kafka
│
├── .github/workflows/
│   ├── ci-cd.yml                # 9-stage pipeline: quality→security→test→build→deploy
│   └── load-tests.yml           # Load test workflow (manual + weekly schedule)
│
└── pom.xml                      # Root Maven POM
```

---

## CI/CD Pipeline

The pipeline runs on every push to `main` or `develop`:

| Stage | What it does |
|-------|-------------|
| 1. Code Quality | Spotless format check, compile, TODO detection |
| 2. Security Scan | OWASP Dependency Check (fail CVSS ≥ 7), Trivy filesystem scan |
| 3. Unit Tests | Parallel matrix across all 10 services, JaCoCo coverage |
| 4. Integration Tests | PostgreSQL + Redis containers, full service tests |
| 5. Build Images | Maven package → Docker build → push to GHCR, Trivy image scan |
| 6. Helm Lint | `helm lint` + `helm template` validation |
| 7. Deploy Staging | Helm upgrade + curl health check + **k6 smoke test** |
| 8. Deploy Production | Helm upgrade + health check + auto-rollback on failure |
| 9. Notify | Slack webhook + GitHub commit status |

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/my-feature`
3. Format code: `mvn spotless:apply`
4. Run tests: `mvn test -B`
5. Push and open a Pull Request against `develop`

### Code style

- Palantir Java Format (enforced by Spotless — CI will fail if not formatted)
- Run `mvn spotless:apply` before every commit

### Adding a new service

1. Copy an existing service as template
2. Add to root `pom.xml` modules
3. Add Kubernetes manifest in `k8s/base/`
4. Add to Helm `values.yaml`
5. Add route in `GatewayRouteConfig.java`
6. Add to CI matrix in `.github/workflows/ci-cd.yml`

---

## License

Proprietary — HealthLife Platform
