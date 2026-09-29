# Task 18 integration stack recovery report

Status: **DONE**. The four authorized integration classes pass together, alongside 41 directly related unit tests. No full reactor acceptance run was performed.

## Scope and design

- Read Task 18 retry failure evidence, retained Failsafe reports and dumps, and the Task 9/10/13 reports and review chains before changing the fixtures.
- Used systematic debugging and TDD. Existing interrupted edits were treated as candidates and exercised against Docker before acceptance.
- A real production listener-boundary defect was proved during diagnosis. The controller explicitly expanded scope and approved the bounded brainstorming design: preserve the public Object listener methods, unwrap Spring AMQP Message through the existing default SimpleMessageConverter at the existing adapter boundary, and retain the exact JSON/current/legacy validation rules. No new protocol or allowed payload type was introduced.
- No changes to StockConcurrencyIT, stock/order production code, unrelated tests, user files, performance files, or Task 17 documentation/line endings were made by this task. Other agents' concurrent working-tree changes were left untouched.

## Root causes and changes

1. **Incorrect application-context discovery.** The nested ActuatorMetricsIT application was annotated SpringBootConfiguration, so nearby production-context tests could discover the isolated metrics fixture. The interrupted TestConfiguration replacement introduced a second problem: SpringBootTest treats TestConfiguration as supplemental and still discovers SkyApplication, yielding missing EmployeeMapper errors under the fixture's database exclusions. The final nested source uses Configuration plus TestComponent: explicit bootstrap source, excluded from production scanning, and not another application root. ActuatorDependencyReadinessIT explicitly loads SkyApplication and opts into real metrics export with AutoConfigureMetrics.
2. **Mock servlet environment and WebSocket registration.** OrderEventConsumerIT's mock web context has no javax.websocket.server.ServerContainer. The ServerEndpointExporter mock prevents unrelated servlet endpoint registration while the notification port remains the controlled side-effect boundary. DirtiesContext closes test listeners after the class.
3. **Live Rabbit listener argument binding (production defect).** Temporary instrumentation recorded `org.springframework.amqp.core.Message` at the real Object listener methods, followed by `MessageConversionException: Unsupported order event payload org.springframework.amqp.core.Message`. The resolved Spring Rabbit 2.4.6 MessagingMessageListenerAdapter passes the original AMQP Message as a provided method argument; Object accepts it before the converted payload resolver is used. Both adapter entry points now normalize that Message with SimpleMessageConverter, then apply the existing strict envelope/legacy decoding. The temporary instrumentation was removed.
4. **Header fixture type assumptions.** FastJSON/Rabbit preserve the small aggregate ID as Integer, so the Long object equality assertion was incorrect. The assertion still requires numeric value 102. Generic getHeader inference also selected String.valueOf(char[]) for failureReason and threw `String cannot be cast to [C`; an explicit Object type selects the intended overload. DLQ metrics are awaited within the existing 10-second limit because broker receipt precedes confirmed publication, durable failure recording, and the metric increment.
5. **Outbox fixture topology and queue lifetime.** Other application tests declare durable bindings on the shared default vhost. Deleting only the Outbox test queue cannot make an ORDER_PAID publication unroutable; the mandatory-return tests therefore reported SENT. OutboxPublisherIT now owns a unique vhost, declares the real production exchange/routing key inside it, and removes that vhost after the class. Return tests still exercise real mandatory NO_ROUTE returns; successful tests explicitly add their real binding. The receive queue no longer auto-deletes when the first temporary receive consumer cancels, so the second receive can verify absence of a duplicate. Existing exact queue teardown remains.
6. **Pause does not invalidate a Rabbit handshake cache.** Bytecode inspection of the resolved Boot 2.7.3 RabbitHealthIndicator showed health reads Connection.getServerProperties(). A paused broker can therefore appear UP while its cached connection is still open. The fixture now stops the real Rabbit application with rabbitmqctl, which closes its actual connections, asserts readiness DOWN and liveness UP during the outage, restarts it in finally, and verifies readiness recovers. The existing 15-second outage bound was not inflated; the separate recovery check uses the same bound.

## RED evidence

### Fresh four-class reproduction

Command:

`mvn -B -f sky-take-out/pom.xml -pl sky-server -am -DskipTests=false -Dtest=NoMatchingUnitTests -Dit.test=OrderEventConsumerIT,OutboxPublisherIT,ActuatorDependencyReadinessIT,ActuatorMetricsIT -DtrimStackTrace=false verify`

Retained log: `sky-take-out/sky-server/target/task18-stack-red.log`.

Result: **14 tests, 6 failures, 6 errors, BUILD FAILURE**:

- Consumer duplicate: notification never invoked; timeout: no COMPLETED consumption; poison: expected 102L but received 102.
- Outbox mandatory return: expected PENDING, received SENT; repeated return could not progress to terminal FAILED.
- Readiness after pause: expected 503, received 200.
- All six ActuatorMetricsIT bodies: context failure from unintended production EmployeeMapper dependency.

The diagnostic consumer-only rerun is retained as `target/task18-consumer-diagnostic.log`; its full listener stack proves the production Message-versus-payload cause above. No timeout or production change was used to mask these failures.

### Real framework-boundary regression before production change

Command:

`mvn -B -f sky-take-out/pom.xml -pl sky-server -am -Dtest=OrderEventConsumerCompatibilityTest test`

Retained log: `target/task18-listener-red.log`.

Result: **8 tests, 1 failure, 2 errors, BUILD FAILURE**. New JSON/text and stored-legacy tests failed at MessagingMessageListenerAdapter.onMessage with raw Message rejection. The unsafe-envelope test failed because the wrong Message rejection prevented the intended deeper IllegalArgumentException validation. Existing direct compatibility bodies passed.

Regression coverage now invokes actual MessagingMessageListenerAdapter, HandlerAdapter and DefaultMessageHandlerMethodFactory for all three listener methods, JSON bytes/text, stored legacy serialized descriptors, current serialized DTOs, unsupported types and unsafe envelope fields. It asserts notification output, timeout service calls, audit consumption identity and existing MDC behavior.

### Intermediate verification

- `-Dtest=NoMatchingUnitTests -Dit.test=OutboxPublisherIT,ActuatorDependencyReadinessIT,ActuatorMetricsIT verify`: **11/11 passing**, retained `target/task18-fixtures-green.log`.
- First combined run after the production fix: **41 unit tests passed; 13 of 14 ITs passed**, retained `target/task18-stack-green.log`. The remaining error was the failureReason String.valueOf(char[]) fixture cast identified above. This log is retained as intermediate RED, despite its original filename.

## Final GREEN

Exact command:

`mvn -B -f sky-take-out/pom.xml -pl sky-server -am -Dtest=DeadLetterPublisherTest,OrderEventConsumerContractTest,MessageConsumptionServiceTest,OrderEventMessageAdapterTest,OrderEventConsumerCompatibilityTest,OutboxRolloutSafetyTest,OutboxPublisherTest -Dit.test=OrderEventConsumerIT,OutboxPublisherIT,ActuatorDependencyReadinessIT,ActuatorMetricsIT verify`

Retained full log: `sky-take-out/sky-server/target/task18-stack-green-final.log`.

Completed 2026-09-24 13:24:27 +08:00, Maven exit 0, **BUILD SUCCESS**, duration 50.775 seconds.

| Class | Tests | Failures/errors/skips |
| --- | ---: | --- |
| DeadLetterPublisherTest | 9 | 0 / 0 / 0 |
| MessageConsumptionServiceTest | 9 | 0 / 0 / 0 |
| OrderEventConsumerContractTest | 3 | 0 / 0 / 0 |
| OutboxPublisherTest | 7 | 0 / 0 / 0 |
| OrderEventConsumerCompatibilityTest | 8 | 0 / 0 / 0 |
| OrderEventMessageAdapterTest | 3 | 0 / 0 / 0 |
| OutboxRolloutSafetyTest | 2 | 0 / 0 / 0 |
| OrderEventConsumerIT | 3 | 0 / 0 / 0 |
| OutboxPublisherIT | 4 | 0 / 0 / 0 |
| ActuatorDependencyReadinessIT | 1 | 0 / 0 / 0 |
| ActuatorMetricsIT | 6 | 0 / 0 / 0 |

Totals: **41 unit + 14 integration = 55 passing tests**. No assumptions, skips, larger timeouts or H2 substitutes were introduced. MySQL ran all five migrations; real Rabbit handled duplicate delivery, poison retry/DLQ, publisher confirmation/return, concurrent claims and readiness outage/recovery.

## Files and self-review

Exact owned commit scope:

- `sky-take-out/sky-server/src/main/java/com/sky/notification/internal/messaging/OrderEventMessageAdapter.java`
- `sky-take-out/sky-server/src/test/java/com/sky/notification/internal/messaging/OrderEventConsumerCompatibilityTest.java`
- `sky-take-out/sky-server/src/test/java/com/sky/messaging/OrderEventConsumerIT.java`
- `sky-take-out/sky-server/src/test/java/com/sky/messaging/OutboxPublisherIT.java`
- `sky-take-out/sky-server/src/test/java/com/sky/observability/ActuatorDependencyReadinessIT.java`
- `sky-take-out/sky-server/src/test/java/com/sky/observability/ActuatorMetricsIT.java`
- This report.

Self-review confirmed the production diff is limited to nine added lines for the already-proved normalization defect. Existing event validation, idempotency, retry, DLQ persistence and publisher mandatory/confirm semantics remain intact. Broker isolation does not use fake callbacks or delete production-context bindings. Temporary diagnostics are absent.

The successful output is not warning-free: expected intentional Rabbit outage/return logs, pre-existing MySQL 8.4/Flyway compatibility and deprecated schema warnings, and the Windows Surefire/Failsafe PPID checker fallback dumps remain. All retained dumps describe the same `Cannot use PPID ... Going to use NOOP events` process-checker fallback, not a failed assertion or JVM test crash. Full Task 18 reactor acceptance remains the controller's separate step.
