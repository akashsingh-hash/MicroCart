# 03 — API Gateway

## What is it?

The **API Gateway** is the single entry point for all client requests into the MicroCart system. Built using **Spring Cloud Gateway** (reactive, non-blocking), it:

1. **Routes** incoming HTTP requests to the correct downstream microservice
2. **Authenticates** requests by validating JWT tokens from Keycloak
3. **Load balances** across multiple instances of each service using `lb://` URIs
4. **Discovers** service instances via Eureka

Think of it as the **front door** of the entire system — no client talks directly to product-service or order-service.

---

## Location in Project

```
api-gateway/
├── pom.xml
└── src/main/
    ├── java/org/example/apigateway/
    │   ├── ApiGatewayApplication.java
    │   └── config/
    │       └── SecurityConfig.java
    └── resources/
        └── application.yml
```

---

## Full Configuration — `application.yml`

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

---

## Routing Rules Explained

Spring Cloud Gateway uses **route definitions** — each route has an `id`, a `uri`, and `predicates` (conditions).

### Route 1: Order Service
```yaml
- id: order-service
  uri: lb://order-service
  predicates:
    - Path=/api/order/**
```
- Any request matching `/api/order/**` is forwarded to `order-service`
- `lb://` means **load-balanced** — Eureka resolves the actual host:port dynamically

### Route 2: Inventory Service
```yaml
- id: inventory-service
  uri: lb://inventory-service
  predicates:
    - Path=/api/inventory/**
```
- Any request matching `/api/inventory/**` is forwarded to `inventory-service`

### Route 3: Product Service
```yaml
- id: product-service
  uri: lb://product-service
  predicates:
    - Path=/api/products/**
```
- Any request matching `/api/products/**` is forwarded to `product-service`

### Route 4: Eureka Dashboard (Special)
```yaml
- id: discovery-server
  uri: http://localhost:8761
  predicates:
    - Path=/eureka/**
  filters:
    - RewritePath=/eureka/(?<segment>.*), /${segment}
```
- Allows accessing the Eureka dashboard through the gateway
- `RewritePath` filter strips the `/eureka` prefix before forwarding
- Uses a direct HTTP URI (not `lb://`) since Eureka is one fixed instance

---

## Security Configuration

```java
@Configuration
@EnableWebFluxSecurity          // WebFlux because gateway is reactive
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/eureka/**").permitAll()   // Eureka dashboard - no auth needed
                        .anyExchange().authenticated()            // Everything else - must have valid JWT
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})                          // Validate JWT tokens
                );
        return serverHttpSecurity.build();
    }
}
```

### Key Points
- **`@EnableWebFluxSecurity`** — Used instead of `@EnableWebSecurity` because Spring Cloud Gateway is **reactive** (built on Project Reactor / Netty), not servlet-based
- **CSRF disabled** — REST APIs using stateless JWT tokens don't need CSRF protection
- **Eureka permitAll** — The Eureka dashboard `/eureka/**` is publicly accessible through the gateway
- **JWT Validation** — The gateway validates every token against Keycloak's public keys before forwarding

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-cloud-starter-gateway` | The reactive API Gateway |
| `spring-cloud-starter-netflix-eureka-client` | Register with Eureka + use `lb://` |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |

---

## How Load Balancing Works

When the gateway resolves `lb://inventory-service`:
1. It queries the Eureka registry for all instances of `inventory-service`
2. Uses **Round-Robin** load balancing to pick one instance
3. Forwards the request to that specific instance's IP:port

This means if you run 3 instances of inventory-service, traffic is evenly distributed.

---

## Why Spring Cloud Gateway Instead of Zuul?

| Feature | Spring Cloud Gateway | Netflix Zuul 1.x |
|---|---|---|
| Architecture | Reactive (Netty, WebFlux) | Blocking (Servlet) |
| Performance | Higher throughput | Lower |
| Long-lived connections | WebSocket support | Limited |
| Spring Boot 3 support | Yes | Deprecated |

Spring Cloud Gateway is the recommended choice for Spring Boot 3+ projects.

---

## Request Flow Through Gateway

```
Client Request: POST http://localhost:8080/api/order
         │
         ▼
[1] Gateway receives request
         │
         ▼
[2] Security filter checks JWT token in Authorization header
    → Calls Keycloak JWKS endpoint to get public keys (cached)
    → Validates JWT signature, expiry, issuer
         │
         ▼
[3] Routing predicate matches: /api/order/** → order-service
         │
         ▼
[4] Load balancer resolves lb://order-service
    → Queries Eureka → gets [192.168.1.1:8081]
         │
         ▼
[5] Request forwarded to http://192.168.1.1:8081/api/order
         │
         ▼
[6] Order service processes and responds
         │
         ▼
[7] Response returned to client
```
