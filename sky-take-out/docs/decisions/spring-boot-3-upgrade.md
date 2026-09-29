# ADR: Defer the Spring Boot 3 upgrade

- **Status:** Deferred
- **Date:** 2026-09-23

## Context and audit

The current build facts come from the Maven descriptors and source imports:

| Area | Current fact | Boot 3 risk |
|---|---|---|
| Java | Compiler source/target 17 | Meets the language floor; full build/runtime still needs revalidation |
| Spring | Spring Boot parent 2.7.3 | Boot 3 moves to Spring Framework 6 and Jakarta EE namespaces |
| Java EE APIs | Source imports `javax.servlet`, `javax.validation`, `javax.annotation`, and `javax.websocket` | Controllers, filters, validation DTOs, lifecycle hooks, and WebSocket code must migrate to `jakarta.*` |
| MyBatis | MyBatis Spring Boot starter 2.2.0 | Requires a Boot 3-compatible starter and mapper/integration regression |
| Knife4j | Knife4j starter 3.0.2 with Springfox `Docket` and an Ant path-matching workaround | The API-documentation stack must be upgraded or replaced; endpoint exposure must be reviewed |
| WeChat Pay | `wechatpay-apache-httpclient` 0.4.8 | Security-sensitive callback/crypto and transitive compatibility require explicit regression |
| Security | Only `spring-security-crypto` 5.7.3 is declared; authentication uses custom JWT interceptors/session code | Align the crypto version without silently changing password or authentication semantics |
| JWT/JAXB | JJWT 0.9.1 plus explicit `javax.xml.bind:jaxb-api` 2.3.1 | Legacy `javax` coupling and token compatibility need a deliberate upgrade |

Other explicitly pinned Boot integrations, including Druid 1.2.1 and PageHelper starter 1.3.0, also require compatibility verification.

## Decision

**Defer Spring Boot 3.** Keep Java 17 and Spring Boot 2.7.3 for this hardening change. Start a migration only through a separate approved OpenSpec change with explicit compatibility and rollback criteria.

Combining a namespace/dependency migration with open order, payment, messaging, delivery, and performance acceptance would obscure regressions. This decision does not claim the current dependencies are current, indefinitely supported, or production-approved.

## Bounded estimate

| Work item | Estimate |
|---|---:|
| Dependency resolution and API-documentation replacement spike | 1–3 engineer-days |
| `javax` → `jakarta` source/config migration and compile fixes | 2–4 engineer-days |
| MyBatis, validation, WebSocket, JWT/security, and WeChat regression fixes | 3–6 engineer-days |
| Docker-backed integration, operations, documentation, and release evidence | 2–5 engineer-days |
| **Total expected range** | **8–18 engineer-days** |

This assumes no public API redesign and no simultaneous replacement of custom authentication.

## Prerequisites

1. Approve a dedicated OpenSpec change and freeze unrelated dependency upgrades.
2. Inventory all `javax.*` imports and libraries integrating with Spring, Servlet, validation, WebSocket, Jackson, MyBatis, or API documentation.
3. Select a Boot 3-compatible API-documentation stack instead of carrying forward the Springfox path workaround by default.
4. Define compatibility fixtures for existing JWTs/password hashes and signed/decrypted WeChat callbacks.
5. Establish a clean current acceptance baseline so migration regressions can be separated from existing blocked evidence.
6. Confirm the supported build/deployment JDK while retaining Java 17 source compatibility unless another ADR changes it.

## Gates for a future upgrade

- Full `mvn -B -f sky-take-out/pom.xml verify`, architecture rules, and coverage reports.
- Empty and baselined Flyway migrations, rerun through the current version.
- Payment signature rejection, duplicate callback, amount/order binding, and transactional Outbox tests.
- Live RabbitMQ confirms/returns/NACK, idempotency, configured retry, and dead-letter tests.
- JWT/session and BCrypt compatibility for existing credentials.
- WebSocket ticket/authentication and after-commit notification tests.
- Live Compose smoke with readiness and non-root runtime checks.
- Rollback drill proving prior-image restoration without sentinel or Flyway-history loss.
- Identical-input performance comparison with all correctness gates.
- Secret scanning and retained CI artifacts for the exact candidate commit.

## Consequences

The hardening change stays reviewable and can close its evidence gaps first. The repository continues carrying Boot 2-era `javax`, Springfox/Knife4j, JWT/JAXB, and pinned integration dependencies as explicit upgrade debt; no Boot 3/Jakarta compatibility claim is valid until the separate change passes these gates.
