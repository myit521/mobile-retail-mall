[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,47}$')][string]$RunId,
    [string]$Scenario='read-only', [string]$JtlPath,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [string]$BaseUrl, [string]$DbTarget, [string]$RabbitManagementUrl,
    [string]$ConfirmNonProduction, [string]$ComposeFile, [string]$OverrideFile, [string]$EnvFile, [string]$ComposeProject,
    [string]$BeforeSnapshotPath, [string]$FixturePath, [switch]$SnapshotOnly
)
$ErrorActionPreference='Stop'
if(-not$ComposeFile){$ComposeFile=Join-Path $PSScriptRoot '../compose.yml'};if(-not$EnvFile){$EnvFile=Join-Path $PSScriptRoot '../.env'}

function Assert-PrivateUrl([string]$Value) {
    if([string]::IsNullOrWhiteSpace($Value)){throw 'BASE_URL is required.'}; $u=[Uri]$Value; $h=$u.DnsSafeHost.ToLowerInvariant()
    $ok=$h -in @('localhost','127.0.0.1','::1') -or $h.EndsWith('.test') -or $h.EndsWith('.local') -or $h-match'^10\.' -or $h-match'^192\.168\.' -or $h-match'^172\.(1[6-9]|2[0-9]|3[01])\.'
    if(-not $ok){throw "Refusing non-private BASE_URL host '$h'."}; if($ConfirmNonProduction-cne'I_UNDERSTAND_THIS_IS_NON_PRODUCTION'){throw 'Explicit non-production confirmation is required.'}
}
function Assert-ComposeBoundary {
    if($DbTarget-cne'compose://mysql' -or $RabbitManagementUrl-cne'compose://rabbitmq'){throw 'Only Task 14 Compose targets are supported.'}
    $expected=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../compose.yml')).Path
    $actual=(Resolve-Path -LiteralPath $ComposeFile).Path; $item=Get-Item -LiteralPath $ComposeFile -Force
    if($actual-cne$expected -or $item.LinkType){throw 'ComposeFile must be the canonical non-symlink Task 14 compose.yml.'}
    $expectedEnv=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.env'));$actualEnv=(Resolve-Path -LiteralPath $EnvFile).Path;$envItem=Get-Item -LiteralPath $EnvFile -Force
    if($actualEnv-cne$expectedEnv -or $envItem.LinkType){throw 'EnvFile must be the canonical non-symlink sky-take-out/.env.'}
    $overrideRoot=[IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) 'sky-perf-overrides')).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
    $actualOverride=(Resolve-Path -LiteralPath $OverrideFile).Path;$overrideItem=Get-Item -LiteralPath $OverrideFile -Force
    if(-not$actualOverride.StartsWith($overrideRoot,[StringComparison]::OrdinalIgnoreCase)-or$overrideItem.LinkType-or$overrideItem.Name-notmatch('^'+[regex]::Escape($RunId)+'-[a-f0-9]{32}\.yml$')){throw 'OverrideFile is not owned by this RUN_ID.'}
    $overrideText=(Get-Content -Raw -LiteralPath $actualOverride).Replace("`r`n","`n").Trim();$expectedOverride='services:'+"`n"+'  app:'+"`n"+'    environment:'+"`n"+'      SKY_JWT_USER_TTL: "${SKY_PERF_USER_TTL_MS:?SKY_PERF_USER_TTL_MS is required}"'+"`n"+'    ports: !override'+"`n"+'      - "127.0.0.1::8080"'
    if($overrideText-cne$expectedOverride){throw 'OverrideFile does not contain the exact random-loopback mapping.'}
    if($ComposeProject-cne("sky-perf-$RunId")){throw 'Compose project must be the run-scoped sky-perf-RUN_ID project.'}
    $services=@(& docker compose --env-file $actualEnv -f $actual -f $actualOverride -p $ComposeProject config --services 2>&1)
    if($LASTEXITCODE-ne0){throw 'Unable to validate Task 14 Compose config.'}
    $names=@($services|ForEach-Object{$_.ToString().Trim()}|Where-Object{$_})
    foreach($required in @('app','mysql','redis','rabbitmq')){if($names-notcontains$required){throw "Task 14 Compose marker service '$required' is missing."}}
    $published=@(& docker compose --env-file $actualEnv -f $actual -f $actualOverride -p $ComposeProject port app 8080 2>&1|ForEach-Object{$_.ToString().Trim()}|Where-Object{$_});if($LASTEXITCODE-ne0-or$published.Count-ne1-or$published[0]-notmatch'^127\.0\.0\.1:(\d+)$'-or$BaseUrl-cne("http://127.0.0.1:$($Matches[1])")){throw 'BASE_URL is not the one unambiguous owned Compose app endpoint.'}
}
function ConvertTo-WindowsNativeArgument([string]$Value){$escaped=[regex]::Replace($Value,'(\\*)"','$1$1\"');$escaped=[regex]::Replace($escaped,'(\\+)$','$1$1');'"'+$escaped+'"'}
function Invoke-ComposeCapture([string]$Service,[string[]]$CommandArgs){
    if($PSVersionTable.PSVersion.Major-gt5){
        $priorPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
        try{$o=& docker compose --env-file $EnvFile -f $ComposeFile -f $OverrideFile -p $ComposeProject exec -T $Service @CommandArgs 2>&1;$nativeExit=$LASTEXITCODE}finally{$ErrorActionPreference=$priorPreference}
        if($nativeExit-ne0){throw "Isolated Compose query failed for '$Service'."};return @($o)
    }
    $process=$null
    try{
        $start=[Diagnostics.ProcessStartInfo]::new()
        $start.FileName=(Get-Command docker.exe -CommandType Application -ErrorAction Stop|Select-Object -First 1).Source
        $argv=@('compose','--env-file',$EnvFile,'-f',$ComposeFile,'-f',$OverrideFile,'-p',$ComposeProject,'exec','-T',$Service)+$CommandArgs
        # Encode once at the final Windows command line; PowerShell 5.1 must not marshal these tokens again.
        $start.Arguments=($argv|ForEach-Object{ConvertTo-WindowsNativeArgument $_})-join' '
        $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WorkingDirectory=(Get-Location).ProviderPath
        $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        $start.StandardOutputEncoding=[Text.Encoding]::UTF8;$start.StandardErrorEncoding=[Text.Encoding]::UTF8
        $process=[Diagnostics.Process]::Start($start)
        # Drain both pipes concurrently before waiting, so either stream can exceed the pipe buffer.
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $process.WaitForExit();$nativeExit=$process.ExitCode
        $outputText=$stdout.GetAwaiter().GetResult();$errorText=$stderr.GetAwaiter().GetResult()
        if($nativeExit-ne0){throw 'Native query failed.'}
    }catch{throw "Isolated Compose query failed for '$Service'."}finally{if($process){$process.Dispose()}}
    foreach($streamText in @($outputText,$errorText)){
        $reader=[IO.StringReader]::new($streamText)
        try{while($null-ne($line=$reader.ReadLine())){$line}}finally{$reader.Dispose()}
    }
}
function Invoke-MySqlScalar([string]$Sql){$x=Invoke-ComposeCapture 'mysql' @('sh','-lc','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --raw --user="$MYSQL_USER" "$MYSQL_DATABASE" --execute="$1"','task16',$Sql);$v=($x|Select-Object -Last 1).ToString().Trim();if($v-notmatch'^\d+$'){throw 'Database returned a non-numeric count.'};[int64]$v}
function Get-LiveSnapshot {
    $prefix="PERF:${RunId}:%"
    $s=[ordered]@{
        scoped_negative_stock=Invoke-MySqlScalar "SELECT COUNT(*) FROM product WHERE model='$RunId' AND name='PERF-PRODUCT-$RunId' AND stock<0;"
        scoped_duplicate_order_number=Invoke-MySqlScalar "SELECT COUNT(*) FROM (SELECT o.number FROM orders o JOIN user u ON u.id=o.user_id WHERE o.number IS NOT NULL AND o.remark LIKE '$prefix' AND u.openid LIKE 'perf-$RunId-%' GROUP BY o.number HAVING COUNT(*)>1) d;"
        global_duplicate_consumer_side_effect=Invoke-MySqlScalar "SELECT COALESCE(SUM(c-1),0) FROM (SELECT COUNT(*) c FROM message_consumption WHERE status='COMPLETED' GROUP BY consumer_name,business_key HAVING COUNT(*)>1) d;"
        global_pending_outbox=Invoke-MySqlScalar "SELECT COUNT(*) FROM outbox_event WHERE status IN ('PENDING','SENDING');"
        global_failed_outbox=Invoke-MySqlScalar "SELECT COUNT(*) FROM outbox_event WHERE status='FAILED';"
        global_dlq=0
    }
    $q=Invoke-ComposeCapture 'rabbitmq' @('sh','-lc','exec rabbitmqadmin --host=localhost --port=15672 --username="$RABBITMQ_DEFAULT_USER" --password="$RABBITMQ_DEFAULT_PASS" --format=tsv list queues name messages')
    foreach($line in $q){if($line-match'^([^\t]+)\t(\d+)$' -and $Matches[1]-match'(dead|dlq)'){$s.global_dlq+=[int64]$Matches[2]}}
    $s
}
function Copy-Snapshot($Source){$o=[ordered]@{};foreach($n in @('scoped_negative_stock','scoped_duplicate_order_number','global_duplicate_consumer_side_effect','global_pending_outbox','global_failed_outbox','global_dlq')){$o[$n]=[int64]$Source.$n};$o}

$counts=[ordered]@{http_samples=0;http_failures=0;business_failures=0;scoped_negative_stock=0;scoped_duplicate_order_number=0;duplicate_consumer_side_effect_growth=0;pending_outbox_growth=0;failed_outbox_growth=0;dlq_growth=0}
$reasons=[Collections.Generic.List[string]]::new();$applicability=[ordered]@{inventory=$true;orders=($Scenario-eq'order-submit');outbox=$false;consumer_side_effect=$false;note='Current scenarios do not produce payment Outbox events; only unexpected global growth is gated.'}
try {
    if($FixturePath){if(-not $RunId.StartsWith('verifier-')){throw 'Fixture mode is restricted to verifier-* runs.'};$after=Copy-Snapshot (Get-Content -Raw -LiteralPath $FixturePath|ConvertFrom-Json)}
    else {Assert-PrivateUrl $BaseUrl;Assert-ComposeBoundary;$after=Get-LiveSnapshot}
    if($SnapshotOnly){$result=[ordered]@{schema_version=2;run_id=$RunId;snapshot=$after};$result|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $OutputPath -Encoding UTF8;exit 0}
    if(-not $JtlPath -or -not(Test-Path -LiteralPath $JtlPath -PathType Leaf)){throw 'JTL file does not exist.'}
    $rows=@(Import-Csv -LiteralPath $JtlPath -Delimiter ',')
    if($rows.Count){$properties=@($rows[0].PSObject.Properties.Name);foreach($c in @('timeStamp','elapsed','label','responseCode','success','failureMessage')){if($properties-notcontains$c){throw "JTL schema is missing '$c'."}}}
    $workload=@($rows|Where-Object{$_.label-ne'auth-check'});$counts.http_samples=$workload.Count
    $counts.http_failures=@($rows|Where-Object{-not $_.success -or $_.success.ToLowerInvariant()-ne'true' -or $_.responseCode-notmatch'^2\d\d$'}).Count
    $counts.business_failures=@($rows|Where-Object{$_.failureMessage-match'^(business_result_not_success|required_result_data_missing|invalid_result_json)$'}).Count
    if($BeforeSnapshotPath){$beforeDoc=Get-Content -Raw -LiteralPath $BeforeSnapshotPath|ConvertFrom-Json;$before=Copy-Snapshot $beforeDoc.snapshot}else{$before=Copy-Snapshot $after}
    $counts.scoped_negative_stock=$after.scoped_negative_stock;$counts.scoped_duplicate_order_number=$after.scoped_duplicate_order_number
    $counts.duplicate_consumer_side_effect_growth=[Math]::Max(0,$after.global_duplicate_consumer_side_effect-$before.global_duplicate_consumer_side_effect)
    $counts.pending_outbox_growth=[Math]::Max(0,$after.global_pending_outbox-$before.global_pending_outbox)
    $counts.failed_outbox_growth=[Math]::Max(0,$after.global_failed_outbox-$before.global_failed_outbox)
    $counts.dlq_growth=[Math]::Max(0,$after.global_dlq-$before.global_dlq)
    if($counts.http_samples-eq0){$reasons.Add('no_http_samples')};foreach($n in $counts.Keys){if($n-ne'http_samples' -and $counts[$n]-gt0){$reasons.Add($n)}}
}catch{$reasons.Add('verification_error');$verificationError=$_.Exception.Message}
$result=[ordered]@{schema_version=2;run_id=$RunId;scenario=$Scenario;checked_at_utc=[DateTime]::UtcNow.ToString('o');pass=($reasons.Count-eq0);counts=$counts;snapshots=[ordered]@{before=$before;after=$after};applicability=$applicability;reasons=@($reasons)};if($verificationError){$result.error=$verificationError}
$parent=Split-Path -Parent $OutputPath;if($parent){New-Item -ItemType Directory -Force -Path $parent|Out-Null};$result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $OutputPath -Encoding UTF8
if(-not$result.pass){exit 2};exit 0
