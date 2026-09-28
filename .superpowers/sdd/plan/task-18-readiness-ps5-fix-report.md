# Task 18: Windows PowerShell 5.1 readiness polling fix

## Scope and root cause

- The rollback candidate is expected to refuse the readiness connection briefly while the application starts.
- With the script-wide `$ErrorActionPreference = 'Stop'`, Windows PowerShell 5.1 promoted `docker exec ... wget` stderr from that expected non-zero probe into a terminating `NativeCommandError`. The loop therefore aborted instead of polling again.

## TDD evidence

RED, before the production change: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File sky-take-out/scripts/tests/rollback-ps5-tests.ps1` exited 1 with `Rollback drill has no candidate readiness function.`

GREEN, after the minimal change:

- `Invoke-CandidateReadiness` temporarily sets the error preference to `Continue`, captures the native body and exit code, and restores the previous preference in `finally`.
- Candidate polling parses `UP` or `DOWN` only when the probe exit code is zero. A non-zero probe remains transient and the loop continues.
- The regression uses a temporary `docker.cmd` shim under Windows PowerShell 5.1. Exit 4 plus connection-refused stderr returns a pollable result without throwing; exit 0 preserves a recognizable `{"status":"DOWN"}` body. The shim directory, PATH, environment variable, and test preference are restored in `finally`.

## Mutation evidence

Both mutations used temporary copies and were removed after execution:

- Removing the readiness function's EAP downgrade made the regression exit 1 because connection-refused stderr became terminating and no exit 4 result was returned.
- Removing the readiness function's EAP restoration made the regression exit 1 with both transient and successful probe restoration failures.

## Verification

| Check | Result |
| --- | --- |
| `scripts/tests/rollback-ps5-tests.ps1` | Exit 0; Flyway argv, transient readiness, DOWN response, stop stderr, and non-zero stop checks passed |
| `scripts/rollback-drill.ps1 -ConfigOnly` | Exit 0; static rollback contract passed |
| `scripts/rollback-drill.ps1 -CleanupLifecycleTest` | Exit 0; cleanup aggregation passed; simulated warning expected |
| `scripts/rollback-drill.ps1 -CandidateLifecycleTest` | Exit 0; candidate lifecycle passed without Docker mutation |
| `scripts/tests/image-revision-tests.ps1` | Exit 0; compose-smoke and rollback image revision checks passed |
| `git diff --check` on scoped files | Exit 0 |

## Remaining risk

The full Docker rollback drill was intentionally not run in this checkpoint. The main task must run it with a fresh RunId. The failed `task18-final2-0925` dependency containers and volumes were not cleaned, adopted, or reused.
