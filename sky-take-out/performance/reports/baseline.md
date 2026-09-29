# Reproducible performance baseline

## Scope and provenance

This baseline uses the three versioned JMeter scenarios in `performance/jmeter`, each measured with cold and warm cache procedures. Every accepted run used 10 users, a 10-second ramp-up, a 60-second requested duration, JMeter 5.6.3, Java 21.0.8, and an isolated Docker Compose project with a random loopback application port.

Read-only and light-write were captured at commit `9a13ad033b41504b66f6d127edc959aafc98c938`. The first order-submit attempt exposed two correctness defects: stale takeout-only fields in the retail order mapper and a non-atomic order-number fast path. Those defects were fixed with regression tests in commits `b6bc6fe` and `8112295`; order-submit cold and warm were then captured at `81122954798067def0fe97828c5df31737c6b731`. Cold/warm comparisons remain revision-identical within every scenario. Cross-scenario totals must not be interpreted as measurements of one identical binary.

## Controlled environment

- Host: Microsoft Windows NT 10.0.26200.0, 16 logical processors, 16,366,567,424 bytes RAM.
- Runtime: Java 21.0.8 and Apache JMeter 5.6.3.
- Topology: one owned Compose project per run; MySQL, Redis, RabbitMQ, and the application are isolated and removed in the finalizer.
- Dataset: one run-scoped category, one run-scoped product reset to price `1.00` and stock `1,000,000`, plus 10 isolated users and Redis-backed sessions.
- Cold procedure: flush the owned Redis database before dataset seed and measured load.
- Warm procedure: execute a separate authenticated read-only warmup; warmup samples are excluded from measured summaries.
- Safety gates: zero HTTP and business failures, zero negative stock, zero duplicate order numbers, no unexpected Outbox/DLQ growth, no credential-bearing artifacts, and no remaining Compose or temporary resources.
- Container CPU and memory were not explicitly capped. Host/container time-series CPU, JVM, connection-pool, slow-query, Redis-hit, and Rabbit-depth samples were not preserved by these runs; no bottleneck conclusion is inferred from absent telemetry.

## Results

| Scenario | Cache | Samples | Failures | Throughput req/s | Mean ms | P50 ms | P95 ms | P99 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| read-only | cold | 149,810 | 0 | 2,554.83 | 3.28 | 3 | 7 | 11 |
| read-only | warm | 159,043 | 0 | 2,706.10 | 3.09 | 3 | 5 | 9 |
| light-write | cold | 71,523 | 0 | 1,220.82 | 7.28 | 8 | 14 | 19 |
| light-write | warm | 71,958 | 0 | 1,222.67 | 7.24 | 8 | 13 | 20 |
| order-submit | cold | 19,316 | 0 | 330.19 | 28.05 | 23 | 53 | 62 |
| order-submit | warm | 19,775 | 0 | 334.64 | 27.47 | 19 | 51 | 60 |

All six accepted runs passed their correctness and cleanup gates. Exact unrounded values, immutable image IDs, and raw-directory pointers are recorded in `performance/results/baseline-summary.json`.

## Request composition

- Read-only cold/warm: 10 authentication checks and approximately equal category-list, product-list, product-search, and order-query traffic.
- Light-write cold/warm: 10 authentication checks and approximately equal cart-add, cart-list, and cart-sub traffic.
- Order-submit cold: 10 authentication checks, 9,659 cart additions, and 9,657 successful order submissions.
- Order-submit warm: 10 authentication checks, 9,889 cart additions, and 9,886 successful order submissions.

## Interpretation boundary

Warm cache improved read-only throughput by about 5.9% and reduced its P95 from 7 ms to 5 ms. Light-write changed little; its warm P99 increased from 19 ms to 20 ms, so it is not classified as a tail-latency improvement. Order-submit warm improved throughput by about 1.3% and reduced P50/P95/P99 from 23/53/62 ms to 19/51/60 ms.

These comparisons establish a reproducible baseline; they do not yet identify a causal bottleneck. A production optimization requires correlated resource or query evidence and a predeclared rejection condition before changing code.
