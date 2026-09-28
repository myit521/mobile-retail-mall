param([string]$RollbackDrillPath = (Join-Path $PSScriptRoot '../rollback-drill.ps1'))

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) { throw 'Run this regression under Windows PowerShell 5.1.' }

$path = (Resolve-Path $RollbackDrillPath).Path
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Rollback drill has parse errors.' }

$queries = @($ast.FindAll({
    param($node)
    $node -is [Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -eq 'Invoke-Compose' -and
        $node.Extent.Text -match 'flyway_schema_history'
}, $true))
if ($queries.Count -ne 2) { throw "Expected two Flyway fingerprint queries, found $($queries.Count)." }

$probePath = Join-Path ([IO.Path]::GetTempPath()) "rollback-argv-$([Guid]::NewGuid().ToString('N')).js"
$failures = @()
try {
    Set-Content -LiteralPath $probePath -Value 'WScript.Echo(WScript.Arguments.Item(0));' -Encoding ASCII
    $expected = '--execute=SELECT CONCAT(installed_rank,CHAR(124),version,CHAR(124),description,CHAR(124),type,CHAR(124),checksum,CHAR(124),success) FROM flyway_schema_history ORDER BY installed_rank;'
    foreach ($query in $queries) {
        $argument = [string]$query.CommandElements[-1].SafeGetValue()
        $delivered = @(& cscript.exe //nologo $probePath $argument)
        if ($LASTEXITCODE -ne 0 -or $delivered.Count -ne 1 -or $delivered[0] -cne $expected) {
            $failures += "Flyway fingerprint SQL was changed by native argv: $($delivered -join ' ')"
        }
    }
}
finally { Remove-Item -LiteralPath $probePath -Force }
if (-not $failures.Count) { Write-Output 'PASS: both Flyway fingerprints survive Windows PowerShell 5.1 native argv unchanged.' }

$readiness = $ast.Find({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-CandidateReadiness'
}, $true)
if (-not $readiness) { throw 'Rollback drill has no candidate readiness function.' }
. ([scriptblock]::Create($readiness.Extent.Text))

$readinessShimDirectory = Join-Path ([IO.Path]::GetTempPath()) "rollback-readiness-$([Guid]::NewGuid().ToString('N'))"
$previousPath = $env:PATH
$previousMode = $env:ROLLBACK_READINESS_TEST_MODE
$expectedReadinessPreference = $ErrorActionPreference
$readinessFailureCount = $failures.Count
try {
    New-Item -ItemType Directory -Path $readinessShimDirectory | Out-Null
    $dockerShim = Join-Path $readinessShimDirectory 'docker.cmd'
    Set-Content -LiteralPath $dockerShim -Encoding ASCII -Value @(
        '@echo off'
        'if "%ROLLBACK_READINESS_TEST_MODE%"=="refused" ('
        '  echo wget: connection refused 1>&2'
        '  exit /b 1'
        ')'
        'if "%ROLLBACK_READINESS_TEST_MODE%"=="down" ('
        '  echo %* | findstr /C:"--server-response" >nul'
        '  if not errorlevel 1 echo   HTTP/1.1 503 Service Unavailable 1>&2'
        '  exit /b 1'
        ')'
        'if "%ROLLBACK_READINESS_TEST_MODE%"=="up" ('
        '  echo %* | findstr /C:"--server-response" >nul'
        '  if not errorlevel 1 echo   HTTP/1.1 200 OK 1>&2'
        '  echo {"status":"UP"}'
        '  exit /b 0'
        ')'
        'echo unexpected readiness test mode 1>&2'
        'exit /b 9'
    )
    $env:PATH = "$readinessShimDirectory;$previousPath"

    $env:ROLLBACK_READINESS_TEST_MODE = 'refused'
    $refused = $null
    try { $refused = . Invoke-CandidateReadiness 'candidate-test' }
    catch { $failures += "Transient readiness failure became terminating: $($_.Exception.GetType().Name): $($_.Exception.Message)" }
    if ($refused.ExitCode -ne 1 -or $refused.StatusCode -or $refused.State -ne 'PENDING') {
        $failures += "Connection refusal was not classified PENDING without an HTTP status: exit=$($refused.ExitCode), status=$($refused.StatusCode), state=$($refused.State)"
    }
    if ($ErrorActionPreference -ne 'Stop') { $failures += 'Transient readiness probe did not restore ErrorActionPreference.' }

    $env:ROLLBACK_READINESS_TEST_MODE = 'down'
    $down = . Invoke-CandidateReadiness 'candidate-test'
    if ($down.ExitCode -ne 1 -or $down.StatusCode -ne 503 -or $down.State -ne 'DOWN') {
        $failures += "HTTP 503 readiness was not classified DOWN: exit=$($down.ExitCode), status=$($down.StatusCode), state=$($down.State)"
    }
    if ($ErrorActionPreference -ne 'Stop') { $failures += 'DOWN readiness probe did not restore ErrorActionPreference.' }

    $env:ROLLBACK_READINESS_TEST_MODE = 'up'
    $up = . Invoke-CandidateReadiness 'candidate-test'
    if ($up.ExitCode -ne 0 -or $up.StatusCode -ne 200 -or $up.State -ne 'UP') {
        $failures += "HTTP 200 readiness body was not classified UP: exit=$($up.ExitCode), status=$($up.StatusCode), state=$($up.State)"
    }
    if ($ErrorActionPreference -ne 'Stop') { $failures += 'UP readiness probe did not restore ErrorActionPreference.' }
}
finally {
    $ErrorActionPreference = $expectedReadinessPreference
    $env:PATH = $previousPath
    if ($null -eq $previousMode) { Remove-Item Env:ROLLBACK_READINESS_TEST_MODE -ErrorAction SilentlyContinue }
    else { $env:ROLLBACK_READINESS_TEST_MODE = $previousMode }
    if (Test-Path -LiteralPath $readinessShimDirectory) { Remove-Item -LiteralPath $readinessShimDirectory -Recurse -Force }
}
if ($failures.Count -gt $readinessFailureCount) {
    throw ($failures[$readinessFailureCount..($failures.Count - 1)] -join "`n")
}
if ($failures.Count -eq 0) { Write-Output 'PASS: readiness classifies connection refusal as PENDING, HTTP 503 as DOWN, and HTTP 200 plus UP body as UP.' }

$stop = $ast.Find({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Stop-OwnedStableApp'
}, $true)
if (-not $stop) { throw 'Rollback drill has no stable app stop function.' }
. ([scriptblock]::Create($stop.Extent.Text))
$projectName = 'rollback-test'
$composeFile = 'compose.yml'
$envFile = '.env.example'

function docker {
    if ($args[0] -eq 'compose' -and $args -contains 'stop') {
        Write-Error 'Container rollback-test-app-1 Stopping'
        $global:LASTEXITCODE = $stopExit
        return
    }
    if ($args[0] -eq 'compose' -and $args -contains 'ps') {
        $script:preferenceAtPs = $ErrorActionPreference
        $global:LASTEXITCODE = 0
        return 'test-app-id'
    }
    if ($args[0] -eq 'inspect') {
        $global:LASTEXITCODE = 0
        return 'false'
    }
    throw "Unexpected Docker invocation: $($args -join ' ')"
}

$stopExit = 0
$script:preferenceAtPs = $null
try { Stop-OwnedStableApp } catch { $failures += "Normal Docker stop progress failed cleanup: $($_.Exception.Message)" }
if ($script:preferenceAtPs -ne 'Stop') { $failures += "Stable app post-stop inspection saw ErrorActionPreference=$script:preferenceAtPs instead of Stop." }
if ($ErrorActionPreference -ne 'Stop') { throw 'Stable app stop did not restore ErrorActionPreference.' }
if ($failures.Count -eq 0) { Write-Output 'PASS: normal Docker stop progress on stderr does not fail cleanup.' }

$stopExit = 23
$failure = $null
try { Stop-OwnedStableApp } catch { $failure = $_.Exception.Message }
if ($failure -ne 'Compose stop returned a non-zero exit code') {
    $failures += "Non-zero Docker stop was not rejected by exit code: $failure"
}
if ($ErrorActionPreference -ne 'Stop') { throw 'Failed stable app stop did not restore ErrorActionPreference.' }
if ($failure -eq 'Compose stop returned a non-zero exit code') { Write-Output 'PASS: non-zero Docker stop still fails cleanup.' }
if ($failures.Count) { throw ($failures -join "`n") }
