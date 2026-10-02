# 01 — Project Overview

## What is MicroCart?

**MicroCart** is a backend system for an online shopping cart, built using the **Microservices Architecture**. Instead of building one big monolithic application, the system is split into multiple small, independently deployable services that each own a specific business domain.

This project demonstrates real-world patterns used in enterprise-level microservice systems including:
- Service discovery and registration
- API gateway with routing and security
- Inter-service synchronous communication
- Fault tolerance, retries, and circuit breakers with Resilience4j
- Operational monitoring and health checks with Spring Boot Actuator
- JWT-based security with Keycloak
- Polyglot persistence (MongoDB + MySQL)

---

## 🏛️ Architecture Style: Microservices

### What is Microservices Architecture?
Microservices architecture decomposes an application into a set of small, autonomous services where each service:
- Runs in its own process
- Communicates over well-defined APIs (HTTP/REST in this case)
- Has its own database (database-per-service pattern)
- Can be deployed independently

### Why not a Monolith?
| Aspect | Monolith | Microservices |
|---|---|---|
| Deployment | Whole app must be redeployed | Individual services deployable |
| Scaling | Scale the entire app | Scale only bottleneck services |
| Technology | Tied to one stack | Polyglot (each service chooses) |
| Failure | One failure can crash all | Isolated failures |
| Team | Large teams, merge conflicts | Small, independent teams |

---

## 🧩 Services in this System

| Service | Port | Database | Role |
|---|---|---|---|
| `discovery-server` | 8761 | None | Service registry (Eureka) |
| `api-gateway` | 8080 | None | Entry point, routing, JWT validation |
| `product-service` | Random (0) | MongoDB | Product catalog CRUD |
| `order-service` | 8081 | MySQL | Order placement, calls inventory, Resilience4j |
| `inventory-service` | Random (0) | MySQL | Stock level checking |

> **Keycloak** (Identity Provider) runs separately on port **8181** (not a part of this codebase, but required externally).

---

## 🛠️ Technology Stack

### Core Framework
- **Java 21** — Latest LTS, uses modern features (records, text blocks, pattern matching)
- **Spring Boot 3.3.0** — Rapid application development framework
- **Spring Cloud 2023.0.3** — Distributed system patterns (discovery, gateway, etc.)

### Spring Dependencies Breakdown

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API with embedded Tomcat |
| `spring-boot-starter-webflux` | Reactive programming (WebClient for inter-service calls) |
| `spring-boot-starter-data-jpa` | ORM for relational databases |
| `spring-boot-starter-data-mongodb` | ODM for MongoDB documents |
| `spring-cloud-starter-netflix-eureka-server` | Run a Eureka service registry |
| `spring-cloud-starter-netflix-eureka-client` | Register services with Eureka |
| `spring-cloud-starter-gateway` | API Gateway (reactive-based routing) |
| `spring-boot-starter-security` | Spring Security |
| `spring-boot-starter-oauth2-resource-server` | JWT token validation |
| `spring-boot-starter-actuator` | Health/metrics endpoints & circuit breaker monitoring |
| `resilience4j-spring-boot3` | Fault tolerance auto-configuration for Spring Boot 3 |
| `resilience4j-circuitbreaker` | Circuit breaker implementation |
| `resilience4j-retry` | Automatic retries for transient failures |

### Databases
- **MongoDB** — Used by `product-service` (document store, schema-flexible)
- **MySQL** — Used by `order-service` and `inventory-service` (relational, ACID)

### Security
- **Keycloak** — Open-source Identity and Access Management (IAM)
- **OAuth 2.0 + JWT** — Industry standard token-based authentication
- **Spring Security** — Security filter chain in each service

### Developer Tools
- **Lombok** — Reduces Java boilerplate (`@Data`, `@Builder`, `@RequiredArgsConstructor`)
- **Testcontainers** — Spin up Docker containers in tests (MongoDB for product-service tests)
- **JUnit 5** — Unit and integration testing

---

## 📁 Project Structure

```
online-shopping-cart/          ← Maven parent (aggregator)
├── pom.xml                    ← Parent POM, manages all dependencies
├── .gitignore
├── README.md
├── docs/                      ← 📚 Documentation (this folder)
│
├── discovery-server/          ← Eureka Server
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../DiscoveryServerApplication.java
│       └── resources/application.properties
│
├── api-gateway/               ← Spring Cloud Gateway
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../config/SecurityConfig.java
│       ├── java/.../ApiGatewayApplication.java
│       └── resources/application.yml
│
├── product-service/           ← Product Catalog
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── controller/ProductController.java
│       ├── service/ProductService.java
│       ├── model/Product.java
│       ├── dto/{ProductRequest, ProductResponse}.java
│       ├── repository/ProductRepository.java
│       ├── config/SecurityConfig.java
│       └── ProductServiceApplication.java
│
├── order-service/             ← Order Management
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── controller/OrderController.java
│       ├── service/OrderService.java
│       ├── model/{Order, OrderLineItems}.java
│       ├── dto/{OrderRequest, OrderLineItemsDto, InventoryResponse}.java
│       ├── repository/OrderRepository.java
│       ├── config/{SecurityConfig, WebClientConfig, KeycloakJwtAuthenticationConverter}.java
│       └── OrderServiceApplication.java
│
└── inventory-service/         ← Stock Management
    ├── pom.xml
    └── src/main/java/.../
        ├── controller/InventoryController.java
        ├── service/InventoryService.java
        ├── model/Inventory.java
        ├── dto/InventoryResponse.java
        ├── repository/InventoryRepository.java
        ├── config/SecurityConfig.java
        └── InventoryServiceApplication.java
```

---

## 🔑 Design Decisions & Patterns Used

### 1. Database-per-Service Pattern
Each service has its own dedicated database. This ensures:
- Loose coupling — one service's schema change doesn't break others
- Technology freedom — Product uses MongoDB (flexible documents), Order/Inventory use MySQL (relational)

### 2. API Gateway Pattern
All clients talk only to the API Gateway (`localhost:8080`). The gateway:
- Routes requests to the correct microservice
- Validates JWT tokens (so services don't need to validate themselves... well, they also do for defense in depth)
- Provides a single consistent entry point

### 3. Service Discovery Pattern (Client-Side)
Services register themselves with Eureka. The API Gateway and Order Service use `lb://` URIs (load-balanced) to resolve service instances. This eliminates hardcoded IPs/ports.

### 4. Synchronous Inter-Service Communication
Order Service calls Inventory Service synchronously using **Spring WebClient** (reactive HTTP client). Token forwarding ensures the downstream call is also authenticated.

### 5. Random Port Assignment (`server.port=0`)
Product-service and inventory-service use port 0, meaning Spring picks a random free port at startup. This allows running multiple instances on the same machine without port conflicts. Eureka tracks the actual port.

---

## 🌐 Request Flow Example — Placing an Order

```
1. Client sends POST /api/order with JWT token
2. API Gateway (8080) receives request
3. Gateway validates JWT against Keycloak (8181)
4. Gateway routes to order-service (lb://order-service → 8081)
5. OrderService extracts line items from request
6. OrderService calls inventory-service via WebClient:
   GET http://inventory-service/api/inventory?skuCode=sku1&skuCode=sku2
   (with forwarded JWT Bearer token)
7. InventoryService queries MySQL, returns stock status
8. If ALL items are in stock → Order is saved to MySQL
9. If ANY item is out of stock → IllegalAccessException thrown → 500 error
10. Response propagates back to client
```
