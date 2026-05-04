# Инструкция по запуску проекта в Kubernetes

## Предварительные требования
Перед началом убедитесь, что у вас установлены:
- **Docker**: Для сборки образов.
- **kubectl**: Для управления Kubernetes-кластером.
- **Kind**: Для запуска локального Kubernetes-кластера.
- **Maven**: Для сборки Java-проектов.
- **Make**: Для выполнения команд из `Makefile`.

## Шаги по запуску

### 1. Запуск локального Kubernetes-кластера
1. Перейдите в директорию `k8s`:
   ```powershell
   cd k8s
   ```
2. Запустите кластер с помощью Kind:
   ```powershell
   kind create cluster --config kind-cluster.yaml
   ```

### 2. Сборка Docker-образов
1. Вернитесь в корневую директорию проекта:
   ```powershell
   cd ..
   ```
2. Выполните команду для сборки всех Docker-образов:
   ```powershell
   make docker-build
   ```

### 3. Настройка локального реестра Docker
Kind использует локальный реестр Docker. Чтобы образы были доступны в кластере:
1. Тегируйте образы для локального реестра:
   ```powershell
   docker tag healthlife/auth-service:latest localhost:5000/auth-service:latest
   ```
   Повторите для всех сервисов.
2. Загрузите образы в кластер:
   ```powershell
   kind load docker-image localhost:5000/auth-service:latest
   ```
   Повторите для всех сервисов.

### 4. Деплой в Kubernetes
1. Примените манифесты для пространства имен:
   ```powershell
   kubectl apply -f k8s/base/namespace.yaml
   ```
2. Примените все манифесты:
   ```powershell
   make k8s-deploy
   ```

### 5. Проверка состояния
1. Убедитесь, что все поды запущены:
   ```powershell
   kubectl get pods -n healthlife
   ```
2. Убедитесь, что сервисы доступны:
   ```powershell
   kubectl get svc -n healthlife
   ```

### 6. Настройка секретов
Перед запуском убедитесь, что секреты `healthlife-secrets` и `healthlife-db-credentials` созданы:
1. Создайте секреты:
   ```powershell
   kubectl create secret generic healthlife-secrets --from-literal=jwt-secret=your_jwt_secret -n healthlife
   kubectl create secret generic healthlife-db-credentials --from-literal=username=db_user --from-literal=password=db_password -n healthlife
   ```

### 7. Тестирование
1. Проверьте доступность сервисов через `kubectl port-forward` или настройте Ingress-контроллер.
2. Например, для `auth-service`:
   ```powershell
   kubectl port-forward svc/auth-service 8081:80 -n healthlife
   ```
   Затем откройте в браузере: [http://localhost:8081](http://localhost:8081).

### 8. Остановка
Для удаления всех ресурсов:
```powershell
make k8s-undeploy
```