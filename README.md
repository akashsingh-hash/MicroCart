# MicroCart - Microservices-Based Online Shopping Cart System

A scalable, distributed e-commerce backend built with **Spring Boot 3.3.x**, **Spring Cloud (2023.0.x)**, and **Java 21**.

---

## 🏛️ Architecture Overview

The system consists of the following microservices:

1. **`discovery-server`** (Eureka Server)
   - Service discovery and registration hub for all microservice instances.
2. **`api-gateway`** (Spring Cloud Gateway)
   - Unified entry point for routing client requests to backend services with load balancing.
3. **`product-service`**
   - Manages product catalog, inventory creation, and metadata.
4. **`order-service`**
   - Handles customer orders, checkout flows, and coordinates with inventory.
5. **`inventory-service`**
   - Tracks stock availability and validates SKU counts before order fulfillment.

---

## 🛠️ Technology Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.3.0, Spring Cloud 2023.0.3
- **Service Discovery:** Netflix Eureka
- **API Gateway:** Spring Cloud Gateway
- **Distributed Tracing & Observability:** Micrometer Tracing (Brave bridge), Zipkin, Spring Boot Actuator
- **Fault Tolerance & Resilience:** Resilience4j (Circuit Breaker, Retry, Time Limiter)
- **Security & IAM:** Keycloak (OAuth2 Resource Server / JWT)
- **Data Persistence:** Spring Data JPA (MySQL) & Spring Data MongoDB
- **Utilities:** Project Lombok
- **Testing:** JUnit 5, Testcontainers

---

## 📚 Documentation

Comprehensive project documentation is available in the [`docs/`](./docs) directory:
- [01 - Project Overview](./docs/01-project-overview.md)
- [02 - Discovery Server](./docs/02-discovery-server.md)
- [03 - API Gateway](./docs/03-api-gateway.md)
- [04 - Product Service](./docs/04-product-service.md)
- [05 - Order Service (Resilience4j & Actuator)](./docs/05-order-service.md)
- [06 - Inventory Service](./docs/06-inventory-service.md)
- [07 - Security & Keycloak](./docs/07-security-keycloak.md)
- [08 - Inter-Service Communication](./docs/08-inter-service-communication.md)
- [09 - Data Models & DTOs](./docs/09-data-models-dtos.md)
- [10 - Configuration Reference](./docs/10-configuration-reference.md)
- [11 - Startup & Running Guide](./docs/11-startup-running-guide.md)
- [12 - Interview Q&A](./docs/12-interview-qa.md)

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21** or later installed and configured in `JAVA_HOME`.
- **Maven 3.9+** (or use included Maven wrappers).
- Database instances (MySQL / MongoDB / Docker) depending on the services configured.
- Keycloak 22+ running on `http://localhost:8181`.
- Zipkin server running on `http://localhost:9411` (`docker run -d -p 9411:9411 openzipkin/zipkin`).

### Build the Project
From the root directory:
```bash
mvn clean install -DskipTests
```

### Startup Order
For proper registration and discovery, start services in the following order:
1. `discovery-server` (Eureka)
2. `inventory-service`
3. `product-service`
4. `order-service`
5. `api-gateway`

---

## 📄 License
This project is open-source.
