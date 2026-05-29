# Чеклист до глобального релиза HealthLife

Дата оценки: май 2026  
Ветка: `main`  
Оценка готовности: **~55–60%** (сильный backend для staging, **не готов** к App Store / Google Play + production billing без доработок ниже).

---

## Краткий вердикт

| Область | Готовность | Комментарий |
|---------|------------|-------------|
| Backend (микросервисы) | 70% | Архитектура, Flyway, JWT, gateway, CI-сборка |
| Kubernetes / Helm | 65% | Манифесты есть; нужны реальные секреты, ingress, мониторинг |
| Безопасность | 60% | Hardening сделан; OWASP/Trivy в CI не блокируют |
| Платежи (Stripe) | 40% | Webhook открыт в Security; нужны live keys, Price ID, E2E |
| Mobile (Expo) | 35% | Нет EAS projectId, placeholder price IDs, нет CI |
| Observability | 45% | Prometheus path не совпадает с actuator exposure |
| Документация / ops | 55% | Есть runbook; часть README устарела |
| Юридическое / store | 0%* | Privacy Policy, Terms, возраст, модерация — вне репо |

\* Зависит от ваших процессов, в коде не проверялось.

---

## P0 — Блокеры (без этого релиз ломается)

### Инфраструктура и деплой

- [ ] **Реальный Kubernetes-кластер** (staging + production) с `KUBE_CONFIG_B64` в GitHub Secrets — иначе CI «деплоит вхолостую».
- [ ] **External Secrets / Sealed Secrets**: заполнить `k8s/base/external-secrets.yaml` (JWT, DB, Redis, Stripe, DeepSeek, Firebase, SMTP, OAuth, Anthropic).
- [ ] **Ingress + TLS**: cert-manager, домены `api.*` / `app.*` (сейчас в манифестах placeholder / `healthlife.app`).
- [ ] **GHCR pull secret** в namespace `healthlife`.
- [ ] **Post-deploy health check в CI**: путь `/internal/actuator/health` (сейчас в workflow может быть старый `/actuator/health`).

### Платежи

- [ ] **Stripe Dashboard (live mode)**: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, Price IDs (`STRIPE_PRICE_PRO`, `PREMIUM`, `FAMILY`).
- [ ] **Webhook URL** в Stripe: `https://<api-domain>/api/v1/payments/webhook` — проверить end-to-end после деплоя.
- [ ] **Mobile**: заменить `price_pro` / `price_premium` в `mobile/HealthLife/.../SubscriptionScreen.tsx` на реальные Stripe Price ID.
- [ ] **Return URLs** в `payment-service` под ваш домен (`https://app.healthlife.com/...` → ваш prod URL).

### Mobile (магазины приложений)

- [ ] **EAS**: заполнить `projectId` в `app.json` / `EAS_PROJECT_ID` для production (`app.config.js` уже требует).
- [ ] **EXPO_PUBLIC_API_URL** для production build.
- [ ] **Иконки, splash, скриншоты**, описания RU/EN в App Store Connect / Google Play Console.
- [ ] **Privacy Policy + Terms of Service** (ссылки в сторах; для health/mediation — отдельное внимание юристу).
- [ ] **CI для mobile**: `npm test`, `tsc`, опционально EAS build on tag.

### Безопасность prod

- [ ] **JWT_SECRET** ≥ 256 бит, уникальный на prod, ротация плана.
- [ ] **CORS_ALLOWED_ORIGINS** — только ваши домены, не `localhost`.
- [ ] **ANTHROPIC_API_KEY** (support chat) — лимиты и мониторинг расходов.
- [ ] Отключить Swagger в prod (`spring.profiles.active=production`).

### Наблюдаемость

- [ ] **Prometheus**: в `application.yml` сейчас exposure `health,info`, а `k8s/monitoring/servicemonitor.yaml` скрейпит `/actuator/prometheus` — либо включить endpoint + путь `/internal/actuator/prometheus`, либо обновить ServiceMonitor.
- [ ] Алерты Alertmanager → Slack/PagerDuty (SMTP в values — placeholder).

---

## P1 — Важно до публичного запуска

### Качество и CI

- [ ] Поднять **JaCoCo minimum** в CI с 10% до целевого (README обещает 60% — привести в соответствие).
- [ ] Сделать **gitleaks / OWASP / Trivy** fail pipeline (сейчас `continue-on-error` / `|| true`).
- [ ] Добавить **integration-тест**: register → login → gateway → payment webhook (mock Stripe).
- [ ] **Smoke-тест** после deploy на staging (auth health, gateway route).

### Backend

- [ ] **OAuth prod**: `OAUTH_GOOGLE_CLIENT_ID`, `OAUTH_APPLE_AUDIENCE`.
- [ ] **Email**: SMTP для reset password / уведомлений.
- [ ] **Firebase** FCM для push (`notification-service`).
- [ ] **DeepSeek** (`ai-coach-service`) — ключ и лимиты.
- [ ] Проверить **rate limits** auth (login/register/reset) под нагрузкой.
- [ ] **Kafka** в `infrastructure/docker-compose.yml` не используется Java-кодом — убрать из prod compose или подключить.

### K8s

- [ ] Один канонический способ деплоя: **Helm** (`k8s/helm/healthlife`) *или* `kubectl apply -k k8s/base` — не смешивать без документации.
- [ ] **Frontend ingress** (`k8s/base/ingress-app.yaml`) — сейчас TODO, нет сервиса.
- [ ] **Admin ingress** (`ingress-admin.yaml`) — аналогично.

### Mobile

- [ ] Синхронизировать **BUILD.md** с Expo SDK 53 (`package.json`).
- [ ] Deep links для возврата из Stripe Checkout.
- [ ] Тест на физическом устройстве: API не `127.0.0.1`, а LAN/tunnel (`docs/LOCAL-RUN.md`).

### Support / mental health

- [ ] Юридический дисклеймер в приложении (не замена врачу) — UI + support prompt уже частично есть.
- [ ] Процесс эскалации кризисных обращений (не только текст бота).

---

## P2 — После запуска / polish

- [ ] Load tests (`load-tests/k6/`) на staging по расписанию + пороги SLA.
- [ ] WAF / DDoS (Cloudflare, AWS ALB).
- [ ] Backup Postgres (Velero / managed snapshots).
- [ ] GDPR: экспорт/удаление данных пользователя.
- [ ] Локализация всех бизнес-сообщений (сейчас i18n для `error.*` / `support.*`, не весь API).
- [ ] `package-lock.json` в git для воспроизводимых mobile-сборок.
- [ ] Ротация секретов runbook в `docs/OPERATIONS.md`.

---

## Переменные окружения (prod) — сводка

| Переменная | Сервисы |
|------------|---------|
| `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` | все с security |
| `SPRING_DATASOURCE_*` | сервисы с Postgres |
| `SPRING_DATA_REDIS_HOST` | gateway, notification, analytics, ai-coach |
| `CORS_ALLOWED_ORIGINS` | все |
| `STRIPE_*` | payment |
| `DEEPSEEK_API_KEY` | ai-coach |
| `ANTHROPIC_API_KEY` | mental (support) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | notification |
| `MAIL_*` | auth, notification |
| `OAUTH_GOOGLE_CLIENT_ID`, `OAUTH_APPLE_AUDIENCE` | auth |
| `GATEWAY_ROUTE_*` | gateway |
| `EXPO_PUBLIC_API_URL` | mobile build |

Полный шаблон: `.env.example` в корне и `mobile/HealthLife/.env.example`.

---

## Рекомендуемый порядок работ (2–4 недели)

1. **Неделя 1**: staging K8s + секреты + исправления CI health + Prometheus + Stripe test mode E2E.  
2. **Неделя 2**: mobile EAS preview + реальные Price ID + OAuth/SMTP/FCM на staging.  
3. **Неделя 3**: security hardening CI, integration tests, load test, legal/store metadata.  
4. **Неделя 4**: production deploy, мониторинг, rollback drill, store submission.

---

## Что уже сделано в репозитории (не дублировать)

- Микросервисная архитектура (11 сервисов + shared modules).
- JWT, BCrypt(12), rate limit auth, `/internal/actuator`, CORS из env.
- Support chat (Claude) в mental-service, i18n ru/en для ошибок.
- Flyway на сервисах с БД.
- GitHub Actions: build, test, push images, helm lint.
- K8s base + Helm chart + monitoring templates.

---

## Контакты и ссылки в репозитории

| Документ | Путь |
|----------|------|
| Локальный запуск | `docs/LOCAL-RUN.md` |
| Kubernetes | `docs/K8S-RUN.md`, `docs/K8S-SETUP.md` |
| Деплой | `docs/DEPLOYMENT.md` |
| Безопасность | `docs/SECURITY.md` |
| Операции | `docs/OPERATIONS.md` |

При изменении статуса отмечайте пункты в этом файле и обновляйте оценку готовности.
