# Инструкция по запуску HealthLife в Kubernetes

## Обзор

HealthLife — платформа из 11 микросервисов, запускаемых в Kubernetes через kind (Kubernetes IN Docker).

```
kind-healthlife (3 ноды: control-plane + 2 worker)
├── Namespace: healthlife
│   ├── PostgreSQL (1 под, 7 баз данных)
│   ├── Redis (1 под)
│   ├── gateway-service      :8080  ← входная точка
│   ├── auth-service         :8081  (DB + Redis)
│   ├── user-service         :8082  (DB)
│   ├── health-data-service  :8083  (DB)
│   ├── mental-service       :8084  (DB)
│   ├── nutrition-service    :8085  (DB)
│   ├── ai-coach-service     :8086  (DB + Redis)
│   ├── social-service       :8087  (DB)
│   ├── notification-service :8088
│   ├── analytics-service    :8089  (Redis)
│   └── payment-service      :8090  (DB)
└── Ingress: nginx (порт 80 хоста → gateway)
```

## Предварительные требования

| Инструмент | Версия | Установка на Windows |
|---|---|---|
| **Docker Desktop** | 20+ | https://desktop.docker.com — выделите **8 ГБ RAM** и **4 CPU** |
| **kind** | 0.20+ | `choco install kind` или скачайте exe с https://kind.sigs.k8s.io |
| **kubectl** | 1.28+ | `choco install kubernetes-cli` |
| **helm** | 3.12+ | `choco install kubernetes-helm` |

> **Важно:** Docker Desktop должен быть запущен перед началом.

## Быстрый запуск

### Windows (PowerShell)

```powershell
# Из корня проекта (Docker Desktop должен быть запущен)
# Опционально: PAT GitHub с правом read:packages — иначе поды могут не скачать образы с GHCR
$env:GHCR_TOKEN = "<ваш_github_pat>"
.\k8s\Start-Cluster.ps1
```

### Linux / macOS

```bash
chmod +x k8s/start-cluster.sh
./k8s/start-cluster.sh
```

Скрипт автоматически:
1. Создаёт kind-кластер
2. Устанавливает NGINX Ingress Controller
3. Создаёт namespace и секреты
4. Разворачивает PostgreSQL (8 БД) и Redis
5. Устанавливает все 11 сервисов через Helm
6. Применяет NetworkPolicies

## Ручная установка (пошагово)

### 1. Создание кластера

```bash
kind create cluster --config k8s/kind-cluster.yaml --wait 120s
kubectl config use-context kind-healthlife
```

### 2. NGINX Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for condition=ready pod \
  --selector app.kubernetes.io/component=controller \
  --timeout=120s
```

### 3. Namespace и лейблы

```bash
kubectl apply -f k8s/base/namespace.yaml
kubectl label namespace healthlife name=healthlife --overwrite
kubectl label namespace ingress-nginx name=ingress-nginx --overwrite
```

### 4. Секреты

```bash
# JWT секрет
kubectl create secret generic healthlife-secrets \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  -n healthlife

# Данные БД
kubectl create secret generic healthlife-db-credentials \
  --from-literal=username=healthlife \
  --from-literal=password=healthlife_pass \
  -n healthlife
```

### 5. Инфраструктура (PostgreSQL + Redis)

```bash
kubectl apply -f k8s/infrastructure/postgres-init-cm.yaml
kubectl apply -f k8s/infrastructure/postgres.yaml
kubectl apply -f k8s/infrastructure/redis.yaml

# Ожидание готовности
kubectl wait --for=condition=ready pod -l app=postgres -n healthlife --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n healthlife --timeout=60s
```

PostgreSQL автоматически создаёт 8 баз данных через init-скрипт (только при **первом** создании тома данных):
`healthlife_auth`, `healthlife_user`, `healthlife_healthdata`, `healthlife_mental`, `healthlife_nutrition`, `healthlife_aicoach`, `healthlife_social`, `healthlife_payment`

### 6. Микросервисы (Helm)

```bash
helm upgrade --install healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=latest \
  --timeout 300s
```

Для конкретного тега образа:

```bash
helm upgrade --install healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=sha-abc1234
```

### 7. NetworkPolicies

```bash
kubectl apply -f k8s/base/network-policy.yaml
```

## Доступ к приложению

### Почему в браузере `ERR_CONNECTION_REFUSED` на localhost:8080

Сервисы в Kubernetes имеют тип **ClusterIP**: на вашем ПК **нет** процесса, который слушает `:8080`, пока вы не запустите **port-forward** (или ingress на 80 с hosts). Открыть только браузер без туннеля — нормально приведёт к отказу в соединении.

### Способ 1: Port-forward (обязательный минимум для localhost:8080)

**Windows (отдельное окно PowerShell, оставьте его открытым):**

```powershell
cd C:\Project\Application
.\k8s\Port-Forward-Gateway.ps1
```

Или после установки кластера сразу открыть второе окно с пробросом:

```powershell
.\k8s\Start-Cluster.ps1 -StartGatewayPortForward
```

**Linux / macOS:**

```bash
chmod +x k8s/port-forward-gateway.sh
./k8s/port-forward-gateway.sh
```

Вручную то же самое:

```bash
kubectl port-forward svc/gateway-service 8080:80 -n healthlife
```

Открыть: http://localhost:8080 (например http://localhost:8080/actuator/health).

### Способ 2: Ingress (production-like)

1. Добавить в hosts-файл:
   - **Windows:** `C:\Windows\System32\drivers\etc\hosts`
   - **Linux/macOS:** `/etc/hosts`

```
127.0.0.1 healthlife.local
```

2. Открыть: http://healthlife.local

## API endpoints

Через gateway (порт 80 → :8080 внутри):

| Путь | Сервис |
|---|---|
| `/api/v1/auth/**` | auth-service |
| `/api/v1/users/**` | user-service |
| `/api/v1/health/**` | health-data-service |
| `/api/v1/mental/**` | mental-service |
| `/api/v1/nutrition/**` | nutrition-service |
| `/api/v1/ai/**` | ai-coach-service |
| `/api/v1/social/**` | social-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/analytics/**` | analytics-service |
| `/api/v1/payments/**` | payment-service |

Примеры:

```bash
# Регистрация
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@healthlife.com","password":"Test1234!"}'

# Health check
curl http://localhost:8080/actuator/health
```

## Полезные команды

```bash
# Статус подов
kubectl get pods -n healthlife

# Следить за запуском
kubectl get pods -n healthlife -w

# Сервисы
kubectl get svc -n healthlife

# HPA
kubectl get hpa -n healthlife

# Логи конкретного сервиса
kubectl logs -f deployment/gateway-service -n healthlife

# Подключение к PostgreSQL
kubectl exec -it statefulset/postgres -n healthlife -- psql -U healthlife -d healthlife_auth

# Подключение к Redis
kubectl exec -it deployment/redis -n healthlife -- redis-cli ping

# Описание пода (для диагностики)
kubectl describe pod <pod-name> -n healthlife
```

## Масштабирование

```bash
# Ручное масштабирование
kubectl scale deployment auth-service --replicas=4 -n healthlife

# HPA автоматически масштабирует 2-8 реплик по CPU
kubectl get hpa -n healthlife -w
```

## Rolling Update

```bash
helm upgrade healthlife ./k8s/helm/healthlife \
  --namespace healthlife \
  --set image.tag=новый-тег
```

## Rollback

```bash
helm history healthlife -n healthlife
helm rollback healthlife 1 -n healthlife
```

## Удаление

```bash
# Только релиз
helm uninstall healthlife -n healthlife

# Весь кластер
kind delete cluster --name healthlife
```

## Устранение неполадок

| Проблема | Решение |
|---|---|
| `ImagePullBackOff` | Создайте `ghcr-secret` (PAT с `read:packages`) или задайте `$env:GHCR_TOKEN` перед `Start-Cluster.ps1`; см. `k8s/base/registry-secret.yaml` |
| `ERR_CONNECTION_REFUSED` на `localhost:8080` | Запустите `.\k8s\Port-Forward-Gateway.ps1` (окно не закрывать) или `kubectl port-forward svc/gateway-service 8080:80 -n healthlife` |
| `CrashLoopBackOff` | Смотрите логи: `kubectl logs <pod> -n healthlife` |
| PostgreSQL не готов | `kubectl describe pod -l app=postgres -n healthlife` |
| Ingress не работает | Проверьте поды: `kubectl get pods -n ingress-nginx` |
| NetworkPolicy блокирует | Временно удалите: `kubectl delete networkpolicy healthlife-default-deny -n healthlife` |
| kind не создаётся | Убедитесь что Docker Desktop запущен с 8+ ГБ RAM |
| Поды зависли в Pending | Проверьте ресурсы: `kubectl describe node` |

## Ресурсы

| Компонент | CPU | RAM |
|---|---|---|
| PostgreSQL | 100m-500m | 256Mi-512Mi |
| Redis | 50m-200m | 64Mi-128Mi |
| Каждый микросервис | 100m-500m | 256Mi-512Mi |
| **Итого (2 реплики)** | ~3 vCPU | ~6 ГБ |

Docker Desktop: минимум **8 ГБ RAM**, **4 CPU**.
