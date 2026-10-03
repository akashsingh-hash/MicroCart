# 05 — Order Service

## What is it?

The **Order Service** is the most complex service in MicroCart. It handles:
- **Placing orders** — accepting order requests from clients
- **Stock validation** — synchronously calling inventory-service to check availability
- **Order persistence** — saving confirmed orders to MySQL
- **Role-based access control** — only `USER` role can place orders, only `ADMIN` can view all orders

This service demonstrates **synchronous inter-service communication** using Spring WebClient.

---

## Location in Project

```
order-service/
├── pom.xml
└── src/main/
    ├── java/com/akash/order_service/
    │   ├── OrderServiceApplication.java
    │   ├── controller/
    │   │   └── OrderController.java
    │   ├── service/
    │   │   └── OrderService.java
    │   ├── model/
    │   │   ├── Order.java
    │   │   └── OrderLineItems.java
    │   ├── dto/
    │   │   ├── OrderRequest.java
    │   │   ├── OrderLineItemsDto.java
    │   │   └── InventoryResponse.java
    │   ├── repository/
    │   │   └── OrderRepository.java
    │   └── config/
    │       ├── SecurityConfig.java
    │       ├── WebClientConfig.java
    │       └── KeycloakJwtAuthenticationConverter.java
    └── resources/
        └── application.properties
```

---

## Configuration — `application.properties`

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

# Resilience4j timeout properties
resilience4j.timelimiter.instances.inventoryService.timeoutDuration=3s

# Retry for inventory service
resilience4j.retry.instances.inventoryService.maxAttempts=3
resilience4j.retry.instances.inventoryService.waitDuration=2s

# Expose actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
management.health.circuitbreakers.enabled=true

# Tracing (Micrometer & Zipkin for Spring Boot 3)
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

**`ddl-auto=update`** — Hibernate will update the schema on startup (add columns if missing), but won't drop and recreate tables.

---

## Data Models

### `Order.java`

```java
@Entity
@Table(name = "t_orders")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String orderNumber;           // UUID string (e.g., "3fa85f64-5717-...")

    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderLineItems> orderLineItemsList;
}
```

### `OrderLineItems.java`

```java
@Entity
@Table(name = "t_order_line_items")
@Data @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderLineItems {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String skuCode;          // Stock Keeping Unit code (e.g., "iphone-15-pro")
    private BigDecimal price;        // Price at time of order
    private Integer quantity;        // How many units
}
```

### Database Tables

**`t_orders`**
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (AUTO) | Primary key |
| `order_number` | VARCHAR | UUID-based unique order reference |

**`t_order_line_items`**
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT | Primary key |
| `sku_code` | VARCHAR | Product SKU |
| `price` | DECIMAL | Unit price |
| `quantity` | INT | Quantity ordered |
| `order_id` (FK) | BIGINT | Links to `t_orders` |

---

## DTOs

### `OrderRequest.java` — Client sends this

```java
public class OrderRequest {
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}
```

### `OrderLineItemsDto.java` — Inside OrderRequest

```java
public class OrderLineItemsDto {
    private Long id;
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
}
```

### `InventoryResponse.java` — Returned by inventory-service call

```java
public class InventoryResponse {
    private String skuCode;
    private Boolean isInStock;
}
```

---

## REST Controller

```java
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")        // Only USER role can place orders
    public ResponseEntity<CompletableFuture<String>> placeOrder(@RequestBody OrderRequest orderRequest)
            throws IllegalAccessException {
        CompletableFuture<String> message = orderService.placeOrder(orderRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")       // Only ADMIN role can view all orders
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.status(HttpStatus.OK).body(orders);
    }
}
```

### API Endpoints

| Method | URL | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/order` | `USER` | Place a new order |
| `GET` | `/api/order` | `ADMIN` | Get all placed orders |

---

## Service Layer — The Core Logic

```java
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    @TimeLimiter(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")
    @Retry(name = "inventoryService")
    public CompletableFuture<String> placeOrder(OrderRequest orderRequest) throws IllegalAccessException {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList().stream().map(this::mapToDto).toList();
        order.setOrderLineItemsList(orderLineItems);

        // Get all sku Code
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // Call Inventory Service
        InventoryResponse[] result = webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        Boolean allProductsInStock = Arrays.stream(result).allMatch(InventoryResponse::getIsInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
            return CompletableFuture.supplyAsync(() -> "Order Placed Successfully");
        } else {
            throw new IllegalAccessException("Product not in stock, please try again.");
        }
    }

    // Fallback must match placeOrder signature + Throwable
    public CompletableFuture<String> placeOrderFallback(OrderRequest orderRequest, Throwable t) {
        return CompletableFuture.supplyAsync(() -> "Inventory service is down, fallback triggered " + t.getMessage());
        // You can decide what to do here: reject order, log, or save with status "PENDING"
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public OrderLineItems mapToDto(OrderLineItemsDto orderLineItems) {
        return OrderLineItems.builder()
                .id(orderLineItems.getId())
                .skuCode(orderLineItems.getSkuCode())
                .price(orderLineItems.getPrice())
                .quantity(orderLineItems.getQuantity())
                .build();
    }
}
```

### Order Placement Flow

```
Client POST /api/order
    │
    ▼
[1] Validate JWT (gateway + service-level)
    │
    ▼
[2] Check @PreAuthorize("hasRole('USER')")
    │
    ▼
[3] Enter placeOrder() protected by:
    - @CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")
    - @Retry(name = "inventoryService")
    - @TimeLimiter(name = "inventoryService")
    │
    ▼
[4] Call inventory-service via WebClient (lb://):
    GET http://inventory-service/api/inventory?skuCode=sku1&skuCode=sku2
    │
    ├── NORMAL EXECUTION (inventory-service is UP):
    │     │
    │     ├── All in stock (isInStock == true)
    │     │     └── Save order to MySQL → Return 201 "Order Placed Successfully"
    │     │
    │     └── Out of stock
    │           └── Throws IllegalAccessException ("Product not in stock, please try again.")
    │
    └── DOWN / TIMEOUT / FAILURE:
          │
          ├── @Retry attempts up to 3 times (wait 2s between attempts)
          ├── If all retries fail or timeout exceeds 3s:
          │     └── Circuit Breaker records failure
          └── Resilience4j invokes placeOrderFallback(orderRequest, t)
                └── Returns: "Inventory service is down, fallback triggered <exception>"
```

---

## Configuration Classes

### `WebClientConfig.java`

```java
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced              // Enables lb:// URI resolution via Eureka
    public WebClient.Builder webClient() {
        return WebClient.builder()
                .filter(new ServletBearerExchangeFilterFunction());
                // ↑ Automatically forwards the incoming Bearer JWT token
                //   to downstream service calls (order → inventory)
    }
}
```

**Why `@LoadBalanced`?**
Without it, `http://inventory-service/...` would fail because `inventory-service` is not a real hostname. `@LoadBalanced` tells Spring Cloud to intercept the call and resolve it via Eureka.

**Why `ServletBearerExchangeFilterFunction`?**
When order-service receives a request with a JWT token, it needs to pass that same token to inventory-service. This filter automatically reads the token from the current request's `SecurityContext` and adds it as `Authorization: Bearer <token>` to the outgoing WebClient request.

### `SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
                        // ↑ Uses custom converter to extract Keycloak roles
                );
        return http.build();
    }
}
```

---

## Resilience4j Fault Tolerance & Circuit Breaker

### What is Resilience4j and Why It's Used

**Resilience4j** is a lightweight, fault-tolerance library designed for Java 17/21 and Spring Boot 3. In a microservices architecture, network latency, transient glitches, or downstream failures (such as `inventory-service` crashing or hanging) can quickly cause thread pools to exhaust and lead to cascading failures across the entire system.

Resilience4j provides:
- **Fault Tolerance:** Isolates failures in downstream calls so order-service continues running smoothly.
- **Circuit Breakers:** Cuts off doomed calls to an unavailable service, avoiding useless network waits.
- **Retries:** Automatically retries transient failures (e.g., temporary network blip) before giving up.
- **Time Limiters:** Places a hard upper bound on asynchronous task execution time.
- **Graceful Fallbacks:** Provides meaningful degradation messages to the client instead of unhandled 500 crashes.

### Key Annotations in OrderService

Order Service protects its `placeOrder` method with three coordinating Resilience4j annotations:

```java
@TimeLimiter(name = "inventoryService")
@CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")
@Retry(name = "inventoryService")
public CompletableFuture<String> placeOrder(OrderRequest orderRequest) throws IllegalAccessException { ... }
```

- **`@TimeLimiter(name = "inventoryService")`** — Cancels call execution if it exceeds 3 seconds (`timeoutDuration=3s`).
- **`@CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")`** — Tracks failure rate. When failure rate exceeds 50% over a 10-call sliding window, the circuit trips to `OPEN` and immediately invokes `placeOrderFallback`.
- **`@Retry(name = "inventoryService")`** — Re-attempts the inventory call up to 3 times with a 2-second delay between attempts before marking the call as failed.
- **`CompletableFuture<String>`** — Asynchronous wrapper required by `@TimeLimiter` and non-blocking circuit breaker execution.

### Circuit Breaker States & Lifecycle

```
       ┌────────────────────────────────────────────────────────┐
       │                                                        │
       ▼                                                        │
   ┌─────────┐   Failure Rate >= 50%   ┌─────────┐              │
   │ CLOSED  │ ──────────────────────► │  OPEN   │              │ Success Rate
   │ (Normal)│                         │ (Tripped)              │ Satisfied
   └─────────┘                         └────┬────┘              │
       ▲                                    │                   │
       │                                    │ Wait 10 seconds   │
       │                                    ▼                   │
       │                               ┌───────────┐            │
       └────────────────────────────── │ HALF-OPEN │ ───────────┘
                                       │ (3 trials)│
                                       └───────────┘
                                             │
                                             │ Any trial fails
                                             ▼
                                        (Back to OPEN)
```

- **CLOSED (Normal):** Requests flow through to `inventory-service`. Failure rate is tracked in a sliding window.
- **OPEN (Tripped):** When failure rate reaches 50%, all calls are blocked from reaching `inventory-service` and route instantly to `placeOrderFallback()`.
- **HALF-OPEN (Trial):** After waiting 10s (`waitDurationInOpenState`), the breaker lets 3 trial calls through (`permittedNumberOfCallsInHalfOpenState`). If they succeed, it closes; if they fail, it reopens for another 10s.

### Fallback Method & Behavior

When `inventory-service` is unreachable, timed out, or returning errors, Resilience4j redirects the call to `placeOrderFallback`:

```java
// Fallback must match placeOrder signature + Throwable parameter
public CompletableFuture<String> placeOrderFallback(OrderRequest orderRequest, Throwable t) {
    return CompletableFuture.supplyAsync(() -> "Inventory service is down, fallback triggered " + t.getMessage());
}
```

- **Contract Rule:** The fallback method must have the exact same return type (`CompletableFuture<String>`) and arguments (`OrderRequest`) as the protected method, plus an additional `Throwable` parameter at the end.
- **Graceful Degradation:** The client receives an informative response instead of a raw HTTP 500 error or a timed-out connection, allowing clients to notify users or retry later.

### Resilience4j Configuration Properties

Configured in `order-service/src/main/resources/application.properties`:

| Property | Value | Explanation |
|---|---|---|
| `resilience4j.circuitbreaker.instances.inventoryService.registerHealthIndicator` | `true` | Exposes circuit breaker health inside Spring Boot Actuator |
| `resilience4j.circuitbreaker.instances.inventoryService.eventConsumerBufferSize` | `10` | Size of internal buffer for circuit breaker lifecycle events |
| `resilience4j.circuitbreaker.instances.inventoryService.slidingWindowType` | `COUNT_BASED` | Calculates failure rate based on the last N calls (vs TIME_BASED) |
| `resilience4j.circuitbreaker.instances.inventoryService.slidingWindowSize` | `10` | Monitors the last 10 requests to evaluate error threshold |
| `resilience4j.circuitbreaker.instances.inventoryService.failureRateThreshold` | `50` | Circuit opens if 50% (5 out of 10) calls fail |
| `resilience4j.circuitbreaker.instances.inventoryService.waitDurationInOpenState` | `10s` | Time breaker stays in OPEN state before transitioning to HALF-OPEN |
| `resilience4j.circuitbreaker.instances.inventoryService.permittedNumberOfCallsInHalfOpenState` | `3` | Number of test requests allowed when in HALF-OPEN state |
| `resilience4j.timelimiter.instances.inventoryService.timeoutDuration` | `3s` | Max allowed execution time before throwing a timeout |
| `resilience4j.retry.instances.inventoryService.maxAttempts` | `3` | Maximum retry attempts for failed requests |
| `resilience4j.retry.instances.inventoryService.waitDuration` | `2s` | Wait interval between consecutive retry attempts |

---

## Spring Boot Actuator Integration

### What is Actuator and Why It's Used

**Spring Boot Actuator** provides production-ready monitoring, metrics gathering, and management endpoints for the application. In `order-service`, Actuator is used to:
- Monitor application health and uptime.
- Expose circuit breaker state (`CLOSED`, `OPEN`, `HALF_OPEN`) directly over HTTP.
- Track call metrics (successful calls, failed calls, throttled calls, retries).

### Configuration Properties

```properties
# Expose actuator endpoints over HTTP
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
management.health.circuitbreakers.enabled=true
```

| Property | Value | Explanation |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Exposes the `/actuator/health`, `/actuator/info`, and `/actuator/metrics` endpoints |
| `management.endpoint.health.show-details` | `always` | Shows granular component health (MySQL DB, Disk, Eureka, Circuit Breakers) |
| `management.health.circuitbreakers.enabled` | `true` | Enables Resilience4j health indicator integration in `/actuator/health` |

### Key Actuator Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `http://localhost:8081/actuator/health` | `GET` | Overall service health + individual circuit breaker state |
| `http://localhost:8081/actuator/info` | `GET` | General application build and metadata |
| `http://localhost:8081/actuator/metrics` | `GET` | List of all available metrics |
| `http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.calls` | `GET` | Circuit breaker call statistics (successful, failed, not_permitted) |
| `http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.state` | `GET` | Current state value of the circuit breaker |
| `http://localhost:8081/actuator/metrics/resilience4j.retry.calls` | `GET` | Counts of retry attempts and outcomes |

#### Example Health Output (`/actuator/health`):
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "inventoryService": {
          "status": "UP",
          "details": {
            "state": "CLOSED",
            "failureRate": "0.0%",
            "failureRateThreshold": "50.0%",
            "slowCallRate": "-1.0%",
            "slowCallRateThreshold": "100.0%",
            "bufferedCalls": 2,
            "failedCalls": 0,
            "slowCalls": 0,
            "notPermittedCalls": 0
          }
        }
      }
    },
    "db": { "status": "UP" },
    "discoveryComposite": { "status": "UP" }
  }
}
```

---

## How to Test the Integration

Follow these steps to verify Resilience4j Circuit Breaker, Retry, Fallback, and Actuator metrics in real time:

### Step 1: Normal Flow Verification
1. Ensure `discovery-server`, `inventory-service`, and `order-service` are running.
2. Send a valid order request via POST `http://localhost:8080/api/order` with a valid JWT token.
3. Observe `201 Created` with response `"Order Placed Successfully"`.
4. Inspect `http://localhost:8081/actuator/health` — verify `inventoryService` circuit breaker state is `"CLOSED"`.

### Step 2: Stop Inventory Service
1. Terminate or stop the `inventory-service` process.
2. Verify in Eureka dashboard (`http://localhost:8761`) that `INVENTORY-SERVICE` is no longer available.

### Step 3: Observe Fallback Response
1. Send the POST `/api/order` request again.
2. Notice the response takes ~4-6 seconds due to the configured `@Retry` (3 attempts with 2s wait).
3. The response is returned gracefully with HTTP status `201` containing the fallback message:
   ```
   Inventory service is down, fallback triggered Connection refused: no further information
   ```
4. The application does not crash or return an unhandled 500 error page.

### Step 4: Verify Circuit Breaker Trip in Actuator
1. Send 10 consecutive order requests while `inventory-service` is down to fill the sliding window (`slidingWindowSize=10`).
2. After 5 failed calls (50% threshold), the circuit trips to **`OPEN`**.
3. Subsequent requests now fail immediately into the fallback method without waiting for retry delays.
4. Check `http://localhost:8081/actuator/health`:
   ```json
   "inventoryService": {
     "status": "CIRCUIT_OPEN",
     "details": {
       "state": "OPEN",
       "failureRate": "100.0%"
     }
   }
   ```
5. Check `http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.calls` to observe `not_permitted` call increments.

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST endpoints (servlet-based) |
| `spring-boot-starter-webflux` | WebClient for calling inventory-service |
| `spring-boot-starter-data-jpa` | JPA/Hibernate for MySQL |
| `mysql-connector-j` | MySQL JDBC driver |
| `spring-cloud-starter-netflix-eureka-client` | Service registration + `lb://` |
| `spring-boot-starter-security` | Security filter chain |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `resilience4j-spring-boot3` | Resilience4j auto-configuration for Spring Boot 3 |
| `resilience4j-circuitbreaker` | Core Circuit Breaker implementation |
| `resilience4j-retry` | Retry mechanism for transient faults |
| `spring-boot-starter-actuator` | Production metrics, health endpoints, circuit breaker monitoring |
| `lombok` | Reduce boilerplate |

---

## Sample Request & Responses

### Sample Request

```http
POST http://localhost:8080/api/order
Authorization: Bearer <JWT_TOKEN_WITH_USER_ROLE>
Content-Type: application/json

{
  "orderLineItemsDtoList": [
    {
      "skuCode": "iphone-15-pro",
      "price": 134900.00,
      "quantity": 1
    },
    {
      "skuCode": "airpods-pro",
      "price": 24900.00,
      "quantity": 2
    }
  ]
}
```

### Responses

#### Success Response (`201 Created`):
```
Order Placed Successfully
```

#### Fallback Response (`201 Created` - Inventory Service Down):
```
Inventory service is down, fallback triggered Connection refused: no further information
```

#### Out of Stock Response (`500 Internal Server Error`):
```
Product not in stock, please try again.
```
