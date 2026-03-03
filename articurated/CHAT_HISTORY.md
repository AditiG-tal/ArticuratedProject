# Chat History — AI-Assisted Design Journey

## Overview

This document chronicles the key design decisions made during the development of the ArtiCurated Order & Returns Management System, including how AI assistance was used to explore and evaluate alternatives.

---

## Phase 1: Architecture Brainstorming

**Prompt:** "I need to build an order + returns management system in Spring Boot. What are the key design patterns I should consider?"

**AI Suggestions & Evaluation:**

| Pattern | Considered | Decision |
|---------|-----------|----------|
| State Machine (explicit) | Yes | ✅ Chosen — clear validation, easy to extend |
| Spring State Machine library | Yes | ❌ Rejected — adds heavy dependency, overkill for this scope |
| Event Sourcing | Yes | ❌ Rejected — adds complexity; simple history table sufficient |
| CQRS | Yes | ❌ Rejected — single application scope doesn't justify it |

**Key Decision:** Use hand-rolled `OrderStateMachine` and `ReturnStateMachine` classes backed by `EnumMap<Status, EnumSet<Status>>`. This gives us a clear, inspectable transition table with zero library overhead.

---

## Phase 2: Asynchronous Job Processing

**Prompt:** "What's the best way to handle async jobs in Spring Boot — Hangfire-like, simple @Async, or a message queue?"

**AI Analysis:**

| Option | Pros | Cons | Decision |
|--------|------|------|---------|
| `@Async` + ThreadPool | Simple, no infra | No persistence, lost on restart | ❌ |
| Spring Scheduler | Simple | Not event-driven | ❌ |
| RabbitMQ | Durable, decoupled, scales, DLQ support | Requires broker infra | ✅ |
| Apache Kafka | High-throughput | Overkill for this use case | ❌ |

**Key Decision:** RabbitMQ with durable queues and Dead Letter Queues. Messages survive application restarts and failed jobs can be inspected/replayed.

---

## Phase 3: Audit Trail Design

**Prompt:** "What's the best way to implement audit logging for state changes?"

**AI Suggestions:**

1. **Envers / Hibernate Auditing** — full row-level versioning
2. **Custom history tables** — targeted, readable

**Decision:** Custom `OrderStatusHistory` and `ReturnStatusHistory` tables. These store only the relevant state change data (`fromStatus`, `toStatus`, `changedBy`, `notes`, timestamp) rather than full entity snapshots. This is lighter weight, easier to query, and directly answers "who changed what and when."

---

## Phase 4: PDF Generation Library

**Prompt:** "What Java library should I use to generate PDF invoices?"

**AI Comparison:**

| Library | License | Ease of Use | Decision |
|---------|---------|-------------|---------|
| iText 7 | AGPL/Commercial | Good API, rich features | ✅ Chosen |
| Apache PDFBox | Apache 2.0 | Lower-level | Consider for future |
| OpenPDF | LGPL | iText fork | Alternative |

**Decision:** iText 7 — mature, well-documented, good layout API.

---

## Phase 5: Testing Strategy

**Prompt:** "How should I structure tests to avoid needing real RabbitMQ and PostgreSQL in CI?"

**AI Recommendation:**
- Use H2 in-memory DB with `spring.jpa.hibernate.ddl-auto=create-drop` for integration tests
- Mock `RabbitTemplate`, `PdfInvoiceService`, and `MockPaymentGatewayService` with a `@TestConfiguration` class
- Unit test state machines independently (no Spring context needed)
- JaCoCo for coverage reporting

**Decision:** Implemented exactly as recommended. All tests pass without any external services.

---

## Phase 6: Error Handling

**Prompt:** "What HTTP status codes and error format should I use for state machine violations vs business rules vs not-found?"

**AI Recommendation:**

| Exception | HTTP Status | Rationale |
|-----------|------------|-----------|
| `ResourceNotFoundException` | 404 | Standard REST |
| `InvalidStateTransitionException` | 409 Conflict | State conflict |
| `BusinessRuleException` | 422 Unprocessable | Valid request, rule violation |
| Validation errors | 400 Bad Request | Input validation |

**Decision:** Adopted all recommendations. Implemented in `GlobalExceptionHandler`.

---

## Summary of AI Usage

- Used AI as a **thinking partner** to enumerate and evaluate trade-offs for each major architectural decision
- AI was asked to generate code snippets which were then **critically reviewed and adapted**
- All key design decisions were made by weighing AI-provided options against project constraints
- Tests and edge cases were partly AI-suggested, then extended manually
