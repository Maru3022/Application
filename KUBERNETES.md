# HealthLife — Запуск в Kubernetes

## Оглавление
1. [Что было исправлено](#что-было-исправлено)
2. [Требования](#требования)
3. [Быстрый старт (локально с Kind)](#быстрый-старт-локально-с-kind)
4. [Запуск в облаке](#запуск-в-облаке)
5. [Переменные окружения и секреты](#переменные-окружения-и-секреты)
6. [Структура K8s манифестов](#структура-k8s-манифестов)

---

## Что было исправлено

### 🔴 Критические баги

#### 1. GatewayRouteConfig — хардкод `localhost` (все сервисы недоступны)
**Файл:** `services/gateway-service/.../GatewayRouteConfig.java`

```java
// ДО (сломано в K8s — localhost не работает между подами):
private static final Map<String, String> ROUTES = Map.of(
    "/api/v1/auth", "http://localhost:8081",
    "/api/v1/users", "http://localhost:8082",
    ...
);

// ПОСЛЕ (читает из env vars, дефолты = K8s service names):
@Value("${services.auth.url:http://auth-service}") String authUrl,
@Value("${services.user.url:http://user-service}")  String userUrl,
...
```

#### 2. K8s YAML — слитые строки (все деплойменты не применяются)
**Файлы:** `k8s/base/user-service.yaml`, `health-data-service.yaml`, `mental-service.yaml`, `nutrition-service.yaml`, `ai-coach-service.yaml`, `social-service.yaml`

```yaml
# ДО (YAML парсер падает — строки слиплись):
- name: JWT_SECRET
  valueFrom:
    secretKeyRef:
      name: healthlife-secrets
      key: jwt-secret            - name: SPRING_DATASOURCE_URL   ← ошибка!

# ПОСЛЕ (правильные отступы):
- name: JWT_SECRET
  valueFrom:
    secretKeyRef:
      name: healthlife-secrets
      key: jwt-secret
- name: DB_HOST
  value: "postgres"
```

#### 3. Все `application.yml` — хардкод `localhost` для БД и Redis
**Файлы:** все сервисы

```yaml
# ДО:
datasource:
  url: jdbc:postgresql://localhost:5432/healthlife_auth
redis:
  host: localhost

# ПОСЛЕ:
datasource:
  url: jdbc:postgresql://${DB_HOST:postgres}:${DB_PORT:5432}/healthlife_auth
redis:
  host: ${SPRING_DATA_REDIS_HOST:redis}
```

#### 4. Secrets не существовали в репо
**Файл создан:** `k8s/base/secrets.yaml`

Все деплойменты ссылались на `healthlife-secrets`, `healthlife-db-credentials`, `healthlife-mail-credentials` — но сами Secret-объекты не были созданы.

#### 5. RateLimitFilter — race condition (пропускает лишние запросы)
**Файл:** `services/gateway-service/.../RateLimitFilter.java`

```java
// ДО (race condition: get→check→increment не атомарно):
String current = redisTemplate.opsForValue().get(key);
int count = current != null ? Integer.parseInt(current) : 0;
if (count >= MAX_REQUESTS) { ... }
redisTemplate.opsForValue().increment(key);  // уже поздно!

// ПОСЛЕ (атомарный INCR первым):
Long count = redisTemplate.opsForValue().increment(key);  // атомарно
if (count == 1) redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
if (count > MAX_REQUESTS) { ... return 429; }
```

### 🟠 Серьёзные баги

#### 6. User Enumeration в requestPasswordReset
**Файл:** `services/auth-service/.../AuthService.java`

```java
// ДО (возвращает 404 если email не найден → атакующий знает какие emails зарегистрированы):
.orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

// ПОСЛЕ (молча возвращает без ошибки):
if (userOpt.isEmpty()) {
    log.warn("Password reset for non-existent email (silently ignored)");
    return;  // не раскрываем существование аккаунта
}
```

#### 7. AiCoachService — hashCode() как ключ кэша (коллизии)
**Файл:** `services/ai-coach-service/.../AiCoachService.java`

```java
// ДО (hashCode может быть одинаковым для разных строк, может быть отрицательным):
String cacheKey = "ai:chat:" + userId + ":" + request.getMessage().hashCode();

// ПОСЛЕ (SHA-256):
MessageDigest md = MessageDigest.getInstance("SHA-256");
byte[] digest = md.digest(message.getBytes(UTF_8));
String msgHash = HexFormat.of().formatHex(digest).substring(0, 16);
```

#### 8. AiCoachService — JSON injection через string formatting
**Файл:** `services/ai-coach-service/.../AiCoachService.java`

```java
// ДО (если message содержит " или \n — JSON сломается):
String requestBody = """
    { "messages": [{"content": "%s"}] }
""".formatted(userMessage.replace("\"", "\\\""));

// ПОСЛЕ (Jackson ObjectMapper — безопасно):
Map<String, Object> requestMap = Map.of("messages", List.of(Map.of("content", fullUserMessage)));
String requestBody = mapper.writeValueAsString(requestMap);
```

#### 9. SocialService — пустые методы (updateProgress, inviteFriend)
**Файл:** `services/social-service/.../SocialService.java`

```java
// ДО (метод ничего не делал):
public void updateProgress(UUID challengeId, Integer progress) {
    UUID userId = SecurityUtils.getCurrentUserId();
    // Update progress logic  ← пусто!
}

// ПОСЛЕ:
ChallengeParticipant participant = challengeParticipantRepository
    .findByChallengeIdAndUserId(challengeId, userId)
    .orElseThrow(...);
participant.setProgress(progress);
challengeParticipantRepository.save(participant);
```

#### 10. SocialService — likePost делает двойной запрос к БД
**Файл:** `services/social-service/.../SocialService.java`

```java
// ДО (Post запрашивается в каждой ветке if/else — 2 запроса):
if (exists) { Post post = findById(...); ... }
else         { Post post = findById(...); ... }

// ПОСЛЕ (один запрос):
Post post = postRepository.findById(postId).orElseThrow(...);
if (exists) { post.setLikesCount(count - 1); }
else        { post.setLikesCount(count + 1); }
postRepository.save(post);
```

#### 11. JwtTokenProvider — нет валидации длины ключа
**Файл:** `shared/common-security/.../JwtTokenProvider.java`

```java
// ПОСЛЕ (добавлена проверка):
if (secret.getBytes(UTF_8).length < 32) {
    throw new IllegalArgumentException("JWT secret must be at least 32 characters for HS256");
}
```

#### 12. Postgres — отсутствует POSTGRES_DB и PGDATA
**Файл:** `k8s/infrastructure/postgres.yaml`

```yaml
# ПОСЛЕ:
- name: POSTGRES_DB
  value: "healthlife"
- name: PGDATA
  value: /var/lib/postgresql/data/pgdata
```

#### 13. Analytics и Notification сервисы — не было конфига Redis
**Файлы:** `analytics-service/application.yml`, `notification-service/application.yml`

Эти сервисы используют `StringRedisTemplate`, но Redis не был сконфигурирован в `application.yml`.

#### 14. ChallengeParticipantRepository — отсутствовал нужный метод
**Файл:** `services/social-service/.../ChallengeParticipantRepository.java`

```java
// Добавлен:
Optional<ChallengeParticipant> findByChallengeIdAndUserId(UUID challengeId, UUID userId);
```

#### 15. RestTemplate в Gateway — нет таймаутов
**Файл:** `services/gateway-service/.../GatewayRouteConfig.java`

```java
// ДО (может висеть вечно):
private final RestTemplate restTemplate = new RestTemplate();

// ПОСЛЕ:
this.restTemplate = restTemplateBuilder
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(30))
    .build();
```

---

## Требования

| Инструмент | Версия | Установка |
|-----------|--------|-----------|
| Docker     | ≥ 24   | https://docs.docker.com/get-docker/ |
| Kind       | ≥ 0.23 | `brew install kind` / https://kind.sigs.k8s.io |
| kubectl    | ≥ 1.29 | `brew install kubectl` |
| Java/JDK   | 21     | `brew install openjdk@21` |
| Maven      | ≥ 3.9  | `brew install maven` |

---

## Быстрый старт (локально с Kind)

### Шаг 1 — Клонируй репозиторий
```bash
git clone https://github.com/Maru3022/Application.git
cd Application
```

### Шаг 2 — Запусти скрипт деплоя
```bash
chmod +x k8s/deploy-local.sh
./k8s/deploy-local.sh
```

Скрипт автоматически:
- Создаст Kind кластер из 3 нод
- Установит NGINX Ingress
- Соберёт Maven проект
- Сбилдит все 10 Docker образов
- Загрузит их в Kind
- Создаст Secrets, ConfigMaps
- Запустит Postgres и Redis
- Задеплоит все сервисы
- Дождётся готовности

### Шаг 3 — Добавь запись в /etc/hosts
```bash
echo "127.0.0.1  healthlife.local" | sudo tee -a /etc/hosts
```

### Шаг 4 — Проверь что всё работает
```bash
# Проверь поды
kubectl get pods -n healthlife

# Проверь сервисы
kubectl get services -n healthlife

# Тест регистрации
curl -X POST http://healthlife.local/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!","displayName":"Test User"}'

# Тест логина
curl -X POST http://healthlife.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!"}'
```

### Шаг 5 — Пересоздать кластер с нуля
```bash
./k8s/deploy-local.sh --clean
```

---

## Запуск в облаке (например, GKE / EKS / AKS)

### 1. Подключись к кластеру
```bash
# GKE
gcloud container clusters get-credentials <cluster-name> --zone <zone>
# EKS
aws eks update-kubeconfig --name <cluster-name> --region <region>
```

### 2. Создай namespace и секреты
```bash
kubectl apply -f k8s/base/namespace.yaml

# Замени значения на реальные!
kubectl -n healthlife create secret generic healthlife-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 64)" \
  --from-literal=deepseek-api-key="sk-..."

kubectl -n healthlife create secret generic healthlife-db-credentials \
  --from-literal=username="healthlife" \
  --from-literal=password="$(openssl rand -base64 32)"

kubectl -n healthlife create secret generic healthlife-mail-credentials \
  --from-literal=username="your@email.com" \
  --from-literal=password="your-smtp-password"
```

### 3. Собери и запушь образы
```bash
mvn -B clean package -DskipTests

REGISTRY="ghcr.io/maru3022/application"
for svc in auth-service user-service health-data-service mental-service nutrition-service ai-coach-service social-service notification-service analytics-service gateway-service; do
  docker build -t "$REGISTRY/$svc:latest" "services/$svc"
  docker push "$REGISTRY/$svc:latest"
done
```

### 4. Задеплой
```bash
kubectl apply -f k8s/infrastructure/
kubectl apply -f k8s/base/

# Дождись готовности
kubectl rollout status deployment --all -n healthlife
```

---

## Переменные окружения и секреты

| Переменная | Где | Описание |
|-----------|-----|----------|
| `JWT_SECRET` | Secret: `healthlife-secrets` | Ключ подписи JWT (мин. 32 символа) |
| `DEEPSEEK_API_KEY` | Secret: `healthlife-secrets` | Ключ Anthropic API (опционально) |
| `DB_HOST` | env var в деплойменте | Хост Postgres (дефолт: `postgres`) |
| `DB_USERNAME` | Secret: `healthlife-db-credentials` | Логин Postgres |
| `DB_PASSWORD` | Secret: `healthlife-db-credentials` | Пароль Postgres |
| `SPRING_DATA_REDIS_HOST` | env var | Хост Redis (дефолт: `redis`) |
| `MAIL_USERNAME` | Secret: `healthlife-mail-credentials` | SMTP логин (опционально) |
| `MAIL_PASSWORD` | Secret: `healthlife-mail-credentials` | SMTP пароль (опционально) |

---

## Структура K8s манифестов

```
k8s/
├── kind-cluster.yaml          # Kind кластер (1 control-plane + 2 workers)
├── deploy-local.sh            # Скрипт деплоя для локального запуска
├── base/
│   ├── namespace.yaml         # Namespace: healthlife
│   ├── secrets.yaml           # Заглушки секретов (замени перед деплоем!)
│   ├── gateway.yaml           # Gateway + Ingress
│   ├── auth-service.yaml
│   ├── user-service.yaml
│   ├── health-data-service.yaml
│   ├── mental-service.yaml
│   ├── nutrition-service.yaml
│   ├── ai-coach-service.yaml
│   ├── social-service.yaml
│   ├── notification-service.yaml
│   ├── analytics-service.yaml
│   ├── network-policy.yaml
│   └── vpa.yaml
├── infrastructure/
│   ├── postgres.yaml          # PostgreSQL StatefulSet
│   ├── postgres-init-cm.yaml  # Init скрипт для создания баз данных
│   └── redis.yaml             # Redis Deployment
└── monitoring/
    ├── servicemonitor.yaml    # Prometheus ServiceMonitor
    └── alertmanager-rules.yaml
```

---

## Полезные команды

```bash
# Логи конкретного сервиса
kubectl logs -n healthlife -l app=auth-service --tail=100 -f

# Войти в под
kubectl exec -it -n healthlife deployment/auth-service -- sh

# Войти в Postgres
kubectl exec -it -n healthlife statefulset/postgres -- psql -U healthlife -d healthlife_auth

# Проверить Redis
kubectl exec -it -n healthlife deployment/redis -- redis-cli ping

# Форвард портов для отладки
kubectl port-forward -n healthlife svc/gateway-service 8080:80

# Перезапустить сервис
kubectl rollout restart deployment/auth-service -n healthlife

# Удалить кластер
kind delete cluster --name healthlife
```
