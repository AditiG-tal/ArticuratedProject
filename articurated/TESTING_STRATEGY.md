# TESTING STRATEGY — ArtiCurated Order & Returns Management

**Project:** `com.articurated:order-returns-management`  
**Stack:** Spring Boot 3.2.5 · Java 17 · PostgreSQL · RabbitMQ · iText7  
**Document Version:** 1.0  

---

## 1. Objectives

| # | Objective |
|---|-----------|
| 1 | Verify all valid Order and Return state-machine transitions are accepted |
| 2 | Verify all invalid transitions are rejected with HTTP 409 |
| 3 | Confirm business rules (e.g. return only allowed on DELIVERED orders) are enforced |
| 4 | Validate input constraints produce HTTP 400 with meaningful field errors |
| 5 | Ensure 404 is returned for unknown resource IDs |
| 6 | Confirm asynchronous jobs (invoice generation, refund processing) are triggered correctly |
| 7 | Achieve ≥ 80% line coverage across service and controller layers |

---

## 2. Scope

### In Scope
- `OrderService` — create, transition, get, list, history
- `OrderStateMachine` — all valid and invalid transitions
- `ReturnService` — create, transition, get, list, history
- `ReturnStateMachine` (inferred) — all valid and invalid transitions
- `OrderController` — REST endpoint routing and HTTP status codes
- `ReturnController` — REST endpoint routing and HTTP status codes
- `GlobalExceptionHandler` — mapping of exceptions to HTTP responses
- `MockPaymentGatewayService` — refund simulation
- `InvoiceGenerationConsumer` / `RefundProcessingConsumer` — message listener logic
- DTO validation constraints (`@NotBlank`, `@Email`, `@Min`, `@DecimalMin`, etc.)

### Out of Scope
- PostgreSQL DDL / schema migration scripts
- RabbitMQ broker configuration (infrastructure)
- iText7 PDF rendering output fidelity
- Authentication / Authorization (not yet implemented)

---

## 3. Test Levels

### 3.1 Unit Tests

Target classes that contain pure business logic with no Spring context.

| Class | What to Test |
|---|---|
| `OrderStateMachine` | Every allowed transition returns without exception; every forbidden transition throws `InvalidStateTransitionException` |
| `MockPaymentGatewayService` | Returns a non-null, `TXN-`-prefixed transaction ID; handles interrupt correctly |
| `OrderService.mapToResponse()` | Field-by-field mapping from `Order` entity to `OrderDto.OrderResponse` |
| DTO validation annotations | `@Valid` constraints on `CreateOrderRequest` and `CreateReturnRequest` using `javax.validation` validator directly |

**Tools:** JUnit 5, Mockito, `javax.validation.Validator` (standalone)

---

### 3.2 Integration / Slice Tests

#### 3.2.1 Service Layer — `@SpringBootTest` with H2

Spin up the Spring context using the H2 in-memory database (already in `pom.xml` at `test` scope). Mock `RabbitTemplate` to avoid requiring a broker.

| Test Class | Scenarios |
|---|---|
| `OrderServiceIntegrationTest` | Create order → assert `PENDING_PAYMENT`; full happy-path chain through all statuses; cancel from `PENDING_PAYMENT`; cancel from `PAID`; attempt cancel from `SHIPPED` → expect 409; total-amount calculation with multiple items |
| `ReturnServiceIntegrationTest` | Create return on `DELIVERED` order → `REQUESTED`; full happy-path through `APPROVED → IN_TRANSIT → RECEIVED → COMPLETED`; reject a return `REQUESTED → REJECTED`; attempt return on non-`DELIVERED` order → expect `BusinessRuleException`; duplicate active return handling |

#### 3.2.2 Controller Layer — `@WebMvcTest`

Use `MockMvc` to test HTTP layer. Mock all service beans.

| Test Class | Scenarios |
|---|---|
| `OrderControllerTest` | `POST /api/v1/orders` → 201; `POST` with missing fields → 400 with field errors; `GET /api/v1/orders/{id}` with valid UUID → 200; with unknown UUID → 404; `PATCH /api/v1/orders/{id}/status` → 200; `GET /api/v1/orders/{id}/history` → 200 |
| `ReturnControllerTest` | `POST /api/v1/orders/{orderId}/returns` → 201; `GET /api/v1/orders/{orderId}/returns` → 200; `GET /api/v1/returns/{returnId}` → 200 / 404; `PATCH /api/v1/returns/{returnId}/status` → 200 / 409; `GET /api/v1/returns/{returnId}/history` → 200 |

#### 3.2.3 Exception Handler — `@WebMvcTest` + `GlobalExceptionHandlerTest`

| Exception | Expected HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `InvalidStateTransitionException` | 409 |
| `BusinessRuleException` | 422 |
| `MethodArgumentNotValidException` | 400 with `errors` map in body |
| Generic `Exception` | 500 |

#### 3.2.4 Message Consumer Tests

Mock `PdfInvoiceService` / `MockPaymentGatewayService` / repositories and directly invoke consumer methods (no broker required).

| Test Class | Scenarios |
|---|---|
| `InvoiceGenerationConsumerTest` | Happy path — `invoicePath` set on order; service throws exception → re-thrown (DLQ trigger); order not found in repository → no NPE |
| `RefundProcessingConsumerTest` | Happy path — `refundTransactionId` set on return; payment service throws → re-thrown; return not found → no NPE |

---

### 3.3 End-to-End / Smoke Tests (Optional, CI-gated)

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate` and Testcontainers (PostgreSQL + RabbitMQ). These are appropriate for a staging pipeline and should be tagged `@Tag("e2e")` so they can be excluded from the standard unit-test run.

**Flows to cover:**
1. Full order lifecycle: `PENDING_PAYMENT → PAID → PROCESSING_IN_WAREHOUSE → SHIPPED → DELIVERED`
2. Full return lifecycle on a DELIVERED order: `REQUESTED → APPROVED → IN_TRANSIT → RECEIVED → COMPLETED`
3. Invoice generation message appears on the queue after `→ SHIPPED` transition

---

## 4. State Transition Test Matrix

### 4.1 Order State Machine

| From → To | Expected Outcome |
|---|---|
| `PENDING_PAYMENT → PAID` | ✅ 200 OK |
| `PAID → PROCESSING_IN_WAREHOUSE` | ✅ 200 OK |
| `PROCESSING_IN_WAREHOUSE → SHIPPED` | ✅ 200 OK + invoice job queued |
| `SHIPPED → DELIVERED` | ✅ 200 OK |
| `PENDING_PAYMENT → CANCELLED` | ✅ 200 OK |
| `PAID → CANCELLED` | ✅ 200 OK |
| `PROCESSING_IN_WAREHOUSE → CANCELLED` | ❌ 409 Conflict |
| `SHIPPED → CANCELLED` | ❌ 409 Conflict |
| `DELIVERED → PAID` | ❌ 409 Conflict |
| `CANCELLED → PAID` | ❌ 409 Conflict |
| Any status → same status | ❌ 409 Conflict |

### 4.2 Return State Machine

| From → To | Expected Outcome |
|---|---|
| `REQUESTED → APPROVED` | ✅ 200 OK |
| `REQUESTED → REJECTED` | ✅ 200 OK |
| `APPROVED → IN_TRANSIT` | ✅ 200 OK (trackingNumber required) |
| `IN_TRANSIT → RECEIVED` | ✅ 200 OK |
| `RECEIVED → COMPLETED` | ✅ 200 OK + refund job queued |
| `REQUESTED → IN_TRANSIT` | ❌ 409 Conflict |
| `REJECTED → APPROVED` | ❌ 409 Conflict |
| `COMPLETED → REQUESTED` | ❌ 409 Conflict |

---

## 5. Negative / Edge Case Scenarios

| Scenario | Expected |
|---|---|
| Create order with empty `items` list | 400 — `"Order must have at least one item"` |
| Create order with invalid email | 400 — `"Invalid email format"` |
| Create order with `quantity = 0` | 400 — `"Quantity must be at least 1"` |
| Create order with `unitPrice = 0.00` | 400 — `"Unit price must be greater than 0"` |
| Get order with malformed UUID in path | 400 |
| Get order with valid but non-existent UUID | 404 |
| Create return on order in `PROCESSING_IN_WAREHOUSE` state | 422 BusinessRuleException |
| `PATCH /status` with null `targetStatus` | 400 |
| Total amount calculation: 3 items × mixed quantities and prices | Exact BigDecimal sum |

---

## 6. Test Data Strategy

- **Builder pattern** — Use Lombok `@Builder` entities directly in tests; avoid loading SQL fixtures
- **UUID generation** — Use `UUID.randomUUID()` in setup; never hardcode UUIDs
- **BigDecimal precision** — Always assert with `compareTo()` not `equals()` for monetary values
- **RabbitTemplate** — Mock with `@MockBean`; verify `convertAndSend()` is called with correct routing key
- **Time assertions** — Use `assertThat(response.getCreatedAt()).isNotNull()` rather than exact timestamp equality

---

## 7. Toolchain

| Tool | Purpose |
|---|---|
| JUnit 5 | Test runner |
| Mockito | Mocking / stubbing |
| MockMvc | HTTP layer testing (`@WebMvcTest`) |
| AssertJ | Fluent assertions |
| H2 (in-memory) | Database for integration tests (already in `pom.xml`) |
| JaCoCo 0.8.11 | Code coverage reporting (already in `pom.xml`) |
| Testcontainers *(optional)* | Real PostgreSQL + RabbitMQ for E2E |

---

## 8. Coverage Targets

| Layer | Target |
|---|---|
| Service (`OrderService`, `ReturnService`) | ≥ 85% |
| State Machines | 100% (all branches enumerated) |
| Controllers | ≥ 80% |
| Exception Handler | 100% (5 handlers, each testable) |
| Message Consumers | ≥ 75% |
| **Overall** | **≥ 80%** |

---

## 9. Test File Naming & Location Convention

```
src/test/java/com/articurated/
├── service/
│   ├── OrderServiceIntegrationTest.java
│   ├── OrderStateMachineTest.java
│   ├── ReturnServiceIntegrationTest.java
│   └── MockPaymentGatewayServiceTest.java
├── controller/
│   ├── OrderControllerTest.java
│   └── ReturnControllerTest.java
├── exception/
│   └── GlobalExceptionHandlerTest.java
├── jobs/
│   ├── InvoiceGenerationConsumerTest.java
│   └── RefundProcessingConsumerTest.java
└── dto/
    └── OrderDtoValidationTest.java
```

---

## 10. CI / CD Integration

1. Run `mvn test` on every pull request — unit + slice tests only
2. JaCoCo report published to CI artefacts (`target/site/jacoco/index.html`)
3. Build fails if overall coverage drops below **80%** (enforce via JaCoCo `<rule>` in `pom.xml`)
4. E2E tests tagged `@Tag("e2e")` run only on merge to `main` with `mvn test -Pintegration`
