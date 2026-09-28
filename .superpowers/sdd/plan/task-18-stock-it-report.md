# Task 18 StockConcurrencyIT recovery report

Status: **DONE**

## Root cause and scope decision

- The interrupted partial change deleted the `SimpleMeterRegistry` / `BusinessMetrics` fixture and the assertion that one losing deduction increments `sky.stock.failures{operation=deduct}`. With that deletion, the focused Docker-backed integration suite passed 5/5.
- Restoring the original fixture and assertion reproduced the acceptance failure exactly: `MeterNotFoundException` at `StockConcurrencyIT.java:116`, with no meter named `sky.stock.failures` registered.
- The fixture itself was valid. The concurrent helper invoked `InventoryProductMapper.deductIfAvailable` directly, while production increments the failure counter only in `StockServiceImpl.deductStock` after a zero-row conditional update (or a missing product). The test injected metrics into the service but bypassed that service for the deduction race.
- Deleting the assertion would weaken an explicit Task 13 requirement. Task 13 requires business counters to increment at tested production decision points, and its accepted fix re-review specifically names `StockConcurrencyIT` as the evidence that exactly one failed deduction is counted.
- The correction is test-only: keep the real synchronized MySQL transactions and atomic mapper operation, but route both concurrent attempts through the real `StockServiceImpl.deductStock` decision path. The helper converts the expected losing `BaseException` into the existing `1/0` winner result. No production source or unrelated fixture changed.

## TDD evidence

### Current partial-change reproduction

Command:

`mvn -f sky-take-out/pom.xml -B -pl sky-server -am "-Dtest=__NoUnitTests__" "-Dit.test=StockConcurrencyIT" verify`

Result before restoring the metric evidence: 5 tests, 0 failures, 0 errors, `BUILD SUCCESS`. This proved only that deleting the assertion hid the defect.

### RED

Restored the original registry injection and exact counter assertion, then ran the same command.

Result: 5 tests, 0 failures, 1 error, `BUILD FAILURE`. `twoSimultaneousLastItemDeductionsHaveOneWinnerAndNeverGoNegative` raised `MeterNotFoundException`: no `sky.stock.failures` meter existed. The failure was expected because both workers still called the mapper directly and never entered the service decision point.

### GREEN

Changed only the test fixture/helper so both synchronized workers invoke `StockServiceImpl.deductStock` inside the existing explicit `READ_COMMITTED` transaction. The winner commits the stock/log change; the expected loser observes the zero-row conditional update, increments the injected metric once, and returns the existing losing result to the assertion.

Command (four consecutive final-version runs, including the fresh post-self-review run):

`mvn -f sky-take-out/pom.xml -B -pl sky-server -am "-Dtest=__NoUnitTests__" "-Dit.test=StockConcurrencyIT" verify`

Result each run: 5 tests, 0 failures, 0 errors, `BUILD SUCCESS`. Docker Engine 29.7.2 and the real Testcontainers MySQL service were used.

## Related regression verification

Command:

`mvn -f sky-take-out/pom.xml -B -pl sky-server -am "-Dtest=StockServiceImplTest,StockServiceImplContextTest,ActuatorMetricsIT" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Result: 8 tests, 0 failures, 0 errors, `BUILD SUCCESS`.

No full reactor `verify` was run, per the recovery task's explicit scope. Maven emitted existing compiler/deprecation and Flyway/MySQL-version warnings; there were no test failures or errors in the successful runs.

## Files changed

- `sky-take-out/sky-server/src/test/java/com/sky/inventory/StockConcurrencyIT.java`
- `.superpowers/sdd/plan/task-18-stock-it-report.md`

## Self-review

- The original one-winner, final-zero, never-negative, and exactly-one-failure-metric assertions remain intact.
- The latch still opens inside two real database transactions, so the change does not weaken concurrency coverage.
- Distinct order IDs are used for the two production service calls and are already inside the fixture's cleanup range.
- The production service proxy remains in use for release tests; the deduction helper calls the same service target inside its explicit transaction so the expected exception can be converted to the existing loser result without marking the outer transaction rollback-only.
- `git diff --check` reports no whitespace errors (only the repository's existing LF-to-CRLF warning).
- No production source, other integration test, Task 17 line-ending file, user file, documentation set, or performance artifact was modified by this recovery.

## Concerns

None within the authorized scope. The broader Task 18 acceptance failures remain separate and were not rerun or changed here.
