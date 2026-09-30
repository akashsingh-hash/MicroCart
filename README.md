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
- **Data Persistence:** Spring Data JPA / MongoDB / MySQL / PostgreSQL (service dependent)
- **Utilities:** Project Lombok
- **Testing:** JUnit 5, Testcontainers

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21** or later installed and configured in `JAVA_HOME`.
- **Maven 3.9+** (or use included Maven wrappers).
- Database instances (MySQL / MongoDB / Docker) depending on the services configured.

### Build the Project
From the root directory:
```bash
mvn clean install
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
