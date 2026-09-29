# Measured bottleneck and optimization gate

## Observed bottleneck

The accepted order-submit JTL files isolate latency in `submit-unique-order`, not in the preceding cart operation:

| Cache | Sampler | Samples | Mean ms | P95 ms | P99 ms |
|---|---|---:|---:|---:|---:|
| cold | cart-add-before-order | 9,659 | 12.39 | 17 | 21 |
| cold | submit-unique-order | 9,657 | 43.72 | 57 | 66 |
| warm | cart-add-before-order | 9,889 | 12.07 | 16 | 20 |
| warm | submit-unique-order | 9,886 | 42.87 | 55 | 64 |

The submission sampler is therefore the measured endpoint bottleneck: its mean is about 3.5 times the cart-add mean in both cache states. The similarity between cold and warm results also makes Redis cache misses an unlikely primary explanation.

## Causal hypothesis

The following observations are **unversioned diagnostic observations, not accepted or reproducible evidence**. They narrow the next investigation but cannot support a released optimization conclusion or select a production change.

- `t17sql3` was an ignored local run at revision/image revision `01edb8c3e26cbbf273fa492025bdc566278798b7`, using 10 users, a 10-second ramp, a 60-second warm order-submit workload, and image ID prefix `514bd9`. It recorded 18,848 total JMeter samples: 18,838 workload samples plus 10 authentication samples. Its local SQL snapshots ranked the conditional stock update ahead of the product read, but the raw statement-timing snapshots were not retained or committed.
- `t17met` was a separate ignored local run with the same revision and workload shape, using image ID prefix `f7fde0`. Its local Prometheus snapshot did not show obvious CPU, heap, Rabbit publication, or Outbox saturation at the sampled instant, but the raw telemetry snapshot was not retained or committed.

A fresh clone therefore contains neither diagnostic run's raw SQL nor telemetry evidence. This report is a boundary statement about those observations, not a substitute for the missing primary evidence. The observations are consistent with a hot shared inventory row as a hypothesis, but they cannot independently establish row-lock wait time, lock-holder duration, commit contribution, resource saturation, or causality. Direct, versioned measurement is still required.

## Rejected candidate change (recovery assessment, 2026-09-22)

The interrupted implementation moved shopping-cart deletion immediately before `inventoryService.deductStock(...)` and moved the existing product SELECT from before the conditional UPDATE to after it, deriving stock-log values as `before = after + quantity`. These candidate edits were reviewed and restored to HEAD; no production optimization remains.

For the single-product workload, the candidate does not eliminate a query and leaves the stock-log INSERT, Rabbit call, and transaction completion unchanged. It moves the product read to after the conditional update, so the change does not clearly shorten the critical transaction path. Because the raw diagnostic snapshots were not versioned and no direct lock timing was captured, the report cannot quantify any lock-interval reduction.

The accepted warm submission P95 is 55 ms, so the predeclared 10% gate requires at least 5.5 ms improvement. Queueing can amplify lock-hold changes, but neither versioned direct hold/wait measurements nor a like-for-like retest establish a material improvement. The candidate is therefore rejected for insufficient causal and reproducible evidence, not reported as a measured failed optimization.

Code inspection finds that `submit` and `deductStock` use the existing Spring transaction, `ShoppingCartServiceImpl.clean` directly invokes its mapper, and stock failures throw a runtime `BaseException`. Moving the deletion earlier therefore appears to preserve rollback of cart/order/inventory writes and the conditional stock guard. It also changes lock acquisition order and extends cart-lock lifetime through the inventory wait; no concurrent transaction test validates those effects. Changing audit values is a separate correctness concern, not evidence of performance gain. The mock-based candidate tests, including `OrderSubmissionLockOrderingTest`, supply fixed values and check call order or arithmetic; they cannot establish database locking, concurrent audit accuracy, or latency reduction. No reproducible RED/GREEN evidence was supplied for the interrupted attempt.

## Evidence required before a replacement change

No replacement production change is selected. Continue with bounded, isolated measurement before implementation:

1. Correlate UPDATE row-lock wait/holder evidence with time from successful UPDATE through stock-log insertion, Rabbit publication, and transaction completion. Capture distributions, not only aggregate statement averages.
2. Establish whether the removable time is database commit/I/O, application work, or synchronous messaging while holding the lock. Verify the single hot product and actual index/access path. Use unchanged baseline durability and transaction semantics.
3. If meaningful reduction requires moving timeout publication outside the transaction, introducing durable event delivery, changing inventory contention design, or altering durability settings, obtain an explicit design decision. Those changes are not implied by the measured SQL ranking and must not be smuggled into an ordering change.
4. For the selected cause, reproduce a real regression before implementation and preserve RED/GREEN evidence. A mock call-order assertion alone is insufficient for a lock-contention hypothesis. Verify atomic deduction, rollback, and relevant concurrency behavior.

Do not move Rabbit publication before successful deduction or commit inventory independently of the order. Both would alter failure semantics. Do not change the hot-product dataset or concurrency to manufacture an improvement.

The existing acceptance gate remains unchanged: an identical 10-user, 10-second-ramp, 60-second order-submit run must keep every correctness gate at zero failures and improve P95 by at least 10%, without reducing throughput or worsening P99 by more than 5%. Record endpoint and whole-workload metrics with explicit scope, cache state, dataset, and image provenance. For the warm submission sampler, the 55 ms baseline P95 implies a target of at most 49.5 ms. Otherwise revert or report the result as rejected. No optimized-summary artifact or performance commit is produced for this rejected candidate.
