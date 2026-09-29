# Reproducible performance scenarios

This directory contains versioned, non-GUI JMeter plans for the read-only,
light-write (shopping cart), and order-submit paths. A run is acceptable only
when both JMeter and `verify-results.ps1` pass. Do not quote performance numbers
without the matching `environment.json`, `summary.json`, JTL, HTML report, and
`correctness.json` from the same result directory.

## Safety boundary

The scripts support only the isolated stack from `../compose.yml`. The runner
publishes the owned app on a Docker-assigned random loopback port and derives
`BASE_URL` from `docker compose port app 8080`. Omit `-BaseUrl`, or supply it
only as an exact expected-value assertion for that derived endpoint. Database
and RabbitMQ targets are fixed to the same named Compose project; public or
independent targets are rejected. Raw result directories are gitignored.

The runner provisions one database user and one Redis `login:user:<sessionId>`
session per JMeter worker. Temporary OS-directory CSVs contain only non-bearer
IDs and namespaces. Each JMeter thread reads the JWT secret from its inherited
`SKY_PERF_USER_JWT_SECRET` environment and creates the production-compatible
HS256 `userId`, `sessionId`, and `exp` token in memory. An `auth-check` sampler
validates the contract. Tokens are never placed in command arguments, JTL,
environment metadata, logs, or result files; the process environment is restored,
temporary CSVs are removed, and run sessions are deleted during cleanup.

## Prerequisites

- Java 17 or newer and Apache JMeter 5.6.3 on `PATH`.
- Docker Desktop; the runner creates a unique `sky-perf-<RUN_ID>` Task 14 stack.
- A canonical `../.env` copied from `../.env.example`; alternate paths and
  symlinks are rejected, and every Compose call receives `--env-file`.
- `USER_JWT_SECRET` set in the process environment to the isolated user JWT
  secret. The runner passes the same value to its run-scoped application without
  printing or persisting it.

Run a smoke workload:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sky-take-out/performance/run.ps1 `
  -Scenario read-only -Users 2 -RampUpSeconds 2 -DurationSeconds 10 `
  -CacheState warm `
  -ConfirmNonProduction I_UNDERSTAND_THIS_IS_NON_PRODUCTION
```

`warm` preserves Redis and primes the public category endpoint before the timed
run. `cold` flushes only the unique run-scoped Compose Redis database before
the run. The canonical, non-symlink `../compose.yml` is mandatory; alternate
Compose paths are refused before any mutation. The runner stops its project
and deletes that unique project's volumes by default. `-PreserveVolumes` is an
explicit debugging opt-in; clean preserved state with
the exact temporary override and project recorded for that invocation before
allowing the runner to exit. A same-`RUN_ID` invocation fails on an exclusive
OS-temp lock before any Docker command; different run IDs receive independent
random host ports.
`seed.sql` uses validated `RUN_ID`/worker namespaces,
`INSERT ... WHERE NOT EXISTS`, and exact count validation. Order remarks are unique
(`PERF:<RUN_ID>:<thread>:<sequence>`) so a run can be audited independently.

## Parameters and artifacts

All plans consume the same required parameters: `BASE_URL`, `USERS`,
`RAMP_UP_SECONDS`, `DURATION_SECONDS`, `CACHE_STATE`, `RUN_ID`, and
`SESSION_TTL_SECONDS`. The runner derives the identity lifetime from setup
safety, ramp-up, and duration; the same lifetime drives Redis `PEXPIRE` and JWT
`exp`, including the maximum accepted 86,400-second ramp and duration.
The runner supplies each thread the seeded `CATEGORY_ID` and `PRODUCT_ID`; the
read-only assertion requires that product-list contains that exact product.

Each timestamped directory under `results/` contains:

- `results.jtl` and `html/`: raw samples and the JMeter dashboard;
- `environment.json`: commit, topology, machine, and scenario inputs (never credentials);
- `summary.json`: workload-only samples, observed interval throughput, error
  rate, average, P50, P95, and P99;
- `before-snapshot.json`: pre-load global invariant counts used for deltas;
- `correctness.json`: complete counts/reasons/pass state for HTTP failures,
  run-scoped negative stock/duplicate orders and before/after growth in consumer
  duplicates, pending/failed Outbox rows, and DLQ depth. Outbox/consumer business
  correlation is explicitly N/A because these scenarios do not execute payment.

Correlate each run with the internal Actuator/Prometheus metrics for CPU, memory,
JVM, HikariCP, MySQL slow SQL, Redis, and RabbitMQ. A lower average is not an
improvement when tail latency or correctness regresses.

## Verifier regression fixture

The verifier has a fixture mode only for `verifier-*` run IDs. The repository's
plan-owned test creates one negative-stock fixture, expects a non-zero exit and
the scoped negative-stock reason, checks HTTP-200 business failures, and proves
that unchanged old defects do not contaminate a run while new growth does:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .superpowers/sdd/plan/task-16-verifier-tests.ps1
```
