$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) { throw 'Run this regression under Windows PowerShell 5.1.' }

$scripts = @(
    (Join-Path $PSScriptRoot '../compose-smoke.ps1'),
    (Join-Path $PSScriptRoot '../rollback-drill.ps1')
)
$revision = '1234567890abcdef1234567890abcdef12345678'
$image = "sky-take-out:$revision"
$imageId = 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'

function docker {
    if ($args.Count -ne 3 -or $args[0] -cne 'image' -or $args[1] -cne 'inspect' -or $args[2] -cne $image) {
        throw "Unexpected Docker invocation: $($args -join ' ')"
    }
    $global:LASTEXITCODE = if ($mode -eq 'exit') { 64 } else { 0 }
    if ($mode -eq 'malformed') { return '{invalid' }
    $result = @{
        Id = if ($mode -eq 'bad-id') { 'not-an-image-id' } else { $imageId }
        RepoTags = @(if ($mode -eq 'wrong-tag') { 'sky-take-out:other' } else { $image })
        Config = @{ Labels = @{
            'org.opencontainers.image.revision' = if ($mode -eq 'missing-label') { $null } elseif ($mode -eq 'wrong-label') { 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' } else { $revision }
        } }
    }
    if ($mode -eq 'array-id') { $result.Id = @($imageId) }
    if ($mode -eq 'array-label') { $result.Config.Labels['org.opencontainers.image.revision'] = @($revision) }
    $items = if ($mode -eq 'empty') { @() } elseif ($mode -eq 'multiple') { @($result, $result) } else { @($result) }
    ConvertTo-Json -InputObject @($items) -Depth 6
}

foreach ($path in $scripts) {
    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile((Resolve-Path $path).Path, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw "$path has parse errors." }
    $helper = $ast.Find({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-ImageRevision'
    }, $true)
    if (-not $helper) { throw "$path has no executable image revision verifier." }
    $calls = @($ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Assert-ImageRevision'
    }, $true))
    if ($calls.Count -ne 1 -or $ast.Extent.Text -match 'docker\s+image\s+inspect\s+--format') {
        throw "$path must invoke the verifier and avoid quoted image inspect templates."
    }
    . ([scriptblock]::Create($helper.Extent.Text))

    $failures = @()
    foreach ($case in @(
        @{ Name = 'valid'; Reject = $false },
        @{ Name = 'exit'; Reject = $true },
        @{ Name = 'empty'; Reject = $true },
        @{ Name = 'multiple'; Reject = $true },
        @{ Name = 'bad-id'; Reject = $true },
        @{ Name = 'array-id'; Reject = $true },
        @{ Name = 'wrong-tag'; Reject = $true },
        @{ Name = 'missing-label'; Reject = $true },
        @{ Name = 'wrong-label'; Reject = $true },
        @{ Name = 'array-label'; Reject = $true },
        @{ Name = 'malformed'; Reject = $true }
    )) {
        $mode = $case.Name
        $caught = $null
        try { Assert-ImageRevision $image $revision } catch { $caught = $_.Exception.Message }
        if ($case.Reject -and -not $caught) { $failures += "$path accepted $mode image provenance." }
        if (-not $case.Reject -and $caught) { $failures += "$path rejected valid image provenance: $caught" }
    }
    if ($failures.Count) { throw ($failures -join ' ') }
    Write-Output "PASS image revision verifier: $path"
}
