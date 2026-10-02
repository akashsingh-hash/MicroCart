# 08 — Inter-Service Communication

## Overview

In MicroCart, **Order Service** needs to communicate with **Inventory Service** to check stock before placing an order. This is **synchronous inter-service communication** — order-service waits for inventory-service's response before proceeding.

The technology used is **Spring WebClient** — Spring's reactive, non-blocking HTTP client (part of WebFlux).

---

## Communication Pattern: Synchronous REST

```
Order Service ──── HTTP GET ────► Inventory Service
              ◄─── Response ──────
```

This is a **synchronous, blocking** call in this implementation (`.block()` is used). The order-service thread waits until inventory-service responds.

### Why not Asynchronous/Messaging (Kafka/RabbitMQ)?

For stock validation before placing an order, synchronous communication makes sense:
- We need the answer **immediately** to decide whether to accept or reject the order
- Eventual consistency is not acceptable here (you can't place an order and check stock later)

---

## WebClient Configuration

### `WebClientConfig.java` (in order-service)

```java
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced              // KEY annotation — enables lb:// URI resolution
    public WebClient.Builder webClient() {
        return WebClient.builder()
                .filter(new ServletBearerExchangeFilterFunction());
    }
}
```

### `@LoadBalanced` — How It Works

Without `@LoadBalanced`:
```java
// This would fail — "inventory-service" is not a real hostname
webClient.get().uri("http://inventory-service/api/inventory")
```

With `@LoadBalanced`:
1. Spring Cloud intercepts the WebClient call
2. Detects the URI has a service name (`inventory-service`) instead of an IP
3. Queries Eureka for all instances of `inventory-service`
4. Selects one using Round-Robin load balancing
5. Rewrites the URI: `http://inventory-service/` → `http://192.168.1.5:54321/`

---

## Token Forwarding

### `ServletBearerExchangeFilterFunction`

```java
.filter(new ServletBearerExchangeFilterFunction())
```

This is a **WebClient filter** that automatically:
1. Reads the current HTTP request's `SecurityContext`
2. Extracts the Bearer JWT token
3. Adds `Authorization: Bearer <token>` to every outgoing WebClient request

**Why is this needed?**

When a user sends a request to order-service, the JWT is in the `Authorization` header. When order-service calls inventory-service, inventory-service also requires a valid JWT. Without token forwarding, inventory-service would reject the request as unauthorized.

```
Client ──[JWT Token]──► Order Service ──[JWT Token forwarded]──► Inventory Service
                              ↑                                          ↑
                        validates token                           validates same token
```

---

## The Actual WebClient Call

```java
// From OrderService.java
InventoryResponse[] result = webClientBuilder.build()
        .get()
        .uri("http://inventory-service/api/inventory",
                uriBuilder -> uriBuilder
                        .queryParam("skuCode", skuCodes)
                        .build())
        .retrieve()
        .bodyToMono(InventoryResponse[].class)
        .block();    // Blocking call — thread waits here
```

### Step-by-Step Breakdown

| Step | Code | What Happens |
|---|---|---|
| 1 | `webClientBuilder.build()` | Creates a configured WebClient instance |
| 2 | `.get()` | HTTP GET method |
| 3 | `.uri(...)` | Build URI with query params (e.g., `?skuCode=sku1&skuCode=sku2`) |
| 4 | `.retrieve()` | Execute the request and get the response |
| 5 | `.bodyToMono(InventoryResponse[].class)` | Deserialize JSON response body to array |
| 6 | `.block()` | **Block the current thread** and wait for the response |

### Generated URL

For skuCodes = ["iphone-15-pro", "airpods-pro"]:
```
GET http://inventory-service/api/inventory?skuCode=iphone-15-pro&skuCode=airpods-pro
```

After `@LoadBalanced` resolution:
```
GET http://192.168.1.5:54321/api/inventory?skuCode=iphone-15-pro&skuCode=airpods-pro
```

---

## Service Discovery Role in Communication

```
Order Service calls: http://inventory-service/api/inventory
                            │
                     @LoadBalanced intercepts
                            │
                     Queries Eureka:
                     "Who is 'inventory-service'?"
                            │
                     Eureka responds:
                     [Instance 1: 192.168.1.5:54321,
                      Instance 2: 192.168.1.5:54322]
                            │
                     Round-Robin picks: Instance 1
                            │
                     Actual call: http://192.168.1.5:54321/api/inventory
```

---

## Response Handling

```java
InventoryResponse[] result = ...; // Array from inventory service

Boolean allProductsInStock = Arrays.stream(result)
        .allMatch(InventoryResponse::getIsInStock);
```

`allMatch` — returns true only if **every single item** in the response has `isInStock = true`. If even one item is out of stock, the order is rejected.

---

## InventoryResponse DTO (Shared Concept)

Both services have this DTO (not shared as a library — defined separately in each):

**In inventory-service:**
```java
public class InventoryResponse {
    private String skuCode;
    private Boolean isInStock;
}
```

**In order-service:**
```java
public class InventoryResponse {
    private String skuCode;
    private Boolean isInStock;
}
```

This is intentional **duplication** — in microservices, sharing DTOs via a common library creates coupling. Each service defines its own copy of shared contracts. This is the "shared-nothing" principle.

---

## Fault Tolerance & Resilience (Resilience4j Integration)

In a microservice architecture, calling another service synchronously over HTTP introduces a point of failure. If `inventory-service` goes down, lags, or experiences transient network failures:
- Order Service threads could hang waiting for responses.
- The failure could cascade upstream to clients and the API Gateway.

To prevent this, **Resilience4j** is integrated into `OrderService` when communicating with `inventory-service`.

### Resilience Patterns Applied

| Pattern | Annotation / Component | Configured Behavior |
|---|---|---|
| **Circuit Breaker** | `@CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")` | Trips OPEN if failure rate >= 50% across 10 calls; waits 10s before testing with 3 calls in HALF-OPEN |
| **Retry** | `@Retry(name = "inventoryService")` | Automatically retries failed inventory requests up to 3 times, with a 2-second wait between attempts |
| **Time Limiter** | `@TimeLimiter(name = "inventoryService")` | Restricts inventory call execution to 3 seconds before timing out |
| **Fallback** | `placeOrderFallback(OrderRequest, Throwable)` | Gracefully returns a fallback message instead of throwing an unhandled exception |

### Code Implementation in `OrderService.java`

```java
@TimeLimiter(name = "inventoryService")
@CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")
@Retry(name = "inventoryService")
public CompletableFuture<String> placeOrder(OrderRequest orderRequest) throws IllegalAccessException {
    Order order = new Order();
    order.setOrderNumber(UUID.randomUUID().toString());
    List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList().stream().map(this::mapToDto).toList();
    order.setOrderLineItemsList(orderLineItems);

    List<String> skuCodes = order.getOrderLineItemsList().stream()
            .map(OrderLineItems::getSkuCode)
            .toList();

    // Call Inventory Service synchronously
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

// Fallback method triggered when inventory-service call fails
public CompletableFuture<String> placeOrderFallback(OrderRequest orderRequest, Throwable t) {
    return CompletableFuture.supplyAsync(() -> "Inventory service is down, fallback triggered " + t.getMessage());
}
```

### Fallback Behavior When Inventory Service Is Down

1. **Retry Phase:** Order Service retries the inventory call up to 3 times (with 2 seconds interval).
2. **Circuit Evaluation:** If calls fail or exceed the 3-second timeout, the failure is recorded by the Circuit Breaker.
3. **Fallback Invocation:** Resilience4j skips the normal flow and invokes `placeOrderFallback(orderRequest, t)`.
4. **Client Response:** The caller receives:
   ```
   Inventory service is down, fallback triggered Connection refused: no further information
   ```
   instead of an unhandled HTTP 500 error or thread exhaustion.
5. **Circuit Tripping:** If 5 out of 10 requests fail (50%), the circuit opens. Future requests trigger the fallback immediately without even attempting network calls to inventory-service.

---

## WebClient vs RestTemplate vs Feign

| Feature | RestTemplate | WebClient | OpenFeign |
|---|---|---|---|
| Style | Synchronous/Blocking | Reactive/Non-blocking | Declarative (interface) |
| Spring Boot 3 | Deprecated | Recommended | Available |
| `@LoadBalanced` | Yes | Yes | Yes (via `spring-cloud-openfeign`) |
| Token forwarding | Manual | Filter-based | Interceptor-based |
| Code style | Verbose | Fluent/Builder | Clean — just interface methods |
| Best for | Legacy | High throughput, reactive | Clean codebases, lots of calls |

MicroCart uses **WebClient** — the recommended choice for Spring Boot 3, even for blocking calls (when using `.block()`).

---

## Full Inter-Service Communication Sequence

```
Client
  │  POST /api/order (with JWT)
  ▼
API Gateway (port 8080)
  │  Validates JWT
  │  Routes to order-service via lb://
  ▼
Order Service (port 8081)
  │  SecurityConfig validates JWT again
  │  @PreAuthorize("hasRole('USER')") passes
  │  Extracts SKU codes from request
  │  Enters @CircuitBreaker, @Retry, @TimeLimiter protected placeOrder()
  │  
  │  WebClient call (with JWT forwarded):
  │  GET http://inventory-service/api/inventory?skuCode=...
  ▼
Eureka Registry (port 8761)
  │  Resolves "inventory-service" → actual IP:port
  │
  ├── [CASE A: Inventory Service is UP]
  │     ▼
  │   Inventory Service (port: random)
  │     │  SecurityConfig validates JWT
  │     │  Queries MySQL: SELECT * FROM t_inventory WHERE sku_code IN (...)
  │     │  Maps quantity > 0 to isInStock boolean
  │     │  Returns List<InventoryResponse>
  │     ▼
  │   Order Service (receives response)
  │     ├── All in stock → saves order to MySQL → Returns 201 "Order Placed Successfully"
  │     └── Out of stock → throws IllegalAccessException
  │
  └── [CASE B: Inventory Service is DOWN / Slow]
        ▼
      Order Service
        │  @Retry attempts 3 times (2s delay)
        │  If still failing / timed out (> 3s):
        │  Circuit breaker records failure (trips OPEN if >= 50% errors)
        │  Invokes placeOrderFallback(orderRequest, t)
        ▼
      API Gateway → Client
        └── 201 "Inventory service is down, fallback triggered <error>"
```
