.PHONY: help build test verify format-check docker-build docker-push k8s-deploy k8s-undeploy local-up local-down

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

build: ## Compile all modules
	mvn clean compile -B

test: ## Run unit tests
	mvn test -B

verify: ## Run full verification (tests, integration tests, coverage)
	mvn clean verify -B

format-check: ## Check code formatting with Spotless
	mvn spotless:check -B

format-apply: ## Apply code formatting with Spotless
	mvn spotless:apply -B

docker-build: ## Build all Docker images locally
	@for service in services/*/; do \
		name=$$(basename $$service); \
		echo "Building $$name..."; \
		docker build -t healthlife/$$name:latest $$service; \
	done

k8s-deploy: ## Deploy to local Kubernetes (kubectl)
	kubectl apply -f k8s/base/namespace.yaml
	kubectl apply -f k8s/base/

k8s-undeploy: ## Remove from local Kubernetes
	kubectl delete -f k8s/base/ --ignore-not-found=true

local-up: ## Start local infrastructure (Postgres, Redis, Kafka)
	docker-compose -f infrastructure/docker-compose.yml up -d

local-down: ## Stop local infrastructure
	docker-compose -f infrastructure/docker-compose.yml down

security-scan: ## Run OWASP dependency check
	mvn org.owasp:dependency-check-maven:check -B
