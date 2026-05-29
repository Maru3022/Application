# Standalone-манифесты mental-service

Шаблоны для деплоя без Helm. Перед применением:

1. Замените `your-registry/healthlife-mental-service:latest` в `deployment.yaml`.
2. Заполните `secret.yaml` через CI/CD или `kubectl create secret`.
3. Укажите домен в `ingress.yaml`.

Сборка образа из корня репозитория:

```bash
docker build -f Dockerfile -t your-registry/healthlife-mental-service:latest .
```
