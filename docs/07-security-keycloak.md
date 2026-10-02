# 07 — Security & Keycloak Integration

## Overview

MicroCart uses **OAuth 2.0 with JWT (JSON Web Tokens)** for authentication and authorization. The identity provider is **Keycloak** — an open-source IAM (Identity and Access Management) solution.

Security is enforced at **two levels**:
1. **API Gateway** — validates JWT before routing (first line of defense)
2. **Each microservice** — independently validates JWT (defense in depth)

---

## The Technology Stack

| Technology | Role |
|---|---|
| **Keycloak** | Identity Provider (IdP) — issues JWT tokens |
| **OAuth 2.0** | Authorization framework |
| **JWT (JSON Web Token)** | Stateless token format |
| **Spring Security** | Security filter chain in each service |
| **OAuth 2.0 Resource Server** | Spring's built-in JWT validation library |

---

## How Keycloak is Set Up

Keycloak runs externally on **port 8181** (not part of this codebase). The setup involves:

### Realm
A **realm** is an isolated security domain in Keycloak. This project uses:
```
Realm name: ecommerce
```

### Clients
A **client** in Keycloak represents an application that can request tokens. You'd register your frontend or Postman here.

### Roles
Two roles are defined in the `ecommerce` realm:
- **`USER`** — Can place orders (`POST /api/order`)
- **`ADMIN`** — Can view all orders (`GET /api/order`)

These are **realm-level roles** stored in the JWT under `realm_access.roles`.

### Users
Users are created in Keycloak, assigned roles, and authenticate to get tokens.

---

## JWT Token Flow

```
1. User logs in → Keycloak UI or API
   POST http://localhost:8181/realms/ecommerce/protocol/openid-connect/token
   body: grant_type=password&client_id=...&username=...&password=...

2. Keycloak validates credentials and returns:
   {
     "access_token": "eyJhbGciOiJSUzI1NiIsInR...",
     "token_type": "Bearer",
     "expires_in": 300
   }

3. Client includes token in every request:
   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR...

4. API Gateway validates token:
   → Fetches Keycloak's public keys from JWKS endpoint
   → Verifies JWT signature
   → Checks expiry, issuer

5. Request forwarded to microservice (with token)

6. Microservice validates token again (defense in depth)
   → Extracts roles from JWT claims
   → Applies method-level security (@PreAuthorize)
```

---

## JWT Structure

A Keycloak JWT decoded looks like:

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT"
  },
  "payload": {
    "exp": 1728123456,
    "iat": 1728119856,
    "jti": "abc123",
    "iss": "http://localhost:8181/realms/ecommerce",
    "sub": "user-uuid-here",
    "preferred_username": "akash",
    "realm_access": {
      "roles": ["USER", "ADMIN", "default-roles-ecommerce"]
    },
    "resource_access": {
      "account": {
        "roles": ["manage-account", "view-profile"]
      }
    }
  }
}
```

**Key claims:**
- `iss` — Issuer (must match `jwt.issuer-uri` config)
- `exp` — Expiry timestamp
- `realm_access.roles` — The application roles assigned to this user
- `preferred_username` — The username (used as principal name)

---

## `KeycloakJwtAuthenticationConverter.java`

This is the most important security class in the project. It's custom-built for order-service.

```java
@Component
public class KeycloakJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultConverter.convert(jwt).stream(),    // Standard Spring OAuth2 authorities
                extractKeycloakRoles(jwt).stream()         // + Keycloak realm roles
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities,
                jwt.getClaimAsString("preferred_username"));  // Principal name = Keycloak username
    }

    private Collection<GrantedAuthority> extractKeycloakRoles(Jwt jwt) {
        List<GrantedAuthority> roles = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            realmRoles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .forEach(roles::add);
        }
        return roles;
    }
}
```

### Why is this needed?

**Problem:** Spring Security expects roles as `ROLE_XXX` in `GrantedAuthority`. By default, the `JwtGrantedAuthoritiesConverter` reads `scope` and `scp` claims from the JWT. But Keycloak stores roles under `realm_access.roles`, not `scope`.

**Solution:** This custom converter:
1. Reads `realm_access.roles` from the JWT payload
2. Prefixes each with `ROLE_` and uppercases it: `USER` → `ROLE_USER`, `ADMIN` → `ROLE_ADMIN`
3. Merges with the default converter's authorities
4. Sets `preferred_username` as the principal name (so you can use `@AuthenticationPrincipal` to get the username)

---

## Security Configuration per Service

### API Gateway (`@EnableWebFluxSecurity`)

```java
@Configuration
@EnableWebFluxSecurity           // Reactive gateway → WebFlux security
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/eureka/**").permitAll()  // Eureka dashboard public
                        .anyExchange().authenticated()           // Everything else needs JWT
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}
```

### Order Service (`@EnableWebSecurity`)

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
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                keycloakJwtAuthenticationConverter))  // Custom converter
                );
        return http.build();
    }
}
```

### Product and Inventory Services (Standard JWT)

```java
// No custom converter — roles not needed for authorization in these services
http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
```

---

## Role-Based Access Control (RBAC)

Only the order-service uses `@PreAuthorize`:

```java
@PostMapping
@PreAuthorize("hasRole('USER')")    // Spring checks ROLE_USER in GrantedAuthorities
public ResponseEntity<String> placeOrder(...) { ... }

@GetMapping
@PreAuthorize("hasRole('ADMIN')")   // Spring checks ROLE_ADMIN
public ResponseEntity<List<Order>> getAllOrders() { ... }
```

`hasRole('USER')` automatically checks for `ROLE_USER` (Spring adds the prefix). This is why the converter maps `USER` → `ROLE_USER`.

---

## JWKS (JSON Web Key Set) Endpoint

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=
    http://localhost:8181/realms/ecommerce/protocol/openid-connect/certs
```

This endpoint returns Keycloak's public keys used to verify JWT signatures. Spring Security fetches and caches these keys, refreshing when a new key ID (kid) is seen. This way, no symmetric secret is shared between Keycloak and the services — only asymmetric public keys.

---

## Keycloak Configuration Steps (External Setup)

To replicate this setup:

1. **Start Keycloak:**
   ```bash
   docker run -p 8181:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
     quay.io/keycloak/keycloak:latest start-dev
   ```

2. **Create Realm:** `ecommerce`

3. **Create Roles:** `USER`, `ADMIN` (under Realm Roles)

4. **Create Users:**
   - `user1` with role `USER`
   - `admin1` with role `ADMIN`

5. **Create Client:** (for Postman/frontend to get tokens)
   - Client ID: `shopping-cart-client`
   - Access type: `public` (or `confidential` for server apps)
   - Direct Access Grants Enabled: `true`

6. **Get Token via Postman:**
   ```
   POST http://localhost:8181/realms/ecommerce/protocol/openid-connect/token
   Content-Type: application/x-www-form-urlencoded
   
   grant_type=password
   client_id=shopping-cart-client
   username=user1
   password=user1password
   ```

---

## Security Summary Table

| Service | Security Type | Custom Converter | Method-Level Security |
|---|---|---|---|
| API Gateway | WebFlux OAuth2 JWT | No | No (`authorizeExchange`) |
| Product Service | OAuth2 JWT | No | No (all requests authenticated) |
| Inventory Service | OAuth2 JWT | No | No (all requests authenticated) |
| Order Service | OAuth2 JWT | **Yes** (`KeycloakJwtAuthenticationConverter`) | **Yes** (`@PreAuthorize`) |
