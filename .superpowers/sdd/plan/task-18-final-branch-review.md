# Task 18: Final whole-branch review

## Review scope

- Reviewed revision: `791101eb69e0dfb153048cb181bf1a3ed77fa02e` (`docs: clarify acceptance evidence revisions`), the branch HEAD at this checkpoint.
- Technical fix revision: `e2ebe93b4b4dfe632970bfa06ac14d6a16da1966` (`fix: allowlist dead-letter headers`). The commits after it through the reviewed revision are documentation-only (`ab41d0f`, `791101e`).
- Review covered the complete branch through the reviewed revision, including the final documentation and the earlier technical evidence. This record does not claim that historical smoke gates were rerun at `e2ebe93` or `791101e`.

## Finding and resolution

The only prior Important finding was that `DeadLetterPublisher` used `MessageBuilder.fromMessage(original)`, which copied arbitrary inbound headers into durable dead-letter messages. Revision `e2ebe93` fixes this by rebuilding from the unchanged body and setting only the safe envelope identity allowlist and generated failure metadata.

The regression in `sky-take-out/sky-server/src/test/java/com/sky/messaging/OrderEventConsumerIT.java`, method `poisonEnvelopeIsDeadLetteredAfterExactlyConfiguredAttemptsWithSafeIdentityHeaders`, verifies that the body is unchanged; the non-`spring_` application-header set is exactly `eventId`, `correlationId`, `aggregateId`, `payloadVersion`, `eventType`, `failureReason`, `consumerName`, and `deliveryAttempts`; and `Authorization`, `token`, and `internalDebug` are absent. The assertion excludes Spring AMQP runtime `spring_` headers from the application-header set.

Evidence locations:

- Implementation: `sky-take-out/sky-server/src/main/java/com/sky/messaging/consumer/DeadLetterPublisher.java`
- Regression test: `sky-take-out/sky-server/src/test/java/com/sky/messaging/OrderEventConsumerIT.java`
- Focused and full-gate record, including the reported RED/GREEN outcomes: `.superpowers/sdd/plan/task-18-dlq-header-allowlist-report.md`
- Consolidated gate revisions and remaining acceptance gaps: `sky-take-out/docs/verification/final-acceptance.md`

The DLQ report documents the RED outcome as a result summary. No raw RED console log is claimed or added by this record.

## Verified evidence and revision boundaries

- At `e2ebe93`, the focused RabbitMQ selector passed 1/1. Full `mvn -B -f sky-take-out/pom.xml verify` completed with 171 Surefire and 30 Failsafe tests, 0 failures, 0 errors, and 0 skips. The detailed recorded output summary is in `.superpowers/sdd/plan/task-18-dlq-header-allowlist-report.md` and `sky-take-out/docs/verification/final-acceptance.md`.
- Compose, rollback, and low-load performance-controller smoke evidence remains attributable to `13a1783`, as recorded in `sky-take-out/docs/verification/final-acceptance.md`. It is historical evidence and was not rerun for `e2ebe93` or this documentation-only checkpoint.
- The final whole-branch reviewer approved revision `791101e`: Critical 0, Important 0, Minor 0. The earlier Important finding is resolved by `e2ebe93` as described above.

## Final verdict and open items

**Reviewer verdict: Approved.** The reviewed branch has no remaining Critical, Important, or Minor findings.

**Release status remains `DONE_WITH_CONCERNS / NOT PRODUCTION-READY`.** Review approval does not close the program-level acceptance gaps. Preserve these open items:

- `AO-3`: one accepted searchable end-to-end correlation record across payment, committed Outbox, broker publication, idempotent consumption, and notification/log outcome.
- `PE-1`: two accepted equivalent performance runs, differing only by run ID.
- `PE-5`: accepted optimization and like-for-like before/after evidence.
- `CQ-5` / `CQ-6`: fail-closed and successful GitHub Actions runs with downloadable reports for their exact commits.
- `RD-5` / `RD-6`: remote repository/exported-image secret scans and complete clean-checkout CI for the exact candidate commit.

These gaps remain open in `sky-take-out/docs/verification/final-acceptance.md`; all unchecked acceptance items and the production-readiness conclusion remain unchanged.
