$ErrorActionPreference='Stop'
$runner=(Resolve-Path (Join-Path $PSScriptRoot '../run.ps1')).Path
$tokens=$null;$parseErrors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($runner,[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count){throw 'Runner parse errors.'}
$outer=@($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.TryStatementAst]})[0]
# Execute the shipped provenance and metadata statements; replace only external
# Git/Docker/Compose calls and the metadata sink. No real daemon or load is used.
$selection='VCS_REF|\$commit\b|\$renderedCompose\b|\$appContainer\b|\$imageId\b|\$imageRevision\b|\$imageInfo\b'
$initial=@($ast.EndBlock.Statements|Where-Object{$_ -isnot [Management.Automation.Language.TryStatementAst] -and $_.Extent.Text-match'VCS_REF'})
$body=@($outer.Body.Statements|Where-Object{$_.Extent.Text-match$selection})
$cleanup=@($outer.Finally.Statements|Where-Object{$_.Extent.Text-match'VCS_REF'})
$initialCode=[scriptblock]::Create(($initial.Extent.Text-join"`n"))
$bodyCode=[scriptblock]::Create('$PSScriptRoot=Split-Path $runner'+"`n"+($body.Extent.Text-join"`n"))
$cleanupCode=[scriptblock]::Create(($cleanup.Extent.Text-join"`n"))
$head='1234567890abcdef1234567890abcdef12345678'
$id='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
$prior=$env:VCS_REF;$hadPrior=Test-Path Env:VCS_REF
function git {
    if(($args-join' ')-notmatch'rev-parse HEAD$'){throw 'Unexpected Git boundary.'}
    $global:LASTEXITCODE=if($mode-eq'git-exit'){9}else{0}
    if($mode-eq'bad-head'){'malformed'}elseif($mode-eq'multiple-heads'){$head;$head}else{$head}
}
function Compose([string[]]$Arguments) {
    $script:calls.Add(($Arguments-join' '))
    if(($Arguments-join' ')-eq'config --format json'){
        $revision=if($env:VCS_REF){$env:VCS_REF}else{'local'}
        if($mode-eq'rendered-mismatch'){$revision='local'}
        $argumentRevision=if($mode-eq'rendered-arg-mismatch'){'local'}else{$revision}
        return (@{services=@{app=@{image="sky-take-out:$revision";build=@{args=@{VCS_REF=$argumentRevision}}}}}|ConvertTo-Json -Depth 6)
    }
    if(($Arguments-join' ')-eq'ps -q app'){return 'fixture-container'}
    throw 'Unexpected Compose boundary.'
}
function docker {
    $script:calls.Add(($args-join' '));$global:LASTEXITCODE=0
    if(($args-join' ')-eq'container inspect --format {{.Image}} fixture-container'){
        if($mode-eq'container-exit'){$global:LASTEXITCODE=9}
        if($mode-eq'bad-image-id'){return 'invalid'}
        return $id
    }
    if(($args-join' ')-eq"image inspect $id"){
        if($mode-eq'inspect-exception'){throw 'SECRET_CANARY'}
        if($mode-eq'inspect-exit'){$global:LASTEXITCODE=9}
        if($mode-eq'invalid-image-json'){return '{invalid'}
        $revision=if($mode-eq'local-label'){'local'}elseif($mode-eq'wrong-label'){'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'}elseif($mode-eq'missing-label'){$null}else{$head}
        $returnedId=if($mode-eq'image-id-mismatch'){'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'}else{$id}
        return (,@{Id=$returnedId;Config=@{Labels=@{'org.opencontainers.image.revision'=$revision}}}|ConvertTo-Json -Depth 6)
    }
    throw 'Unexpected Docker boundary.'
}
function Set-Content {param([Parameter(ValueFromPipeline=$true)]$Value,[string]$LiteralPath,[string]$Encoding) process {$script:metadata=$Value|ConvertFrom-Json}}
try {
    foreach($case in @(
        @{Mode='good';Prior='local';Reject=$false},
        @{Mode='good';Prior=$null;Reject=$false},
        @{Mode='bad-head';Prior='prior-value';Reject=$true},
        @{Mode='bad-head';Prior=$null;Reject=$true},
        @{Mode='git-exit';Prior='prior-value';Reject=$true},
        @{Mode='multiple-heads';Prior=$null;Reject=$true},
        @{Mode='rendered-mismatch';Prior='local';Reject=$true},
        @{Mode='rendered-arg-mismatch';Prior='local';Reject=$true},
        @{Mode='local-label';Prior='prior-value';Reject=$true},
        @{Mode='wrong-label';Prior=$null;Reject=$true},
        @{Mode='inspect-exit';Prior='prior-value';Reject=$true},
        @{Mode='container-exit';Prior=$null;Reject=$true},
        @{Mode='bad-image-id';Prior=$null;Reject=$true},
        @{Mode='image-id-mismatch';Prior=$null;Reject=$true},
        @{Mode='missing-label';Prior=$null;Reject=$true},
        @{Mode='invalid-image-json';Prior=$null;Reject=$true},
        @{Mode='inspect-exception';Prior=$null;Reject=$true}
    )){
        $mode=$case.Mode
        if($null-eq$case.Prior){Remove-Item Env:VCS_REF -ErrorAction SilentlyContinue}else{$env:VCS_REF=$case.Prior}
        $script:calls=[Collections.Generic.List[string]]::new();$script:metadata=$null
        $cleanupFailures=[Collections.Generic.List[string]]::new();$caught=$null
        $RunId='fixture';$environmentPath='fixture.json';$jmeterVersion='5.6.3'
        . $initialCode
        try{. $bodyCode}catch{$caught=$_.Exception.Message}finally{. $cleanupCode}
        if(-not$case.Reject-and($caught-or$metadata.commit-cne$head-or$metadata.image_revision-cne$head-or$metadata.image_id-cne$id)){
            throw "Runner accepted canonical env-file VCS_REF=local while recording Git HEAD without verified image provenance (mode=$mode; error=$caught; calls=$($calls-join'|'))."
        }
        if($case.Reject-and-not$caught){throw "Runner accepted invalid provenance: $mode"}
        if($case.Reject-and$metadata){throw 'Rejected image provenance reached environment metadata output.'}
        if($caught-match'SECRET_CANARY'){throw 'Native error leaked a secret canary.'}
        if($env:VCS_REF-cne$case.Prior-or(Test-Path Env:VCS_REF)-ne($null-ne$case.Prior)){throw "VCS_REF restoration failed: $mode"}
        if($cleanupFailures.Count){throw 'Unexpected cleanup failure.'}
        if($ErrorActionPreference-cne'Stop'){throw 'Caller error preference was not restored.'}
        if($mode-in@('bad-head','git-exit','multiple-heads')-and$calls.Count){throw 'Invalid Git HEAD reached Docker.'}
        if($mode-in@('rendered-mismatch','rendered-arg-mismatch')-and$calls.Count-ne1){throw 'Rendered mismatch reached image inspection.'}
        Write-Output "PASS provenance $mode"
    }
    if($cleanup.Count-ne1-or$cleanup[0] -isnot [Management.Automation.Language.TryStatementAst]){throw 'VCS_REF restoration must be an independent finalizer.'}
    $hadVcsRef=$false;$env:VCS_REF=$head
    $cleanupFailures=[Collections.Generic.List[string]]::new()
    function Remove-Item { [CmdletBinding()]param([string]$Path) throw 'RESTORE_SECRET_CANARY' }
    try{$cleanupOutput=@(. $cleanupCode *>&1)}finally{Microsoft.PowerShell.Management\Remove-Item Function:\Remove-Item}
    if($cleanupOutput.Count-or$cleanupFailures.Count-ne1-or$cleanupFailures[0]-cne'vcs-ref-environment-restore'){throw 'Restoration failure must produce only its bounded category.'}
    $bodyText=$outer.Body.Extent.Text
    if($bodyText.IndexOf('$env:VCS_REF=$commit')-gt$bodyText.IndexOf('$services=@') -or
       $bodyText.IndexOf('$renderedCompose=')-gt$bodyText.IndexOf("Compose @('up'") -or
       $bodyText.IndexOf('$imageRevision=')-gt$bodyText.IndexOf("if(`$CacheState-eq'warm')")){
        throw 'Provenance gates must precede Compose build and every load.'
    }
    Write-Output 'TASK17_IMAGE_PROVENANCE_TEST_PASS'
} finally {
    if($hadPrior){$env:VCS_REF=$prior}else{Remove-Item Env:VCS_REF -ErrorAction SilentlyContinue}
}
