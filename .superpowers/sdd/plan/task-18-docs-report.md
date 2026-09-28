# Task 18 final documentation report

Status: **DONE_WITH_CONCERNS / NOT PRODUCTION-READY**

Candidate revision: `13a17837868da2e47b259517853f3f2f2f484e6e` (`13a1783`)

## Documentation delivered

- `sky-take-out/README.md` is the maintained entry point and describes the repository as an enterprise-hardened Java modular-monolith/mobile-retail backend, not an Agent project.
- `docs/architecture/architecture.md` records Maven/module ownership, reliability boundaries, and tradeoffs.
- `docs/architecture/order-payment-messaging-sequence.md` documents order timeout, verified payment/Outbox, idempotent consumption/dead letter, and after-commit notification. It distinguishes passing Docker-backed component evidence from the missing end-to-end correlation record.
- `docs/decisions/spring-boot-3-upgrade.md` defers the Boot 3 migration with a bounded estimate, prerequisites, risks, and future gates.
- `docs/verification/spec-coverage.md` maps all 31 requirements and 41 scenarios to exact tests/verifiers and current truthful statuses.
- `docs/verification/final-acceptance.md` records the local technical gate evidence and preserves the remaining program-level gaps.
- The four legacy root guides retain prominent superseded notices directing readers to the maintained README. Their bodies remain historical context and do not override current documentation.

## Final local evidence

### Maven verification — PASS

- Command: `mvn -B -f sky-take-out/pom.xml verify`
- Revision: `13a1783`
- Completed: `2026-09-25 06:55 +08`
- Result: exit `0`, reactor `BUILD SUCCESS`
- Tests: 171 unit/architecture and 30 Failsafe integration tests; 0 failures, 0 errors, 0 skips
- Effect on coverage: closes the current Docker-backed rows `AO-5`, `OR-1`, the MySQL companion for `OR-2`, `OR-3`, `OR-4`, `OR-6`, `OR-7`, `OR-8`, `OR-9`, `OR-10`, `RD-1`, and `RD-2`.

### Compose smoke — PASS

- Command: `sky-take-out/scripts/compose-smoke.ps1`
- Revision: `13a1783`
- Result: exit `0`
- Verified: exact image revision, dependencies/migrations/readiness, non-root application process, and MySQL/Redis/RabbitMQ persistence across recreation
- Cleanup: smoke containers and network removed automatically
- Effect on coverage: closes `RD-3` and local `RD-4`.

### Rollback drill — PASS

- Run ID: `task18-final4-0925`
- Candidate: `13a17837868da2e47b259517853f3f2f2f484e6e`
- Stable: `69dae09fd5ec4bc90c7ed67e6f80493a3ef57fb7`
- Result: exit `0`; candidate returned `HTTP 503` / `DOWN`, exact stable image restored, sentinel and Flyway history preserved
- Lifecycle: stable application finally stopped; dependency containers and named volumes intentionally retained by script design
- Effect on coverage: closes `RD-7`.

### Task 18 performance-controller smoke — PASS, limited scope

- Run ID: `task18-perf-0925`
- Revision: `13a1783`
- Inputs: read-only, 2 users, 2-second ramp, 10-second duration, warm cache
- Result: 3,393 business samples, 0 failures, 375.872/s throughput, 4.585 ms average, P50 4 ms, P95 7 ms, P99 9 ms; JMeter exit `0`
- Correctness: passed; every scoped/global exception counter increased by 0
- Cleanup: dedicated stack and volumes removed by the runner
- Boundary: this is low-load controller executability evidence only. It is not Task 17 optimization evidence and does not close `PE-1` or `PE-5`.

## Remaining open evidence

- `AO-3` — no single accepted searchable payment → committed Outbox → broker → idempotent consumer → notification/log correlation record.
- `PE-1` — no accepted identical-input repeat pair where only run ID differs.
- `CQ-5` / `CQ-6` — no retained fail-closed and successful GitHub Actions executions with reports for exact commits.
- `RD-5` / `RD-6` — no retained remote repository/exported-image secret-scan and complete clean-checkout CI evidence for the exact candidate.
- `PE-5` / Task 17 — baseline committed, candidate rejected/restored, but no accepted optimization, like-for-like optimized retest, optimized summary, or improvement claim.

Consequently, local technical gates are green but overall acceptance remains **DONE_WITH_CONCERNS / NOT PRODUCTION-READY**.

## Commit scope

The documentation commit contains only the maintained README, `sky-take-out/docs/**`, the four legacy-guide superseded notices, and this report. It excludes the pre-existing miniapp edit, `.env`, root `jmeter.log`, `nul`, `openspec/`, line-ending-only Java files, raw performance results, and `sky-take-out/performance/reports/optimization.md`.

## Documentation checks

- Maintained-document relative links/paths: **PASS**.
- OpenSpec inventory and coverage matrix: **PASS**, 31 requirements / 41 scenarios / 41 matrix rows.
- Mermaid and Markdown code fences: **PASS**, balanced.
- Legacy notices: **PASS**, present near the top of all four retained guides.
- `git diff --check` for the documentation scope: **PASS**.
- Staged-file allowlist and protected-file exclusions: **PASS**.
