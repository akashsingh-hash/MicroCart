# 10 — Configuration Reference

Complete reference for all application configuration properties used across all services.

---

## Parent POM Configuration

**File:** `pom.xml` (root)

| Property | Value | Meaning |
|---|---|---|
| `groupId` | `com.akash` | Maven group ID for all services |
| `version` | `1.0.0` | Parent project version |
| `java.version` | `21` | Java 21 LTS |
| `spring-cloud.version` | `2023.0.3` | Spring Cloud BOM version |
| `lombok.version` | `1.18.46` | Lombok version (overrides parent) |

---

## Discovery Server

**File:** `discovery-server/src/main/resources/application.properties`

```properties
spring.application.name=discovery-server
server.port=8761
eureka.instance.hostname=localhost
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

| Property | Value | Explanation |
|---|---|---|
| `spring.application.name` | `discovery-server` | Service name in Eureka |
| `server.port` | `8761` | Eureka conventional port |
| `eureka.instance.hostname` | `localhost` | Hostname for Eureka self-reference |
| `eureka.client.register-with-eureka` | `false` | Server doesn't register itself |
| `eureka.client.fetch-registry` | `false` | No need to cache registry locally |

---

## API Gateway

**File:** `api-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: api-server

  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**

        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**

        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**

        - id: discovery-server
          uri: http://localhost:8761
          predicates:
            - Path=/eureka/**
          filters:
            - RewritePath=/eureka/(?<segment>.*), /${segment}

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8181/realms/ecommerce

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

logging:
  level:
    root: info
    org.springframework.cloud.gateway: TRACE
    route:
      RouteDefinitionLocator: INFO
```

| Property | Value | Explanation |
|---|---|---|
| `server.port` | `8080` | Gateway listens here (public port) |
| `spring.application.name` | `api-server` | Name registered with Eureka |
| `gateway.routes[*].id` | e.g., `order-service` | Unique name for each route |
| `gateway.routes[*].uri` | `lb://service-name` | Load-balanced URI via Eureka |
| `gateway.routes[*].predicates` | `Path=/api/order/**` | URL pattern to match |
| `jwt.issuer-uri` | Keycloak realm URL | Used to validate JWT issuer claim |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka/` | Where to register/find services |
| `logging.level.org.springframework.cloud.gateway` | `TRACE` | Verbose gateway logging for debugging |

---

## Product Service

**File:** `product-service/src/main/resources/application.properties`

```properties
spring.application.name=product-service
spring.data.mongodb.uri=mongodb://localhost:27017/product-service
server.port=0
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/ecommerce
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs
```

| Property | Value | Explanation |
|---|---|---|
| `spring.application.name` | `product-service` | Eureka registration name |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/product-service` | MongoDB connection string |
| `server.port` | `0` | Random port assigned at startup |
| `eureka.client.service-url.defaultZone` | Eureka URL | Where to register |
| `jwt.issuer-uri` | Keycloak realm URL | Validates `iss` claim in JWT |
| `jwt.jwk-set-uri` | Keycloak JWKS endpoint | Fetches public keys for signature verification |

---

## Order Service

**File:** `order-service/src/main/resources/application.properties`

```properties
spring.application.name=order-service
spring.datasource.url=jdbc:mysql://localhost:3306/order_service?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=Akash@0786
spring.jpa.hibernate.ddl-auto=update
server.port=8081
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# Keycloak JWT validation
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/ecommerce
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs

# Circuit breaker for inventory service
resilience4j.circuitbreaker.instances.inventoryService.registerHealthIndicator=true
resilience4j.circuitbreaker..instances.inventoryService.eventConsumerBufferSize=10
resilience4j.circuitbreaker.instances.inventoryService.slidingWindowType=COUNT_BASED
resilience4j.circuitbreaker.instances.inventoryService.slidingWindowSize=10
resilience4j.circuitbreaker.instances.inventoryService.failureRateThreshold=50
resilience4j.circuitbreaker.instances.inventoryService.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.inventoryService.permittedNumberOfCallsInHalfOpenState=3

# Resilience 4j timeout properties
resilience4j.timelimiter.instances.inventoryService.timeoutDuration=3s

# Retry for inventory service
resilience4j.retry.instances.inventoryService.maxAttempts=3
resilience4j.retry.instances.inventoryService.waitDuration=2s

# Expose actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
management.health.circuitbreakers.enabled=true
```

| Property | Value | Explanation |
|---|---|---|
| `spring.application.name` | `order-service` | Eureka registration name |
| `spring.datasource.url` | MySQL JDBC URL | Connects to `order_service` database |
| `allowPublicKeyRetrieval=true` | URL param | Required for MySQL 8 password auth |
| `useSSL=false` | URL param | Disables SSL (dev only — enable in prod) |
| `serverTimezone=UTC` | URL param | Consistent timezone handling |
| `spring.datasource.username` | `root` | MySQL username |
| `spring.datasource.password` | `Akash@0786` | MySQL password (**use env vars in prod!**) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema update without drop |
| `server.port` | `8081` | Fixed port for order-service |
| `eureka.client.service-url.defaultZone` | Eureka URL | Service registry endpoint |
| `spring.security.oauth2...jwt.issuer-uri` | Keycloak realm URL | Validates JWT token issuer |
| `spring.security.oauth2...jwt.jwk-set-uri` | Keycloak certs URL | Fetches public keys to verify JWT signatures |
| `resilience4j.circuitbreaker...registerHealthIndicator` | `true` | Publishes circuit breaker status to Spring Boot Actuator |
| `resilience4j.circuitbreaker...slidingWindowType` | `COUNT_BASED` | Evaluates failure rates based on call count (not time duration) |
| `resilience4j.circuitbreaker...slidingWindowSize` | `10` | Size of the sliding window (number of calls monitored) |
| `resilience4j.circuitbreaker...failureRateThreshold` | `50` | Opens circuit breaker when >= 50% calls fail |
| `resilience4j.circuitbreaker...waitDurationInOpenState` | `10s` | How long the circuit stays OPEN before transitioning to HALF-OPEN |
| `resilience4j.circuitbreaker...permittedNumberOfCallsInHalfOpenState` | `3` | Trial calls permitted in HALF-OPEN state to test service recovery |
| `resilience4j.timelimiter...timeoutDuration` | `3s` | Maximum allowed execution time before timeout exception |
| `resilience4j.retry...maxAttempts` | `3` | Maximum attempts for failed requests |
| `resilience4j.retry...waitDuration` | `2s` | Delay between consecutive retry attempts |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Exposes Actuator web endpoints over HTTP |
| `management.endpoint.health.show-details` | `always` | Always displays full component details in `/actuator/health` |
| `management.health.circuitbreakers.enabled` | `true` | Enables Resilience4j health indicator in `/actuator/health` |

---

## Inventory Service

**File:** `inventory-service/src/main/resources/application.properties`

```properties
spring.application.name=inventory-service
spring.datasource.url=jdbc:mysql://localhost:3306/inventory_service?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=Akash@0786
spring.jpa.hibernate.ddl-auto=create-drop
server.port=0
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.instance-id=${spring.application.name}:${random.value}
eureka.instance.prefer-ip-address=true
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/ecommerce
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs
```

| Property | Value | Explanation |
|---|---|---|
| `spring.application.name` | `inventory-service` | Eureka name (used by `lb://`) |
| `spring.datasource.url` | MySQL JDBC URL | Connects to `inventory_service` database |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | **Drops and recreates schema each restart** |
| `server.port` | `0` | Random port — supports multiple instances |
| `eureka.instance.instance-id` | `inventory-service:{random}` | Unique ID per instance for Eureka |
| `eureka.instance.prefer-ip-address` | `true` | Register by IP, not hostname |

---

## `ddl-auto` Values Comparison

| Value | Behavior | Use Case |
|---|---|---|
| `create` | Creates schema at startup, doesn't drop on shutdown | Fresh dev setup |
| `create-drop` | Creates on startup, **drops on shutdown** | Testing, dev |
| `update` | Adds missing columns/tables, never drops | Dev/staging |
| `validate` | Validates schema matches entities, no changes | Production |
| `none` | No schema action | Production with Flyway/Liquibase |

> **Production Best Practice:** Use `none` with a proper migration tool like Flyway or Liquibase.

---

## Keycloak Configuration Reference

All services share the same Keycloak configuration pattern:

```properties
# Tells Spring where Keycloak is — used to:
# 1. Validate the 'iss' claim in JWTs
# 2. Auto-discover other endpoints (JWKS, token, etc.)
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/ecommerce

# Explicitly points to the public key endpoint
# Spring uses this to verify JWT signatures
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs
```

**Format of these URLs:**
- `http://{keycloak-host}:{port}/realms/{realm-name}` — Issuer URI
- `http://{keycloak-host}:{port}/realms/{realm-name}/protocol/openid-connect/certs` — JWKS URI

---

## Logging Configuration (Gateway)

```yaml
logging:
  level:
    root: info
    org.springframework.cloud.gateway: TRACE    # Very verbose gateway logs
    route:
      RouteDefinitionLocator: INFO              # Route loading logs
```

During development, `TRACE` on the gateway gives visibility into:
- Which route matched a request
- How URIs were resolved
- Filter execution order
- Load balancer selections

---

## Port Summary

| Service | Port | Type |
|---|---|---|
| Discovery Server | 8761 | Fixed |
| API Gateway | 8080 | Fixed |
| Order Service | 8081 | Fixed |
| Product Service | Random | Dynamic (registered with Eureka) |
| Inventory Service | Random | Dynamic (registered with Eureka) |
| Keycloak | 8181 | Fixed (external) |
| MySQL | 3306 | Fixed (external) |
| MongoDB | 27017 | Fixed (external) |

---

## Security: Move Credentials to Environment Variables (Production)

**Never commit passwords to version control.** Replace:
```properties
spring.datasource.password=Akash@0786
```

With environment variable references:
```properties
spring.datasource.password=${DB_PASSWORD}
```

Then set the environment variable before running:
```bash
export DB_PASSWORD=your_secure_password
```

Or use Spring Cloud Config / Vault / AWS Secrets Manager in production.
