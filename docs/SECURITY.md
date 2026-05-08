# Security Documentation

## Threat Model

### Assets
- User health data (PHI — Protected Health Information)
- Authentication credentials (passwords, MFA secrets)
- JWT tokens (access + refresh)
- Personal identifiable information (PII)
- API keys (DeepSeek, Firebase)

### Threats & Mitigations

| Threat | Mitigation | Status |
|--------|-----------|--------|
| SQL Injection | Parameterized queries via JPA, input validation (`@Valid`) | ✅ |
| XSS | Content-Security-Policy header, output encoding | ✅ |
| CSRF | Stateless JWT, no cookies | ✅ |
| Credential stuffing / brute force | Rate limiting (10 req/min on login), BCrypt(12) | ✅ |
| Token theft | Short-lived access tokens (15 min), refresh token rotation | ✅ |
| MFA bypass via header injection | Email required in request body, not `X-User-Email` header | ✅ |
| Man-in-the-middle | TLS 1.3, HSTS (1 year, includeSubDomains) | ✅ |
| Path traversal | Gateway rejects `../`, `%2e%2e` before forwarding | ✅ |
| Email relay abuse | Notification endpoints require `ROLE_ADMIN` + `@PreAuthorize` | ✅ |
| Container escape | Non-root (UID 1000), read-only root fs, all capabilities dropped | ✅ |
| Dependency vulnerabilities | OWASP Dependency Check (fail CVSS ≥ 7) + Trivy in CI | ✅ |
| Secrets in git | External Secrets Operator (AWS Secrets Manager) for production | ✅ |
| User enumeration | Password reset silently ignores unknown emails | ✅ |
| Analytics userId spoofing | userId taken from JWT, not request param | ✅ |
| CORS misconfiguration | Origins configurable via `CORS_ALLOWED_ORIGINS` env var | ✅ |

---

## Authentication & Authorization

### JWT

- Algorithm: **HS256**
- Key length: minimum 32 bytes (validated at startup — throws `IllegalArgumentException` if shorter)
- Access token TTL: **15 minutes** (`jwt.access-token.expiration=900000`)
- Refresh token TTL: **7 days** (`jwt.refresh-token.expiration=604800000`)
- Claims: `sub` (userId), `email`, `role`, `type` (access/refresh)
- Refresh tokens stored in PostgreSQL — invalidated on logout and password reset

### MFA (TOTP)

- RFC 6238 compliant via `com.warrenstrange:googleauth`
- 6-digit code, 30-second window
- Secret stored encrypted in `users.mfa_secret`
- Setup: `POST /api/v1/auth/mfa/setup` → returns secret + QR code URI
- Verify: `POST /api/v1/auth/mfa/verify` — email **must** be in request body (not header)

### Password Security

- BCrypt with cost factor **12** (~300ms per hash — brute-force resistant)
- Password reset: time-limited token (1 hour), single-use, invalidates all refresh tokens
- Email verification: 24-hour token, single-use

### Role-Based Access Control

- Roles: `USER`, `ADMIN`
- `@EnableMethodSecurity(prePostEnabled = true)` active on all services
- Notification endpoints: `@PreAuthorize("hasRole('ADMIN')")`

---

## Rate Limiting

Implemented in `RateLimitFilter` using **atomic Redis INCR** (no race conditions):

| Tier | Limit | Window | Key |
|------|-------|--------|-----|
| Authenticated user | 300 req/min | 60s | `rate_limit:user:{userId}` |
| Unauthenticated / IP | 100 req/min | 60s | `rate_limit:ip:{remoteAddr}` |
| Login endpoint | 10 req/min | 60s | Resilience4j `@RateLimiter(name="login")` |

Response on limit exceeded: `HTTP 429` with `Retry-After: 60` header.

---

## Security Headers

Applied by `SecurityHeadersFilter` on every response:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

---

## Container Security

All pods enforce:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
  seccompProfile:
    type: RuntimeDefault

containerSecurityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: [ALL]
```

Dockerfiles:
- Base image: `eclipse-temurin:21-jre-alpine` (minimal attack surface)
- Init process: `tini` (proper signal handling, no zombie processes)
- Non-root user: `healthlife` (UID 1000)
- JAR permissions: `chmod 444` (read-only)

---

## Network Security

`NetworkPolicy` in `k8s/base/network-policy.yaml`:
- **Default deny** all ingress and egress
- Allow ingress from: `ingress-nginx`, `monitoring` namespaces, within `healthlife` namespace
- Allow egress to: `healthlife` namespace (inter-service), DNS (53/TCP+UDP), HTTPS (443), SMTP (587)
- Block all other external traffic

---

## Secret Management

### Local / Staging

`k8s/base/secrets.yaml` — placeholder values, **never commit real secrets**.

### Production

External Secrets Operator (`k8s/base/external-secrets.yaml`):
- Pulls from AWS Secrets Manager
- Auto-refreshes every 1 hour
- Secrets never stored in git or container images

Secret paths in AWS Secrets Manager:
```
healthlife/production/jwt-secret
healthlife/production/db-username
healthlife/production/db-password
healthlife/production/deepseek-api-key
healthlife/production/firebase-service-account-json
healthlife/production/mail-username
healthlife/production/mail-password
```

---

## Dependency Scanning

### OWASP Dependency Check

Runs in CI on every push. Fails build if any dependency has CVSS score ≥ 7.

```bash
# Run manually
mvn org.owasp:dependency-check-maven:check -B -DfailBuildOnCVSS=7
```

Suppressions: `owasp-suppressions.xml` (false positives only, with justification).

### Trivy

Runs in CI on:
1. Filesystem scan (source code + dependencies)
2. Container image scan (after Docker build)

Both scans upload SARIF results to GitHub Security tab.

---

## Security Incident Response

See [OPERATIONS.md — Security Incident](OPERATIONS.md#security-incident) for the full runbook.

Quick reference:
```bash
# Block an IP at ingress
kubectl annotate ingress gateway-ingress -n healthlife \
  nginx.ingress.kubernetes.io/server-snippet='deny <IP>;'

# Rotate JWT secret (invalidates ALL existing tokens)
# 1. Update secret in AWS Secrets Manager
# 2. Restart all services
kubectl rollout restart deployment -n healthlife

# Isolate a compromised service
kubectl scale deployment <service-name> --replicas=0 -n healthlife
```

---

## GDPR Compliance

- **Right to access** (Article 15): `GET /api/v1/users/me/data-export` — returns all user data as JSON
- **Right to erasure** (Article 17): `DELETE /api/v1/users/me` — soft-deletes account (`deleted_at` timestamp)
- **Data minimisation**: Only health data necessary for the service is collected
- **Data retention**: Analytics events expire after 30 days (Redis TTL)

> **Note:** Full GDPR compliance requires a Data Processing Agreement (DPA) with cloud providers,
> a Privacy Policy, and a Cookie Policy. The code implements the technical controls only.
