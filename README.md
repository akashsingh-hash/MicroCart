# 🛒 MicroCart — Microservices-Based Online Shopping Cart

A production-inspired, distributed e-commerce backend built entirely with **Java 21**, **Spring Boot 3.3**, and **Spring Cloud 2023**. MicroCart demonstrates real-world microservices patterns including service discovery, API gateway routing, JWT security, event-driven messaging, fault tolerance, and distributed tracing — all wired together without a monolith.

---

## 🏛️ Architecture at a Glance

```
Client (Postman / Frontend)
         │
         ▼
  ┌──────────────────────┐
  │      API Gateway     │  :8080  ← Single entry point
  │  (Spring Cloud GW)   │         ← JWT validation via Keycloak
  └──────────┬───────────┘         ← Routes via lb:// (Eureka)
             │
     ┌───────▼────────────────────────────────────┐
     │            Discovery Server                │  :8761
     │           (Netflix Eureka)                 │
     └──────┬──────────┬──────────────────────────┘
            │          │
    ┌───────▼──┐  ┌────▼──────┐  ┌──────────────────┐
    │ Product  │  │   Order   │  │    Inventory     │
    │ Service  │  │  Service  ├─►│    Service       │
    │(MongoDB) │  │  (MySQL)  │  │    (MySQL)       │
    └──────────┘  └─────┬─────┘  └──────────────────┘
                        │ Kafka Event (OrderPlacedEvent)
                        ▼
               ┌─────────────────────┐
               │  Notification Svc   │  ← @KafkaListener
               │  (Kafka Consumer)   │  ← Async, decoupled
               └─────────────────────┘

  ══════════════════════════════════════════════════════
    Distributed Tracing: Micrometer (Brave) ──► Zipkin :9411
  ══════════════════════════════════════════════════════
```

---

## 🧩 Services

| Service | Port | Database | Role |
|---|---|---|---|
| `discovery-server` | 8761 | — | Eureka service registry |
| `api-gateway` | 8080 | — | Routing, JWT validation, load balancing |
| `product-service` | Random | MongoDB | Product catalog (CRUD) |
| `order-service` | 8081 | MySQL | Order management, calls inventory, Resilience4j |
| `inventory-service` | Random | MySQL | Stock availability checks |
| `notification-service` | Random | — | Kafka consumer — async order event notifications |

> **External infrastructure** (not part of the codebase but required):
> - **Keycloak** `:8181` — OAuth2 Identity Provider
> - **Zipkin** `:9411` — Distributed tracing UI & collector
> - **Apache Kafka** `:9092` — Message broker for async events

---

## 🛠️ Technology Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Core Framework | Spring Boot 3.3.0 |
| Microservices | Spring Cloud 2023.0.3 |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Async Messaging | Apache Kafka + Spring Kafka |
| Security | Keycloak · OAuth2 · JWT (Spring Security Resource Server) |
| Inter-service Calls | Spring WebClient (reactive, load-balanced) |
| Fault Tolerance | Resilience4j (Circuit Breaker · Retry · Time Limiter) |
| Distributed Tracing | Micrometer Tracing (Brave) + Zipkin Reporter |
| Observability | Spring Boot Actuator |
| Databases | MySQL (JPA/Hibernate) · MongoDB (Spring Data) |
| Utilities | Project Lombok |
| Testing | JUnit 5 · Testcontainers |

---

## 🔑 Key Patterns Implemented

- **API Gateway Pattern** — All traffic enters via a single gateway; no direct service exposure.
- **Service Discovery (Client-Side)** — Services register with Eureka; gateway resolves them via `lb://service-name`.
- **Database-per-Service** — Each service owns its own datastore (MongoDB for products, MySQL for orders/inventory).
- **Event-Driven Architecture** — Order Service publishes `OrderPlacedEvent` to Kafka; Notification Service consumes it asynchronously.
- **Circuit Breaker + Retry** — Order → Inventory calls are protected with Resilience4j; fallback response returned when inventory is down.
- **Distributed Tracing** — Trace IDs propagate across all services via Micrometer (Brave); full traces visible in Zipkin.
- **JWT Security** — Every API request is validated against a Keycloak-issued JWT at both the gateway and service level.
- **Random Port + Eureka Instance ID** — Product, inventory, and notification services run on `port=0` with unique Eureka instance IDs, enabling seamless horizontal scaling.

---

## 📚 Documentation

Full documentation lives in the [`docs/`](./docs) folder:

| # | Document | What's Inside |
|---|---|---|
| 01 | [Project Overview](./docs/01-project-overview.md) | Architecture, tech stack, design decisions |
| 02 | [Discovery Server](./docs/02-discovery-server.md) | Eureka deep-dive |
| 03 | [API Gateway](./docs/03-api-gateway.md) | Routing rules, JWT config, YAML reference |
| 04 | [Product Service](./docs/04-product-service.md) | MongoDB, CRUD endpoints |
| 05 | [Order Service](./docs/05-order-service.md) | Resilience4j, Actuator, Kafka publishing |
| 06 | [Inventory Service](./docs/06-inventory-service.md) | Stock checks, SKU validation |
| 07 | [Security & Keycloak](./docs/07-security-keycloak.md) | OAuth2, JWT, realm setup |
| 08 | [Inter-Service Communication](./docs/08-inter-service-communication.md) | WebClient, load balancing, token forwarding |
| 09 | [Data Models & DTOs](./docs/09-data-models-dtos.md) | Entity and DTO reference |
| 10 | [Configuration Reference](./docs/10-configuration-reference.md) | All `application.properties` explained |
| 11 | [Startup & Running Guide](./docs/11-startup-running-guide.md) | Step-by-step setup and test guide |
| 12 | [Interview Q&A](./docs/12-interview-qa.md) | 50+ microservices interview questions |
| 13 | [Notification Service](./docs/13-notification-service.md) | Kafka consumer, Eureka & tracing setup |

---

## 🚀 Getting Started

### 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 21+ | Set in `JAVA_HOME` |
| Maven | 3.9+ | Or use included `./mvnw` wrapper |
| MySQL | 8.0+ | Runs on `localhost:3306` |
| MongoDB | 6.0+ | Runs on `localhost:27017` |
| Keycloak | 22+ | Runs on `localhost:8181` |
| Zipkin | Latest | `docker run -d -p 9411:9411 openzipkin/zipkin` |
| Apache Kafka | 3.x+ | Runs on `localhost:9092` |

### 2. Database Setup

```sql
-- MySQL: create databases (Hibernate creates the tables automatically)
CREATE DATABASE order_service;
CREATE DATABASE inventory_service;
```

MongoDB requires no manual setup — Spring creates the database on first use.

### 3. Build

```bash
# From the project root
mvn clean install -DskipTests
```

### 4. Startup Order

Start infrastructure first, then services in this order:

```
0. Keycloak (:8181)  +  Zipkin (:9411)  +  Kafka (:9092)
1. discovery-server  (:8761)   ← Eureka must be up first
2. inventory-service (random)
3. product-service   (random)
4. order-service     (:8081)
5. api-gateway       (:8080)
6. notification-service (random)
```

```bash
# Example: run any service
cd <service-name>
./mvnw spring-boot:run
```

### 5. Get a JWT Token (Keycloak)

```bash
curl -X POST http://localhost:8181/realms/ecommerce/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=shopping-cart-client&username=user1&password=user1password"
```

Use the returned `access_token` as `Authorization: Bearer <token>` in all API requests.

---

## 📬 Kafka Event Flow

When an order is placed successfully, Order Service publishes an event to Kafka:

```
order-service  ──publishes──►  notificationTopic  ──consumed by──►  notification-service
                                (Kafka Broker)                        (@KafkaListener)
```

This decouples notification logic completely — the order response is not blocked by notification processing.

---

## 📄 License

This project is open-source and free to use for learning and reference purposes.
