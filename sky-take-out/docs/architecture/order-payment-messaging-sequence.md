# Order, payment, messaging, and notification sequences

Solid messages below describe current source behavior. Revision `13a1783` passed the Docker-backed component/integration tests for these paths. Notes marked **acceptance gap** distinguish those verified fragments from evidence that still does not span the complete flow.

## Order submission and timeout

```mermaid
sequenceDiagram
    actor Client
    participant Order as OrderServiceImpl
    participant Inventory as InventoryService
    participant DB as MySQL
    participant Broker as RabbitMQ
    participant Consumer as OrderEventConsumer
    Client->>Order: submit(order DTO)
    Order->>DB: insert order and details
    Order->>Inventory: conditionally deduct stock
    Order->>DB: clear cart
    Order->>Broker: publish delayed timeout directly
    Note over Order,Broker: Implemented inside local transaction; not transactional Outbox
    Order-->>Client: order result
    Broker-->>Consumer: timeout delivery
    Consumer->>DB: insert idempotency identity
    Consumer->>Order: cancelTimeoutOrder(order number)
    Consumer->>DB: mark consumption COMPLETED
```

The direct delayed-message path is implemented and its Docker-backed listener path passed the full verification suite at revision `13a1783`. Database completion plus broker publication are still not atomic. Moving timeout events to the Outbox is future scope, not current behavior.

## Verified payment and transactional Outbox

```mermaid
sequenceDiagram
    participant WeChat as WeChat Pay
    participant HTTP as PayNotifyController
    participant Payment as PaymentCallbackService
    participant Order as OrderApplicationService
    participant DB as MySQL
    participant Publisher as OutboxPublisher
    participant Broker as RabbitMQ
    WeChat->>HTTP: signed callback
    HTTP->>HTTP: verify signature and decrypt
    HTTP->>Payment: handle verified fields
    Payment->>DB: insert callback identity
    alt duplicate matching SUCCESS callback
        DB-->>Payment: duplicate key
        Payment-->>HTTP: DUPLICATE; no repeated transition
    else first accepted callback
        Payment->>Order: completeVerifiedPayment
        Order->>DB: conditional payment transition
        Payment->>DB: insert pending ORDER_PAID event
        Note over Payment,DB: Callback, order transition, and Outbox row share one transaction
        DB-->>Payment: commit
        Payment-->>HTTP: APPLIED
    end
    Publisher->>DB: lease due event
    Publisher->>Broker: persistent envelope
    Broker-->>Publisher: ACK, return, NACK, or timeout
    alt ACK and routable
        Publisher->>DB: mark SENT
    else publication failure
        Publisher->>DB: bounded retry or mark FAILED
    end
```

This mechanism is implemented. Docker-backed triple-delivery, process-gap, mandatory-return, NACK, and retry outcomes passed in the full verification suite at revision `13a1783`. **Acceptance gap:** no single retained record yet correlates one payment through every stage to its notification/log outcome (`AO-3`).

## Idempotent consumer and dead letter

```mermaid
sequenceDiagram
    participant Broker as Rabbit listener
    participant Consumer as OrderEventConsumer
    participant Idem as MessageConsumptionService
    participant DB as MySQL
    participant Action as Business action
    participant Retry as Retry interceptor
    participant DLQ as DeadLetterPublisher
    participant DLX as Dead-letter exchange
    Broker->>Consumer: event envelope
    Consumer->>Idem: consume(consumer, eventId, key, action)
    Idem->>DB: insert PROCESSING identity
    alt already COMPLETED with same key
        DB-->>Idem: duplicate identity
        Idem-->>Consumer: false; skip action
        Consumer-->>Broker: acknowledge
    else new identity
        Idem->>Action: execute once in transaction
        Idem->>DB: mark COMPLETED
        Consumer-->>Broker: acknowledge
    else processing failure
        Consumer-->>Retry: exception
        Retry->>Consumer: retry to configured limit
        Retry->>DLQ: recover exhausted message
        DLQ->>DLX: publish sanitized failure envelope
        DLX-->>DLQ: publisher confirmation
        DLQ->>DB: record FAILED in new transaction
    end
```

Persistence, retry, idempotent duplicate handling, configured retry count, and confirmed dead-letter outcomes passed their Docker-backed integration tests at revision `13a1783`. This remains fragment-level evidence for `AO-3`, not one searchable record spanning the full payment-to-notification chain.

## After-commit notification

```mermaid
sequenceDiagram
    participant Consumer as OrderEventConsumer
    participant Tx as MessageConsumptionService transaction
    participant Adapter as WebSocketOrderNotificationAdapter
    participant DB as MySQL
    participant Sessions as WebSocketSessionRegistry
    Consumer->>Tx: consume order-paid-notify
    Tx->>Adapter: broadcast(message)
    Adapter->>Adapter: register afterCommit callback
    Tx->>DB: mark consumption COMPLETED
    DB-->>Tx: commit
    Tx-->>Adapter: afterCommit
    Adapter->>Sessions: best-effort broadcast
    alt WebSocket throws
        Adapter->>Adapter: log warning; keep committed fact
    end
```

With no active transaction synchronization, the adapter sends immediately with the same best-effort failure handling. Rollback does not trigger a registered send. The boundary unit test passes, but coverage gap `AO-3` still lacks one accepted searchable payment → Outbox → consumer → notification correlation record.

## Future or blocked flows

- Order timeout has not been migrated to the transactional Outbox.
- Durable offline WebSocket replay is not implemented.
- The combined searchable correlation record remains open even though the component-level MySQL/RabbitMQ tests pass.
- Remote GitHub Actions/security-scan evidence and identical-input performance repeatability remain open.
- Task 17 has no accepted optimization candidate or optimized comparison. See [spec coverage](../verification/spec-coverage.md) and [final acceptance](../verification/final-acceptance.md).
