# HealthLife — Docker Deployment Guide

## Requirements

| Tool | Minimum version |
|------|----------------|
| Docker | 24+ |
| Docker Compose | v2.20+ (`docker compose` not `docker-compose`) |
| RAM | 8 GB available |
| Java 21 + Maven | Required to **build** JARs before Docker |

---

## Quick Start (3 steps)

### 1 — Configure environment

```bash
cp .env.example .env
# Open .env and fill in all required values (JWT_SECRET, DB_PASSWORD, REDIS_PASSWORD, etc.)
nano .env
```

**Minimum required variables** (others can stay empty to disable features):

| Variable | Description |
|----------|-------------|
| `JWT_SECRET` | At least 256 bits — `openssl rand -base64 64` |
| `DB_PASSWORD` | Postgres password — `openssl rand -base64 32` |
| `REDIS_PASSWORD` | Redis password — `openssl rand -base64 32` |
| `CORS_ALLOWED_ORIGINS` | Your frontend domain, e.g. `https://app.yourdomain.com` |

### 2 — Start the stack

```bash
docker compose up -d --build
```

The gateway is available at **http://localhost:8080** (or `GATEWAY_PORT` you set).

---

## Architecture

```
Internet → :8080 (gateway-service)
              ├─ /api/v1/auth/**       → auth-service:8081
              ├─ /api/v1/users/**      → user-service:8082
              ├─ /api/v1/health/**     → health-data-service:8083
              ├─ /api/v1/mental/**     → mental-service:8084
              ├─ /api/v1/nutrition/**  → nutrition-service:8085
              ├─ /api/v1/ai/**         → ai-coach-service:8086
              ├─ /api/v1/social/**     → social-service:8087
              ├─ /api/v1/notifications → notification-service:8088
              ├─ /api/v1/analytics/**  → analytics-service:8089
              └─ /api/v1/payments/**   → payment-service:8090
```

All services share an internal `healthlife` Docker network. Only the gateway port is exposed.

---

## Common Commands

```bash
# View live logs for all services
docker compose logs -f

# View logs for one service
docker compose logs -f auth-service

# Check health status
docker compose ps

# Restart a single service (no downtime for others)
docker compose restart auth-service

# Stop everything (keeps volumes/data)
docker compose down

# Stop and DELETE all data (full reset)
docker compose down -v

# Rebuild a single service after code change
docker compose up -d --build auth-service
```

---

## Health Check Endpoints

Each service exposes liveness/readiness probes on its internal port:

```
GET /internal/actuator/health/liveness
GET /internal/actuator/health/readiness
```

Example (from host, only if you exposed the port):
```bash
curl http://localhost:8080/internal/actuator/health/liveness
```

---

## Optional Features

| Feature | Required env var | How to enable |
|---------|-----------------|---------------|
| Email (password reset) | `MAIL_*` | Set SMTP credentials |
| Stripe payments | `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_*` | Set live keys from Stripe Dashboard |
| AI Coach (DeepSeek) | `DEEPSEEK_API_KEY` | Set API key |
| Support Chat (Anthropic) | `ANTHROPIC_API_KEY` | Set API key |
| Google OAuth | `OAUTH_GOOGLE_CLIENT_ID` | Set from Google Cloud Console |
| Push Notifications (FCM) | `FIREBASE_SERVICE_ACCOUNT_JSON` | Paste JSON as single line |

All optional features gracefully degrade when their env var is empty — the service starts, the feature is disabled.

---

## Production Checklist

- [ ] `JWT_SECRET` is unique, random, ≥ 256 bits and stored securely (not in git)
- [ ] `DB_PASSWORD` and `REDIS_PASSWORD` are strong random strings
- [ ] `.env` is added to `.gitignore` (already done)
- [ ] Put a reverse proxy (nginx / Caddy / Traefik) in front of the gateway for TLS
- [ ] Set `CORS_ALLOWED_ORIGINS` to your actual domain only
- [ ] Set up database backups (pg_dump cron or managed snapshots)

---

## Troubleshooting

**Service exits immediately after start**
→ Check logs: `docker compose logs <service-name>`
→ Most common cause: missing required env var (JWT_SECRET, DB_PASSWORD)

**"connection refused" to Postgres/Redis**
→ The service started before the dependency was ready. Run `docker compose up -d` again — healthchecks ensure correct order.

**Port 8080 already in use**
→ Set `GATEWAY_PORT=8090` (or any free port) in `.env`

**Out of memory during build**
→ Increase Docker Desktop memory limit to 8 GB in Settings → Resources

---

## HTTPS / Production Mode (with nginx)

To start with TLS termination via nginx, first place your SSL certificates in the `ssl/` directory, then start with the `production` Docker profile:

```bash
# Place certificates (see ssl/README.md for Let's Encrypt instructions)
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem ssl/fullchain.pem
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem ssl/privkey.pem

# Edit nginx.conf: replace api.yourdomain.com with your actual domain

# Start everything including nginx
docker compose --profile production up -d --build
```

Without `--profile production`, the backend starts on `http://localhost:8080` (useful for local development and testing).

**Important:** Edit `nginx.conf` and replace `api.yourdomain.com` with your actual domain before starting.
