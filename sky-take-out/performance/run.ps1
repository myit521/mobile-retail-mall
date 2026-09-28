[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('read-only','light-write','order-submit')][string]$Scenario,
    [Parameter(Mandatory=$true)][ValidateRange(1,1000)][int]$Users,
    [Parameter(Mandatory=$true)][ValidateRange(1,86400)][int]$RampUpSeconds,
    [Parameter(Mandatory=$true)][ValidateRange(1,86400)][int]$DurationSeconds,
    [Parameter(Mandatory=$true)][ValidateSet('cold','warm')][string]$CacheState,
    [string]$BaseUrl,
    [Parameter(Mandatory=$true)][ValidateSet('I_UNDERSTAND_THIS_IS_NON_PRODUCTION')][string]$ConfirmNonProduction,
    [string]$DbTarget='compose://mysql', [string]$RabbitManagementUrl='compose://rabbitmq',
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,47}$')][string]$RunId,
    [string]$ComposeFile, [string]$EnvFile, [switch]$PreserveVolumes
)
$ErrorActionPreference='Stop'

function Resolve-PathUnderRoot([string]$Root,[string]$Child) {
    $r=[IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
    $c=[IO.Path]::GetFullPath((Join-Path $Root $Child))
    if(-not $c.StartsWith($r,[StringComparison]::OrdinalIgnoreCase)){throw 'Path escaped its owned root.'};$c
}
function Acquire-RunLock([string]$Id) {
    $root=Join-Path ([IO.Path]::GetTempPath()) 'sky-perf-locks';[IO.Directory]::CreateDirectory($root)|Out-Null
    $path=Resolve-PathUnderRoot $root "$Id.lock"
    try{$stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}catch [IO.IOException]{throw "RUN_ID '$Id' is already owned by another invocation."}
    $bytes=[Text.Encoding]::UTF8.GetBytes("pid=$PID");$stream.Write($bytes,0,$bytes.Length);$stream.Flush();[pscustomobject]@{Path=$path;Stream=$stream}
}
function Get-SessionTtlMs([int]$Ramp,[int]$Duration) {
    # Covers compose setup/readiness, ramp, requested duration, and teardown safety.
    ([int64]$Ramp+[int64]$Duration+900L)*1000L
}
function Write-JMeterPropertyFile([string]$Path,[string]$CredentialDirectory) {
    $value=$CredentialDirectory.Replace('\','/')
    $escaped=[Text.StringBuilder]::new()
    foreach($character in $value.ToCharArray()){
        $code=[int]$character
        if($code-lt0x20-or$code-gt0x7e){[void]$escaped.AppendFormat('\u{0:X4}',$code)}
        elseif($character-eq'\'){[void]$escaped.Append('\\')}
        else{[void]$escaped.Append($character)}
    }
    [IO.File]::WriteAllText($Path,"CREDENTIAL_DIR=$escaped`n",[Text.ASCIIEncoding]::new())
}
function Get-ArtifactCredentialCategories([string]$Directory,[string]$Secret) {
    # "JWT-shaped credential found" is represented only by the jwt-shape category;
    # matched values are never returned or logged.
    if(-not$Directory-or-not(Test-Path -LiteralPath $Directory)){return}
    $files=@(Get-ChildItem -LiteralPath $Directory -File -Force -Recurse -ErrorAction Stop)
    if($files.Count-and(Select-String -LiteralPath $files.FullName -Pattern 'eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}' -Quiet)){'jwt-shape'}
    if($files.Count-and-not[string]::IsNullOrEmpty($Secret)-and(Select-String -LiteralPath $files.FullName -SimpleMatch $Secret -Quiet)){'secret-match'}
}
function Assert-NoReparsePointInPath([string]$Path) {
    $fullPath=[IO.Path]::GetFullPath($Path)
    $current=[IO.Path]::GetPathRoot($fullPath)
    $relative=$fullPath.Substring($current.Length)
    foreach($segment in @($relative-split'[\\/]'|Where-Object{$_})){
        $current=Join-Path $current $segment
        $item=Get-Item -LiteralPath $current -Force
        if($item.LinkType-or(($item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne0)){throw 'Credential cleanup path contains a reparse point.'}
    }
}
function Remove-OwnedCredentialRun([string]$Root,[string]$Directory) {
    $rootPath=[IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $directoryItem=Get-Item -LiteralPath $Directory -Force
    $directoryPath=[IO.Path]::GetFullPath($directoryItem.FullName)
    $prefix=$rootPath+[IO.Path]::DirectorySeparatorChar
    if($directoryPath-eq$rootPath-or-not$directoryPath.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Credential cleanup target is outside its owned result root.'}
    Assert-NoReparsePointInPath $rootPath
    Assert-NoReparsePointInPath $directoryPath
    Remove-Item -LiteralPath $directoryPath -Recurse -Force
}

$priorAppSecret=$env:SKY_JWT_USER_SECRET_KEY;$priorJmeterSecret=$env:SKY_PERF_USER_JWT_SECRET;$priorPerfTtl=$env:SKY_PERF_USER_TTL_MS
$hadVcsRef=Test-Path Env:VCS_REF;$priorVcsRef=$env:VCS_REF
$lock=$null;$started=$false;$sessionIds=@();$credentialDir=$null;$propertyPath=$null;$overrideFile=$null;$runFailure=$null
try {
    if(-not $RunId){$RunId='perf-'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)}
    $project="sky-perf-$RunId"
    $expectedCompose=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../compose.yml')).Path
    if(-not $ComposeFile){$ComposeFile=$expectedCompose};$actualCompose=(Resolve-Path -LiteralPath $ComposeFile).Path;$composeItem=Get-Item -LiteralPath $ComposeFile -Force
    if($actualCompose-cne$expectedCompose -or $composeItem.LinkType){throw 'ComposeFile must be the canonical non-symlink Task 14 compose.yml.'}
    $expectedEnv=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.env'));if(-not $EnvFile){$EnvFile=$expectedEnv};$actualEnv=(Resolve-Path -LiteralPath $EnvFile).Path;$envItem=Get-Item -LiteralPath $EnvFile -Force
    if($actualEnv-cne$expectedEnv -or $envItem.LinkType){throw 'EnvFile must be the canonical non-symlink sky-take-out/.env.'}
    if($DbTarget-cne'compose://mysql' -or $RabbitManagementUrl-cne'compose://rabbitmq'){throw 'Only isolated Task 14 Compose targets are supported.'}
    $envLines=Get-Content -LiteralPath $actualEnv
    foreach($required in @('MYSQL_DATABASE','MYSQL_USERNAME','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD','REDIS_PASSWORD','RABBITMQ_USERNAME','RABBITMQ_PASSWORD','SKY_JWT_ADMIN_SECRET_KEY')){
        $fromFile=@($envLines|Where-Object{$_-match("^"+[regex]::Escape($required)+"=.+$")}).Count-gt0
        $fromProcess=-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($required))
        if(-not $fromFile -and -not $fromProcess){throw "Required isolated Compose variable '$required' is missing."}
    }

    # CreateNew is the first shared-resource ownership action and precedes every Docker call.
    $lock=Acquire-RunLock $RunId
    $secret=$env:USER_JWT_SECRET;if([string]::IsNullOrWhiteSpace($secret)){throw 'USER_JWT_SECRET must be supplied through the process environment.'}
    $jmeter=Get-Command jmeter -ErrorAction Stop;[void](Get-Command java -ErrorAction Stop);[void](Get-Command docker -ErrorAction Stop)
    # Both tools may write version output to stderr. Capture and validate each
    # probe before another native command can overwrite its exit code.
    $probePreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$javaOutput=@(& java -version 2>&1);$javaExit=$LASTEXITCODE}finally{$ErrorActionPreference=$probePreference}
    $javaVersion=@($javaOutput|ForEach-Object{$_.ToString()}|Where-Object{$_-match'^(java|openjdk) version '}|Select-Object -First 1)
    if($javaExit-ne0-or$javaVersion.Count-ne1){throw 'Java version probe failed.'};$javaVersion=$javaVersion[0]
    $probePreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$jmeterOutput=@(& $jmeter.Source --version 2>&1);$jmeterExit=$LASTEXITCODE}finally{$ErrorActionPreference=$probePreference}
    $jmeterVersion=@($jmeterOutput|ForEach-Object{$_.ToString()}|Where-Object{$_-match'\b5\.6\.3$'}|Select-Object -First 1)
    if($jmeterExit-ne0-or$jmeterVersion.Count-ne1){throw 'JMeter version probe failed.'};$jmeterVersion=$jmeterVersion[0]
    try {
        $repositoryRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path.Replace('\','/')
        $revisionPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{$commit=@(& git -c "safe.directory=$repositoryRoot" -C $repositoryRoot rev-parse HEAD 2>$null);$revisionExit=$LASTEXITCODE}finally{$ErrorActionPreference=$revisionPreference}
        if($revisionExit-ne0-or$commit.Count-ne1){throw 'Invalid source revision.'}
        $commit=$commit[0].ToString().Trim()
        if($commit-cnotmatch'^[0-9a-f]{40}$'){throw 'Invalid source revision.'}
        $env:VCS_REF=$commit
    }catch{throw 'Unable to resolve exact Git HEAD for the performance image.'}
    $memoryBytes=$null;try{$memoryBytes=(Get-CimInstance Win32_ComputerSystem -ErrorAction Stop).TotalPhysicalMemory}catch{}
    $ttlMs=Get-SessionTtlMs $RampUpSeconds $DurationSeconds;$ttlSeconds=[Math]::Ceiling($ttlMs/1000.0)
    $env:SKY_JWT_USER_SECRET_KEY=$secret;$env:SKY_PERF_USER_TTL_MS=$ttlMs

    $resultRoot=Join-Path $PSScriptRoot 'results';$runDirectory=Resolve-PathUnderRoot $resultRoot ([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+$RunId);New-Item -ItemType Directory -Path $runDirectory|Out-Null
    $jtl=Join-Path $runDirectory 'results.jtl';$html=Join-Path $runDirectory 'html';$environmentPath=Join-Path $runDirectory 'environment.json';$summaryPath=Join-Path $runDirectory 'summary.json';$correctnessPath=Join-Path $runDirectory 'correctness.json';$beforePath=Join-Path $runDirectory 'before-snapshot.json';$propertyPath=Join-Path $runDirectory '.runtime.properties'
    $credentialDir=Join-Path ([IO.Path]::GetTempPath()) ("sky-perf-workers-$RunId-"+[Guid]::NewGuid().ToString('N'))
    $overrideRoot=Join-Path ([IO.Path]::GetTempPath()) 'sky-perf-overrides';[IO.Directory]::CreateDirectory($overrideRoot)|Out-Null
    $overrideFile=Resolve-PathUnderRoot $overrideRoot ("$RunId-"+[Guid]::NewGuid().ToString('N')+'.yml')
    @"
services:
  app:
    environment:
      SKY_JWT_USER_TTL: "`${SKY_PERF_USER_TTL_MS:?SKY_PERF_USER_TTL_MS is required}"
    ports: !override
      - "127.0.0.1::8080"
"@.TrimStart()|Set-Content -LiteralPath $overrideFile -Encoding UTF8
    function Compose([string[]]$Arguments){& docker compose --env-file $actualEnv -f $actualCompose -f $overrideFile -p $project @Arguments;if($LASTEXITCODE-ne0){throw 'Owned Task 14 Compose command failed.'}}

    $services=@(& docker compose --env-file $actualEnv -f $actualCompose -f $overrideFile -p $project config --services 2>&1);if($LASTEXITCODE-ne0){throw 'Unable to validate owned Compose config.'}
    foreach($s in @('app','mysql','redis','rabbitmq')){if(@($services)-notcontains$s){throw "Task 14 Compose marker '$s' is missing."}}
    try {
        $renderedCompose=(Compose @('config','--format','json'))|ConvertFrom-Json
        if($renderedCompose.services.app.image-cne"sky-take-out:$commit"-or$renderedCompose.services.app.build.args.VCS_REF-cne$commit){throw 'Source revision mismatch.'}
    }catch{throw 'Owned Compose image and build argument must match exact Git HEAD.'}
    $existing=@(& docker ps -aq --filter "label=com.docker.compose.project=$project" 2>&1);$containerQueryExit=$LASTEXITCODE
    $volumes=@(& docker volume ls -q --filter "label=com.docker.compose.project=$project" 2>&1);$volumeQueryExit=$LASTEXITCODE
    if($containerQueryExit-ne0-or$volumeQueryExit-ne0-or@($existing|Where-Object{$_}).Count-or@($volumes|Where-Object{$_}).Count){throw 'Owned Compose project is unavailable or already exists.'}
    $started=$true;Compose @('up','-d','--build')
    try {
        $appContainer=@(Compose @('ps','-q','app'))
        if($appContainer.Count-ne1-or[string]::IsNullOrWhiteSpace($appContainer[0])){throw 'Application container is ambiguous.'}
        $revisionPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{$imageId=@(& docker container inspect --format '{{.Image}}' $appContainer[0] 2>$null);$imageExit=$LASTEXITCODE}finally{$ErrorActionPreference=$revisionPreference}
        if($imageExit-ne0-or$imageId.Count-ne1){throw 'Image identity unavailable.'}
        $imageId=$imageId[0].ToString().Trim()
        if($imageId-cnotmatch'^sha256:[0-9a-f]{64}$'){throw 'Invalid image identity.'}
        $revisionPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{$imageInfo=@(& docker image inspect $imageId 2>$null);$imageExit=$LASTEXITCODE}finally{$ErrorActionPreference=$revisionPreference}
        if($imageExit-ne0){throw 'Image inspection failed.'}
        $imageInfo=@(($imageInfo-join"`n")|ConvertFrom-Json)
        if($imageInfo.Count-ne1-or$imageInfo[0].Id-cne$imageId){throw 'Image identity mismatch.'}
        $imageRevision=$imageInfo[0].Config.Labels.'org.opencontainers.image.revision'
        if($imageRevision-cne$commit){throw 'Image source revision mismatch.'}
    }catch{throw 'Owned application image revision must match exact Git HEAD.'}

    $published=@(Compose @('port','app','8080')|ForEach-Object{$_.ToString().Trim()}|Where-Object{$_})
    if($published.Count-ne1-or$published[0]-notmatch'^127\.0\.0\.1:(\d+)$'){throw 'Compose app port is not one unambiguous owned loopback mapping.'};$derivedBaseUrl="http://127.0.0.1:$($Matches[1])"
    if($BaseUrl){$expectedUri=$null;if(-not[Uri]::TryCreate($BaseUrl,[UriKind]::Absolute,[ref]$expectedUri)-or$expectedUri.Scheme-cne'http'-or$expectedUri.UserInfo-or$expectedUri.AbsolutePath-cne'/'-or$expectedUri.Query-or$expectedUri.Fragment-or$expectedUri.AbsoluteUri.TrimEnd('/')-cne$derivedBaseUrl){throw 'Expected BaseUrl does not exactly equal the owned Compose app endpoint.'}}

    # Compose health is driven by the container-internal readiness endpoint.
    # Query that state directly to avoid PowerShell 5.1 rewriting nested sh
    # quotes differently from pwsh 7 when invoking the native Docker CLI.
    $deadline=[DateTime]::UtcNow.AddMinutes(4);do{Start-Sleep -Seconds 3;$probePreference=$ErrorActionPreference;$ErrorActionPreference='Continue';try{$healthOutput=@(& docker compose --env-file $actualEnv -f $actualCompose -f $overrideFile -p $project ps app --format '{{.Health}}' 2>$null);$healthExit=$LASTEXITCODE}finally{$ErrorActionPreference=$probePreference};$healthValues=@($healthOutput|ForEach-Object{$_.ToString().Trim()}|Where-Object{$_});$ready=$healthExit-eq0-and$healthValues.Count-eq1-and$healthValues[0]-ceq'healthy'}until($ready-or[DateTime]::UtcNow-ge$deadline)
    if(-not $ready){throw 'Owned application did not become ready.'}
    if($CacheState-eq'cold'){Compose @('exec','-T','redis','sh','-lc','exec redis-cli --no-auth-warning -a "$REDIS_PASSWORD" FLUSHDB')}

    $seed="SET @PERF_RUN_ID='$RunId'; SET @PERF_USERS=$Users;`n"+(Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'dataset/seed.sql'))
    $seedOut=@($seed|& docker compose --env-file $actualEnv -f $actualCompose -f $overrideFile -p $project exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE"');if($LASTEXITCODE-ne0){throw 'Isolated seed failed.'}
    $validation=@($seedOut|Where-Object{$_.ToString().StartsWith("VALIDATION`t")});if($validation.Count-ne1){throw 'Seed validation row missing.'};$v=$validation[0].ToString()-split"`t";if($v.Count-ne4-or$v[1]-ne'1'-or$v[2]-ne'1'-or[int]$v[3]-ne$Users){throw 'Seed is not idempotently unique.'}
    $workers=@($seedOut|Where-Object{$_.ToString().StartsWith("WORKER`t")});if($workers.Count-ne$Users){throw 'Seed did not return one identity per worker.'}
    $records=@()
    foreach($line in $workers){$f=$line.ToString()-split"`t";if($f.Count-ne5-or$f[1]-notmatch'^\d+$'-or$f[2]-notmatch'^\d+$'){throw 'Invalid worker mapping.'};$sid=[Guid]::NewGuid().ToString();$sessionIds+=$sid;$userId=[long]$f[2];$username="PERF-$RunId-$($f[1])";$now=[DateTime]::UtcNow.ToString('o');Compose @('exec','-T','redis','sh','-lc','redis-cli --no-auth-warning -a "$REDIS_PASSWORD" HSET "login:user:$1" id "$2" username "$3" loginTime "$4" >/dev/null && redis-cli --no-auth-warning -a "$REDIS_PASSWORD" PEXPIRE "login:user:$1" "$5" >/dev/null','task16',$sid,$userId,$username,$now,$ttlMs);$records+=[pscustomobject]@{WORKER_NO=[int]$f[1];USER_ID=$userId;SESSION_ID=$sid;ORDER_NAMESPACE="PERF:${RunId}:$($f[1])";CATEGORY_ID=$f[3];PRODUCT_ID=$f[4]}}
    if(@($records.USER_ID|Sort-Object -Unique).Count-ne$Users-or@($records.SESSION_ID|Sort-Object -Unique).Count-ne$Users-or@($records.ORDER_NAMESPACE|Sort-Object -Unique).Count-ne$Users){throw 'Worker identities are not unique.'}
    New-Item -ItemType Directory -Path $credentialDir|Out-Null;foreach($record in $records){$record|Select-Object USER_ID,SESSION_ID,ORDER_NAMESPACE,CATEGORY_ID,PRODUCT_ID|ConvertTo-Csv -NoTypeInformation|Select-Object -Skip 1|Set-Content -LiteralPath (Join-Path $credentialDir "worker-$($record.WORKER_NO).csv") -Encoding UTF8};Write-JMeterPropertyFile $propertyPath $credentialDir

    if($CacheState-eq'warm'){
        # Prime the authenticated read path with an in-memory JMeter JWT. The
        # separate JTL remains raw evidence and is excluded from main metrics.
        $warmupJtl=Join-Path $runDirectory 'warmup.jtl';$warmupArguments=@('-n','-t',(Join-Path $PSScriptRoot 'jmeter/read-only.jmx'),'-l',$warmupJtl,"-JBASE_URL=$derivedBaseUrl",'-JUSERS=1','-JRAMP_UP_SECONDS=1','-JDURATION_SECONDS=3',"-JCACHE_STATE=$CacheState","-JRUN_ID=$RunId","-JSESSION_TTL_SECONDS=$ttlSeconds",'-Jjmeter.save.saveservice.output_format=csv','-Jjmeter.save.saveservice.print_field_names=true','-Jjmeter.save.saveservice.successful=true','-Jjmeter.save.saveservice.assertion_results_failure_message=true','-Jjmeter.save.saveservice.response_data=false','-Jjmeter.save.saveservice.samplerData=false','-Jjmeter.save.saveservice.requestHeaders=false','-Jjmeter.save.saveservice.responseHeaders=false','-q',$propertyPath)
        $env:SKY_PERF_USER_JWT_SECRET=$secret;try{& $jmeter.Source @warmupArguments;$warmupExit=$LASTEXITCODE}finally{if($null-ne$priorJmeterSecret){$env:SKY_PERF_USER_JWT_SECRET=$priorJmeterSecret}else{Remove-Item Env:SKY_PERF_USER_JWT_SECRET -ErrorAction SilentlyContinue}}
        $warmupRows=if(Test-Path $warmupJtl){@(Import-Csv -LiteralPath $warmupJtl)}else{@()};$warmupWork=@($warmupRows|Where-Object{$_.label-ne'auth-check'});$warmupFailures=@($warmupWork|Where-Object{-not$_.success-or$_.success.ToLowerInvariant()-ne'true'-or$_.responseCode-notmatch'^2\d\d$'}).Count
        if($warmupExit-ne0-or-not$warmupWork.Count-or$warmupFailures){throw 'Authenticated warm-cache prime failed.'}
    }

    & powershell.exe -NoProfile -File (Join-Path $PSScriptRoot 'verify-results.ps1') -RunId $RunId -Scenario $Scenario -OutputPath $beforePath -SnapshotOnly -BaseUrl $derivedBaseUrl -DbTarget $DbTarget -RabbitManagementUrl $RabbitManagementUrl -ConfirmNonProduction $ConfirmNonProduction -ComposeFile $actualCompose -OverrideFile $overrideFile -EnvFile $actualEnv -ComposeProject $project;if($LASTEXITCODE-ne0){throw 'Before snapshot failed.'}
    [ordered]@{schema_version=3;run_id=$RunId;scenario=$Scenario;commit=$commit;image_id=$imageId;image_revision=$imageRevision;started_at_utc=[DateTime]::UtcNow.ToString('o');base_url=$derivedBaseUrl;users=$Users;ramp_up_seconds=$RampUpSeconds;requested_duration_seconds=$DurationSeconds;session_ttl_seconds=$ttlSeconds;cache_state=$CacheState;compose_project=$project;topology='owned Task 14 Compose with random loopback app port';os=[Environment]::OSVersion.VersionString;processors=[Environment]::ProcessorCount;machine_memory_bytes=$memoryBytes;java=$javaVersion;jmeter=$jmeterVersion}|ConvertTo-Json|Set-Content -LiteralPath $environmentPath -Encoding UTF8

    # jmeter -n targets only the endpoint derived from this owned Compose project.
    $arguments=@('-n','-t',(Join-Path $PSScriptRoot "jmeter/$Scenario.jmx"),'-l',$jtl,'-e','-o',$html,"-JBASE_URL=$derivedBaseUrl","-JUSERS=$Users","-JRAMP_UP_SECONDS=$RampUpSeconds","-JDURATION_SECONDS=$DurationSeconds","-JCACHE_STATE=$CacheState","-JRUN_ID=$RunId","-JSESSION_TTL_SECONDS=$ttlSeconds",'-Jjmeter.save.saveservice.output_format=csv','-Jjmeter.save.saveservice.print_field_names=true','-Jjmeter.save.saveservice.successful=true','-Jjmeter.save.saveservice.assertion_results_failure_message=true','-Jjmeter.save.saveservice.response_data=false','-Jjmeter.save.saveservice.samplerData=false','-Jjmeter.save.saveservice.requestHeaders=false','-Jjmeter.save.saveservice.responseHeaders=false','-q',$propertyPath)
    $env:SKY_PERF_USER_JWT_SECRET=$secret;try{& $jmeter.Source @arguments;$jmeterExit=$LASTEXITCODE}finally{if($null-ne$priorJmeterSecret){$env:SKY_PERF_USER_JWT_SECRET=$priorJmeterSecret}else{Remove-Item Env:SKY_PERF_USER_JWT_SECRET -ErrorAction SilentlyContinue}}
    $rows=if(Test-Path $jtl){@(Import-Csv -LiteralPath $jtl)}else{@()};$work=@($rows|Where-Object{$_.label-ne'auth-check'});$failures=@($work|Where-Object{-not$_.success-or$_.success.ToLowerInvariant()-ne'true'-or$_.responseCode-notmatch'^2\d\d$'}).Count;$times=@($work|ForEach-Object{[long]$_.timeStamp}|Sort-Object);$ends=@($work|ForEach-Object{[long]$_.timeStamp+[long]$_.elapsed}|Sort-Object);$observed=if($times.Count){[Math]::Max(.001,($ends[-1]-$times[0])/1000.0)}else{0};$elapsed=@($work|ForEach-Object{[double]$_.elapsed}|Sort-Object);function P($a,$p){if(-not$a.Count){return$null};$a[[Math]::Min($a.Count-1,[Math]::Ceiling($p*$a.Count)-1)]};[ordered]@{schema_version=3;run_id=$RunId;samples=$work.Count;failures=$failures;requested_duration_seconds=$DurationSeconds;observed_duration_seconds=$observed;throughput_per_second=if($observed){$work.Count/$observed}else{0};error_rate=if($work.Count){$failures/$work.Count}else{1};average_ms=if($elapsed.Count){($elapsed|Measure-Object -Average).Average}else{$null};p50_ms=P $elapsed .5;p95_ms=P $elapsed .95;p99_ms=P $elapsed .99;jmeter_exit_code=$jmeterExit}|ConvertTo-Json|Set-Content -LiteralPath $summaryPath -Encoding UTF8
    & powershell.exe -NoProfile -File (Join-Path $PSScriptRoot 'verify-results.ps1') -RunId $RunId -Scenario $Scenario -JtlPath $jtl -OutputPath $correctnessPath -BeforeSnapshotPath $beforePath -BaseUrl $derivedBaseUrl -DbTarget $DbTarget -RabbitManagementUrl $RabbitManagementUrl -ConfirmNonProduction $ConfirmNonProduction -ComposeFile $actualCompose -OverrideFile $overrideFile -EnvFile $actualEnv -ComposeProject $project;$verifyExit=$LASTEXITCODE
    if($jmeterExit-ne0-or$verifyExit-ne0){throw "Performance gate failed (JMeter=$jmeterExit correctness=$verifyExit)."};Write-Output $runDirectory
} catch {
    $runFailure=$_
    throw
} finally {
    $cleanupFailures=[Collections.Generic.List[string]]::new()
    try {
        if($runDirectory-and(Test-Path -LiteralPath $runDirectory)){
            $artifactCategories=@(Get-ArtifactCredentialCategories $runDirectory $secret)
            if($artifactCategories.Count){
                $artifactName=Split-Path -Leaf $runDirectory
                Remove-OwnedCredentialRun $resultRoot $runDirectory
                Write-Warning "Removed owned result directory '$artifactName'; credential categories: $((@($artifactCategories|Sort-Object -Unique))-join ',')."
                $cleanupFailures.Add('credential-artifact-removed')
            }
        }
    } catch {$cleanupFailures.Add('artifact-scan')}
    try {
        if($credentialDir-and(Test-Path -LiteralPath $credentialDir)){Remove-Item -LiteralPath $credentialDir -Recurse -Force -ErrorAction Stop}
        if($propertyPath-and(Test-Path -LiteralPath $propertyPath)){Remove-Item -LiteralPath $propertyPath -Force -ErrorAction Stop}
    } catch {$cleanupFailures.Add('temporary-credential-cleanup')}
    if($started-and$sessionIds.Count){foreach($sid in $sessionIds){try{& docker compose --env-file $actualEnv -f $actualCompose -f $overrideFile -p $project exec -T redis sh -lc 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" DEL "login:user:$1" >/dev/null' task16 $sid 2>$null|Out-Null;if($LASTEXITCODE-ne0){$cleanupFailures.Add('session-revocation')}}catch{$cleanupFailures.Add('session-revocation')}}}
    try {if($started){$down=@('compose','--env-file',$actualEnv,'-f',$actualCompose,'-f',$overrideFile,'-p',$project,'down','--remove-orphans');if(-not$PreserveVolumes){$down+='--volumes'};
        $downPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{& docker @down 2>&1|Out-Null;$downExit=$LASTEXITCODE}finally{$ErrorActionPreference=$downPreference}
        if($downExit-ne0){$cleanupFailures.Add('compose-down')}}}catch{$cleanupFailures.Add('compose-down')}
    try {if($overrideFile-and(Test-Path -LiteralPath $overrideFile)){Remove-Item -LiteralPath $overrideFile -Force -ErrorAction Stop}}catch{$cleanupFailures.Add('override-cleanup')}
    try {if($null-ne$priorJmeterSecret){$env:SKY_PERF_USER_JWT_SECRET=$priorJmeterSecret}else{Remove-Item Env:SKY_PERF_USER_JWT_SECRET -ErrorAction SilentlyContinue}}catch{$cleanupFailures.Add('jmeter-environment-restore')}
    try {if($null-ne$priorAppSecret){$env:SKY_JWT_USER_SECRET_KEY=$priorAppSecret}else{Remove-Item Env:SKY_JWT_USER_SECRET_KEY -ErrorAction SilentlyContinue};$secret=$null}catch{$cleanupFailures.Add('app-environment-restore')}
    try {if($null-ne$priorPerfTtl){$env:SKY_PERF_USER_TTL_MS=$priorPerfTtl}else{Remove-Item Env:SKY_PERF_USER_TTL_MS -ErrorAction SilentlyContinue}}catch{$cleanupFailures.Add('ttl-environment-restore')}
    try {if($hadVcsRef){$env:VCS_REF=$priorVcsRef}elseif(Test-Path Env:VCS_REF){Remove-Item Env:VCS_REF -ErrorAction Stop}}catch{$cleanupFailures.Add('vcs-ref-environment-restore')}
    try {if($lock){$lock.Stream.Dispose();Remove-Item -LiteralPath $lock.Path -Force -ErrorAction Stop}}catch{$cleanupFailures.Add('lock-cleanup')}
    if($cleanupFailures.Count){$cleanupSummary=(@($cleanupFailures|Sort-Object -Unique))-join',';if($runFailure){Write-Warning "Owned cleanup categories: $cleanupSummary."}else{throw "Owned run cleanup failed: $cleanupSummary."}}
}
