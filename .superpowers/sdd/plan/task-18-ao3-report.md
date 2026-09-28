# Task 18 AO-3 payment notification trace evidence

Status: **DONE_WITH_CONCERNS / NOT PRODUCTION-READY**. AO-3 now has one accepted continuous Docker-backed business-chain record; the program-level gates listed below remain open.

Checkpoint: 2026-09-28, Asia/Shanghai. Baseline: `875cb676f933872a9257d0fca879ae65f4bc3fd9`, branch `codex/enterprise-project-hardening`. The first commit containing this report is the technical candidate; the subsequent acceptance-document commit records its exact hash.

## Scope and implementation

Production change: `OutboxPublisher.publishNext()` emits `INFO Outbox event published successfully` only after the real broker confirm is ACK, no mandatory return exists, and `markSent(eventId, leaseOwner)` returns 1. The existing MDC scope supplies eventId, correlationId and orderId. The message has no payload, callback, credential, or exception arguments. The lost-lease warning and all state transitions are preserved.

The new `PaymentNotificationTraceIT#paymentOutboxAndNotificationExposeOneSafeSearchableBusinessChain` uses the real Spring payment service and order service, transactional MySQL/MyBatis persistence, Outbox mapper/publisher, configured mandatory RabbitTemplate with correlated publisher confirms, production Rabbit topology/listeners, production OrderEventConsumer, and real message-consumption storage. Only OrderNotificationPort (external WebSocket boundary) and ServerEndpointExporter (test framework boundary) are mocked. The port captures the production consumer's exact JSON payload into a thread-safe queue.

Scheduled Outbox polling is disabled for this fixture so the test explicitly invokes the real publisher. It clears claimable rows only in the disposable Testcontainers database before seeding the one pending order. No production data or user files are deleted.

The test-only Logback appender attaches to the three production loggers, calls `prepareForDeferredProcessing()` on the logging thread, stores events in a ConcurrentLinkedQueue, and detaches/stops after the test. Stage matching uses the actual logger name and successful event meaning; it does not grep test or production source. Each matched INFO record must contain the exact eventId read from the committed Outbox row, literal correlationId `AO3-TXN-98103`, and literal orderId `98103`. The production payment service generates its UUID naturally; no UUID or event persistence is mocked.

## TDD and execution record

Focused command, used unchanged for RED, GREEN, and the removal mutation:

```powershell
mvn -B -f sky-take-out/pom.xml -pl sky-server -am "-Dtest=__NoUnitTests__" "-Dit.test=PaymentNotificationTraceIT" verify
```

Full command after restoring the mutation:

```powershell
mvn -B -f sky-take-out/pom.xml verify
```

| Run | Finished (Asia/Shanghai) | Exit | Surefire tests / failures / errors / skips | Failsafe tests / failures / errors / skips |
|---|---|---:|---|---|
| RED, before any production edit | 2026-09-28 23:36:31 | 1 | 0 / 0 / 0 / 0 selected | 1 / 1 / 0 / 0 |
| focused GREEN | 2026-09-28 23:38:06 | 0 | 0 / 0 / 0 / 0 selected | 1 / 0 / 0 / 0 |
| mutation: remove only the new success-log branch | 2026-09-28 23:39:24 | 1 | 0 / 0 / 0 / 0 selected | 1 / 1 / 0 / 0 |
| full verify, restored production log | 2026-09-28 23:41:43 | 0 | 171 / 0 / 0 / 0 | 31 / 0 / 0 / 0 |

RED and the removal mutation both reached the complete real business flow first: payment APPLIED, committed PENDING row, real broker publication, exact notification, Outbox SENT, and paid-notify COMPLETED. Both then failed only at:

```text
[successful stage log from OutboxPublisher: Outbox event published successfully]
Expected size: 1 but was: 0 in: []
```

RED eventId: `bc315465-64bd-4523-8d21-70bc00fb0284`. Mutation eventId: `fedad30a-93bc-4725-b7b9-a226908b0d20`. After the mutation, apply_patch restored the same two production lines and the full suite passed, including the focused test. This is executed behavioral mutation proof, not a source-content assertion.

For the mutation/full commands, console output was filtered with PowerShell Select-String and `exit $LASTEXITCODE` preserved Maven's native result. Maven test selection and execution were unchanged. No report files were created by shell redirection.

Fresh report verification: the current full run wrote 24 Surefire suites totaling 171 and 9 Failsafe suites totaling 31. `sky-take-out/sky-server/target/failsafe-reports/failsafe-summary.xml` records completed=31, failures=0, errors=0, skipped=0. Current-run XML timestamps (at or after 2026-09-28 23:39:40) were used to exclude pre-existing Surefire XML from September 8-25; those stale files are not current failures or current test counts and were left untouched. The Maven console independently reports 171 and 31 with zero failures/errors/skips. Generated reports remain build artifacts, excluded from commits.

## Accepted database, RabbitMQ and log record

Focused GREEN accepted eventId: `201405c9-1e25-49b2-9b3f-13593f4ab6a1`. Full-verify accepted eventId: `be2b100a-52f2-44a0-8dc5-bbf2dda968af`. Both use literal orderNumber `AO3-ORDER-98103`, orderId `98103`, transaction/correlationId `AO3-TXN-98103`, userId `7`, and amount `12.34` (callback total `1234`).

The assertions hand-check the committed initial Outbox row: event_id is the naturally generated UUID above, correlation_id=`AO3-TXN-98103`, aggregate_id=98103, event_type=`ORDER_PAID`, status=`PENDING`. The committed order has status=2 and pay_status=1. After real publication/consumption, Outbox is `SENT`, attempt_count=0 and last_error=NULL; message_consumption has consumer_name=`order-paid-notify`, the same event_id, status=`COMPLETED`, and business_key=`AO3-ORDER-98103`.

The exact emitted notification JSON object has only these fields (JSON object key order is immaterial):

```json
{"type":1,"orderId":98103,"content":"订单号：AO3-ORDER-98103"}
```

Focused GREEN used MySQL at `localhost:60437/sky_test` and a real AMQP connection to RabbitMQ at `localhost:60441`. The publisher sends a persistent message on the production `ORDER_PAID` routing path, waits for the real correlated broker confirm, and persists SENT only after ACK without return. The production paid-notify listener consumes on its Rabbit listener thread and writes COMPLETED; it is not called directly by the test. This real consumer result plus exact port output proves broker delivery, not just Outbox database state.

Accepted full-verify production records (each has eventId=`be2b100a-52f2-44a0-8dc5-bbf2dda968af`, correlationId=`AO3-TXN-98103`, orderId=`98103` in its captured MDC):

| Time | Production logger | Level and event meaning | Thread |
|---|---|---|---|
| 23:41:38.530 | com.sky.payment.internal.PaymentCallbackService | INFO Verified payment persisted with pending outbox event, orderNo=AO3-ORDER-98103 | main |
| 23:41:38.585 | com.sky.messaging.outbox.OutboxPublisher | INFO Outbox event published successfully | main |
| 23:41:38.567 | com.sky.notification.internal.messaging.OrderEventConsumer | INFO MQ 处理支付成功通知完成，orderNo=AO3-ORDER-98103 | RabbitListenerEndpointContainer#2-1 |

Consumption can run before the publisher's SENT success log because the broker dispatches concurrently; the test asserts the continuous identities and successful outcomes, not a false cross-thread timestamp order. The production console pattern already exposes these MDC fields, so a search for correlationId `AO3-TXN-98103` or orderId `98103` locates all three stages, with eventId joining them to the committed database row.

The secret canary `AO3-secret-canary-do-not-log` is injected into the caller's non-allowlisted `token` MDC entry and into an extra `token` field of the stored message payload. The real Rabbit path carries that payload through the production adapter. The test asserts that every captured record omits the canary, complete original/wire payload, JSON userId/amount fields, and the token MDC key/value. All three safe stage records and the exact notification assertions pass with this canary present.

## File and commit boundaries

First commit (`feat: prove payment notification trace`) contains exactly:

- sky-take-out/sky-server/src/main/java/com/sky/messaging/outbox/OutboxPublisher.java
- sky-take-out/sky-server/src/test/java/com/sky/observability/PaymentNotificationTraceIT.java
- .superpowers/sdd/plan/task-18-ao3-report.md

Second commit (`docs: close async trace evidence gap`) contains exactly:

- sky-take-out/docs/verification/spec-coverage.md
- sky-take-out/docs/verification/final-acceptance.md

The new IT is ignored by the existing `**/test/` rule and this report by `.superpowers/sdd/.gitignore`; only those explicitly required files are force-added. Both staged name sets are checked before committing; the final staged set is required to be empty. Existing miniapp edits, `.env`, root logs/nul, openspec/, and the three protected line-ending-only Java files are excluded and preserved. No schema, envelope/interface, queue topology, retry policy, or WebSocket production change is made. Compose smoke, rollback, JMeter and push are not run.

## Remaining concerns

PE-1, PE-5, CQ-5/CQ-6 and RD-5/RD-6 remain open. Historical Compose/rollback/performance evidence remains attributed to `13a1783` and is not refreshed by this source-only change. Adding AO-3 production code after the recorded whole-branch review requires a fresh whole-branch review; the acceptance checkbox must be unchecked without deleting the historical review. The actual external WebSocket/client delivery is intentionally outside this accepted trace test's fake port boundary. Existing JDK 21/target 17 and MySQL 8.4/Flyway support warnings did not prevent the successful gates.
