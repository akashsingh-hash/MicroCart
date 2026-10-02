# 09 — Data Models & DTOs Reference

Complete reference for all entities and Data Transfer Objects across all services.

---

## Product Service

### Entity: `Product`
- **Package:** `com.akash.product_service.model`
- **Database:** MongoDB
- **Collection:** `product`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `String` | `_id` | MongoDB ObjectId, auto-generated |
| `name` | `String` | `name` | Product name |
| `description` | `String` | `description` | Product description |
| `price` | `BigDecimal` | `price` | Monetary value (exact decimal) |

**Lombok Annotations:** `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder`
**Spring Annotations:** `@Document(value = "product")`, `@Id`

---

### DTO: `ProductRequest`
- **Direction:** Client → Service (incoming)
- **Used in:** `POST /api/products`

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Product name |
| `description` | `String` | Product description |
| `price` | `BigDecimal` | Product price |

> Note: No `id` field — the client doesn't set the ID, MongoDB generates it.

---

### DTO: `ProductResponse`
- **Direction:** Service → Client (outgoing)
- **Used in:** `GET /api/products`, `POST /api/products` response

| Field | Type | Description |
|---|---|---|
| `id` | `String` | MongoDB-generated product ID |
| `name` | `String` | Product name |
| `description` | `String` | Product description |
| `price` | `BigDecimal` | Product price |

---

## Inventory Service

### Entity: `Inventory`
- **Package:** `com.akash.inventory_service.model`
- **Database:** MySQL
- **Table:** `t_inventory`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `Long` | `id` | Auto-increment primary key |
| `skuCode` | `String` | `sku_code` | Stock Keeping Unit identifier |
| `quantity` | `Integer` | `quantity` | Current stock count |

**Lombok Annotations:** `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`
**JPA Annotations:** `@Entity`, `@Table(name="t_inventory")`, `@Id`, `@GeneratedValue(IDENTITY)`

---

### DTO: `InventoryResponse`
- **Package:** `com.akash.inventory_service.dto`
- **Direction:** Service → Caller (outgoing, called by order-service)
- **Used in:** `GET /api/inventory` response

| Field | Type | Description |
|---|---|---|
| `skuCode` | `String` | The SKU being checked |
| `isInStock` | `Boolean` | `true` if quantity > 0, `false` otherwise |

**Lombok Annotations:** `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder`

---

## Order Service

### Entity: `Order`
- **Package:** `com.akash.order_service.model`
- **Database:** MySQL
- **Table:** `t_orders`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `Long` | `id` | Auto-generated (GenerationType.AUTO) |
| `orderNumber` | `String` | `order_number` | UUID string, unique per order |
| `orderLineItemsList` | `List<OrderLineItems>` | (FK relationship) | Cascade ALL |

**Relationships:** `@OneToMany(cascade = CascadeType.ALL)` — One order has many line items.
**Lombok Annotations:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

---

### Entity: `OrderLineItems`
- **Package:** `com.akash.order_service.model`
- **Database:** MySQL
- **Table:** `t_order_line_items`

| Field | Type | DB Column | Notes |
|---|---|---|---|
| `id` | `Long` | `id` | Auto-increment primary key |
| `skuCode` | `String` | `sku_code` | Product SKU |
| `price` | `BigDecimal` | `price` | Price at the time of order |
| `quantity` | `Integer` | `quantity` | Units ordered |

**Lombok Annotations:** `@Data`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

---

### DTO: `OrderRequest`
- **Direction:** Client → Order Service (incoming)
- **Used in:** `POST /api/order`

| Field | Type | Description |
|---|---|---|
| `orderLineItemsDtoList` | `List<OrderLineItemsDto>` | List of items being ordered |

---

### DTO: `OrderLineItemsDto`
- **Direction:** Inside `OrderRequest` (nested DTO)

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Optional — can be null for new items |
| `skuCode` | `String` | Product SKU to order |
| `price` | `BigDecimal` | Price of the product |
| `quantity` | `Integer` | How many units to order |

---

### DTO: `InventoryResponse` (Order Service copy)
- **Package:** `com.akash.order_service.dto`
- **Direction:** Inventory Service → Order Service (received from cross-service call)

| Field | Type | Description |
|---|---|---|
| `skuCode` | `String` | The SKU checked |
| `isInStock` | `Boolean` | Stock availability flag |

> This is a **copy** of the same DTO from inventory-service. Defined separately to avoid tight coupling between services.

---

## Why `BigDecimal` for Money?

`double` and `float` have floating-point precision issues:
```java
double price = 0.1 + 0.2;
// price = 0.30000000000000004  ← WRONG!

BigDecimal price = new BigDecimal("0.1").add(new BigDecimal("0.2"));
// price = 0.3  ← CORRECT!
```

For monetary values, **always use `BigDecimal`** to avoid rounding errors.

---

## Database Schema Summary

### MySQL Databases

**`order_service` database:**
```sql
CREATE TABLE t_orders (
    id BIGINT NOT NULL,
    order_number VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE t_order_line_items (
    id BIGINT AUTO_INCREMENT NOT NULL,
    sku_code VARCHAR(255),
    price DECIMAL(38,2),
    quantity INT,
    PRIMARY KEY (id)
);

-- JPA creates a join table for the @OneToMany relationship
CREATE TABLE t_orders_order_line_items_list (
    t_orders_id BIGINT NOT NULL,
    order_line_items_list_id BIGINT NOT NULL,
    UNIQUE (order_line_items_list_id),
    FOREIGN KEY (t_orders_id) REFERENCES t_orders(id),
    FOREIGN KEY (order_line_items_list_id) REFERENCES t_order_line_items(id)
);
```

**`inventory_service` database:**
```sql
CREATE TABLE t_inventory (
    id BIGINT AUTO_INCREMENT NOT NULL,
    sku_code VARCHAR(255),
    quantity INT,
    PRIMARY KEY (id)
);
```

### MongoDB Database

**`product-service` database:**
```json
// Collection: "product"
{
  "_id": ObjectId("64f8a2b1c3d4e5f678901234"),
  "name": "iPhone 15 Pro",
  "description": "Latest iPhone with titanium design",
  "price": 134900.00
}
```

---

## DTO vs Entity — Why the Separation?

| Concern | Entity | DTO |
|---|---|---|
| Purpose | Represents DB row | Represents API contract |
| Exposed to clients? | Never directly | Yes |
| Contains lazy-loaded relationships? | Yes (could cause N+1 or serialization issues) | No |
| Validation annotations | Database constraints | Request validation |
| Can change independently? | Schema migration needed | No migration needed |
| Example risk | Exposing `password` field | Controlled — only expose what's needed |

**Best Practice:** Controllers accept DTOs, pass to service, service maps to entities, repository saves entities. Response goes entity → service maps to DTO → controller returns DTO.
