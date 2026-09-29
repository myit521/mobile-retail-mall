# Task 18: Windows PowerShell 5.1 rollback drill fix

## Scope and cause

- The two Flyway fingerprint queries passed `"|"` through a native executable argument. Windows PowerShell 5.1 removed those double quotes, so MySQL received an invalid `CONCAT(installed_rank,|,version,...)` expression.
- Docker Compose can write normal stop progress to stderr. With `$ErrorActionPreference = 'Stop'`, that progress could interrupt stable-app cleanup before its exit code was checked.

## Changes

- Both before and after Flyway queries now use `CHAR(124)` between the same six fields: `installed_rank|version|description|type|checksum|success`. The full-row before/after comparison remains unchanged.
- `Stop-OwnedStableApp` temporarily uses `Continue` only for `docker compose stop app`, records its native exit code, restores the prior preference in `finally`, rejects a nonzero exit code, and retains the post-stop container-state check.
- Added `scripts/tests/rollback-ps5-tests.ps1`. It extracts both production SQL arguments and checks their actual Windows PowerShell 5.1 native argv delivery; it also executes the production stop function with simulated Docker stderr progress and exit codes.

## RED and GREEN evidence

RED, before production edits: `powershell.exe -NoProfile -File sky-take-out/scripts/tests/rollback-ps5-tests.ps1` exited 1. It reported both Flyway arguments reaching the native probe as `CONCAT(installed_rank,|,version,...)`, normal stop progress failing cleanup, and progress masking the nonzero stop exit.

GREEN, after production edits:

| Check | Result |
| --- | --- |
| `scripts/tests/rollback-ps5-tests.ps1` | Exit 0; both Flyway argv checks, normal stderr progress, and nonzero stop exit passed |
| `scripts/rollback-drill.ps1 -ConfigOnly` | Exit 0; static contract passed |
| `scripts/rollback-drill.ps1 -CleanupLifecycleTest` | Exit 0; cleanup aggregation passed; its simulated warning is expected |
| `scripts/rollback-drill.ps1 -CandidateLifecycleTest` | Exit 0; candidate lifecycle passed |
| `scripts/tests/image-revision-tests.ps1` | Exit 0; compose smoke and rollback image revision verifiers passed |
| `git diff --check` on the scoped implementation and test files | Exit 0 |

## Remaining runtime check

The full rollback runtime was intentionally left to the main task. The existing owned project `sky-rollback-task18-final-0925` app was already `Exited (143)` when inspected, so it could not provide a meaningful fresh stop-progress test. No project volumes were deleted.

## Review follow-up: verify restoration inside the stop function

The original test checked `$ErrorActionPreference` after `Stop-OwnedStableApp` returned. PowerShell function scope made that check insufficient: the caller's value remained `Stop` even if the function failed to restore its own local preference. The Docker test double now records the preference seen by the subsequent `compose ps` call inside `Stop-OwnedStableApp` and requires `Stop` there.

Mutation RED: a temporary copy of the production script replaced only the stop function's `finally { $ErrorActionPreference = $previousPreference }` with `finally { }`. The corrected regression exited 1 with `Stable app post-stop inspection saw ErrorActionPreference=Continue instead of Stop.` The temporary copy was removed; the production script was not changed.

Restored GREEN: the corrected `rollback-ps5-tests.ps1`, `rollback-drill.ps1 -ConfigOnly`, `-CleanupLifecycleTest`, `-CandidateLifecycleTest`, and `image-revision-tests.ps1` each exited 0 under Windows PowerShell 5.1.
