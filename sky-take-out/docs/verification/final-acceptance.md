# Final acceptance record

**Overall status: DONE_WITH_CONCERNS / NOT PRODUCTION-READY**

**Checkpoint date: 2026-09-28**

**Candidate revision: `9e6af253a171c6913584b47fe5fc8eda61933b44`**

This is an evidence record, not a release certificate. The local technical evidence below applies to the explicitly recorded revisions, and the program-level gaps listed later remain open. A later revision requires fresh evidence.

## Executed local technical gates

- [x] **PASS — full Maven verification**

  ```powershell
  mvn -B -f sky-take-out/pom.xml verify
  ```

  For the code committed as `9e6af25`, the reactor exited `0` on 2026-09-28: 171 Surefire unit/architecture tests and 31 Failsafe integration tests completed with 0 failures, 0 errors, and 0 skips. The Docker-backed MySQL/RabbitMQ readiness, concurrency, payment, Outbox, consumer, dead-letter, Flyway, and continuous AO-3 trace tests ran in this gate. Current-run XML totals were checked separately from pre-existing stale target reports; see the [AO-3 execution report](../../../.superpowers/sdd/plan/task-18-ao3-report.md).

- [x] **PASS — AO-3 continuous payment notification trace**

  ```powershell
  mvn -B -f sky-take-out/pom.xml -pl sky-server -am "-Dtest=__NoUnitTests__" "-Dit.test=PaymentNotificationTraceIT" verify
  ```

  `PaymentNotificationTraceIT#paymentOutboxAndNotificationExposeOneSafeSearchableBusinessChain` passed 1/1 focused and in full verify for the code committed as `9e6af25`. The real Spring payment, committed PENDING Outbox, real broker publication, real paid-notify consumer, COMPLETED consumption row, exact notification payload, and SENT Outbox are one accepted record. The payment, successful publisher, and notification-consumer INFO logs all expose eventId `be2b100a-52f2-44a0-8dc5-bbf2dda968af`, correlationId `AO3-TXN-98103`, and orderId `98103` in the full run; payload and injected secret-canary absence are asserted. The only fake business boundary is external WebSocket delivery through OrderNotificationPort. Both initial RED and removing the new success log failed specifically at the missing successful-publication record after the business flow completed. See the [test](../../sky-server/src/test/java/com/sky/observability/PaymentNotificationTraceIT.java) and [retained execution evidence](../../../.superpowers/sdd/plan/task-18-ao3-report.md).

  The broker may deliver asynchronously before the publisher records ACK/SENT success: in this accepted run the notification log at 23:41:38.567 precedes the publisher log at 23:41:38.585. All three stages carry the same correlation/event/order identifiers; acceptance does not require a false cross-thread timestamp ordering.

- [x] **PASS — DLQ header allowlist security regression**

  ```powershell
  mvn -B -q -f sky-take-out/pom.xml -pl sky-server `
    -Dtest=OrderEventConsumerIT#poisonEnvelopeIsDeadLetteredAfterExactlyConfiguredAttemptsWithSafeIdentityHeaders test
  ```

  At revision `e2ebe93`, the focused selector passed 1/1. Restoring `MessageBuilder.fromMessage(original)` produced the intended RED failure because `Authorization`, `token`, and `internalDebug` were copied into the durable DLQ message. The fix rebuilds the message from its unchanged body and sets only the safe envelope identity allowlist plus generated failure metadata. The integration assertion requires the exact non-`spring_` application-header set; `spring_` headers are Spring AMQP runtime correlation headers rather than application-controlled inputs. An independent review approved the change.

- [x] **PASS — Compose smoke**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/compose-smoke.ps1
  ```

  At revision `13a1783`, the script verified the exact image revision, healthy dependencies and application, current Flyway schema, private management readiness, a non-root application process, and MySQL/Redis/RabbitMQ persistence across dependency recreation. It exited `0` and removed its smoke containers and network. This historical gate was not rerun for `9e6af25`.

- [x] **PASS — rollback drill**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/rollback-drill.ps1 `
    -StableRevision HEAD^ -CandidateRevision HEAD -RunId task18-final4-0925
  ```

  The candidate `13a1783` produced the intended readiness failure (`HTTP 503`, `DOWN`). The drill restored the exact stable revision `69dae09fd5ec4bc90c7ed67e6f80493a3ef57fb7`, preserved the sentinel and Flyway history, exited `0`, and finally stopped the stable application. Dependency containers and named volumes remain by script design for inspection/reuse; this is not a cleanup failure.

- [x] **PASS — low-load performance-controller smoke**

  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/performance/run.ps1 `
    -Scenario read-only -Users 2 -RampUpSeconds 2 -DurationSeconds 10 `
    -CacheState warm -ConfirmNonProduction I_UNDERSTAND_THIS_IS_NON_PRODUCTION `
    -RunId task18-perf-0925
  ```

  At revision `13a1783`, the isolated run completed 3,393 business samples with 0 failures; throughput was 375.872/s, average 4.585 ms, P50 4 ms, P95 7 ms, and P99 9 ms. JMeter exited `0`, correctness checks passed, every scoped/global exception counter increased by 0, and the runner removed its dedicated stack and volumes. This is only a Task 18 low-load executability smoke. It is not Task 17 optimization evidence and does not close `PE-1`.

## Coverage closure and remaining gaps

The matrix inventories **31 requirements / 41 scenarios**. Revision `9e6af25` retains the previously closed local rows, including the accepted `e2ebe93` DLQ header-allowlist regression for `OR-10`, and closes `AO-3` with the continuous Docker-backed trace and searchable production-log record. The Compose, rollback, and performance-smoke evidence remains attributable to its recorded revision `13a1783`; it was not rerun for this source-only messaging change.

The following program-level evidence is still missing:

- [ ] **GAP `PE-1`:** retain two accepted runs with identical revision, dataset, users, ramp, duration, cache state, and scenario; only run ID may differ.
- [ ] **OPEN `CQ-5` / `CQ-6`:** retain both a fail-closed and a successful GitHub Actions run with downloadable reports for their exact commits.
- [ ] **OPEN `RD-5` / `RD-6`:** retain the remote repository/exported-image secret scans and complete clean-checkout CI execution for the exact candidate commit.
- [ ] **OPEN/BLOCKED `PE-5`:** produce an accepted optimization and like-for-like before/after comparison before publishing an improvement claim.

## Task 17 performance status

- [x] A committed reproducible [baseline](../../performance/reports/baseline.md) exists.
- [x] The attempted candidate was rejected on causal support and the production source was restored.
- [ ] **BLOCKED — optimized like-for-like retest:** no accepted production optimization exists to retest.
- [ ] **BLOCKED — optimized summary/claim:** no optimized result or accepted improvement may be published.

The low-load Task 18 smoke above proves only that the controller, correctness checks, and cleanup can run. It must not be presented as an optimization result or compared with the Task 17 baseline.

## Repository hygiene and decision

- [x] The documentation commit excludes the pre-existing miniapp edit, `.env`, root `jmeter.log`, `nul`, `openspec/`, line-ending-only Java files, raw performance results, and the uncommitted Task 17 optimization note.
- [x] Published Task 18 performance-smoke numbers are labelled as low-load executability evidence and are not presented as an optimization claim.
- [x] Generated logs, credentials, build output, and raw large artifacts are absent from the documentation commit.
- [ ] Every OpenSpec scenario has accepted evidence or an approved exception.
- [ ] A fresh final whole-branch review is required for the AO-3 code added in `9e6af25`. The [historical review record](../../../.superpowers/sdd/plan/task-18-final-branch-review.md) is retained; its approval predates this added production code and does not approve the latest candidate.

Full Maven verification (171 Surefire / 31 Failsafe) and AO-3 are green for the code committed as `9e6af25`; the DLQ header-allowlist regression remains accepted historical evidence at `e2ebe93` and also passed in the new full verify. Compose, rollback, and low-load performance-controller gates remain green historical evidence for `13a1783`. A fresh whole-branch review is required. Overall acceptance remains **DONE_WITH_CONCERNS / NOT PRODUCTION-READY** until the unchecked evidence is closed or explicitly accepted as an exception.
