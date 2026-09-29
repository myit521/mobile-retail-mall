$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) {
    throw 'This regression must run under Windows PowerShell 5.1.'
}

$runner = Join-Path $PSScriptRoot '../run.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Resolve-Path $runner).Path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'run.ps1 has parse errors.' }
$outer = $ast.EndBlock.Statements | Where-Object { $_ -is [Management.Automation.Language.TryStatementAst] }
$teardown = @($outer.Finally.Statements | Where-Object { $_.Extent.Text.Contains('$down=@(') })
if ($teardown.Count -ne 1) { throw 'Expected one production Compose teardown boundary.' }
$boundary = [scriptblock]::Create($teardown[0].Extent.Text)

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('task17-compose-down-' + [Guid]::NewGuid().ToString('N'))
$priorPath = $env:PATH
$priorMode = $env:TASK17_DOWN_MODE
try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    Add-Type -TypeDefinition @'
using System;
public static class Task17ComposeDown
{
    public static int Main(string[] args)
    {
        string expected = "compose|--env-file|safe.env|-f|safe-compose.yml|-f|safe-override.yml|-p|safe-project|down|--remove-orphans|--volumes";
        if (String.Join("|", args) != expected) return 88;
        string mode = Environment.GetEnvironmentVariable("TASK17_DOWN_MODE");
        if (mode.StartsWith("stderr")) Console.Error.WriteLine("Container safe-probe Removed");
        if (mode == "stderr-failure") Console.Error.WriteLine("TASK17_DOWN_SECRET_CANARY");
        if (mode == "stderr-success") Console.Out.WriteLine("TASK17_DOWN_SECRET_CANARY");
        return mode.EndsWith("failure") ? 9 : 0;
    }
}
'@ -Language CSharp -OutputAssembly (Join-Path $temporaryRoot 'docker.exe') -OutputType ConsoleApplication
    $env:PATH = $temporaryRoot + [IO.Path]::PathSeparator + $priorPath
    $started = $true
    $PreserveVolumes = $false
    $actualEnv = 'safe.env'
    $actualCompose = 'safe-compose.yml'
    $overrideFile = 'safe-override.yml'
    $project = 'safe-project'
    $cases = @(
        @{ Mode = 'stderr-success'; Failures = 0 },
        @{ Mode = 'silent-success'; Failures = 0 },
        @{ Mode = 'silent-failure'; Failures = 1 },
        @{ Mode = 'stderr-failure'; Failures = 1 }
    )
    foreach ($case in $cases) {
        $env:TASK17_DOWN_MODE = $case.Mode
        $cleanupFailures = [Collections.Generic.List[string]]::new()
        $ErrorActionPreference = 'Stop'
        $global:LASTEXITCODE = 73
        $output = @(. $boundary *>&1)
        if ($output.Count) { throw "Compose teardown exposed native output in $($case.Mode)." }
        if ($ErrorActionPreference -cne 'Stop') { throw 'Compose teardown changed the caller error preference.' }
        if ($cleanupFailures.Count -ne $case.Failures -or
            ($case.Failures -and $cleanupFailures[0] -cne 'compose-down')) {
            throw "Compose teardown misclassified $($case.Mode): expected $($case.Failures) failure categories, got $($cleanupFailures.Count)."
        }
        Write-Output "PASS $($case.Mode)"
    }
    Write-Output 'TASK17_COMPOSE_DOWN_TEST_PASS'
} finally {
    $env:PATH = $priorPath
    if ($null -eq $priorMode) { Remove-Item Env:TASK17_DOWN_MODE -ErrorAction SilentlyContinue } else { $env:TASK17_DOWN_MODE = $priorMode }
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $resolvedRoot = [IO.Path]::GetFullPath($temporaryRoot)
    if (-not $resolvedRoot.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolvedRoot) -notmatch '^task17-compose-down-[a-f0-9]{32}$') { throw 'Unsafe test cleanup target.' }
    if (Test-Path -LiteralPath $resolvedRoot) { Remove-Item -LiteralPath $resolvedRoot -Recurse -Force }
}
