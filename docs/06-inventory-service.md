# 06 — Inventory Service

## What is it?

The **Inventory Service** is responsible for tracking the **stock availability** of products. It answers one critical question:

> "Is this product (identified by SKU code) currently in stock?"

It is called **internally** by the Order Service before an order is confirmed — clients do not typically call it directly. It uses **MySQL** to persist stock data.

---

## Location in Project

```
inventory-service/
├── pom.xml
└── src/main/
    ├── java/com/akash/inventory_service/
    │   ├── InventoryServiceApplication.java
    │   ├── controller/
    │   │   └── InventoryController.java
    │   ├── service/
    │   │   └── InventoryService.java
    │   ├── model/
    │   │   └── Inventory.java
    │   ├── dto/
    │   │   └── InventoryResponse.java
    │   ├── repository/
    │   │   └── InventoryRepository.java
    │   └── config/
    │       └── SecurityConfig.java
    └── resources/
        └── application.properties
```

---

## Configuration — `application.properties`

```properties
spring.application.name=inventory-service

spring.datasource.url=jdbc:mysql://localhost:3306/inventory_service?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=Akash@0786
spring.jpa.hibernate.ddl-auto=create-drop

# Random port — allows multiple instances
server.port=0

eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.instance-id=${spring.application.name}:${random.value}
eureka.instance.prefer-ip-address=true

# Keycloak JWT validation
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/ecommerce
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs
```

### Important Configuration Notes

| Property | Value | Note |
|---|---|---|
| `server.port` | `0` | Random port — supports multiple instances |
| `ddl-auto` | `create-drop` | **Drops and recreates** the table every restart (for dev; use `validate` in prod) |
| `instance-id` | `inventory-service:{random}` | Ensures unique Eureka registration for each instance |
| `prefer-ip-address` | `true` | Register with IP so load balancer can route correctly |

---

## Data Model

### `Inventory.java`

```java
@Entity
@Table(name = "t_inventory")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String skuCode;    // Stock Keeping Unit (unique product identifier)
    private Integer quantity;  // Current stock count (0 = out of stock)
}
```

### Database Table — `t_inventory`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (AUTO_INCREMENT) | Primary key |
| `sku_code` | VARCHAR | Unique product identifier |
| `quantity` | INT | Current stock count |

**SKU Code** (Stock Keeping Unit) — a unique code used to identify each product variant (e.g., `iphone-15-pro-black-256gb`). Note that inventory tracks SKUs, not product IDs — this is intentional as a product can have multiple SKU variants (different colors, sizes, etc.).

---

## DTO

### `InventoryResponse.java`

```java
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class InventoryResponse {
    private String skuCode;
    private Boolean isInStock;   // true if quantity > 0, false otherwise
}
```

This is a simple, lean DTO — the inventory service doesn't expose quantity numbers (business rule: clients only need to know in/out of stock).

---

## Repository

```java
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findBySkuCodeIn(List<String> skuCode);
    // ↑ Spring Data JPA generates SQL:
    //   SELECT * FROM t_inventory WHERE sku_code IN ('sku1', 'sku2', ...)
}
```

`findBySkuCodeIn` — A derived query method. Spring Data JPA parses the method name and generates the SQL automatically. This efficiently fetches all matching inventory records in **one database query** (not N queries for N SKUs).

---

## Service Layer

```java
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)    // Optimization: read-only transaction
    public List<InventoryResponse> isInStock(List<String> skuCode) {
        return inventoryRepository.findBySkuCodeIn(skuCode)
                .stream()
                .map(inventory ->
                    InventoryResponse.builder()
                            .skuCode(inventory.getSkuCode())
                            .isInStock(inventory.getQuantity() > 0)   // Core logic
                            .build()
                )
                .toList();
    }
}
```

### Key Design Points

1. **`@Transactional(readOnly = true)`** — Tells Hibernate this is a read-only operation. Benefits:
   - JPA skips dirty-checking (no need to track entity changes)
   - Database driver can optimize for read replicas
   - Flush mode is set to NEVER (no accidental writes)

2. **Batch check** — Takes a `List<String>` of SKU codes, fetches all in one SQL query, maps to responses. Efficient compared to calling the service once per SKU.

3. **`quantity > 0`** — This is the core business rule. Zero quantity means out of stock.

---

## REST Controller

```java
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping()
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> isInStock(@RequestParam List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }
}
```

### API Endpoint

| Method | URL | Auth Required | Description |
|---|---|---|---|
| `GET` | `/api/inventory?skuCode=sku1&skuCode=sku2` | Yes (JWT) | Check stock for multiple SKUs |

### URL Example
```
GET /api/inventory?skuCode=iphone-15-pro&skuCode=airpods-pro
```

Response `200 OK`:
```json
[
  { "skuCode": "iphone-15-pro", "isInStock": true },
  { "skuCode": "airpods-pro", "isInStock": false }
]
```

---

## Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                );
        return http.build();
    }
}
```

The inventory service requires JWT authentication for all requests. The JWT token is forwarded from order-service via `ServletBearerExchangeFilterFunction` — see [08 - Inter-Service Communication](./08-inter-service-communication.md) for details.

---

## How Inventory Data Gets Populated

Since `ddl-auto=create-drop`, the database is empty each restart. In a real system you would:
1. Use `ddl-auto=update` or `validate` with Flyway/Liquibase migrations
2. Have a separate "stock management" service or admin API to add/update inventory
3. Use database seed scripts

For this project (dev/learning), you can manually insert data:
```sql
INSERT INTO t_inventory (sku_code, quantity) VALUES ('iphone-15-pro', 100);
INSERT INTO t_inventory (sku_code, quantity) VALUES ('airpods-pro', 50);
INSERT INTO t_inventory (sku_code, quantity) VALUES ('ipad-mini', 0);
```

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-jpa` | JPA/Hibernate for MySQL |
| `spring-boot-starter-web` | REST endpoints |
| `mysql-connector-j` | MySQL JDBC driver |
| `spring-cloud-starter-netflix-eureka-client` | Service registration + multi-instance support |
| `spring-boot-starter-security` | Security filter chain |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `lombok` | Reduce boilerplate |

---

## Why Separate Inventory from Product Service?

This is the **Single Responsibility Principle** applied at the service level:
- **Product Service** = What the product IS (name, description, price, metadata)
- **Inventory Service** = How MUCH of the product we HAVE (stock levels)

This separation means:
- Inventory can be scaled independently (order-heavy systems check inventory constantly)
- Product catalog updates don't affect stock management
- Inventory can have its own optimized database (e.g., Redis for ultra-fast stock checks)
