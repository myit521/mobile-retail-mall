[CmdletBinding()]
param(
    [int]$TimeoutSeconds = 240,
    [switch]$ConfigOnly,
    [string]$StableRevision,
    [string]$CandidateRevision,
    [string]$RunId,
    [switch]$OwnershipOnly,
    [ValidateRange(0, 30)][int]$OwnershipHoldSeconds = 0,
    [switch]$CandidateLifecycleTest,
    [switch]$CleanupLifecycleTest
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryRoot = (Resolve-Path (Join-Path $root '..')).Path
$composeFile = Join-Path $root 'compose.yml'
$envFile = Join-Path $root '.env.example'

function Invoke-GitRevision([string]$Reference) {
    $safeRoot = $repositoryRoot -replace '\\', '/'
    $output = & git -c "safe.directory=$safeRoot" -C $repositoryRoot rev-parse "$Reference^{commit}"
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw "Unable to resolve Git revision '$Reference'" }
    $revision = ([string]$output).Trim()
    if ($revision -notmatch '^[0-9a-f]{40}$') { throw "'$Reference' did not resolve to a full Git revision" }
    return $revision
}

function Invoke-Compose {
    & docker compose --project-name $projectName --file $composeFile --env-file $envFile @args
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

function Assert-ImageRevision([string]$Image, [string]$ExpectedRevision) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $output = @(& docker image inspect $Image 2>$null); $inspectExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previousPreference }
    if ($inspectExit -ne 0) { throw "Unable to inspect image $Image" }
    $imageInfo = ConvertFrom-Json -InputObject ($output -join "`n")
    if ($imageInfo -isnot [array] -or $imageInfo.Count -ne 1 -or
        $imageInfo[0].Id -isnot [string] -or
        $imageInfo[0].Id -cnotmatch '^sha256:[0-9a-f]{64}$' -or
        @($imageInfo[0].RepoTags) -cnotcontains $Image -or
        $imageInfo[0].Config.Labels.'org.opencontainers.image.revision' -isnot [string] -or
        $imageInfo[0].Config.Labels.'org.opencontainers.image.revision' -cne $ExpectedRevision) {
        throw "Image $Image does not carry expected revision $ExpectedRevision"
    }
}

function Read-EnvironmentFile {
    $values = @{}
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $values[$matches[1].Trim()] = $matches[2].Trim() }
    }
    return $values
}

function Assert-RequiredEnvironment([hashtable]$Values) {
    $required = @(
        'MYSQL_DATABASE', 'MYSQL_USERNAME', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD',
        'REDIS_PASSWORD', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD',
        'SKY_JWT_ADMIN_SECRET_KEY', 'SKY_JWT_USER_SECRET_KEY', 'GRAFANA_ADMIN_PASSWORD', 'VCS_REF'
    )
    $missing = @($required | Where-Object { -not $Values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($Values[$_]) })
    if ($missing.Count -gt 0) { throw "Missing required .env.example entries: $($missing -join ', ')" }
}

function Assert-ComposeEnvironmentContract {
    $composeText = Get-Content $composeFile -Raw
    $supported = @(
        'WECHAT_APP_ID', 'WECHAT_APP_SECRET', 'WECHAT_MCH_ID', 'WECHAT_MCH_SERIAL_NO',
        'WECHAT_PRIVATE_KEY_FILE', 'WECHAT_API_V3_KEY', 'WECHAT_PAY_CERT_FILE',
        'WECHAT_NOTIFY_URL', 'WECHAT_REFUND_NOTIFY_URL', 'ALIYUN_OSS_ENDPOINT',
        'ALIYUN_OSS_ACCESS_KEY_ID', 'ALIYUN_OSS_ACCESS_KEY_SECRET', 'ALIYUN_OSS_BUCKET_NAME',
        'BAIDU_MAP_AK', 'SHOP_ADDRESS', 'SILICONFLOW_API_KEY', 'SILICONFLOW_BASE_URL',
        'SILICONFLOW_MODEL', 'SILICONFLOW_TIMEOUT', 'SILICONFLOW_ENABLED',
        'SKY_WEBSOCKET_TICKET_TTL', 'SKY_WEBSOCKET_TICKET_KEY_PREFIX', 'SKY_OUTBOX_ENABLED',
        'SKY_OUTBOX_WORKER_ID', 'SKY_OUTBOX_LEASE_DURATION', 'SKY_OUTBOX_INITIAL_BACKOFF',
        'SKY_OUTBOX_MAX_BACKOFF', 'SKY_OUTBOX_CONFIRM_TIMEOUT', 'SKY_OUTBOX_MAX_ATTEMPTS',
        'SKY_OUTBOX_POLL_DELAY_MS', 'SKY_OUTBOX_HEALTH_PENDING_THRESHOLD',
        'SKY_OUTBOX_HEALTH_FAILED_THRESHOLD', 'SKY_OUTBOX_HEALTH_REFRESH_MS',
        'SKY_MESSAGING_CONSUMER_MAX_ATTEMPTS', 'MANAGEMENT_SERVER_ADDRESS', 'MANAGEMENT_SERVER_PORT'
    )
    $missing = @($supported | Where-Object { $composeText -notmatch "(?m)^\s+$([regex]::Escape($_)):\s+" })
    if ($missing.Count -gt 0) { throw "Compose does not map supported application variables: $($missing -join ', ')" }
}

function Assert-SourceRevision([string]$Revision) {
    $safeRoot = $repositoryRoot -replace '\\', '/'
    foreach ($requiredPath in @('sky-take-out/Dockerfile', 'sky-take-out/pom.xml')) {
        & git -c "safe.directory=$safeRoot" -C $repositoryRoot cat-file -e "$Revision`:$requiredPath"
        if ($LASTEXITCODE -ne 0) { throw "Revision $Revision has no $requiredPath" }
    }
}

function New-SourceContext([string]$Revision, [string]$Slot, [string]$TemporaryRoot) {
    Assert-SourceRevision $Revision
    $archive = Join-Path $TemporaryRoot "$Slot.tar"
    $contextParent = Join-Path $TemporaryRoot $Slot
    New-Item -ItemType Directory -Path $contextParent | Out-Null
    & git -c "safe.directory=$($repositoryRoot -replace '\\', '/')" -C $repositoryRoot archive --format=tar --output=$archive $Revision -- sky-take-out
    if ($LASTEXITCODE -ne 0) { throw "Unable to archive source revision $Revision" }
    & tar -xf $archive -C $contextParent
    if ($LASTEXITCODE -ne 0) { throw "Unable to extract source revision $Revision" }
    $context = Join-Path $contextParent 'sky-take-out'
    if (-not (Test-Path (Join-Path $context 'Dockerfile'))) { throw "Extracted $Slot context has no Dockerfile" }
    Set-Content -LiteralPath (Join-Path $contextParent '.source-revision') -Value $Revision -NoNewline
    if ((Get-Content -LiteralPath (Join-Path $contextParent '.source-revision') -Raw) -ne $Revision) { throw "Source identity check failed for $Slot" }
    return $context
}

function Remove-OwnedCandidate([hashtable]$State, [scriptblock]$InspectExact, [scriptblock]$RemoveExact) {
    if (-not $State.NameOwned) { return }
    $before = & $InspectExact
    if ($before.ExitCode -ne 0) { throw 'Unable to inspect owned candidate before removal' }
    if (-not $before.Exists) {
        $State.NameOwned = $false
        $State.Started = $false
        return
    }
    $removeExitCode = & $RemoveExact
    if ($removeExitCode -ne 0) { throw "Owned candidate removal failed with exit code $removeExitCode" }
    $after = & $InspectExact
    if ($after.ExitCode -ne 0) { throw 'Unable to verify owned candidate removal' }
    if ($after.Exists) { throw 'Owned candidate still exists after successful removal command' }
    $State.NameOwned = $false
    $State.Started = $false
}

function Invoke-DockerCandidateInspect {
    $match = & docker ps --all --quiet --filter "name=^/$candidateName$"
    $exitCode = $LASTEXITCODE
    return @{ ExitCode = $exitCode; Exists = [bool]$match }
}

function Invoke-DockerCandidateRemove {
    & docker rm --force $candidateName 2>$null | Out-Null
    return $LASTEXITCODE
}

function Stop-OwnedStableApp {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & docker compose --project-name $projectName --file $composeFile --env-file $envFile stop app 2>$null | Out-Null
        $stopExit = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previousPreference }
    if ($stopExit -ne 0) { throw 'Compose stop returned a non-zero exit code' }
    $appContainers = & docker compose --project-name $projectName --file $composeFile --env-file $envFile ps --all --quiet app 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect stable app after stop' }
    foreach ($container in @($appContainers)) {
        if (-not $container) { continue }
        $running = (& docker inspect --format '{{.State.Running}}' $container 2>$null).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect stable app container state after stop' }
        if ($running -eq 'true') { throw 'Stable app is still running after Compose stop' }
    }
}

function Invoke-CandidateReadiness([string]$ContainerName) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $probeOutput = @(& docker exec $ContainerName wget --server-response --output-document=- http://localhost:8081/actuator/health/readiness 2>&1)
        $probeExit = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previousPreference }
    $response = (@($probeOutput) | ForEach-Object { [string]$_ }) -join "`n"
    $statusMatches = [regex]::Matches($response, '(?im)\bHTTP/\d+(?:\.\d+)?\s+(\d{3})\b')
    $statusCode = if ($statusMatches.Count -gt 0) { [int]$statusMatches[$statusMatches.Count - 1].Groups[1].Value } else { $null }
    $state = if ($statusCode -eq 503) { 'DOWN' }
        elseif ($statusCode -eq 200 -and $probeExit -eq 0 -and $response -match '"status"\s*:\s*"UP"') { 'UP' }
        else { 'PENDING' }
    return @{ State = $state; StatusCode = $statusCode; ExitCode = $probeExit }
}

function Invoke-CleanupSteps([object[]]$Steps) {
    $failures = [Collections.Generic.List[string]]::new()
    foreach ($step in $Steps) {
        try { & $step.Action }
        catch {
            $message = ($_.Exception.Message -replace '[\r\n\t]+', ' ').Trim()
            if ($message.Length -gt 160) { $message = $message.Substring(0, 160) }
            $failures.Add("$($step.Name): $message")
        }
    }
    return ,$failures.ToArray()
}

function Complete-Cleanup([string[]]$Failures, [bool]$MainFailed) {
    if (-not $Failures -or $Failures.Count -eq 0) { return }
    $summary = $Failures -join '; '
    if ($MainFailed) {
        Write-Warning "Rollback cleanup failures (primary failure preserved): $summary"
        return
    }
    throw "Rollback cleanup failed: $summary"
}

if (-not $CandidateRevision) { $CandidateRevision = Invoke-GitRevision 'HEAD' }
else { $CandidateRevision = Invoke-GitRevision $CandidateRevision }
if (-not $StableRevision) { $StableRevision = Invoke-GitRevision 'HEAD^' }
else { $StableRevision = Invoke-GitRevision $StableRevision }
if ($StableRevision -eq $CandidateRevision) { throw 'Stable and candidate revisions must be distinct Git commits' }
if (-not $RunId) { $RunId = ([Guid]::NewGuid().ToString('N')).Substring(0, 12) }
if ($RunId -notmatch '^[a-z0-9][a-z0-9-]{7,31}$') { throw 'RunId must be 8-32 lowercase letters, digits, or hyphens and start with a letter or digit' }
$projectName = "sky-rollback-$RunId"
$candidateName = "$projectName-candidate"

$environment = Read-EnvironmentFile
Assert-RequiredEnvironment $environment
Assert-ComposeEnvironmentContract
Assert-SourceRevision $StableRevision
Assert-SourceRevision $CandidateRevision
$stableImage = "sky-take-out:$StableRevision"
$candidateImage = "sky-take-out:$CandidateRevision"
$plan = [ordered]@{
    mode = if ($ConfigOnly) { 'static-contract-only' } else { 'runtime' }
    stableRevision = $StableRevision
    candidateRevision = $CandidateRevision
    stableImage = $stableImage
    candidateImage = $candidateImage
    runId = $RunId
    composeProject = $projectName
    sentinelWrite = 'mysql INSERT into orders through the private Compose network'
    candidateFailure = 'RabbitMQ hostname is deliberately unresolvable; schema and volumes are unchanged'
    readinessGate = 'candidate internal http://localhost:8081/actuator/health/readiness must not become UP'
    rollbackSelection = "restore exact image $stableImage"
    sourceContexts = 'independent git archives for stable and candidate revisions'
    preservationChecks = @('sentinel order', 'flyway_schema_history rows')
    destructiveCleanup = $false
}

if ($ConfigOnly) {
    $plan | ConvertTo-Json -Depth 4
    Write-Host 'PASS (STATIC CONTRACT ONLY): rollback sequencing, revision selection, required variables, and non-destructive cleanup invariants validated. No containers or runtime rollback were exercised.'
    return
}

if ($CandidateLifecycleTest) {
    $partial = @{ NameOwned = $true; Started = $false }
    $partialInspections = [Collections.Queue]::new()
    $partialInspections.Enqueue($true)
    $partialInspections.Enqueue($false)
    Remove-OwnedCandidate $partial { @{ ExitCode = 0; Exists = $partialInspections.Dequeue() } } { 0 }
    if ($partial.NameOwned -or $partial.Started) { throw 'Partial-create cleanup did not clear ownership after verified removal' }

    $failed = @{ NameOwned = $true; Started = $true }
    $failureObserved = $false
    try { Remove-OwnedCandidate $failed { @{ ExitCode = 0; Exists = $true } } { 23 } }
    catch { $failureObserved = $true }
    if (-not $failureObserved -or -not $failed.NameOwned -or -not $failed.Started) {
        throw 'Removal failure did not retain candidate ownership/start state'
    }

    $removed = @{ NameOwned = $true; Started = $true }
    $successInspections = [Collections.Queue]::new()
    $successInspections.Enqueue($true)
    $successInspections.Enqueue($false)
    Remove-OwnedCandidate $removed { @{ ExitCode = 0; Exists = $successInspections.Dequeue() } } { 0 }
    if ($removed.NameOwned -or $removed.Started) { throw 'Verified removal did not clear candidate state' }
    Write-Output 'PASS (CANDIDATE LIFECYCLE): partial-create cleanup, removal failure retention, and verified removal state clearing passed without Docker.'
    return
}

if ($CleanupLifecycleTest) {
    $executed = [Collections.Generic.List[string]]::new()
    $steps = @(
        [pscustomobject]@{ Name = 'candidate'; Action = { $executed.Add('candidate') | Out-Null } },
        [pscustomobject]@{ Name = 'stable'; Action = { $executed.Add('stable') | Out-Null; throw 'simulated stable stop exit' } },
        [pscustomobject]@{ Name = 'temp'; Action = { $executed.Add('temp') | Out-Null; throw 'simulated temp delete failure' } },
        [pscustomobject]@{ Name = 'lock-dispose'; Action = { $executed.Add('lock-dispose') | Out-Null; throw 'simulated lock dispose failure' } },
        [pscustomobject]@{ Name = 'lock-delete'; Action = { $executed.Add('lock-delete') | Out-Null; throw 'simulated lock delete failure' } }
    )
    $failures = Invoke-CleanupSteps $steps
    if (($executed -join ',') -ne 'candidate,stable,temp,lock-dispose,lock-delete' -or $failures.Count -ne 4) {
        throw 'Cleanup aggregation stopped early or lost a failure'
    }
    $successPathFailed = $false
    try { Complete-Cleanup $failures $false }
    catch { $successPathFailed = $true }
    if (-not $successPathFailed) { throw 'Successful main path ignored cleanup failures' }
    $caughtPrimary = $null
    try {
        try { throw 'simulated main failure' }
        finally { Complete-Cleanup $failures $true -WarningAction SilentlyContinue }
    }
    catch { $caughtPrimary = $_.Exception.Message }
    if ($caughtPrimary -ne 'simulated main failure') { throw 'Cleanup failure replaced the simulated primary failure' }
    Write-Output 'PASS (CLEANUP LIFECYCLE): all cleanup steps ran, failures aggregated, primary retained, and success-plus-cleanup-failure became non-zero.'
    return
}

$temporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$lockDirectory = [IO.Path]::GetFullPath((Join-Path $temporaryBase 'sky-rollback-locks'))
if (-not $lockDirectory.StartsWith($temporaryBase, [StringComparison]::OrdinalIgnoreCase)) { throw 'Ownership lock directory escaped the system temp directory' }
[IO.Directory]::CreateDirectory($lockDirectory) | Out-Null
$lockPath = [IO.Path]::GetFullPath((Join-Path $lockDirectory "$RunId.lock"))
if ((Split-Path $lockPath -Parent) -ne $lockDirectory) { throw 'Ownership lock path escaped the lock directory' }
$invocationId = [Guid]::NewGuid().ToString('N')
$temporaryRoot = [IO.Path]::GetFullPath((Join-Path $temporaryBase "sky-rollback-$RunId-$invocationId"))
if (-not $temporaryRoot.StartsWith($temporaryBase, [StringComparison]::OrdinalIgnoreCase)) { throw 'Temporary source root escaped the system temp directory' }
$lockHandle = $null
try {
    $lockHandle = [IO.File]::Open($lockPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
}
catch [IO.IOException] {
    throw "RunId '$RunId' already has an active or stale ownership lock at $lockPath. Refusing to adopt it; remove a stale lock only after independently confirming no owner process is alive."
}

$hadVcsRef = Test-Path Env:VCS_REF
$previousVcsRef = $env:VCS_REF
$stableStarted = $false
$candidateState = @{ NameOwned = $false; Started = $false }
$primaryFailure = $false
$sentinel = "rollback-drill-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"

try {
    $ownerBytes = [Text.Encoding]::UTF8.GetBytes("pid=$PID;created=$([DateTimeOffset]::UtcNow.ToString('O'))")
    $lockHandle.Write($ownerBytes, 0, $ownerBytes.Length)
    $lockHandle.Flush()
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    $ownerMarker = Join-Path $temporaryRoot 'owner.marker'
    Set-Content -LiteralPath $ownerMarker -Value $invocationId -NoNewline
    if ($OwnershipOnly) {
        Write-Output "OWNERSHIP_ACQUIRED=$ownerMarker"
        if ($OwnershipHoldSeconds -gt 0) { Start-Sleep -Seconds $OwnershipHoldSeconds }
        if (-not (Test-Path -LiteralPath $ownerMarker)) { throw 'The invocation ownership marker was removed by another process' }
        Write-Output 'PASS (OWNERSHIP ONLY): exclusive RunId ownership remained held; no Docker command was invoked.'
        return
    }

    $candidatePreflight = Invoke-DockerCandidateInspect
    if ($candidatePreflight.ExitCode -ne 0) { throw 'Unable to inspect exact candidate container before mutation' }
    $existingContainers = & docker ps --all --quiet --filter "label=com.docker.compose.project=$projectName"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Compose project containers before mutation' }
    $existingNetworks = & docker network ls --quiet --filter "label=com.docker.compose.project=$projectName"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Compose project networks before mutation' }
    $existingVolumes = & docker volume ls --quiet --filter "label=com.docker.compose.project=$projectName"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Compose project volumes before mutation' }
    if ($candidatePreflight.Exists -or $existingContainers -or $existingNetworks -or $existingVolumes) {
        throw "RunId '$RunId' is already represented by existing Docker resources; refusing to adopt or mutate them"
    }

    $stableContext = New-SourceContext $StableRevision 'stable' $temporaryRoot
    $candidateContext = New-SourceContext $CandidateRevision 'candidate' $temporaryRoot
    & docker build --build-arg "VCS_REF=$StableRevision" --tag $stableImage $stableContext
    if ($LASTEXITCODE -ne 0) { throw 'Stable image build failed' }
    & docker build --build-arg "VCS_REF=$CandidateRevision" --tag $candidateImage $candidateContext
    if ($LASTEXITCODE -ne 0) { throw 'Candidate image build failed' }

    foreach ($image in @($stableImage, $candidateImage)) {
        $expected = if ($image -eq $stableImage) { $StableRevision } else { $CandidateRevision }
        Assert-ImageRevision $image $expected
    }

    $env:VCS_REF = $StableRevision
    $stableStarted = $true
    Invoke-Compose up --detach --no-build --wait --wait-timeout $TimeoutSeconds mysql redis rabbitmq app

    $flywayBefore = Invoke-Compose exec --no-TTY mysql mysql --batch --skip-column-names `
        "--user=$($environment.MYSQL_USERNAME)" "--password=$($environment.MYSQL_PASSWORD)" "--database=$($environment.MYSQL_DATABASE)" `
        --execute='SELECT CONCAT(installed_rank,CHAR(124),version,CHAR(124),description,CHAR(124),type,CHAR(124),checksum,CHAR(124),success) FROM flyway_schema_history ORDER BY installed_rank;'
    if (@($flywayBefore).Count -lt 1) { throw 'No Flyway history was present after stable startup' }

    $insertSql = "INSERT INTO orders(number,status,user_id,order_time,pay_method,pay_status,amount,remark) VALUES('$sentinel',1,9223372036854770000,UTC_TIMESTAMP(),1,0,0.01,'rollback drill sentinel');"
    Invoke-Compose exec --no-TTY mysql mysql --batch --skip-column-names `
        "--user=$($environment.MYSQL_USERNAME)" "--password=$($environment.MYSQL_PASSWORD)" "--database=$($environment.MYSQL_DATABASE)" `
        "--execute=$insertSql"

    Invoke-Compose stop app
    # The exclusive RunId lock plus clean preflight grants this invocation ownership of the name
    # before Docker can partially create it and still return a non-zero exit code.
    $candidateState.NameOwned = $true
    & docker run --detach --name $candidateName --network "$projectName`_default" `
        --env SPRING_PROFILES_ACTIVE=dev --env MYSQL_HOST=mysql --env MYSQL_PORT=3306 `
        --env "MYSQL_DATABASE=$($environment.MYSQL_DATABASE)" --env "MYSQL_USERNAME=$($environment.MYSQL_USERNAME)" --env "MYSQL_PASSWORD=$($environment.MYSQL_PASSWORD)" `
        --env REDIS_HOST=redis --env REDIS_PORT=6379 --env REDIS_DATABASE=0 --env "REDIS_PASSWORD=$($environment.REDIS_PASSWORD)" `
        --env SKY_RABBITMQ_HOST=rollback-drill-unready.invalid --env SKY_RABBITMQ_PORT=5672 `
        --env "SKY_RABBITMQ_USERNAME=$($environment.RABBITMQ_USERNAME)" --env "SKY_RABBITMQ_PASSWORD=$($environment.RABBITMQ_PASSWORD)" `
        --env MANAGEMENT_SERVER_ADDRESS=0.0.0.0 --env MANAGEMENT_SERVER_PORT=8081 `
        --env "SKY_JWT_ADMIN_SECRET_KEY=$($environment.SKY_JWT_ADMIN_SECRET_KEY)" --env "SKY_JWT_USER_SECRET_KEY=$($environment.SKY_JWT_USER_SECRET_KEY)" `
        --env SILICONFLOW_ENABLED=false $candidateImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Candidate container failed to start' }
    $candidateState.Started = $true

    $candidateBecameReady = $false
    $candidateReportedDown = $false
    $deadline = [DateTime]::UtcNow.AddSeconds([Math]::Min($TimeoutSeconds, 60))
    while ([DateTime]::UtcNow -lt $deadline) {
        $isRunning = (& docker inspect --format '{{.State.Running}}' $candidateName 2>$null).Trim()
        if ($LASTEXITCODE -ne 0 -or $isRunning -ne 'true') { throw 'Candidate crashed instead of remaining alive with an unready RabbitMQ dependency' }
        $candidateEnvironment = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $candidateName
        if ($LASTEXITCODE -ne 0 -or $candidateEnvironment -notcontains 'SKY_RABBITMQ_HOST=rollback-drill-unready.invalid') {
            throw 'Candidate does not contain the intended RabbitMQ fault injection'
        }
        $readiness = Invoke-CandidateReadiness $candidateName
        if ($readiness.State -eq 'UP') { $candidateBecameReady = $true; break }
        if ($readiness.State -eq 'DOWN') { $candidateReportedDown = $true; break }
        Start-Sleep -Seconds 2
    }
    if ($candidateBecameReady) { throw 'Intentionally unready candidate unexpectedly passed the readiness gate' }
    if (-not $candidateReportedDown) { throw 'Candidate stayed alive but did not return an explicit readiness DOWN response' }

    Remove-OwnedCandidate $candidateState ${function:Invoke-DockerCandidateInspect} ${function:Invoke-DockerCandidateRemove}
    $env:VCS_REF = $StableRevision
    Invoke-Compose up --detach --no-build --wait --wait-timeout $TimeoutSeconds app

    $appContainer = (Invoke-Compose ps --quiet app | Select-Object -Last 1).Trim()
    $runningImage = (& docker inspect --format '{{.Config.Image}}' $appContainer).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect restored application container' }
    if ($runningImage -ne $stableImage) { throw "Rollback selected '$runningImage' instead of '$stableImage'" }

    $sentinelCount = Invoke-Compose exec --no-TTY mysql mysql --batch --skip-column-names `
        "--user=$($environment.MYSQL_USERNAME)" "--password=$($environment.MYSQL_PASSWORD)" "--database=$($environment.MYSQL_DATABASE)" `
        "--execute=SELECT COUNT(*) FROM orders WHERE number='$sentinel';"
    if (($sentinelCount | Select-Object -Last 1).Trim() -ne '1') { throw 'Sentinel order was not preserved by rollback' }

    $flywayAfter = Invoke-Compose exec --no-TTY mysql mysql --batch --skip-column-names `
        "--user=$($environment.MYSQL_USERNAME)" "--password=$($environment.MYSQL_PASSWORD)" "--database=$($environment.MYSQL_DATABASE)" `
        --execute='SELECT CONCAT(installed_rank,CHAR(124),version,CHAR(124),description,CHAR(124),type,CHAR(124),checksum,CHAR(124),success) FROM flyway_schema_history ORDER BY installed_rank;'
    if (($flywayBefore -join "`n") -ne ($flywayAfter -join "`n")) { throw 'Flyway history changed during failed candidate deployment and rollback' }

    Write-Host "PASS: candidate $CandidateRevision failed readiness, exact stable image $StableRevision was restored, and sentinel/Flyway history were preserved."
}
catch {
    $primaryFailure = $true
    throw
}
finally {
    $cleanupSteps = @(
        [pscustomobject]@{ Name = 'candidate-remove'; Action = {
            if ($candidateState.NameOwned) {
                Remove-OwnedCandidate $candidateState ${function:Invoke-DockerCandidateInspect} ${function:Invoke-DockerCandidateRemove}
            }
        } },
        [pscustomobject]@{ Name = 'stable-stop'; Action = {
            if ($stableStarted) { Stop-OwnedStableApp }
        } },
        [pscustomobject]@{ Name = 'source-temp-delete'; Action = {
            if (Test-Path -LiteralPath $temporaryRoot) {
                $resolvedTemporaryRoot = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $temporaryRoot).Path)
                if (-not $resolvedTemporaryRoot.StartsWith($temporaryBase, [StringComparison]::OrdinalIgnoreCase) -or
                    (Split-Path $resolvedTemporaryRoot -Leaf) -ne "sky-rollback-$RunId-$invocationId") {
                    throw 'Refusing to delete a source temp path not owned by this invocation'
                }
                Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
                if (Test-Path -LiteralPath $resolvedTemporaryRoot) { throw 'Source temp path still exists after deletion' }
            }
        } },
        [pscustomobject]@{ Name = 'environment-restore'; Action = {
            if ($hadVcsRef) { $env:VCS_REF = $previousVcsRef }
            else { Remove-Item Env:VCS_REF -ErrorAction SilentlyContinue }
        } },
        [pscustomobject]@{ Name = 'lock-handle-dispose'; Action = {
            if ($lockHandle) { $lockHandle.Dispose() }
        } },
        [pscustomobject]@{ Name = 'lock-file-delete'; Action = {
            if ($lockHandle -and (Test-Path -LiteralPath $lockPath)) {
                Remove-Item -LiteralPath $lockPath -Force
                if (Test-Path -LiteralPath $lockPath) { throw 'Ownership lock file still exists after owner deletion' }
            }
        } }
    )
    $cleanupFailures = Invoke-CleanupSteps $cleanupSteps
    Complete-Cleanup $cleanupFailures $primaryFailure
    # Deliberately keep dependency containers and named volumes. Never use `down -v`, `volume rm`, DROP, or database recreation here.
}
