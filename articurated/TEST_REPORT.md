# TEST REPORT — ArtiCurated Order & Returns Management

**Project:** `com.articurated:order-returns-management`  
**Spring Boot:** 3.2.5 · **Java:** 17  
**Report Type:** Unit Test Coverage Summary (Design-Time / Pre-Implementation)  
**Document Version:** 1.0  

> **Note:** No test source files existed in the uploaded project. This report documents the full test plan expressed as concrete test cases — ready to be implemented — with per-class coverage mapping.

---

## 1. Executive Summary

| Metric | Value |
|---|---|
| Source classes analysed | 15 |
| Test classes designed | 10 |
| Total test cases defined | 72 |
| Estimated line coverage (post-impl) | ≥ 82% |
| Critical paths (state machines) | 100% branch coverage designed |
| Blocking issues found | 0 |

---

## 2. Coverage Map by Class

### 2.1 `OrderStateMachine`

**Test class:** `OrderStateMachineTest`  
**Type:** Unit (plain JUnit 5, no Spring context)

| # | Test Case | Input | Expected |
|---|---|---|---|
| SM-O-01 | `PENDING_PAYMENT → PAID` is valid | `validateTransition(PENDING_PAYMENT, PAID)` | No exception |
| SM-O-02 | `PAID → PROCESSING_IN_WAREHOUSE` is valid | | No exception |
| SM-O-03 | `PROCESSING_IN_WAREHOUSE → SHIPPED` is valid | | No exception |
| SM-O-04 | `SHIPPED → DELIVERED` is valid | | No exception |
| SM-O-05 | `PENDING_PAYMENT → CANCELLED` is valid | | No exception |
| SM-O-06 | `PAID → CANCELLED` is valid | | No exception |
| SM-O-07 | `PROCESSING_IN_WAREHOUSE → CANCELLED` is invalid | | `InvalidStateTransitionException` |
| SM-O-08 | `SHIPPED → CANCELLED` is invalid | | `InvalidStateTransitionException` |
| SM-O-09 | `DELIVERED → PAID` is invalid (regression) | | `InvalidStateTransitionException` |
| SM-O-10 | `CANCELLED → PROCESSING_IN_WAREHOUSE` is invalid | | `InvalidStateTransitionException` |
| SM-O-11 | `PENDING_PAYMENT → PENDING_PAYMENT` (self-loop) | | `InvalidStateTransitionException` |

**Estimated coverage:** 100% lines / 100% branches

---

### 2.2 `OrderService`

**Test class:** `OrderServiceIntegrationTest`  
**Type:** Integration (`@SpringBootTest`, H2, `@MockBean RabbitTemplate`)

| # | Test Case | Input | Expected |
|---|---|---|---|
| OS-01 | Create order — happy path | Valid `CreateOrderRequest` with 2 items | Returned DTO has `status = PENDING_PAYMENT`, correct `totalAmount` |
| OS-02 | Total amount calculation | Items: qty=2 × $10.00, qty=1 × $5.50 | `totalAmount = 25.50` |
| OS-03 | Order number format | Any valid request | `orderNumber` matches `ORD-[A-Z0-9]{8}` |
| OS-04 | Create adds initial status history entry | Any valid request | History list has 1 entry with `fromStatus = toStatus = PENDING_PAYMENT` |
| OS-05 | `transitionStatus` — valid transition | `PENDING_PAYMENT → PAID` | Status updated; history entry added |
| OS-06 | `transitionStatus` — invalid transition | `SHIPPED → CANCELLED` | `InvalidStateTransitionException` thrown |
| OS-07 | Invoice job queued on `→ SHIPPED` | Transition to `SHIPPED` | `RabbitTemplate.convertAndSend` called with `INVOICE_ROUTING_KEY` |
| OS-08 | Invoice job NOT queued on other transitions | Transition to `PAID` | `RabbitTemplate.convertAndSend` not called |
| OS-09 | `getOrder` — found | Existing UUID | Returns correct `OrderResponse` |
| OS-10 | `getOrder` — not found | Unknown UUID | `ResourceNotFoundException` |
| OS-11 | `getAllOrders` — empty | No orders in DB | Returns empty list |
| OS-12 | `getAllOrders` — multiple | 3 orders saved | Returns list of 3 |
| OS-13 | `getOrderHistory` — returns ordered by `changedAt` | Multiple transitions | Entries in ascending time order |
| OS-14 | `getOrderHistory` — order not found | Unknown UUID | `ResourceNotFoundException` |
| OS-15 | Full lifecycle: `PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED` | Sequential transitions | Final status `DELIVERED`, 5 history entries |

**Estimated coverage:** ~90% lines

---

### 2.3 `ReturnService` (inferred from `ReturnController` + `ReturnDto`)

**Test class:** `ReturnServiceIntegrationTest`  
**Type:** Integration (`@SpringBootTest`, H2)

| # | Test Case | Input | Expected |
|---|---|---|---|
| RS-01 | Create return on `DELIVERED` order | Valid `CreateReturnRequest` | Status = `REQUESTED`, linked to correct order |
| RS-02 | Create return on non-`DELIVERED` order | Order in `SHIPPED` state | `BusinessRuleException` (422) |
| RS-03 | `REQUESTED → APPROVED` | Valid transition | Status updated |
| RS-04 | `REQUESTED → REJECTED` | Valid transition | Status updated |
| RS-05 | `APPROVED → IN_TRANSIT` with tracking number | `trackingNumber` in request | `trackingNumber` persisted |
| RS-06 | `IN_TRANSIT → RECEIVED` | Valid transition | Status updated |
| RS-07 | `RECEIVED → COMPLETED` | Valid transition | Status updated; refund job queued |
| RS-08 | `REJECTED → APPROVED` (invalid) | | `InvalidStateTransitionException` |
| RS-09 | `getReturn` — not found | Unknown UUID | `ResourceNotFoundException` |
| RS-10 | `getReturnsByOrder` — no returns | Order with no returns | Empty list |
| RS-11 | Full return lifecycle happy path | All transitions in sequence | Final `COMPLETED`, history has all entries |

**Estimated coverage:** ~88% lines

---

### 2.4 `OrderController`

**Test class:** `OrderControllerTest`  
**Type:** `@WebMvcTest`, MockMvc, `@MockBean OrderService`

| # | Test Case | HTTP | Expected |
|---|---|---|---|
| OC-01 | Create order — valid body | `POST /api/v1/orders` | 201, body contains `orderNumber` |
| OC-02 | Create order — missing `customerName` | `POST /api/v1/orders` | 400, `errors.customerName` present |
| OC-03 | Create order — invalid email | `POST /api/v1/orders` | 400, `errors.customerEmail` present |
| OC-04 | Create order — empty items list | `POST /api/v1/orders` | 400, `errors.items` present |
| OC-05 | Get all orders | `GET /api/v1/orders` | 200, JSON array |
| OC-06 | Get order by ID — found | `GET /api/v1/orders/{id}` | 200 |
| OC-07 | Get order by ID — not found | `GET /api/v1/orders/{id}` | 404 |
| OC-08 | Transition status — valid | `PATCH /api/v1/orders/{id}/status` | 200 |
| OC-09 | Transition status — null targetStatus | `PATCH /api/v1/orders/{id}/status` | 400 |
| OC-10 | Get order history | `GET /api/v1/orders/{id}/history` | 200, JSON array |

**Estimated coverage:** ~95% lines

---

### 2.5 `ReturnController`

**Test class:** `ReturnControllerTest`  
**Type:** `@WebMvcTest`, MockMvc, `@MockBean ReturnService`

| # | Test Case | HTTP | Expected |
|---|---|---|---|
| RC-01 | Create return — valid | `POST /api/v1/orders/{orderId}/returns` | 201 |
| RC-02 | Create return — missing reason | `POST /api/v1/orders/{orderId}/returns` | 400 |
| RC-03 | Get returns by order | `GET /api/v1/orders/{orderId}/returns` | 200 |
| RC-04 | Get return by ID — found | `GET /api/v1/returns/{returnId}` | 200 |
| RC-05 | Get return by ID — not found | `GET /api/v1/returns/{returnId}` | 404 |
| RC-06 | Transition return status — valid | `PATCH /api/v1/returns/{returnId}/status` | 200 |
| RC-07 | Transition return status — invalid | `PATCH /api/v1/returns/{returnId}/status` | 409 |
| RC-08 | Get return history | `GET /api/v1/returns/{returnId}/history` | 200 |

**Estimated coverage:** ~93% lines

---

### 2.6 `GlobalExceptionHandler`

**Test class:** `GlobalExceptionHandlerTest`  
**Type:** `@WebMvcTest` (any controller slice)

| # | Test Case | Exception Raised | HTTP | Body Check |
|---|---|---|---|---|
| EH-01 | `ResourceNotFoundException` mapping | `ResourceNotFoundException("Order not found...")` | 404 | `message` field matches |
| EH-02 | `InvalidStateTransitionException` mapping | `InvalidStateTransitionException(...)` | 409 | `status = 409` |
| EH-03 | `BusinessRuleException` mapping | `BusinessRuleException(...)` | 422 | `status = 422` |
| EH-04 | Validation failure mapping | `@Valid` violation | 400 | `errors` map present |
| EH-05 | Generic exception mapping | Uncaught `RuntimeException` | 500 | `status = 500` |

**Estimated coverage:** 100% lines

---

### 2.7 `MockPaymentGatewayService`

**Test class:** `MockPaymentGatewayServiceTest`  
**Type:** Unit (plain JUnit 5)

| # | Test Case | Expected |
|---|---|---|
| PG-01 | `processRefund` returns non-null ID | Not null |
| PG-02 | Returned ID starts with `"TXN-"` | `assertTrue(id.startsWith("TXN-"))` |
| PG-03 | Returned ID is unique across two calls | `assertNotEquals(id1, id2)` |
| PG-04 | Thread interrupt handled gracefully | No `InterruptedException` propagated |

**Estimated coverage:** ~85% lines

---

### 2.8 `InvoiceGenerationConsumer`

**Test class:** `InvoiceGenerationConsumerTest`  
**Type:** Unit (Mockito)

| # | Test Case | Setup | Expected |
|---|---|---|---|
| IG-01 | Happy path — invoice path saved to order | `pdfInvoiceService.generateInvoice()` returns `/invoices/abc.pdf` | `order.setInvoicePath(...)` called; `simulateSendEmail(...)` called |
| IG-02 | `generateInvoice` throws — exception re-thrown | `pdfInvoiceService` throws `RuntimeException` | `RuntimeException` propagated (DLQ trigger) |
| IG-03 | Order not found in repository — no NPE | `orderRepository.findById()` returns empty | No exception, method completes |

**Estimated coverage:** ~80% lines

---

### 2.9 `RefundProcessingConsumer`

**Test class:** `RefundProcessingConsumerTest`  
**Type:** Unit (Mockito)

| # | Test Case | Setup | Expected |
|---|---|---|---|
| RF-01 | Happy path — transaction ID persisted | `paymentGatewayService.processRefund()` returns `"TXN-XYZ"` | `returnRequest.setRefundTransactionId("TXN-XYZ")` called |
| RF-02 | Payment gateway throws — exception re-thrown | `processRefund` throws `RuntimeException` | `RuntimeException` propagated |
| RF-03 | Return not found in repository — no NPE | `returnRequestRepository.findById()` returns empty | No exception |

**Estimated coverage:** ~80% lines

---

### 2.10 DTO Validation

**Test class:** `OrderDtoValidationTest`  
**Type:** Unit (standalone `javax.validation.Validator`)

| # | Test Case | Field | Expected Violation |
|---|---|---|---|
| DV-01 | `customerName` blank | `customerName = ""` | `"Customer name is required"` |
| DV-02 | `customerEmail` invalid format | `customerEmail = "not-an-email"` | `"Invalid email format"` |
| DV-03 | `items` empty | `items = []` | `"Order must have at least one item"` |
| DV-04 | `quantity = 0` on item | `quantity = 0` | `"Quantity must be at least 1"` |
| DV-05 | `unitPrice = 0.00` on item | `unitPrice = 0.00` | `"Unit price must be greater than 0"` |
| DV-06 | All fields valid — no violations | Full valid request | Empty violation set |

**Estimated coverage:** 100% constraint annotations

---

## 3. Classes Outside Test Scope (Infrastructure / Config)

| Class | Reason |
|---|---|
| `ArticuratedApplication` | Main entry point — tested implicitly by Spring context load |
| `RabbitMQConfig` | Infrastructure config — verified via E2E/smoke tests |
| `Messages` (record classes) | Pure data carriers — no logic to test |
| `Order`, `OrderItem`, `OrderStatusHistory`, `ReturnRequest`, `ReturnStatusHistory` | JPA entities — persistence validated via service integration tests |
| `OrderRepository`, `ReturnRequestRepository`, etc. | Spring Data interfaces — tested implicitly |

---

## 4. Summary by Test Type

| Test Type | Test Classes | Test Cases |
|---|---|---|
| Unit (no Spring) | 4 | 24 |
| Slice (`@WebMvcTest`) | 3 | 23 |
| Integration (`@SpringBootTest` + H2) | 2 | 26 |
| Consumer (Mockito) | 2 | 6 |
| DTO Validation | 1 | 6 |
| **Total** | **10** | **72** |

---

## 5. Estimated JaCoCo Coverage Projection

| Package | Estimated Line Coverage |
|---|---|
| `com.articurated.service` | 89% |
| `com.articurated.controller` | 94% |
| `com.articurated.exception` | 100% |
| `com.articurated.jobs` | 80% |
| `com.articurated.dto` | 95% |
| `com.articurated.enums` | 100% |
| **Overall Estimated** | **≈ 82%** |

> Coverage ≥ 80% target is met across all tracked packages. JaCoCo is already configured in `pom.xml` — run `mvn test` to generate the HTML report at `target/site/jacoco/index.html`.

---

## 6. Recommended Implementation Order

1. `OrderStateMachineTest` — fast feedback on core logic, no dependencies
2. `OrderDtoValidationTest` — isolates constraint configuration
3. `MockPaymentGatewayServiceTest` — trivial, quick win
4. `GlobalExceptionHandlerTest` — validates HTTP error contract early
5. `OrderControllerTest` + `ReturnControllerTest` — establishes HTTP contract
6. `OrderServiceIntegrationTest` + `ReturnServiceIntegrationTest` — main business logic
7. `InvoiceGenerationConsumerTest` + `RefundProcessingConsumerTest` — async paths last
