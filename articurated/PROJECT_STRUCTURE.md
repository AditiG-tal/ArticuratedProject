# Project Structure

```
articurated/
├── src/
│   ├── main/
│   │   ├── java/com/articurated/
│   │   │   ├── ArticuratedApplication.java      # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   └── RabbitMQConfig.java          # Exchanges, queues, bindings, DLQs
│   │   │   ├── controller/
│   │   │   │   ├── OrderController.java         # REST endpoints for orders
│   │   │   │   └── ReturnController.java        # REST endpoints for returns
│   │   │   ├── dto/
│   │   │   │   ├── OrderDto.java                # Request/Response DTOs for orders
│   │   │   │   └── ReturnDto.java               # Request/Response DTOs for returns
│   │   │   ├── entity/
│   │   │   │   ├── Order.java                   # Orders table
│   │   │   │   ├── OrderItem.java               # Line items table
│   │   │   │   ├── OrderStatusHistory.java      # Order audit log table
│   │   │   │   ├── ReturnRequest.java           # Returns table
│   │   │   │   └── ReturnStatusHistory.java     # Return audit log table
│   │   │   ├── enums/
│   │   │   │   ├── OrderStatus.java             # PENDING_PAYMENT, PAID, ...
│   │   │   │   └── ReturnStatus.java            # REQUESTED, APPROVED, ...
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java  # Maps exceptions to HTTP responses
│   │   │   │   ├── BusinessRuleException.java   # 422 — business rule violation
│   │   │   │   ├── InvalidStateTransitionException.java  # 409 — bad state change
│   │   │   │   └── ResourceNotFoundException.java        # 404 — entity not found
│   │   │   ├── jobs/
│   │   │   │   ├── Messages.java                # POJO message types for queues
│   │   │   │   ├── InvoiceGenerationConsumer.java  # Listens, generates PDF + emails
│   │   │   │   └── RefundProcessingConsumer.java   # Listens, calls payment gateway
│   │   │   ├── repository/
│   │   │   │   ├── OrderRepository.java
│   │   │   │   ├── OrderStatusHistoryRepository.java
│   │   │   │   ├── ReturnRequestRepository.java
│   │   │   │   └── ReturnStatusHistoryRepository.java
│   │   │   └── service/
│   │   │       ├── OrderService.java            # Core order business logic
│   │   │       ├── OrderStateMachine.java       # Validates order transitions
│   │   │       ├── ReturnService.java           # Core return business logic
│   │   │       ├── ReturnStateMachine.java      # Validates return transitions
│   │   │       ├── PdfInvoiceService.java       # iText7 PDF generation
│   │   │       └── MockPaymentGatewayService.java  # Simulates refund API call
│   │   └── resources/
│   │       └── application.properties           # All configuration with env var defaults
│   └── test/
│       ├── java/com/articurated/
│       │   ├── TestConfig.java                  # Mocks RabbitMQ/PDF/Gateway for tests
│       │   ├── OrderStateMachineTest.java        # Unit tests for order state machine
│       │   ├── ReturnStateMachineTest.java       # Unit tests for return state machine
│       │   ├── OrderServiceIntegrationTest.java  # Integration tests (H2 + mocks)
│       │   └── ReturnServiceIntegrationTest.java # Integration tests (H2 + mocks)
│       └── resources/
│           └── application-test.properties      # H2 config, disables RabbitMQ
├── docker-compose.yml                           # Starts postgres + rabbitmq + app
├── Dockerfile                                   # Multi-stage build
├── pom.xml                                      # Dependencies + JaCoCo plugin
├── README.md
├── PROJECT_STRUCTURE.md                         # This file
├── WORKFLOW_DESIGN.md
├── API-SPECIFICATION.yml
└── CHAT_HISTORY.md
```

## Key Module Responsibilities

### State Machines (`OrderStateMachine`, `ReturnStateMachine`)
Pure business logic components with no persistence dependencies. They hold a static map of `EnumSet` transitions and throw `InvalidStateTransitionException` on illegal moves.

### Services (`OrderService`, `ReturnService`)
Orchestrate persistence, state machine validation, history logging, and publishing async messages. Each method is `@Transactional`.

### RabbitMQ Consumers (`InvoiceGenerationConsumer`, `RefundProcessingConsumer`)
Listen on durable queues. On failure, Spring AMQP routes messages to the Dead Letter Queue automatically.

### Controllers
Thin layer — only maps HTTP requests to service calls and returns appropriate HTTP status codes.
