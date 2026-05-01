# Security Documentation

## Threat Model

### Assets
- User health data (PHI)
- Authentication credentials
- JWT tokens
- Personal identifiable information (PII)

### Threats & Mitigations

| Threat | Mitigation |
|--------|-----------|
| SQL Injection | Parameterized queries via JPA, input validation |
| XSS | Content-Security-Policy header, output encoding |
| CSRF | Stateless JWT, SameSite cookies |
| Credential stuffing | Rate limiting, account lockout policies |
| Token theft | Short-lived access tokens (15 min), refresh token rotation |
| Man-in-the-middle | TLS 1.3, HSTS header |
| Container escape | Non-root users, read-only root fs, dropped capabilities |
| Dependency vulnerabilities | OWASP dependency check, Trivy image scanning |

## Secret Management

- JWT secret: 256-bit random key stored in Kubernetes Secrets
- Database passwords: Kubernetes Secrets or external vault
- TLS: cert-manager with Let's Encrypt

## OWASP Dependency Check

Run manually:

```bash
mvn org.owasp:dependency-check-maven:check -B
```

Reports are generated in `target/dependency-check-report.html` per module.

## Security Headers

The Gateway enforces:
- `Strict-Transport-Security`
- `Content-Security-Policy`
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy`

## Authentication

- JWT access tokens (HS256)
- Refresh tokens stored in PostgreSQL
- Optional MFA via TOTP
- Password reset with time-limited tokens
