# 📚 MicroCart Documentation

Welcome to the complete documentation for **MicroCart** — a production-grade, Microservices-Based Online Shopping Cart System built with Spring Boot 3, Spring Cloud, and Java 21.

---

## 📖 Documentation Index

| Document | Description |
|---|---|
| [01 - Project Overview](./01-project-overview.md) | Architecture, tech stack, design decisions |
| [02 - Discovery Server](./02-discovery-server.md) | Eureka Service Registry — deep dive |
| [03 - API Gateway](./03-api-gateway.md) | Spring Cloud Gateway, routing, security |
| [04 - Product Service](./04-product-service.md) | Product catalog management, MongoDB |
| [05 - Order Service](./05-order-service.md) | Order placement, Resilience4j fault tolerance, Actuator monitoring |
| [06 - Inventory Service](./06-inventory-service.md) | Stock management, SKU validation |
| [07 - Security & Keycloak](./07-security-keycloak.md) | OAuth2 / JWT, Keycloak integration |
| [08 - Inter-Service Communication](./08-inter-service-communication.md) | WebClient, load balancing, Resilience4j, service-to-service auth |
| [09 - Data Models & DTOs](./09-data-models-dtos.md) | Entity and DTO reference for all services |
| [10 - Configuration Reference](./10-configuration-reference.md) | All application properties explained |
| [11 - Startup & Running Guide](./11-startup-running-guide.md) | How to set up, start, and test the whole system |
| [12 - Interview Q&A](./12-interview-qa.md) | 50+ interview questions with detailed answers |
| [13 - Notification Service](./13-notification-service.md) | Kafka consumer, event-driven notifications, Eureka & tracing setup |

---

## 🧭 Quick Architecture Snapshot

```
Client (Postman / Browser)
         │
         ▼
  ┌──────────────────┐
  │   API Gateway    │  :8080  ← Single entry point
  │  (Spring Cloud   │         ← JWT validation
  │    Gateway)      │         ← Route → lb://
  └────────┬─────────┘         ← Tracing span created
           │ Service Discovery (Eureka)
           ▼
  ┌─────────────────────────────────────────┐
  │           Discovery Server              │  :8761
  │         (Netflix Eureka)                │
  └───────────────────────────────────────┬─┘
           │                              │
    ┌──────▼──────┐   ┌─────────────┐   ┌▼──────────────┐
    │   Product   │   │    Order    │   │   Inventory   │
    │  Service    │   │   Service   │   │    Service    │
    │  (MongoDB)  │   │   (MySQL)   │   │   (MySQL)     │
    │  Port: 0*   │   │  Port: 8081 │   │  Port: 0*     │
    └──────┬──────┘   └──────┬──────┘   └──────┬────────┘
           │                 │ WebClient       │
           │                 │ + Resilience4j  │
           │                 └─ calls Inv. ────►
           │                 │                 │
           ▼                 ▼                 ▼
   ══════════════════════════════════════════════════════
     Distributed Tracing: Micrometer Tracing (Brave)
              ──► Zipkin Server (:9411) ◄──
   ══════════════════════════════════════════════════════
```
> `Port: 0*` = random port assigned by Spring; Eureka tracks the actual port.
> Distributed traces are exported to Zipkin on port `9411`.

---

## 🚀 Startup Order

Start infrastructure and services in **this exact order**:

0. `Keycloak` (:8181), `Zipkin` (:9411) & `Kafka` (:9092) — IAM, Distributed Tracing, and Messaging infrastructure
1. `discovery-server` (:8761) — must be up before anyone registers
2. `inventory-service` — order-service depends on it
3. `product-service`
4. `order-service`
5. `api-gateway` (:8080) — after all services are registered
6. `notification-service` — Kafka consumer, after Kafka broker is ready

---

*Generated for MicroCart v1.0.0 — Spring Boot 3.3.0 / Spring Cloud 2023.0.3 / Java 21*
