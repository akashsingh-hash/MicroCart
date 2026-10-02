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
  └────────┬─────────┘
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
    └─────────────┘   └──────┬──────┘   └───────────────┘
                             │  WebClient + Resilience4j
                             │  (CircuitBreaker / Retry / Fallback)
                             └──── calls Inventory ────►
```
> `Port: 0*` = random port assigned by Spring; Eureka tracks the actual port.

---

## 🚀 Startup Order

Start services in **this exact order** to avoid registration failures:

1. `discovery-server` — must be up before anyone registers
2. `inventory-service` — order-service depends on it
3. `product-service`
4. `order-service`
5. `api-gateway` — last, after all services are registered

---

*Generated for MicroCart v1.0.0 — Spring Boot 3.3.0 / Spring Cloud 2023.0.3 / Java 21*
