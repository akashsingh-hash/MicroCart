# 11 — Startup & Running Guide

## Prerequisites

Before starting any service, ensure the following are installed and running:

| Requirement | Version | Purpose |
|---|---|---|
| JDK | 21+ | Run all services |
| Maven | 3.9+ | Build (or use included `mvnw`) |
| MySQL | 8.0+ | order-service, inventory-service |
| MongoDB | 6.0+ | product-service |
| Keycloak | 22+ | Authentication (external IAM) |

---

## Step 1: Prepare Databases

### MySQL Setup

Create the two databases (Hibernate will create the tables):
```sql
CREATE DATABASE order_service;
CREATE DATABASE inventory_service;
```

Ensure MySQL is running on port `3306` with credentials `root / Akash@0786`
(or update `application.properties` to match your credentials).

### MongoDB Setup

MongoDB needs no database pre-creation — Spring Data MongoDB creates it automatically.
Just ensure MongoDB is running on `localhost:27017`.

---

## Step 2: Start Keycloak

### Using Docker (Recommended)

```bash
docker run -d \
  --name keycloak \
  -p 8181:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

Access the Keycloak Admin Console: **http://localhost:8181**

### Configure Keycloak (One-time setup)

1. **Login:** admin / admin
2. **Create Realm:** `ecommerce`
3. **Create Realm Roles:** `USER`, `ADMIN`
4. **Create Client:**
   - Client ID: `shopping-cart-client`
   - Client type: `OpenID Connect`
   - Authentication flow: Enable "Direct access grants"
5. **Create Users:**
   - `user1` → assign role `USER`
   - `admin1` → assign roles `USER` + `ADMIN`
   - Set passwords for each user

---

## Step 3: Build All Services

From the root directory:
```bash
# Using Maven wrapper (no Maven installation needed)
./mvnw clean install -DskipTests

# Or with Maven installed
mvn clean install -DskipTests
```

This builds all 5 services into JAR files in their respective `target/` directories.

---

## Step 4: Start Services (In Order!)

**⚠️ Start order is critical.** Services register with Eureka on startup. If Eureka isn't up, registration fails.

### 1. Start Discovery Server (First!)

```bash
cd discovery-server
./mvnw spring-boot:run

# OR run the JAR
java -jar target/discovery-server-0.0.1-SNAPSHOT.jar
```

✅ Verify: Open **http://localhost:8761** — Eureka dashboard should load.

---

### 2. Start Inventory Service

```bash
cd inventory-service
./mvnw spring-boot:run
```

✅ Verify: In Eureka dashboard, `INVENTORY-SERVICE` should appear under "Instances currently registered".

**Seed some inventory data** (important — `create-drop` means empty DB on start):
```sql
USE inventory_service;
INSERT INTO t_inventory (sku_code, quantity) VALUES ('iphone-15-pro', 100);
INSERT INTO t_inventory (sku_code, quantity) VALUES ('iphone-15-black', 50);
INSERT INTO t_inventory (sku_code, quantity) VALUES ('airpods-pro', 75);
INSERT INTO t_inventory (sku_code, quantity) VALUES ('macbook-pro-m3', 10);
INSERT INTO t_inventory (sku_code, quantity) VALUES ('out-of-stock-item', 0);
```

---

### 3. Start Product Service

```bash
cd product-service
./mvnw spring-boot:run
```

✅ Verify: `PRODUCT-SERVICE` appears in Eureka dashboard.

---

### 4. Start Order Service

```bash
cd order-service
./mvnw spring-boot:run
```

✅ Verify: `ORDER-SERVICE` appears in Eureka dashboard.

---

### 5. Start API Gateway (Last!)

```bash
cd api-gateway
./mvnw spring-boot:run
```

✅ Verify: `API-SERVER` appears in Eureka dashboard.
✅ Verify: All 4 other services are visible in Eureka.

---

## Step 5: Get an Auth Token from Keycloak

All API calls require a JWT token. Get one from Keycloak:

```bash
curl -X POST http://localhost:8181/realms/ecommerce/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=shopping-cart-client" \
  -d "username=user1" \
  -d "password=user1password"
```

Response:
```json
{
  "access_token": "eyJhbGci...",
  "expires_in": 300,
  "token_type": "Bearer"
}
```

Copy the `access_token` value.

---

## Step 6: Test the APIs

Use the JWT token in all requests:

### Test Product Service

**Create a Product:**
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone 15 Pro",
    "description": "256GB, Black Titanium",
    "price": 134900
  }'
```

**Get All Products:**
```bash
curl http://localhost:8080/api/products \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

---

### Test Inventory Service

**Check Stock:**
```bash
curl "http://localhost:8080/api/inventory?skuCode=iphone-15-pro&skuCode=airpods-pro" \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

Expected Response:
```json
[
  {"skuCode": "iphone-15-pro", "isInStock": true},
  {"skuCode": "airpods-pro", "isInStock": true}
]
```

---

### Test Order Service

**Place an Order (requires USER role):**
```bash
curl -X POST http://localhost:8080/api/order \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderLineItemsDtoList": [
      {
        "skuCode": "iphone-15-pro",
        "price": 134900,
        "quantity": 1
      }
    ]
  }'
```

Expected Response (in-stock):
```
Order Placed Successfully
```

**Get All Orders (requires ADMIN role):**
```bash
# Get admin token first, then:
curl http://localhost:8080/api/order \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

---

## Step 7: Test Resilience4j Fault Tolerance & Actuator

Follow these steps to observe fault tolerance, retry logic, fallback response, and Actuator monitoring:

### 1. Check Circuit Breaker Health (Normal State)

```bash
curl http://localhost:8081/actuator/health
```

Expected Response snippet:
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
            "failureRate": "0.0%"
          }
        }
      }
    }
  }
}
```

### 2. Simulate Downstream Outage

Stop the `inventory-service` process (Ctrl+C in terminal or Stop in IDE).

### 3. Place Order & Observe Fallback

Send an order placement request via the API Gateway or directly to Order Service:

```bash
curl -X POST http://localhost:8080/api/order \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "orderLineItemsDtoList": [
      {
        "skuCode": "iphone-15-pro",
        "price": 134900,
        "quantity": 1
      }
    ]
  }'
```

- **Behavior:** The request will attempt 3 retries (waiting 2s between attempts).
- **Graceful Degradation:** Instead of throwing a 500 error or hanging indefinitely, the response returns HTTP 201 with the fallback string:
```
Inventory service is down, fallback triggered Connection refused: no further information
```

### 4. Observe Circuit Breaker Trip in Actuator

Send 10 consecutive order requests while `inventory-service` is down to meet the sliding window threshold (`slidingWindowSize=10`, `failureRateThreshold=50`):

```bash
# Re-check health
curl http://localhost:8081/actuator/health
```

The circuit breaker status will change to **`CIRCUIT_OPEN`** with state **`OPEN`**:
```json
"inventoryService": {
  "status": "CIRCUIT_OPEN",
  "details": {
    "state": "OPEN",
    "failureRate": "100.0%"
  }
}
```

### 5. Inspect Resilience4j Metrics

View call statistics across the circuit breaker:

```bash
curl http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.calls
```

This returns total calls tagged by outcome (`successful`, `failed`, `not_permitted`). While `OPEN`, subsequent requests register as `not_permitted` without even attempting to call inventory-service.

---

## Postman Collection Setup

1. Create a new Collection: "MicroCart"
2. Add Collection Variable: `token` = (your JWT)
3. For all requests, set Authorization: `Bearer {{token}}`
4. Create requests for each endpoint listed above

---

## Running Multiple Instances (Scale Out)

Since product-service and inventory-service use `server.port=0`, you can start multiple instances:

```bash
# Terminal 1
cd inventory-service && ./mvnw spring-boot:run

# Terminal 2 (different terminal, same directory)
cd inventory-service && ./mvnw spring-boot:run
```

Both instances register with Eureka under `INVENTORY-SERVICE`. The gateway and order-service automatically load-balance between them.

---

## Eureka Self-Preservation Mode

You may see this warning in Eureka:
```
EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT.
RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED JUST TO BE SAFE.
```

This is **normal in development**. Self-preservation mode prevents Eureka from evicting services when network is flaky. In production, tune:
```properties
eureka.server.enable-self-preservation=false  # Only in controlled environments
eureka.server.eviction-interval-timer-in-ms=5000
```

---

## Common Issues & Fixes

| Issue | Cause | Fix |
|---|---|---|
| Service not showing in Eureka | Eureka started too late | Start discovery-server first, wait 30s, then start others |
| `Connection refused` to MySQL | MySQL not running | Start MySQL service |
| JWT validation failure (401) | Token expired or wrong realm | Get fresh token; check `issuer-uri` config |
| `inventory-service: UNKNOWN host` | Missing `@LoadBalanced` | Ensure `@LoadBalanced` on `WebClient.Builder` bean |
| `Access Denied` (403) | Wrong role | Ensure user has correct role in Keycloak |
| `Product not in stock` | No inventory data | Insert rows into `t_inventory` table |
| Keycloak `invalid_client` | Wrong client ID | Verify client ID in Keycloak matches config |
