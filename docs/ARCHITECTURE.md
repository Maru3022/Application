# HealthLife Architecture

## C4 Model

### Context Diagram (Level 1)

```mermaid
graph TB
    User[User/Mobile App]
    Admin[Admin]
    HealthLife[HealthLife Platform]
    Email[Email Provider]
    Push[Push Notification Provider]

    User -->|HTTP/REST| HealthLife
    Admin -->|HTTP/REST| HealthLife
    HealthLife -->|SMTP| Email
    HealthLife -->|FCM/APNS| Push
```

### Container Diagram (Level 2)

```mermaid
graph TB
    Mobile[Mobile App<br/>React Native]
    GW[Gateway Service<br/>Port 8080]
    Auth[Auth Service<br/>Port 8081]
    User[User Service<br/>Port 8082]
    Health[Health Data Service<br/>Port 8083]
    Mental[Mental Service<br/>Port 8084]
    Nutrition[Nutrition Service<br/>Port 8085]
    AI[AI Coach Service<br/>Port 8086]
    Social[Social Service<br/>Port 8087]
    Notify[Notification Service<br/>Port 8088]
    Analytics[Analytics Service<br/>Port 8089]

    Postgres[(PostgreSQL)]
    Redis[(Redis)]
    Kafka[(Kafka)]

    Mobile --> GW
    GW --> Auth
    GW --> User
    GW --> Health
    GW --> Mental
    GW --> Nutrition
    GW --> AI
    GW --> Social
    GW --> Notify
    GW --> Analytics

    Auth --> Postgres
    User --> Postgres
    Health --> Postgres
    Mental --> Postgres
    Nutrition --> Postgres
    AI --> Postgres
    Social --> Postgres
    Analytics --> Postgres

    Auth --> Redis
    GW --> Redis
    Notify --> Kafka
```

## Data Flow

1. Client sends request to Gateway with JWT access token
2. Gateway validates token (via JWT signature) and forwards request
3. Target service processes business logic and persists to PostgreSQL
4. Events published to Kafka for async processing (notifications, analytics)
5. Response returned through Gateway to Client

## Technology Decisions

| Decision | Rationale |
|----------|-----------|
| Microservices | Independent scalability and team autonomy |
| Spring Boot 3 | Mature ecosystem, native image support, Java 21 virtual threads |
| PostgreSQL | ACID compliance, rich data types, JSON support |
| Redis | Session caching, rate limiting, distributed locks |
| Kafka | Event sourcing, decoupled async communication |
| Kubernetes | Industry standard container orchestration |
| Helm | Templated, versioned, repeatable deployments |
