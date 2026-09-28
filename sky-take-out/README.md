# Sky Take Out backend

This directory contains the enterprise-hardened Java backend for the mobile-retail system: a modular monolith, reproducible local stack, operational runbooks, verification evidence, and performance harness. It is not an Agent project. This README is the maintained documentation entry point.

> **Acceptance status:** local technical gates passed at revision `13a1783`, but overall status is **DONE_WITH_CONCERNS / NOT PRODUCTION-READY**. End-to-end async correlation, identical-input performance repeatability, remote CI/security evidence, and the Task 17 optimized retest remain open. See [final acceptance](docs/verification/final-acceptance.md).

## Quick start

The Maven reactor targets Java 17 and contains `sky-pojo`, `sky-common`, and the executable `sky-server` application. From the repository root:

```powershell
mvn -B -f sky-take-out/pom.xml verify
```

Docker-backed tests require Docker Desktop with Linux containers. For the isolated Compose stack, copy the committed template and replace every example secret; never commit `.env`:

```powershell
Copy-Item sky-take-out/.env.example sky-take-out/.env
docker compose -f sky-take-out/compose.yml --env-file sky-take-out/.env up -d --build --wait
```

The application listens on `8080`. Management health and Prometheus endpoints use `8081` inside the private host/container boundary and must not be exposed publicly.

## Documentation map

| Area | Maintained source |
|---|---|
| Development | [`application-dev.example.yml`](sky-server/src/main/resources/application-dev.example.yml), [parent POM](pom.xml), [architecture](docs/architecture/architecture.md) |
| Tests | [specification coverage](docs/verification/spec-coverage.md), [final acceptance](docs/verification/final-acceptance.md) |
| Database migration | [deployment migration procedure](docs/operations/deployment.md#database-entry-modes-and-backup), [Flyway migrations](sky-server/src/main/resources/db/migration/) |
| Compose | [`compose.yml`](compose.yml), [`compose.monitoring.yml`](compose.monitoring.yml), [smoke script](scripts/compose-smoke.ps1) |
| Operations | [deployment](docs/operations/deployment.md), [rollback](docs/operations/rollback.md) |
| Observability | [`application.yml`](sky-server/src/main/resources/application.yml), [Prometheus](monitoring/prometheus.yml), [Grafana dashboard](monitoring/grafana/dashboards/sky-overview.json) |
| Performance | [harness](performance/README.md), [accepted baseline](performance/reports/baseline.md); Task 17 has no accepted optimization result |
| Architecture | [architecture and tradeoffs](docs/architecture/architecture.md), [module boundaries](docs/architecture/module-boundaries.md), [order/payment/messaging sequences](docs/architecture/order-payment-messaging-sequence.md) |
| Framework decision | [Spring Boot 3 ADR](docs/decisions/spring-boot-3-upgrade.md) |
| Coverage and final acceptance | [31-requirement/41-scenario matrix](docs/verification/spec-coverage.md), [live checklist](docs/verification/final-acceptance.md) |

## Verification commands

Focused unit and Failsafe integration selectors are:

```powershell
mvn -B -f sky-take-out/pom.xml -pl sky-server -am "-Dtest=<TestClass#method>" test
mvn -B -f sky-take-out/pom.xml -pl sky-server -am "-Dtest=__NoUnitTests__" "-Dit.test=<IntegrationTest#method>" verify
```

Operational checks require Docker and a populated local `.env`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/compose-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/rollback-drill.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/performance/run.ps1 `
  -Scenario read-only -Users 2 -RampUpSeconds 2 -DurationSeconds 10 `
  -CacheState warm -ConfirmNonProduction I_UNDERSTAND_THIS_IS_NON_PRODUCTION
```

The [final acceptance record](docs/verification/final-acceptance.md) captures the successful local executions at revision `13a1783`. A new revision requires new evidence. Numeric optimization claims may be published only after an accepted like-for-like comparison; the committed [baseline](performance/reports/baseline.md) is not evidence of an improvement.

The root-level enterprise, online-readiness, online-action, and technical-reference guides are retained only as historical context. Their superseded notices link here; this README and the maintained documents above govern when content conflicts.
