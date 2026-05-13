# Local run guide (Windows / macOS / Linux)

This document explains how to run **HealthLife** locally (backend + mobile) and how to verify it works.

## Prerequisites

- **Docker Desktop** (or Docker Engine) + Docker Compose
- **Java 21**
- **Maven 3.9+**
- **Node.js 20+** (for mobile)
- (Optional) **Postman/curl**

## 1) Start infrastructure (Postgres per service + Redis + Kafka)

From repo root:

```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

Check containers are healthy:

```bash
docker ps
```

## 2) Configure environment variables (recommended)

Create a local env file (PowerShell):

```powershell
Copy-Item .env.example .env
```

Minimum variables to set for a functional local backend:

- **JWT_SECRET**: at least 64 chars (HS256)
- **SPRING_DATA_REDIS_HOST**: `localhost`
- **SPRING_DATA_REDIS_PORT**: `6379`

Optional (only needed if you test those features):

- **DEEPSEEK_API_KEY** (AI coach calls)
- **STRIPE_SECRET_KEY**, **STRIPE_WEBHOOK_SECRET**, **STRIPE_PRICE_PRO**, **STRIPE_PRICE_PREMIUM**, **STRIPE_PRICE_FAMILY**
- **MAIL_HOST**, **MAIL_USERNAME**, **MAIL_PASSWORD**
- **FIREBASE_SERVICE_ACCOUNT_JSON**

## 3) Start backend services (dev)

### Option A (recommended): run each service in its own terminal

From repo root:

```bash
mvn -pl services/gateway-service spring-boot:run
```

Then in separate terminals:

```bash
mvn -pl services/auth-service spring-boot:run
mvn -pl services/user-service spring-boot:run
mvn -pl services/health-data-service spring-boot:run
mvn -pl services/mental-service spring-boot:run
mvn -pl services/nutrition-service spring-boot:run
mvn -pl services/ai-coach-service spring-boot:run
mvn -pl services/notification-service spring-boot:run
mvn -pl services/analytics-service spring-boot:run
mvn -pl services/social-service spring-boot:run
mvn -pl services/payment-service spring-boot:run
```

Default ports (via gateway):

- Gateway: `http://localhost:8080`
- Health check: `GET http://localhost:8080/actuator/health`

### Option B: run a single service (debug)

Example:

```bash
mvn -pl services/auth-service spring-boot:run
curl http://localhost:8081/actuator/health
```

## 4) Verify backend works (smoke checks)

### 4.1 Health checks

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

### 4.2 Register + login (through gateway)

Register:

```bash
curl -X POST "http://localhost:8080/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"user@example.com\",\"password\":\"Password1!\",\"displayName\":\"Test User\"}"
```

Login:

```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"user@example.com\",\"password\":\"Password1!\"}"
```

## 5) Run mobile app (Expo)

From repo root:

```bash
cd mobile
npm install
npm start
```

Or from the app folder (same result if you use the wrapper from `mobile/`):

```bash
cd mobile/HealthLife
npm install
npx expo start
```

See [`mobile/README.md`](../mobile/README.md) for **`npm run typecheck`** / **`npm test`** from `mobile/` without `cd HealthLife`.

Point the mobile app API base URL to:

- `http://localhost:8080`

If you test on a real device, use your LAN IP instead of `localhost` (e.g. `http://192.168.1.10:8080`).

## 6) Troubleshooting (common local issues)

### “Connection refused” to Postgres/Redis

- Make sure Docker is running
- Confirm ports are free (5432-5439, 6379, 9092)
- Restart infra:

```bash
docker compose -f infrastructure/docker-compose.yml down
docker compose -f infrastructure/docker-compose.yml up -d
```

### Service starts but requests return 502 from gateway

This means gateway is up but the downstream service for that route is not running or has a wrong port.

- Check the service is running and its `/actuator/health` works on its port.

### Tests fail because of Actuator mail/redis health

Tests should run with `spring.profiles.active=test` and health contributors disabled in `application-test.yml`.

## 7) Run tests locally

All tests:

```bash
mvn test -B
```

One service:

```bash
mvn test -pl services/auth-service -am -B
```

