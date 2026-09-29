# Task 18: DLQ header allowlist

## Scope

`DeadLetterPublisher.publish` now builds the dead-letter message from `original.getBody()` and sets the generated failure headers plus the existing sanitized envelope identity allowlist. It no longer carries inbound message headers or properties into the durable DLQ message.

The integration regression sends `Authorization`, `token`, and `internalDebug` headers. It verifies the body is unchanged, the non-`spring_` application header names are exactly `eventId`, `correlationId`, `aggregateId`, `payloadVersion`, `eventType`, `failureReason`, `consumerName`, and `deliveryAttempts`, and all three unapproved input headers are absent. The consumer name assertion accepts either `order-paid-notify` or `order-paid-audit`, since the routing key fans out to both queues.

Spring AMQP adds `spring_` return-correlation headers during mandatory publishing. These are framework runtime headers, so the application-header assertion filters that prefix while still checking for the sensitive and custom inputs explicitly.

## RED

Before the production change, the focused selector exited 1 with 1 test failure. The exact-header assertion found copied `Authorization`, `token`, and `internalDebug` headers in the DLQ, along with the Spring return-correlation headers. This demonstrates that restoring `MessageBuilder.fromMessage(original)` fails the new regression assertion.

## GREEN

- Focused selector: `mvn -B -q -f sky-take-out/pom.xml -pl sky-server -Dtest=OrderEventConsumerIT#poisonEnvelopeIsDeadLetteredAfterExactlyConfiguredAttemptsWithSafeIdentityHeaders test`; exit 0, 1 test, 0 failures, 0 errors.
- Full verification: `mvn -B -f sky-take-out/pom.xml verify`; exit 0, `BUILD SUCCESS` in 1:45. Surefire reported 171 passing tests; Failsafe reported 30 passing tests. `OrderEventConsumerIT` reported 3 passing integration tests.

## Security rationale

Inbound AMQP headers are attacker-controlled and can contain credentials or unrelated data. A fresh body-only message prevents those values and inbound message properties from crossing into durable DLQ storage. Only the existing safe envelope allowlist and generated failure metadata are set by application code.
