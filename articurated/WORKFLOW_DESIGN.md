# Workflow Design

## 1. Order State Machine

```
                        +------------------+
                        |  PENDING_PAYMENT |
                        +--------+---------+
                                 |
               +-----------------+------------------+
               |                                    |
               v                                    v
          +--------+                          +-----------+
          |  PAID  |                          | CANCELLED |
          +---+----+                          +-----------+
              |
              v
 +----------------------------+
 | PROCESSING_IN_WAREHOUSE    |
 +-------------+--------------+
               |
               v
          +--------+          [Background Job: PDF Invoice Generation
          | SHIPPED | -------> queued to RabbitMQ -> iText7 -> simulate email]
          +---+----+
              |
              v
         +-----------+
         | DELIVERED |
         +-----------+
```

**Valid Transitions:**
| From | To |
|------|----|
| PENDING_PAYMENT | PAID |
| PENDING_PAYMENT | CANCELLED |
| PAID | PROCESSING_IN_WAREHOUSE |
| PAID | CANCELLED |
| PROCESSING_IN_WAREHOUSE | SHIPPED |
| SHIPPED | DELIVERED |

Terminal states (no transitions out): `DELIVERED`, `CANCELLED`

---

## 2. Return State Machine

```
  [Precondition: Order must be in DELIVERED state]

      +-----------+
      | REQUESTED |
      +-----+-----+
            |
      +-----+------+
      |            |
      v            v
  +--------+  +----------+
  |APPROVED|  | REJECTED |  <- Terminal
  +----+---+  +----------+
       |
       v
  +------------+
  | IN_TRANSIT | (customer ships item back, provides tracking number)
  +------+-----+
         |
         v
  +----------+
  | RECEIVED | (warehouse confirms item received)
  +----+-----+
       |
       v
  +-----------+          [Background Job: Refund Processing
  | COMPLETED | -------> queued to RabbitMQ -> MockPaymentGateway API call]
  +-----------+          <- Terminal
```

**Valid Transitions:**
| From | To |
|------|----|
| REQUESTED | APPROVED |
| REQUESTED | REJECTED |
| APPROVED | IN_TRANSIT |
| IN_TRANSIT | RECEIVED |
| RECEIVED | COMPLETED |

Terminal states: `REJECTED`, `COMPLETED`

---

## 3. Database Schema

```
orders
-------
id              UUID  PK
order_number    VARCHAR UNIQUE
customer_name   VARCHAR
customer_email  VARCHAR
total_amount    DECIMAL(12,2)
status          VARCHAR (enum: OrderStatus)
invoice_path    VARCHAR NULL
created_at      TIMESTAMP
updated_at      TIMESTAMP

order_items
-----------
id              UUID  PK
order_id        UUID  FK -> orders.id
product_name    VARCHAR
product_sku     VARCHAR
quantity        INT
unit_price      DECIMAL(12,2)

order_status_history      [AUDIT LOG]
--------------------
id              UUID  PK
order_id        UUID  FK -> orders.id
from_status     VARCHAR
to_status       VARCHAR
notes           VARCHAR
changed_by      VARCHAR
changed_at      TIMESTAMP

return_requests
---------------
id                    UUID  PK
order_id              UUID  FK -> orders.id
status                VARCHAR (enum: ReturnStatus)
reason                TEXT
review_notes          VARCHAR NULL
tracking_number       VARCHAR NULL
refund_transaction_id VARCHAR NULL
created_at            TIMESTAMP
updated_at            TIMESTAMP

return_status_history     [AUDIT LOG]
---------------------
id                  UUID  PK
return_request_id   UUID  FK -> return_requests.id
from_status         VARCHAR
to_status           VARCHAR
notes               VARCHAR
changed_by          VARCHAR
changed_at          TIMESTAMP
```

### Relationships
- `orders` 1 ——< `order_items` (cascade delete)
- `orders` 1 ——< `order_status_history` (immutable audit log)
- `orders` 1 ——< `return_requests`
- `return_requests` 1 ——< `return_status_history` (immutable audit log)

---

## 4. Async Background Jobs (RabbitMQ)

```
Order SHIPPED
     |
     v
RabbitTemplate.convertAndSend(order.exchange, order.invoice.generate, InvoiceGenerationMessage)
     |
     v
[invoice.generation.queue]  ——(on failure)——> [invoice.generation.dlq]
     |
     v
InvoiceGenerationConsumer
     ├── PdfInvoiceService.generateInvoice()  -> /tmp/invoices/invoice_ORD-xxx.pdf
     ├── orderRepository.setInvoicePath()
     └── PdfInvoiceService.simulateSendEmail()


Return COMPLETED
     |
     v
RabbitTemplate.convertAndSend(return.exchange, return.refund.process, RefundProcessingMessage)
     |
     v
[refund.processing.queue]  ——(on failure)——> [refund.processing.dlq]
     |
     v
RefundProcessingConsumer
     ├── MockPaymentGatewayService.processRefund()  -> "TXN-XXXXXXXX"
     └── returnRequestRepository.setRefundTransactionId()
```
