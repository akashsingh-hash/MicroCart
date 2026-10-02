# 04 — Product Service

## What is it?

The **Product Service** manages the product catalog for the MicroCart system. It is responsible for:
- **Creating** new products in the catalog
- **Listing** all available products

It uses **MongoDB** as its data store — ideal for product data which is schema-flexible (products can have varying attributes).

---

## Location in Project

```
product-service/
├── pom.xml
└── src/main/
    ├── java/com/akash/product_service/
    │   ├── ProductServiceApplication.java
    │   ├── controller/
    │   │   └── ProductController.java
    │   ├── service/
    │   │   └── ProductService.java
    │   ├── model/
    │   │   └── Product.java
    │   ├── dto/
    │   │   ├── ProductRequest.java
    │   │   └── ProductResponse.java
    │   ├── repository/
    │   │   └── ProductRepository.java
    │   └── config/
    │       └── SecurityConfig.java
    └── resources/
        └── application.properties
```

---

## Configuration — `application.properties`

```properties
spring.application.name=product-service
spring.data.mongodb.uri=mongodb://localhost:27017/product-service

# Random port — Eureka tracks the actual one
server.port=0

eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# Keycloak JWT validation
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8181/realms/ecommerce
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs
```

---

## Data Model

### `Product.java` (MongoDB Document)

```java
@Document(value = "product")   // MongoDB collection name = "product"
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {
    @Id
    private String id;           // MongoDB auto-generates ObjectId (String)
    private String name;
    private String description;
    private BigDecimal price;    // BigDecimal for precise monetary values
}
```

**Why MongoDB for Products?**
- Product catalogs are typically read-heavy
- Products can have vastly different attributes (electronics vs clothing)
- MongoDB's BSON format handles nested documents well
- Horizontal scaling via sharding is straightforward

---

## DTOs (Data Transfer Objects)

### `ProductRequest.java` — Incoming from Client

```java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ProductRequest {
    private String name;
    private String description;
    private BigDecimal price;
}
```

### `ProductResponse.java` — Outgoing to Client

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductResponse {
    private String id;           // ID is returned in response
    private String name;
    private String description;
    private BigDecimal price;
}
```

**Why separate Request and Response DTOs?**
- Security: Never expose internal model fields (e.g., auto-generated IDs in request)
- Flexibility: Request and response shapes can evolve independently
- Validation: Different validation rules can apply to request vs response

---

## REST Controller

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductRequest> createProduct(@RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> response = productService.getAllProducts();
        return ResponseEntity.ok(response);
    }
}
```

### API Endpoints

| Method | URL | Auth Required | Description |
|---|---|---|---|
| `POST` | `/api/products` | Yes (JWT) | Create a new product |
| `GET` | `/api/products` | Yes (JWT) | Get all products |

---

## Service Layer

```java
@Service
@RequiredArgsConstructor
@Slf4j                     // Injects a logger: log.info(...)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product {} with id is saved", savedProduct.getId());

        return mapToProductResponse(savedProduct);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::mapToProductResponse)
                .toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}
```

**Design Notes:**
- The service layer converts between DTOs and domain models — controllers never see domain entities directly
- `@Slf4j` generates a `log` field — used for logging when a product is saved
- Manual mapping (not MapStruct) is used — simple enough without an additional library

---

## Repository

```java
public interface ProductRepository extends MongoRepository<Product, String> {
    // MongoRepository provides: save, findAll, findById, delete, etc.
    // No custom queries needed for this service
}
```

`MongoRepository<Product, String>` — The second generic is the ID type (`String` for MongoDB ObjectId).

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
                        .anyRequest().authenticated()   // All endpoints need auth
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})                 // Validate JWT tokens
                );
        return http.build();
    }
}
```

- All endpoints require authentication
- JWT tokens are validated using Keycloak's public keys (auto-fetched from `jwk-set-uri`)
- `@EnableMethodSecurity` enables method-level security annotations (`@PreAuthorize`, etc.)

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-mongodb` | MongoDB integration |
| `spring-boot-starter-web` | REST endpoints |
| `spring-boot-starter-security` | Security filter chain |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `spring-cloud-starter-netflix-eureka-client` | Service registration |
| `lombok` | Reduce boilerplate |
| `testcontainers:mongodb` | Spin up real MongoDB in tests |
| `testcontainers:junit-jupiter` | JUnit 5 + Testcontainers integration |

---

## Sample Request/Response

### Create Product
```http
POST http://localhost:8080/api/products
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "name": "iPhone 15 Pro",
  "description": "Latest iPhone with titanium design",
  "price": 134900.00
}
```

Response `201 Created`:
```json
{
  "name": "iPhone 15 Pro",
  "description": "Latest iPhone with titanium design",
  "price": 134900.00
}
```

### Get All Products
```http
GET http://localhost:8080/api/products
Authorization: Bearer <JWT_TOKEN>
```

Response `200 OK`:
```json
[
  {
    "id": "64f8a2b1c3d4e5f678901234",
    "name": "iPhone 15 Pro",
    "description": "Latest iPhone with titanium design",
    "price": 134900.00
  }
]
```
