# Task 18: rollback readiness HTTP status classification

## Scope and root cause

- Spring Boot Actuator returns HTTP 503 when readiness is `DOWN`.
- BusyBox `wget` exits 1 for that response, emits `HTTP/1.1 503` only with `--server-response` on stderr, and does not emit the JSON body.
- A refused connection also exits non-zero but has no HTTP status. Treating exit code as readiness state therefore cannot distinguish expected `DOWN` from a transient listener startup failure.

## TDD evidence

RED, before the production change: the Windows PowerShell 5.1 regression exited 1. The native `docker.cmd` shim reported all three missing classifications: connection refusal had no `PENDING` state, HTTP 503 had no `DOWN` state, and HTTP 200 plus an `UP` body had no `UP` state.

GREEN, after the minimal change:

- `Invoke-CandidateReadiness` invokes BusyBox `wget --server-response`, merges stdout and stderr under a temporary `ErrorActionPreference = 'Continue'`, captures the native exit code, and restores the preference in `finally`.
- The final HTTP status in the response is parsed. Status 503 maps to `DOWN`; status 200 plus exit 0 and an `UP` body maps to `UP`; output without an HTTP status remains `PENDING`.
- The polling loop consumes only the returned `State`. Timeout, container liveness, and injected-environment checks are unchanged.

## Mutation evidence

All mutations used temporary copies and were deleted after execution:

- Removing `--server-response` exited 1: 503 and 200 had no status and stayed `PENDING`.
- Removing the 503 classification exited 1: status 503 was captured but stayed `PENDING`.
- Removing the EAP downgrade exited 1 with `NativeCommandError` on the simulated 503 stderr response.
- Removing EAP restoration exited 1 for the refused, DOWN, and UP probe restoration assertions.

## Verification

| Check | Result |
| --- | --- |
| `scripts/tests/rollback-ps5-tests.ps1` | Exit 0; refused=PENDING, HTTP 503=DOWN, HTTP 200 plus UP body=UP |
| `scripts/rollback-drill.ps1 -ConfigOnly` | Exit 0; static rollback contract passed |
| `scripts/rollback-drill.ps1 -CleanupLifecycleTest` | Exit 0; cleanup aggregation passed; simulated warning expected |
| `scripts/rollback-drill.ps1 -CandidateLifecycleTest` | Exit 0; candidate lifecycle passed without Docker mutation |
| `scripts/tests/image-revision-tests.ps1` | Exit 0; compose-smoke and rollback image revision checks passed |

## Remaining risk

The full Docker rollback drill was intentionally not run in this checkpoint. The main task must rerun it with a fresh RunId. Existing `task18-final2-0925` and `task18-final3-0925` dependency containers and volumes were not cleaned, adopted, or reused.
