# 13 — Notification Service

## Overview

The **Notification Service** is a Kafka consumer microservice that listens for domain events published by other services (e.g., `order-service`) and processes them to trigger notifications. It is fully integrated with the Eureka service registry for discoverability and participates in the system's distributed tracing pipeline via Micrometer + Zipkin.

---

## Responsibilities

- **Consume Kafka events** from the `notificationTopic` topic
- **Process order-placed events** and trigger the appropriate notification logic (email, SMS, logging, etc.)
- **Register with Eureka** so it is a visible member of the service mesh
- **Export distributed traces** to Zipkin for full observability

---

## Dependencies (`pom.xml`)

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | Embedded Tomcat + REST support |
| `spring-kafka` | Kafka producer/consumer support |
| `spring-cloud-starter-netflix-eureka-client` | Register with Eureka service registry |
| `spring-boot-starter-actuator` | Health/metrics endpoints; required foundation for Micrometer tracing |
| `micrometer-tracing-bridge-brave` | Distributed tracing bridge (Micrometer → Brave/Zipkin) |
| `zipkin-reporter-brave` | Ships completed trace spans to the Zipkin collector |
| `lombok` | Reduces boilerplate (`@Data`, `@Slf4j`, etc.) |
| `spring-boot-starter-test` | Unit and integration testing |

---

## Configuration (`application.properties`)

```properties
spring.application.name=notification-service

# Pick a random free port
server.port=0

# Eureka Client
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.instance-id=${spring.application.name}:${random.value}
eureka.instance.prefer-ip-address=true

# Kafka Properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.template.default-topic=notificationTopic

# Kafka Producer settings
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Kafka Consumer settings
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer

# Tracing (Micrometer & Zipkin for Spring Boot 3)
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

### Property Reference

| Property | Value | Explanation |
|---|---|---|
| `spring.application.name` | `notification-service` | Eureka registration name |
| `server.port` | `0` | Random port at startup — multiple instances can co-exist |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka/` | Where to register with Eureka |
| `eureka.instance.instance-id` | `notification-service:{random}` | Unique ID per instance for Eureka |
| `eureka.instance.prefer-ip-address` | `true` | Register by IP address, not hostname |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker connection |
| `spring.kafka.template.default-topic` | `notificationTopic` | Default topic for Kafka template publishing |
| `spring.kafka.producer.value-serializer` | `JsonSerializer` | Serialize event objects to JSON for Kafka messages |
| `spring.kafka.consumer.value-deserializer` | `JsonDeserializer` | Deserialize incoming Kafka JSON messages back to objects |
| `management.tracing.sampling.probability` | `1.0` | Sample 100% of requests for distributed tracing |
| `management.zipkin.tracing.endpoint` | `http://localhost:9411/api/v2/spans` | Export spans to Zipkin collector |

---

## Kafka Event Flow

```
order-service
    │
    │  Publishes OrderPlacedEvent to "notificationTopic"
    ▼
Kafka Broker (localhost:9092)
    │
    │  notification-service @KafkaListener
    ▼
notification-service
    │
    ├─ Log the event
    └─ (Extend: send email, SMS, push notification, etc.)
```

### Topic

| Topic | Producer | Consumer |
|---|---|---|
| `notificationTopic` | `order-service` | `notification-service` |

---

## Eureka Registration

Since `server.port=0` is used, the service boots on a random port. The `eureka.instance.instance-id` property ensures each instance gets a **unique Eureka ID** even if multiple instances are running on the same host:

```properties
eureka.instance.instance-id=${spring.application.name}:${random.value}
```

After startup, `NOTIFICATION-SERVICE` will appear in the Eureka dashboard at **http://localhost:8761**.

---

## Distributed Tracing

The notification-service participates in the distributed tracing pipeline:

- **`spring-boot-starter-actuator`** — Required to expose the observation/tracing hooks that Micrometer uses
- **`micrometer-tracing-bridge-brave`** — Bridges Micrometer's tracing API to Brave (the underlying tracing engine)
- **`zipkin-reporter-brave`** — Sends completed spans to the Zipkin server at `http://localhost:9411/api/v2/spans`

With `management.tracing.sampling.probability=1.0`, **every** Kafka message processed by this service will generate a trace entry visible in the Zipkin UI.

---

## Starting the Service

```bash
cd notification-service
./mvnw spring-boot:run
```

> **Note:** Start the Kafka broker and the Discovery Server before starting this service.

✅ Verify registration: Open **http://localhost:8761** — `NOTIFICATION-SERVICE` should appear in "Instances currently registered with Eureka".

---

## Common Issues

| Issue | Cause | Fix |
|---|---|---|
| Service not visible in Eureka | Eureka server not running | Start `discovery-server` first |
| `Connection refused` on Kafka | Kafka broker not running | Start Kafka on `localhost:9092` |
| `JsonDeserializer` error | Trusted packages not configured | Add `spring.kafka.consumer.properties.spring.json.trusted.packages=*` |
| No traces in Zipkin | Zipkin not running | Start Zipkin via Docker on port `9411` |
