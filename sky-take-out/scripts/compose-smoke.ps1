[CmdletBinding()]
param(
    [int]$TimeoutSeconds = 240,
    [switch]$ConfigOnly
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$composeFile = Join-Path $root 'compose.yml'
$envFile = Join-Path $root '.env.example'
$projectName = 'sky-take-out-smoke'
$composeEnvironment = @{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $composeEnvironment[$matches[1].Trim()] = $matches[2].Trim()
    }
}

function Invoke-Compose {
    & docker compose --project-name $projectName --file $composeFile --env-file $envFile @args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Assert-ImageRevision([string]$Image, [string]$ExpectedRevision) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $output = @(& docker image inspect $Image 2>$null); $inspectExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previousPreference }
    if ($inspectExit -ne 0) { throw "Unable to inspect application image $Image" }
    $imageInfo = ConvertFrom-Json -InputObject ($output -join "`n")
    if ($imageInfo -isnot [array] -or $imageInfo.Count -ne 1 -or
        $imageInfo[0].Id -isnot [string] -or
        $imageInfo[0].Id -cnotmatch '^sha256:[0-9a-f]{64}$' -or
        @($imageInfo[0].RepoTags) -cnotcontains $Image -or
        $imageInfo[0].Config.Labels.'org.opencontainers.image.revision' -isnot [string] -or
        $imageInfo[0].Config.Labels.'org.opencontainers.image.revision' -cne $ExpectedRevision) {
        throw "Application image $Image does not carry expected revision $ExpectedRevision"
    }
}

function Wait-Until {
    param([string]$Description, [scriptblock]$Condition)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (& $Condition) { return }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $Description after $TimeoutSeconds seconds"
}

$repositoryCandidate = ((Resolve-Path (Join-Path $root '..')).Path -replace '\\', '/')
$repositoryRootOutput = & git -c "safe.directory=$repositoryCandidate" -C $root rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0 -or -not $repositoryRootOutput) { throw 'Unable to resolve repository root' }
$repositoryRoot = (([string]$repositoryRootOutput).Trim() -replace '\\', '/')
$sourceRevisionOutput = & git -c "safe.directory=$repositoryRoot" -C $repositoryRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or -not $sourceRevisionOutput) { throw 'Unable to resolve Git HEAD' }
$sourceRevision = ([string]$sourceRevisionOutput).Trim()
if ($sourceRevision -notmatch '^[0-9a-f]{40}$') { throw 'Git HEAD is not a full revision' }
$hadVcsRef = Test-Path Env:VCS_REF
$previousVcsRef = $env:VCS_REF
$env:VCS_REF = $sourceRevision
$stackAttempted = $false

try {
    $renderedJson = Invoke-Compose config --format json
    $rendered = $renderedJson | ConvertFrom-Json
    $redisHealthCommand = ([string]$rendered.services.redis.healthcheck.test[1] -replace '\s+', ' ').Trim()
    $redisServerCommand = [string]$rendered.services.redis.command[2]
    $expectedRedisHealth = 'redis-cli -a "$$REDIS_PASSWORD" ping | grep -q PONG'
    if ($redisHealthCommand -ne $expectedRedisHealth) {
        throw "Redis healthcheck must expand REDIS_PASSWORD inside the container shell; rendered: $redisHealthCommand"
    }
    if ($redisServerCommand -notmatch '\$REDIS_PASSWORD' -or $redisServerCommand -match [regex]::Escape($composeEnvironment.REDIS_PASSWORD)) {
        throw 'Redis startup command must defer password expansion to the container shell'
    }
    $rabbitHealthTest = @($rendered.services.rabbitmq.healthcheck.test)
    if ($rabbitHealthTest.Count -ne 4 -or $rabbitHealthTest[0] -cne 'CMD' -or
        $rabbitHealthTest[1] -cne 'rabbitmq-diagnostics' -or $rabbitHealthTest[2] -cne '-q' -or
        $rabbitHealthTest[3] -cne 'check_running') {
        throw 'RabbitMQ healthcheck must require the rabbit application to be running'
    }
    if ($rendered.services.rabbitmq.hostname -cne 'rabbitmq') {
        throw 'RabbitMQ hostname must be rabbitmq so its node identity survives container recreation'
    }
    if ($rendered.services.app.build.args.VCS_REF -ne $sourceRevision -or $rendered.services.app.image -ne "sky-take-out:$sourceRevision") {
        throw 'Compose build argument and image tag must use the current Git HEAD during smoke verification'
    }

    if ($ConfigOnly) {
        Write-Host "PASS: Compose config is safe and traceable to $sourceRevision."
        return
    }

    $stackAttempted = $true
    Invoke-Compose up --detach --build --wait --wait-timeout $TimeoutSeconds

    Assert-ImageRevision $rendered.services.app.image $sourceRevision

    Wait-Until 'application readiness' {
        & docker compose --project-name $projectName --file $composeFile --env-file $envFile exec --no-TTY app wget --quiet --output-document=- http://localhost:8081/actuator/health/readiness 2>$null |
            Select-String -Quiet '"status"\s*:\s*"UP"'
    }

    $flywayCountBefore = Invoke-Compose exec --no-TTY mysql mysql --batch --skip-column-names `
        "--user=$($composeEnvironment.MYSQL_USERNAME)" "--password=$($composeEnvironment.MYSQL_PASSWORD)" "--database=$($composeEnvironment.MYSQL_DATABASE)" `
        --execute='SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1;'
    if ([int]($flywayCountBefore | Select-Object -Last 1) -lt 1) {
        throw 'Flyway schema history contains no successful migrations'
    }

    $uid = Invoke-Compose exec --no-TTY app id -u
    if (($uid | Select-Object -Last 1).Trim() -eq '0') {
        throw 'Application container is running as root'
    }

    Invoke-Compose exec --no-TTY redis redis-cli -a $composeEnvironment.REDIS_PASSWORD SET compose-smoke persisted
    $rabbitVhosts = Invoke-Compose exec --no-TTY rabbitmq rabbitmqctl list_vhosts --quiet
    if (-not ($rabbitVhosts -contains 'compose-smoke')) {
        Invoke-Compose exec --no-TTY rabbitmq rabbitmqctl add_vhost compose-smoke
    }

    Invoke-Compose restart mysql redis rabbitmq
    Invoke-Compose up --detach --wait --wait-timeout $TimeoutSeconds

    $flywayCountAfter = Invoke-Compose exec --no-TTY mysql mysql --batch --skip-column-names `
        "--user=$($composeEnvironment.MYSQL_USERNAME)" "--password=$($composeEnvironment.MYSQL_PASSWORD)" "--database=$($composeEnvironment.MYSQL_DATABASE)" `
        --execute='SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1;'
    if (($flywayCountAfter | Select-Object -Last 1).Trim() -ne ($flywayCountBefore | Select-Object -Last 1).Trim()) {
        throw 'MySQL Flyway history did not survive restart'
    }

    $redisMarker = Invoke-Compose exec --no-TTY redis redis-cli -a $composeEnvironment.REDIS_PASSWORD GET compose-smoke
    if (($redisMarker | Select-Object -Last 1).Trim() -ne 'persisted') { throw 'Redis marker did not survive restart' }

    $rabbitVhosts = Invoke-Compose exec --no-TTY rabbitmq rabbitmqctl list_vhosts --quiet
    if (-not ($rabbitVhosts -contains 'compose-smoke')) { throw 'RabbitMQ vhost did not survive restart' }

    Invoke-Compose exec --no-TTY redis redis-cli -a $composeEnvironment.REDIS_PASSWORD DEL compose-smoke
    Invoke-Compose exec --no-TTY rabbitmq rabbitmqctl delete_vhost compose-smoke

    Write-Host 'PASS: Compose dependencies, migrations, readiness, non-root process, and restart persistence verified.'
}
finally {
    if ($stackAttempted -and (Test-Path $composeFile)) {
        & docker compose --project-name $projectName --file $composeFile --env-file $envFile down --remove-orphans
    }
    if ($hadVcsRef) { $env:VCS_REF = $previousVcsRef } else { Remove-Item Env:VCS_REF }
}
