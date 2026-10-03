# 02 — Discovery Server (Eureka)

## What is it?

The **Discovery Server** is the service registry for the entire MicroCart system. It is built using **Netflix Eureka Server**, wrapped inside a Spring Boot application.

Think of it as the **phonebook** of the microservices world. Every service that starts up "calls" the discovery server to register itself. When one service needs to talk to another, it asks the discovery server for the other's address instead of hardcoding IPs.

---

## Location in Project

```
discovery-server/
├── pom.xml
└── src/main/
    ├── java/org/example/discoveryserver/
    │   └── DiscoveryServerApplication.java
    └── resources/
        └── application.properties
```

---

## Key Configuration

**`application.properties`**
```properties
spring.application.name=discovery-server

server.port=8761

eureka.instance.hostname=localhost
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false

# Tracing (Micrometer & Zipkin for Spring Boot 3)
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

### Configuration Explained

| Property | Value | Meaning |
|---|---|---|
| `server.port` | `8761` | Standard Eureka port (convention) |
| `eureka.client.register-with-eureka` | `false` | The server doesn't register **itself** — it IS the registry |
| `eureka.client.fetch-registry` | `false` | No need to cache registry locally — it is the source of truth |
| `eureka.instance.hostname` | `localhost` | Hostname for the Eureka dashboard |
| `management.tracing.sampling.probability` | `1.0` | Sample 100% of requests for distributed tracing |
| `management.zipkin.tracing.endpoint` | `http://localhost:9411/api/v2/spans` | Zipkin v2 spans ingestion endpoint |

---

## Main Application Class

```java
@SpringBootApplication
@EnableEurekaServer          // ← This single annotation turns it into a Eureka Server
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

The `@EnableEurekaServer` annotation is the magic — it activates all the Eureka registry infrastructure.

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-cloud-starter-netflix-eureka-server` | The Eureka Server itself |
| `spring-boot-starter-web` | Serves the Eureka dashboard UI |
| `spring-boot-starter-actuator` | Exposes `/actuator/health` and metrics |

---

## Eureka Dashboard

Once running, visit: **http://localhost:8761**

You'll see:
- All registered service instances
- Their IP/port
- Their health status (UP / DOWN)
- Number of instances per service name

---

## How Services Register with Eureka

Every other service (product, order, inventory, gateway) has:
```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```
And the dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

When a service starts, it sends a **POST /eureka/apps/{serviceName}** request to register. It then periodically sends **heartbeats** (every 30s by default) to renew its registration. If the server doesn't receive a heartbeat for 90s, it evicts the instance.

---

## Random Ports and Eureka

Product-service and inventory-service use `server.port=0`:
```properties
eureka.instance.instance-id=${spring.application.name}:${random.value}
eureka.instance.prefer-ip-address=true
```

- `instance-id` uses a random UUID so multiple instances have unique IDs
- `prefer-ip-address=true` tells Eureka to register using IP (not hostname)

This enables running multiple instances without port conflicts, perfect for horizontal scaling.

---

## What Happens If Discovery Server Goes Down?

- Existing registered clients have a **cached copy** of the registry, so they continue working for some time
- New services cannot register
- Services that restart will fail to register
- This is a **single point of failure** in this basic setup (in production, you'd run multiple Eureka servers in a cluster)

---

## Eureka vs Consul vs Zookeeper

| Feature | Eureka | Consul | Zookeeper |
|---|---|---|---|
| Consistency | AP (Available, Partition-tolerant) | CP (Consistent) | CP |
| Health Check | Heartbeats | HTTP/TCP/Script/gRPC | Session-based |
| DNS Support | No | Yes | No |
| Spring Cloud Support | Excellent | Good | Good |
| Complexity | Low | Medium | High |

Eureka is chosen here because it integrates seamlessly with Spring Cloud and is easy to understand for learning purposes.
