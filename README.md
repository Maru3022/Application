# HealthLife — Health & Wellness Platform

Production-ready, cloud-native microservices platform for personal health management with an AI coach powered by DeepSeek.

[![CI/CD](https://github.com/maru3022/application/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/maru3022/application/actions/workflows/ci-cd.yml)

---

## Table of Contents

1. [Architecture](#architecture)
2. [Services](#services)
3. [Technology Stack](#technology-stack)
4. [Quick Start (Local)](#quick-start-local)
5. [Mobile App](#mobile-app)
6. [Running on Kubernetes](#running-on-kubernetes)
7. [Environment Variables](#environment-variables)
8. [Testing](#testing)
9. [Security](#security)
10. [Project Structure](#project-structure)

---

## Architecture

HealthLife uses a **database-per-service** microservices architecture. All external traffic enters through an API Gateway (Spring Cloud Gateway). Services communicate internally via HTTP (WebClient).

```
Mobile App (Expo/React Native)
        │
        ▼
  API Gateway :8080
        │
  ┌─────┼──────────────────────────────────────┐
  │     │                                      │
auth  user  health-data  nutrition  ai-coach  payment
  │                                      │
redis                              stripe webhook
  │
postgres (per service)
```

---

## Services

| Service | Port | Description |
|---|---|---|
| `gateway-service` | 8080 | API Gateway, rate limiting, routing |
| `auth-service` | 8081 | Registration, login, JWT, MFA (TOTP), OAuth2 |
| `user-service` | 8082 | User profiles, goals, GDPR export |
| `health-data-service` | 8083 | Steps, sleep, weight, water tracking |
| `mental-service` | 8084 | Mood journal, mindfulness sessions |
| `nutrition-service` | 8085 | Food search (OpenFoodFacts), meal logging, macros |
| `ai-coach-service` | 8086 | DeepSeek AI chat with SSE streaming, daily insights |
| `notification-service` | 8087 | Email (SMTP), push notifications (Firebase FCM) |
| `analytics-service` | 8088 | Event tracking via Redis |
| `social-service` | 8089 | Friends, community challenges, posts |
| `payment-service` | 8090 | Stripe subscriptions (Plus / Pro / Family) |

---

## Technology Stack

**Backend**
- Java 21, Spring Boot 3.2, Spring Security, Spring Data JPA
- PostgreSQL (per-service), Redis (caching, sessions, analytics)
- Flyway (database migrations)
- DeepSeek AI API (chat completion with SSE streaming)
- Stripe (payments), Firebase (push notifications)
- Maven multi-module build

**Mobile**
- React Native, Expo SDK 51
- TypeScript, React Navigation, React Native Paper
- Expo Secure Store (token storage)
- EAS Build (cloud native builds)

**Infrastructure**
- Kubernetes (k8s manifests + Helm chart)
- Docker (multi-stage builds)
- GitHub Actions CI/CD
- Prometheus + Grafana (monitoring)
- k6 (load testing)

---

## Quick Start (Local)

### Prerequisites
- Docker & Docker Compose
- Java 21+ (for local Maven builds)
- Node.js 20+ (for mobile)

### 1. Start infrastructure + all services

```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

This starts PostgreSQL (one per service), Redis, Kafka, and Zookeeper.
Spring Boot services are started via Maven (see `docs/LOCAL-RUN.md`).

### 2. Verify services are up

```bash
curl http://localhost:8080/actuator/health   # gateway
curl http://localhost:8081/actuator/health   # auth
```

### 3. Register a user

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password1!","displayName":"Test User"}'
```

---

## Mobile App

See **[`mobile/README.md`](mobile/README.md)** — run `npm install` / `npm test` / `npm run typecheck` from **`mobile`** (wrapper) or from **`mobile/HealthLife`** (Expo app).

```bash
cd mobile/HealthLife
npm install
npx expo start          # Expo Go (no native build needed)
```

For a native build see [`mobile/HealthLife/BUILD.md`](mobile/HealthLife/BUILD.md).

---

## Running on Kubernetes

See [`docs/K8S-RUN.md`](docs/K8S-RUN.md) for full instructions.

**After the cluster is up**, services use **ClusterIP** — your browser on `http://localhost:8080` will show **connection refused** until you run a port-forward. From the repo root:

```powershell
.\k8s\Port-Forward-Gateway.ps1
```

Quick deploy (Windows): `.\k8s\Start-Cluster.ps1` (optional: `-StartGatewayPortForward` opens the forward in a new window).

```bash
kubectl apply -f k8s/base/namespace.yaml
# Create secrets first (see Environment Variables below)
kubectl apply -f k8s/base/
helm upgrade --install healthlife k8s/helm/healthlife \
  --namespace healthlife \
  --values k8s/helm/healthlife/values.yaml
```

---

## Environment Variables

All secrets are managed via Kubernetes Secrets (or External Secrets Operator for production).  
**Never commit real secrets to git.**

| Variable | Service | Description |
|---|---|---|
| `JWT_SECRET` | all | HS256 signing key, minimum 64 chars |
| `SPRING_DATASOURCE_URL` | all DB services | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | all DB services | DB username |
| `SPRING_DATASOURCE_PASSWORD` | all DB services | DB password |
| `SPRING_DATA_REDIS_HOST` | auth, ai-coach | Redis host |
| `DEEPSEEK_API_KEY` | ai-coach | DeepSeek platform API key |
| `STRIPE_SECRET_KEY` | payment | Stripe secret key (`sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | payment | Stripe webhook signing secret |
| `STRIPE_PRICE_PRO` | payment | Stripe Price ID for Pro plan |
| `STRIPE_PRICE_PREMIUM` | payment | Stripe Price ID for Premium plan |
| `STRIPE_PRICE_FAMILY` | payment | Stripe Price ID for Family plan |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | notification | Firebase service account JSON (base64) |
| `MAIL_HOST` | notification | SMTP host |
| `MAIL_USERNAME` | notification | SMTP username |
| `MAIL_PASSWORD` | notification | SMTP password |

For local development all variables have safe defaults in `application.yml`.

---

## Testing

### Run all tests

```bash
mvn test -B
```

### Run tests for a specific service

```bash
mvn test -pl services/auth-service -am
```

### Test approach

Each service uses **H2 in-memory database** with `@ActiveProfiles("test")` — no external dependencies needed to run tests locally.

| Service | Test type | Coverage |
|---|---|---|
| auth-service | SpringBootTest + MockMvc | 13 tests |
| health-data-service | SpringBootTest | 12 tests |
| nutrition-service | SpringBootTest | 15 tests |
| ai-coach-service | SpringBootTest + MockBean | 8 tests |
| social-service | SpringBootTest | 10 tests |
| notification-service | Pure unit (Mockito) | 9 tests |
| analytics-service | Pure unit (Mockito) | 12 tests |
| payment-service | SpringBootTest | 7 tests |

Minimum coverage threshold: **60%** (enforced by JaCoCo in CI).

---

## Security

- JWT (HS256, configurable expiry)
- MFA via TOTP (Google Authenticator compatible)
- BCrypt password hashing
- Rate limiting at gateway (per-IP, per-user)
- Security headers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options
- OWASP dependency check in CI
- Trivy container image scanning in CI
- GDPR: data export endpoint at `GET /api/v1/users/me/export`

See [`docs/SECURITY.md`](docs/SECURITY.md) for full details.

---

## Project Structure

```
HealthLife/
├── services/                    # 11 Spring Boot microservices
│   ├── gateway-service/
│   ├── auth-service/
│   ├── user-service/
│   ├── health-data-service/
│   ├── mental-service/
│   ├── nutrition-service/
│   ├── ai-coach-service/
│   ├── notification-service/
│   ├── analytics-service/
│   ├── social-service/
│   └── payment-service/
├── shared/                      # Shared libraries (DTOs, exceptions, security)
│   ├── common-dto/
│   ├── common-exceptions/
│   └── common-security/
├── mobile/                      # React Native / Expo mobile app
│   └── HealthLife/
├── k8s/                         # Kubernetes manifests
│   ├── base/                    # Base k8s resources
│   └── helm/                    # Helm chart
├── docs/                        # Documentation + docker-compose
│   ├── docker-compose.yml
│   ├── ARCHITECTURE.md
│   ├── DEPLOYMENT.md
│   ├── OPERATIONS.md
│   └── SECURITY.md
├── load-tests/                  # k6 load test scripts
├── .github/workflows/           # CI/CD pipelines
│   ├── ci-cd.yml               # Main pipeline
│   └── load-tests.yml          # Load test pipeline
└── pom.xml                      # Maven parent POM
```

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make changes with tests
4. Ensure CI passes: `mvn test -B`
5. Open a Pull Request

All PRs require passing CI (tests + security scan + coverage ≥ 60%).
