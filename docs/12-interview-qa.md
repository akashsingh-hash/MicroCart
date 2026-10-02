# 12 — Interview Questions & Answers

Comprehensive Q&A covering everything an interviewer might ask about this project. Organized by topic.

---

## 🏗️ Architecture & Design

### Q1: What is Microservices Architecture? Why did you use it?

**Answer:**
Microservices architecture decomposes an application into small, independently deployable services where each service:
- Owns a specific business domain (product, order, inventory)
- Has its own database (database-per-service pattern)
- Communicates over well-defined APIs
- Can be scaled, deployed, and developed independently

In MicroCart, I used it because:
- Each domain has different scaling needs (inventory is checked far more often than products are created)
- Different data models suit different databases (MongoDB for flexible product catalog, MySQL for relational order/inventory data)
- Teams can work on each service independently without merge conflicts

---

### Q2: How many microservices are in your project? What does each do?

**Answer:**
5 services:
1. **Discovery Server** — Eureka registry; acts as the phonebook for all services
2. **API Gateway** — Single entry point; routes requests, validates JWTs, load-balances
3. **Product Service** — Manages product catalog (create/list products) via MongoDB
4. **Order Service** — Places orders; calls inventory-service to check stock; uses MySQL
5. **Inventory Service** — Checks stock availability by SKU code; uses MySQL

---

### Q3: What is the API Gateway pattern? Why is it needed?

**Answer:**
The API Gateway is a single entry point that sits in front of all microservices. Without it, clients would need to know the IP/port of every service, handle cross-cutting concerns (auth, logging, rate limiting) in every service, and manage CORS separately.

Benefits in MicroCart:
- **Single endpoint** for all clients (port 8080)
- **Centralized JWT validation** — services don't need to validate themselves... well, they do for defense in depth, but the gateway is the first filter
- **Load balancing** — distributes traffic across multiple instances using `lb://` URIs
- **Routing** — maps URL patterns to services (`/api/order/**` → order-service)

---

### Q4: Explain the Database-per-Service pattern. Why do different services use different databases?

**Answer:**
Each microservice has its own dedicated database, isolated from others. No two services share the same database or tables.

In MicroCart:
- **Product Service** uses **MongoDB** — product data is document-oriented, schema-flexible, and benefits from MongoDB's ease of horizontal scaling
- **Order Service** uses **MySQL** — orders have strict relational requirements (orders → line items), need ACID transactions
- **Inventory Service** uses **MySQL** — inventory requires strong consistency and transactional reads

**Why not share one database?**
- Tight coupling — one service's schema change breaks others
- Different services have different throughput needs
- Technology freedom — use the best tool for each domain
- Independent scaling — inventory can use a Redis cache later without affecting others

---

### Q5: What is Service Discovery? How does Eureka work?

**Answer:**
Service discovery allows services to find each other without hardcoded IPs. When services start, they register themselves with the Eureka server. When they want to call another service, they ask Eureka for that service's current address.

**Eureka workflow:**
1. Service starts → sends `POST /eureka/apps/{name}` to register
2. Registered instance sends heartbeats every 30s to renew
3. If no heartbeat for 90s → Eureka evicts the instance
4. Clients query Eureka for instances → get IP:port → make the call

**In MicroCart:**
- Product-service and inventory-service use `server.port=0` (random port)
- Eureka tracks the actual port
- Order-service calls `lb://inventory-service` → Spring Cloud resolves via Eureka

---

### Q6: What is the difference between Client-Side and Server-Side service discovery?

**Answer:**

| Aspect | Client-Side | Server-Side |
|---|---|---|
| Who queries registry | The client service | A dedicated load balancer |
| Example | Eureka + Ribbon/Spring Cloud LB | AWS ELB, Nginx |
| Used in MicroCart | **Yes** — `@LoadBalanced` WebClient | No |
| Advantage | No extra hop | Client is simpler |
| Disadvantage | Client must know Eureka | Extra infrastructure component |

MicroCart uses **client-side discovery**: order-service queries Eureka and directly connects to inventory-service.

---

## 🔐 Security

### Q7: What is OAuth 2.0? How is it used in this project?

**Answer:**
OAuth 2.0 is an authorization framework that allows a third-party application to obtain limited access to a service on behalf of a user without sharing credentials.

In MicroCart:
- **Keycloak** is the Authorization Server (issues tokens)
- **All microservices** are Resource Servers (validate tokens and serve protected resources)
- **Clients** (Postman/frontend) are the OAuth 2.0 Clients

The flow used is **Resource Owner Password Credentials (ROPC)** for simplicity in development:
```
Client → Keycloak (username+password) → Access Token (JWT)
Client → API Gateway (JWT) → Validated and forwarded to service
```

---

### Q8: What is a JWT? What information does it contain?

**Answer:**
JWT (JSON Web Token) is a compact, URL-safe token format. It has 3 parts separated by dots: `header.payload.signature`.

**Header:** Algorithm used (e.g., RS256)
**Payload (Claims):**
- `iss` — Issuer (Keycloak realm URL)
- `exp` — Expiry timestamp
- `sub` — Subject (user ID)
- `preferred_username` — Username
- `realm_access.roles` — User's roles (`["USER", "ADMIN"]`)

**Signature:** Signed with Keycloak's private key; verified using the public key (JWKS endpoint)

**Key feature:** JWTs are **stateless** — the server doesn't need to query a database to validate them. It just verifies the signature.

---

### Q9: Why did you create `KeycloakJwtAuthenticationConverter`? What problem does it solve?

**Answer:**
By default, Spring Security's `JwtGrantedAuthoritiesConverter` reads roles from the `scope` claim in JWTs. But Keycloak stores roles under `realm_access.roles`, not `scope`.

The custom converter:
1. Reads `realm_access.roles` from the JWT
2. Prefixes each with `ROLE_` (Spring Security convention): `USER` → `ROLE_USER`
3. Merges with default converter authorities
4. Sets `preferred_username` as the principal name

Without this, `@PreAuthorize("hasRole('USER')")` would never work because Spring Security wouldn't know about Keycloak's roles.

---

### Q10: What is the difference between Authentication and Authorization?

**Answer:**

| Concept | Definition | Example in MicroCart |
|---|---|---|
| Authentication | Verifying WHO you are | JWT signature validation, `iss`/`exp` check |
| Authorization | Verifying WHAT you can do | `@PreAuthorize("hasRole('USER')")` on placeOrder |

Authentication happens first (gateway + service both validate the token). Authorization happens after (only the order-service has role-based access control).

---

### Q11: What is CSRF? Why is it disabled in this project?

**Answer:**
CSRF (Cross-Site Request Forgery) is an attack where a malicious website tricks a logged-in user's browser into making unwanted requests to another site using session cookies.

**Why disabled here:**
- REST APIs use **stateless JWT tokens** in `Authorization` headers, not session cookies
- CSRF attacks rely on browser automatically sending session cookies
- Since we use `Authorization: Bearer <token>` (not cookies), CSRF attacks don't apply
- Disabling CSRF reduces complexity without any security risk in this setup

---

### Q12: Why does API Gateway use `@EnableWebFluxSecurity` while services use `@EnableWebSecurity`?

**Answer:**
- **API Gateway** is built on **Spring Cloud Gateway**, which is built on **Spring WebFlux** (reactive, non-blocking, runs on Netty)
- **Other services** use `spring-boot-starter-web` which is **Servlet-based** (blocking, runs on Tomcat)

WebFlux and Servlet are two different programming models:
- `@EnableWebFluxSecurity` → `ServerHttpSecurity` → reactive security
- `@EnableWebSecurity` → `HttpSecurity` → servlet security

Using the wrong one causes runtime errors (they're incompatible).

---

## 🔗 Inter-Service Communication

### Q13: How does Order Service call Inventory Service?

**Answer:**
Order Service uses **Spring WebClient** (reactive HTTP client) to call inventory-service:

```java
InventoryResponse[] result = webClientBuilder.build()
        .get()
        .uri("http://inventory-service/api/inventory",
                uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
        .retrieve()
        .bodyToMono(InventoryResponse[].class)
        .block();
```

Key aspects:
- `@LoadBalanced` on the WebClient.Builder enables Eureka-based service resolution
- `ServletBearerExchangeFilterFunction` automatically forwards the JWT token
- `.block()` makes it synchronous (waits for response)

---

### Q14: What is `@LoadBalanced`? Why is it needed?

**Answer:**
`@LoadBalanced` is a Spring Cloud annotation that tells Spring to inject a load-balancing interceptor into the annotated bean.

Without it: `http://inventory-service/...` fails — Java doesn't know how to resolve `inventory-service` as a hostname.

With it: Spring Cloud intercepts the HTTP call, queries Eureka for all instances of `inventory-service`, and uses Round-Robin load balancing to select one instance, then rewrites the URL to the actual `IP:port`.

---

### Q15: What is `ServletBearerExchangeFilterFunction`?

**Answer:**
It's a WebClient filter that automatically propagates the current request's Bearer JWT token to outgoing WebClient requests.

**Problem it solves:**
- Client sends JWT to order-service
- Order-service calls inventory-service
- Inventory-service also validates JWT
- Without token forwarding, inventory-service rejects the call (no token)

**How it works:**
- Reads the `SecurityContext` of the current servlet thread
- Extracts the JWT from the `Authentication` object
- Adds it as `Authorization: Bearer <token>` to the WebClient request

---

### Q16: What is the difference between RestTemplate, WebClient, and OpenFeign?

**Answer:**

| Aspect | RestTemplate | WebClient | OpenFeign |
|---|---|---|---|
| Style | Blocking/Synchronous | Reactive/Non-blocking | Declarative interface |
| Spring Boot 3 | Deprecated | Recommended | Available |
| Load balancing | `@LoadBalanced` | `@LoadBalanced` | Built-in |
| Token forwarding | Manual | Filter-based | Interceptor |
| Code complexity | Medium | Medium (fluent API) | Low (interface only) |

MicroCart uses **WebClient** — the modern, non-blocking choice for Spring Boot 3.

---

### Q17: Is the communication in MicroCart synchronous or asynchronous? What are the trade-offs?

**Answer:**
**Synchronous REST** (via WebClient).

**Advantages here:**
- Immediate feedback — order is accepted/rejected in the same request-response cycle
- Simpler code — no message queues, no consumer setup
- Consistent state — inventory is verified before order is confirmed

**Trade-offs & How We Solved Them:**
- **Temporal coupling:** If inventory-service goes down, order-service could be impacted.
- **Solution:** We integrated **Resilience4j** (`@CircuitBreaker`, `@Retry`, `@TimeLimiter`, and graceful `placeOrderFallback`). If inventory-service fails or times out, the circuit breaker isolates the failure, retries transient glitches, and falls back to a meaningful response without crashing order-service.

**Async alternative with Kafka (Event-Driven):**
- Order placed → event published to Kafka topic (`order-placed`)
- Inventory-service consumes event → reserves stock
- Order-service listens for confirmation event → confirms or cancels order
- More complex but resilient, decoupled, and handles high traffic bursts

---

## 💾 Data & Persistence

### Q18: Why does product-service use MongoDB while order/inventory use MySQL?

**Answer:**
**Product Service → MongoDB:**
- Product data is document-oriented (varying attributes per product type)
- Schema flexibility — electronics, clothing, food have different fields
- Read-heavy (most users browse products; few create them)
- MongoDB's aggregation pipeline is great for catalog searches

**Order/Inventory Services → MySQL:**
- Order data is inherently relational (order → line items)
- Requires ACID transactions (critical for financial data)
- Strong consistency needed — can't have an order saved without its line items
- Inventory needs atomic updates (decrement quantity only if > 0)

---

### Q19: What is `@Transactional(readOnly = true)` in InventoryService? Why use it?

**Answer:**
Marks a method as a read-only transaction. Benefits:
1. **JPA optimization** — Hibernate skips "dirty checking" (no need to track entity changes)
2. **Flush mode** — Set to NEVER, preventing accidental writes
3. **Database optimization** — JDBC drivers can route read-only transactions to read replicas
4. **Performance** — Slightly faster due to reduced overhead

Used in `isInStock()` because it only reads from the database — never writes.

---

### Q20: What is `GenerationType.IDENTITY` vs `GenerationType.AUTO`?

**Answer:**

| Strategy | Behavior | When to Use |
|---|---|---|
| `IDENTITY` | Database auto-increment | MySQL, PostgreSQL with sequences |
| `AUTO` | Hibernate picks best strategy | Generic, but may use sequences (creates extra table in MySQL) |
| `SEQUENCE` | Uses DB sequence object | Oracle, PostgreSQL |
| `TABLE` | Uses a separate key table | Portable but slow |

MicroCart uses:
- `IDENTITY` in `OrderLineItems` and `Inventory` — clear, database-native auto-increment
- `AUTO` in `Order` — may create a `hibernate_sequence` table in MySQL

---

### Q21: What is the difference between `@OneToMany` cascade options?

**Answer:**
In `Order.java`: `@OneToMany(cascade = CascadeType.ALL)`

`CascadeType.ALL` means any JPA operation on Order is cascaded to OrderLineItems:
- **PERSIST** — Saving an Order also saves its line items
- **MERGE** — Merging an Order also merges its line items
- **REMOVE** — Deleting an Order also deletes its line items
- **REFRESH** — Refreshing an Order also refreshes its line items
- **DETACH** — Detaching an Order also detaches its line items

Without cascade, you'd have to manually save each line item separately.

---

## ☁️ Spring Cloud & Patterns

### Q22: What is Spring Cloud? Name its components used in this project.

**Answer:**
Spring Cloud provides tools for building distributed systems (microservices). Components used in MicroCart:

| Component | Library | Purpose |
|---|---|---|
| Service Registry | `spring-cloud-starter-netflix-eureka-server/client` | Service registration and discovery |
| API Gateway | `spring-cloud-starter-gateway` | Routing, filtering, load balancing |
| Load Balancer | Built into Eureka client | `lb://` URI resolution with Round-Robin |

**Not used but commonly asked about:**
- Spring Cloud Config — centralized configuration
- Spring Cloud Circuit Breaker — fault tolerance (Resilience4j)
- Spring Cloud Sleuth — distributed tracing
- Spring Cloud Stream — messaging (Kafka/RabbitMQ)

---

### Q23: What is the difference between the `api-server` name (in gateway config) and `api-gateway`?

**Answer:**
In `api-gateway/application.yml`:
```yaml
spring:
  application:
    name: api-server   # ← This is what registers in Eureka
```

The Eureka registration name is `api-server`, but the folder is called `api-gateway`. This is just a naming choice. The important thing is that other services don't call the gateway by name — only the external client calls the gateway directly. The gateway name in Eureka is irrelevant for routing purposes (no one uses `lb://api-server`).

---

### Q24: What happens if the Discovery Server goes down?

**Answer:**
- **Existing clients** have a local cache of the registry — they continue to route for some time (default cache: 30 seconds)
- **New service instances** can't register
- **Services that restart** can't register → won't receive traffic
- The system **degrades gracefully** for existing registered instances until the cache expires

**This is Eureka's AP behavior** (from CAP theorem): it prioritizes Availability and Partition Tolerance over Consistency. Even if Eureka is down, it doesn't immediately deregister services.

**Production solution:** Run Eureka in a cluster (multiple nodes) for high availability.

---

### Q25: Why does inventory-service use `server.port=0`? What is the benefit?

**Answer:**
`server.port=0` tells Spring Boot to pick a random available port at startup.

Benefits:
1. **Multiple instances** — Can run 3 instances on the same machine without port conflicts
2. **Easy horizontal scaling** — Just start another instance; Eureka registers it automatically
3. **Load balancing works** — Gateway and order-service use `lb://inventory-service`, distributing traffic across all registered instances

Without `port=0`, starting a second instance on the same machine would fail with "Address already in use".

---

## 🔬 Code-Level Questions

### Q26: What are Lombok annotations? What does each one do?

**Answer:**

| Annotation | What it Generates |
|---|---|
| `@Data` | `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor` |
| `@Getter` | Getter methods for all fields |
| `@Setter` | Setter methods for all fields |
| `@Builder` | Builder pattern (`Product.builder().name("x").build()`) |
| `@NoArgsConstructor` | Constructor with no arguments |
| `@AllArgsConstructor` | Constructor with all fields as arguments |
| `@RequiredArgsConstructor` | Constructor for `final` fields (used for dependency injection) |
| `@Slf4j` | `private static final Logger log = LoggerFactory.getLogger(...)` |

---

### Q27: Why is `@RequiredArgsConstructor` used instead of `@Autowired`?

**Answer:**
Both achieve dependency injection, but `@RequiredArgsConstructor` is preferred because:
1. **Immutability** — Final fields can only be set through the constructor (can't be accidentally changed)
2. **Testability** — Easier to inject mock dependencies in tests (just call the constructor)
3. **Mandatory dependencies** — If a required dependency is missing, the application fails to start immediately (fail-fast)
4. **Spring recommendation** — Constructor injection is the Spring team's recommended approach

`@Autowired` on fields uses reflection, which is slower and hides dependencies.

---

### Q28: Explain `@PreAuthorize("hasRole('USER')")`. How does it work?

**Answer:**
`@PreAuthorize` is a Spring Security annotation for method-level security. The expression `hasRole('USER')` checks if the authenticated user's `GrantedAuthority` list contains `ROLE_USER`.

**Flow:**
1. Request arrives at `OrderController.placeOrder()`
2. Spring AOP intercepts (because `@EnableMethodSecurity` is set)
3. Checks the `SecurityContext` for `Authentication` object
4. Calls `hasRole('USER')` → internally checks for `ROLE_USER` in granted authorities
5. If present → method executes
6. If not → throws `AccessDeniedException` → Spring Security returns 403

**Why `ROLE_` prefix?** Spring Security convention. `hasRole('USER')` checks for `ROLE_USER`. Use `hasAuthority('ROLE_USER')` for the full name.

---

### Q29: What is `UUID.randomUUID().toString()` used for?

**Answer:**
In `OrderService.placeOrder()`:
```java
order.setOrderNumber(UUID.randomUUID().toString());
```

UUID (Universally Unique Identifier) generates a random 128-bit value formatted as:
`550e8400-e29b-41d4-a716-446655440000`

Used as `orderNumber` instead of the database ID because:
- **Security** — Database IDs are sequential; exposing them reveals how many orders exist
- **Decoupling** — Clients can reference orders by UUID without knowing the internal DB schema
- **Distributed systems** — UUIDs can be generated by any service without coordination (no ID conflicts across services)

---

### Q30: What is `findBySkuCodeIn(List<String> skuCode)` in InventoryRepository? How is the query generated?

**Answer:**
This is a **Spring Data JPA derived query method**. Spring parses the method name and generates SQL automatically:

`findBy` + `SkuCode` + `In` → 
```sql
SELECT * FROM t_inventory WHERE sku_code IN (?, ?, ?)
```

This allows checking multiple SKU codes in **one database round trip** instead of N separate queries (N+1 problem avoidance). For an order with 5 items, only 1 SQL query is executed.

---

### Q31: What does `.block()` do in WebClient? Is it a problem?

**Answer:**
`.block()` subscribes to a Mono (reactive stream) and blocks the current thread until the response is received — converting reactive/async to synchronous/blocking.

**In MicroCart:** Order-service uses `spring-boot-starter-web` (Servlet-based, blocking), so using `.block()` is acceptable and doesn't cause issues.

**Problem with `.block()` in WebFlux context:** If you use `.block()` inside a reactive pipeline (e.g., in a `@RestController` that returns `Mono<T>`), it blocks the event loop thread, completely defeating the purpose of reactive programming and potentially causing deadlocks.

**Better approach in production:** Use fully reactive WebClient with `flatMap` instead of `block()`:
```java
return webClient.get().uri(...)
        .retrieve()
        .bodyToMono(InventoryResponse[].class)
        .flatMap(result -> {
            // process result reactively
        });
```

---

## 🧪 Testing

### Q32: What is Testcontainers? How is it used?

**Answer:**
Testcontainers is a Java library that provides lightweight, disposable Docker containers for testing. It spins up real databases/services during tests and tears them down afterward.

In product-service:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <scope>test</scope>
</dependency>
```

Instead of mocking MongoDB, you get a **real MongoDB instance** in a Docker container during tests:
- Tests are more realistic (no mock behavior differences)
- No test database setup needed
- Automatically cleaned up after tests

---

## 🚀 Production & Best Practices

### Q33: What improvements would you make to this project for production?

**Answer:**

1. **Event-Driven Architecture (Kafka/RabbitMQ)** — Decouple order placement and inventory reservation using asynchronous message brokers
2. **Distributed Tracing & Observability** — Micrometer Tracing with Zipkin or Jaeger to track requests across gateway, order, and inventory services
3. **Secrets Management** — Move database credentials and Keycloak secrets out of `application.properties` into environment variables or HashiCorp Vault
4. **Database Migrations** — Use Flyway or Liquibase with `ddl-auto=validate` instead of Hibernate auto-ddl
5. **Production HTTPS / TLS** — Enable SSL/TLS encryption on the gateway and inter-service channels
6. **Containerization & Orchestration** — Containerize with Docker and deploy to Kubernetes with autoscaling and health probes
7. **Gateway Rate Limiting** — Add Redis-backed request rate limiting to prevent API abuse
8. **API Versioning** — Explicit API path versioning (e.g., `/api/v1/orders`)

*(Note: Circuit Breaker fault tolerance via Resilience4j and production health/metrics monitoring via Spring Boot Actuator have already been implemented.)*

---

### Q34: What is the difference between `ddl-auto=update` and `ddl-auto=create-drop`?

**Answer:**

| Value | Behavior | Used In |
|---|---|---|
| `update` | Adds new columns/tables; never drops existing ones | order-service |
| `create-drop` | Creates on startup, drops everything on shutdown | inventory-service |

Order-service uses `update` because orders are persistent business data — you never want to lose them on restart.

Inventory-service uses `create-drop` for development convenience — stock data is seeded fresh each run (and `create-drop` is actually dangerous in production for this reason).

---

### Q35: What is the Maven parent POM? What does it manage?

**Answer:**
The root `pom.xml` is a **parent POM** with `<packaging>pom</packaging>`. It:
1. **Declares modules** — Lists all 5 child services
2. **Manages versions** — `spring-cloud.version`, `lombok.version`, Java version
3. **Imports BOMs** — `spring-cloud-dependencies` and `testcontainers-bom` (Bill of Materials) manage consistent versions
4. **Plugin management** — Configures Spring Boot Maven plugin and Lombok annotation processor for all children

Child services inherit from this parent and don't need to specify versions for managed dependencies — they just declare the dependency without a version.

**Benefits:**
- Consistent versions across all services
- Dependency upgrades only in one place
- Enforces standards (Java version, build plugins)

---

## 🛡️ Fault Tolerance & Monitoring (Resilience4j & Actuator)

### Q36: What is Resilience4j and why is it used in Order Service?

**Answer:**
**Resilience4j** is a lightweight, fault-tolerance library designed for Java 17/21 and Spring Boot 3 inspired by Netflix Hystrix, but built specifically for functional programming.

In Order Service, calling Inventory Service over HTTP creates a risk: if Inventory Service slows down, crashes, or suffers network partitions, Order Service threads can get blocked, exhausting Tomcat thread pools and cascading into total system outage.

Resilience4j prevents this by:
- **Isolating failures** via Circuit Breakers
- **Retrying transient network blips** automatically
- **Enforcing strict timeouts** via Time Limiters
- **Executing fallback logic** for graceful degradation instead of crashing

---

### Q37: Explain the Circuit Breaker pattern and its states (CLOSED, OPEN, HALF-OPEN).

**Answer:**
The Circuit Breaker pattern models an electrical circuit breaker:

1. **CLOSED (Normal Operation):**
   - All requests pass through to the downstream service.
   - The breaker measures the success/failure rate over a sliding window (e.g., last 10 calls).
   - If the failure rate exceeds the threshold (e.g., 50%), the circuit trips to **OPEN**.

2. **OPEN (Fast Failure):**
   - Calls to the downstream service are immediately blocked without attempting network communication.
   - Requests instantly invoke the configured fallback method.
   - Protects the failing service from being flooded and saves caller resources.
   - A timer runs (`waitDurationInOpenState = 10s`). Once expired, it transitions to **HALF-OPEN**.

3. **HALF-OPEN (Trial / Probe):**
   - Allows a limited number of trial requests (`permittedNumberOfCallsInHalfOpenState = 3`) to pass through.
   - If trial calls succeed, the circuit resets to **CLOSED**.
   - If any trial call fails above threshold, it transitions back to **OPEN** for another wait duration.

---

### Q38: Why was Netflix Hystrix replaced by Resilience4j in modern Spring Boot 3 applications?

**Answer:**
- **Maintenance Status:** Netflix deprecated Hystrix in 2018 and placed it in maintenance mode.
- **Architecture:** Hystrix relied heavily on Archaius (complex configuration) andRxJava 1.x with thread-pool-per-command isolation overhead.
- **Resilience4j Advantages:**
  - Designed natively for Java functional programming (Lambdas, CompletableFuture).
  - Lightweight: Modular library without external dependencies like Guava or RxJava.
  - Non-intrusive: Uses decorators and Spring Boot 3 AOP annotations.
  - Native integration with Spring Boot Actuator and Micrometer.

---

### Q39: How do `@CircuitBreaker`, `@Retry`, and `@TimeLimiter` annotations interact in OrderService?

**Answer:**
In `OrderService.java`:
```java
@TimeLimiter(name = "inventoryService")
@CircuitBreaker(name = "inventoryService", fallbackMethod = "placeOrderFallback")
@Retry(name = "inventoryService")
public CompletableFuture<String> placeOrder(OrderRequest orderRequest) { ... }
```

**Order of execution (aspect order):**
1. **Retry** wraps the call first: If the call throws an exception, Retry re-executes the method up to `maxAttempts=3` times (waiting `waitDuration=2s`).
2. **Circuit Breaker** tracks the call outcome: If retries are exhausted or a non-retryable error occurs, Circuit Breaker records a failure and checks if the failure threshold is crossed.
3. **Time Limiter** monitors execution duration: If the call takes longer than 3 seconds (`timeoutDuration=3s`), it raises a `TimeoutException`.
4. **Fallback:** If all retries fail, a timeout triggers, or the circuit is `OPEN`, Resilience4j intercepts the exception and executes `placeOrderFallback`.

---

### Q40: What are the strict method signature rules for a Resilience4j fallback method?

**Answer:**
1. **Same Return Type:** The fallback method must return the exact same type as the original method (in this case, `CompletableFuture<String>`).
2. **Matching Parameters:** Must accept all the arguments of the original method in the exact same order (here, `OrderRequest orderRequest`).
3. **Extra Throwable Parameter:** Must include a final `Throwable` (or specific exception type) parameter at the end to receive the caught error:
   ```java
   public CompletableFuture<String> placeOrderFallback(OrderRequest orderRequest, Throwable t) {
       return CompletableFuture.supplyAsync(() -> "Inventory service is down, fallback triggered " + t.getMessage());
   }
   ```
4. **Same Class:** The fallback method must reside in the same class (unless using a fallback factory class).

---

### Q41: What is Spring Boot Actuator and how does it help monitor Resilience4j?

**Answer:**
Spring Boot Actuator exposes production-ready endpoints for inspecting application internals.

By adding `resilience4j.circuitbreaker.instances.inventoryService.registerHealthIndicator=true` and `management.health.circuitbreakers.enabled=true`:
- `/actuator/health` exposes the circuit breaker's current health and state (`CLOSED`, `OPEN`, `HALF_OPEN`), failure rate, and call count.
- `/actuator/metrics/resilience4j.circuitbreaker.calls` provides counters broken down by tags (`kind=successful`, `kind=failed`, `kind=not_permitted`).
- `/actuator/metrics/resilience4j.retry.calls` exposes retry attempts and outcomes.

This allows operations teams and automated monitoring systems (Prometheus, Grafana, Kubernetes liveness/readiness probes) to detect service degradation in real time.

---

### Q42: How do you test that a Circuit Breaker and its fallback mechanism work as expected?

**Answer:**
1. **Baseline Test:** Start all services, send an order request, and confirm HTTP 201 `"Order Placed Successfully"` with `/actuator/health` showing state `CLOSED`.
2. **Simulate Outage:** Stop `inventory-service`.
3. **Verify Retry & Fallback:** Send a new order request. Observe the ~4-6 second delay (retry attempts), followed by HTTP 201 with fallback message `"Inventory service is down, fallback triggered Connection refused..."`.
4. **Trip Circuit to OPEN:** Send repeated requests (10 calls) to exceed the 50% failure rate threshold.
5. **Verify Fast Failure:** Check `/actuator/health` (state is `OPEN`). Send another order request — observe it fails over to the fallback immediately without waiting for retry delays.
6. **Verify Recovery:** Restart `inventory-service`. Wait 10 seconds for the breaker to transition to `HALF-OPEN`, send trial requests, and observe the circuit reset to `CLOSED`.
