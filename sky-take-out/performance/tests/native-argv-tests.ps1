$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) {
    throw 'This regression must run under Windows PowerShell 5.1.'
}

$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$verifierPath = Join-Path $repo 'sky-take-out/performance/verify-results.ps1'
$text = Get-Content -Raw -LiteralPath $verifierPath
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseInput($text, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'verify-results.ps1 has parse errors.' }
foreach ($functionName in @('ConvertTo-WindowsNativeArgument', 'Invoke-ComposeCapture')) {
    $helper = $ast.Find({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $functionName
    }, $true)
    if (-not $helper) { throw "$functionName is missing." }
    . ([scriptblock]::Create($helper.Extent.Text))
}

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('task17 native argv ' + [Guid]::NewGuid().ToString('N'))
$capturePath = Join-Path $temporaryRoot 'argv.txt'
$nativePath = Join-Path $temporaryRoot 'docker.exe'
$priorPath = $env:PATH
$priorCapturePath = $env:TASK17_ARGV_CAPTURE_PATH
$priorExitCode = $env:TASK17_ARGV_EXIT_CODE
$priorStderr = $env:TASK17_ARGV_STDERR
$priorMode = $env:TASK17_ARGV_MODE
$priorStdout = $env:TASK17_ARGV_STDOUT

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;

public static class Task17NativeArgvCapture
{
    public static int Main(string[] args)
    {
        Console.OutputEncoding = new UTF8Encoding(false);
        File.WriteAllLines(
            Environment.GetEnvironmentVariable("TASK17_ARGV_CAPTURE_PATH"),
            Array.ConvertAll(args, value => Convert.ToBase64String(Encoding.UTF8.GetBytes(value))),
            new UTF8Encoding(false));
        string stderr = Environment.GetEnvironmentVariable("TASK17_ARGV_STDERR");
        if (Environment.GetEnvironmentVariable("TASK17_ARGV_MODE") == "flood")
        {
            // Fail boundedly if the parent stops draining either pipe.
            using (var watchdog = new System.Threading.Timer(_ => Environment.Exit(88), null, 15000, -1))
            {
                for (int i = 0; i < 128; i++)
                {
                    Console.Out.WriteLine("OUT:" + new string('o', 2048));
                    Console.Error.WriteLine("ERR:" + new string('e', 2048));
                }
            }
        }
        if (!String.IsNullOrEmpty(stderr)) Console.Error.WriteLine(stderr);
        string stdout = Environment.GetEnvironmentVariable("TASK17_ARGV_STDOUT");
        if (stdout == null) Console.Out.WriteLine("7"); else Console.Out.Write(stdout);
        int exitCode;
        return Int32.TryParse(Environment.GetEnvironmentVariable("TASK17_ARGV_EXIT_CODE"), out exitCode)
            ? exitCode
            : 0;
    }
}
'@ -Language CSharp -OutputAssembly $nativePath -OutputType ConsoleApplication

    $env:PATH = $temporaryRoot + [IO.Path]::PathSeparator + $priorPath
    $env:TASK17_ARGV_CAPTURE_PATH = $capturePath
    $env:TASK17_ARGV_EXIT_CODE = '0'
    Remove-Item Env:TASK17_ARGV_STDERR -ErrorAction SilentlyContinue
    Remove-Item Env:TASK17_ARGV_MODE -ErrorAction SilentlyContinue
    Remove-Item Env:TASK17_ARGV_STDOUT -ErrorAction SilentlyContinue
    $EnvFile = 'safe.env'
    $ComposeFile = 'safe-compose.yml'
    $OverrideFile = 'safe-override.yml'
    $ComposeProject = 'safe-project'

    $shellCommand = 'set -- "$1"; echo WORDS=$#'
    $sql = 'SELECT 1;'
    $global:LASTEXITCODE = 9
    $result = @(Invoke-ComposeCapture 'mysql' @('sh', '-lc', $shellCommand, 'task17', $sql))
    if ($result.Count -ne 1 -or $result[0].ToString() -cne '7') {
        throw 'Native stdout or zero exit status did not propagate.'
    }

    $captured = @(Get-Content -LiteralPath $capturePath | ForEach-Object {
        [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_))
    })
    $expected = @(
        'compose', '--env-file', 'safe.env', '-f', 'safe-compose.yml', '-f', 'safe-override.yml',
        '-p', 'safe-project', 'exec', '-T', 'mysql', 'sh', '-lc', $shellCommand, 'task17', $sql
    )
    if (($captured -join '|') -cne ($expected -join '|')) {
        $actualCommand = if ($captured.Count -gt 14) { $captured[14] } else { '' }
        $words = if ($actualCommand -ceq 'set -- $1; echo WORDS=$#') { 2 } else { -1 }
        throw "Native PowerShell 5.1 argv corrupted the quoted SQL expansion: WORDS=$words."
    }

    $quote = [char]34
    $cases = @(
        [pscustomobject]@{ Name = 'embedded-quote'; Value = "alpha${quote}omega" },
        [pscustomobject]@{ Name = 'backslashes-before-quote-1'; Value = 'alpha' + (('\' * 1) -join '') + $quote + 'omega' },
        [pscustomobject]@{ Name = 'backslashes-before-quote-2'; Value = 'alpha' + (('\' * 2) -join '') + $quote + 'omega' },
        [pscustomobject]@{ Name = 'backslashes-before-quote-3'; Value = 'alpha' + (('\' * 3) -join '') + $quote + 'omega' },
        [pscustomobject]@{ Name = 'backslashes-before-quote-4'; Value = 'alpha' + (('\' * 4) -join '') + $quote + 'omega' },
        [pscustomobject]@{ Name = 'trailing-backslash-1'; Value = 'alpha omega' + (('\' * 1) -join '') },
        [pscustomobject]@{ Name = 'trailing-backslash-2'; Value = 'alpha omega' + (('\' * 2) -join '') },
        [pscustomobject]@{ Name = 'trailing-backslash-3'; Value = 'alpha omega' + (('\' * 3) -join '') },
        [pscustomobject]@{ Name = 'trailing-backslash-4'; Value = 'alpha omega' + (('\' * 4) -join '') },
        [pscustomobject]@{ Name = 'spaces'; Value = 'alpha beta gamma' },
        [pscustomobject]@{ Name = 'tab'; Value = "alpha`tomega" },
        [pscustomobject]@{ Name = 'crlf'; Value = "alpha`r`nomega" },
        [pscustomobject]@{ Name = 'metacharacters'; Value = '&|<>^()%!;$()[]{}' }
    )
    foreach ($length in @(0, 1, 2, 3, 4, 5, 8, 16, 31, 32, 63, 64, 127)) {
        $slashes = '\' * $length
        $cases += [pscustomobject]@{ Name = "review-compact-$length"; Value = 'left' + $slashes + $quote + 'right' }
        $cases += [pscustomobject]@{ Name = "review-whitespace-quote-$length"; Value = 'left ' + $slashes + $quote + ' right' }
        $cases += [pscustomobject]@{ Name = "review-trailing-$length"; Value = 'left right' + $slashes }
    }
    $cases += [pscustomobject]@{ Name = 'review-empty'; Value = '' }
    $cases += [pscustomobject]@{ Name = 'review-consecutive-empty'; Value = @('', '') }
    $cases += [pscustomobject]@{ Name = 'review-unicode'; Value = @(([string][char]0x4E2D + [char]0x6587), [char]::ConvertFromUtf32(0x1F680)) }
    $cases += [pscustomobject]@{ Name = 'tab-quote'; Value = "left`t${quote}`tright" }
    $cases += [pscustomobject]@{ Name = 'crlf-quote'; Value = "left`r`n${quote}`r`nright" }
    $cases += [pscustomobject]@{ Name = 'consecutive-quotes'; Value = 'left """ right' }
    $cases += [pscustomobject]@{ Name = 'all-whitespace'; Value = " `t`r`n " }
    $prefix = @(
        'compose', '--env-file', 'safe.env', '-f', 'safe-compose.yml', '-f', 'safe-override.yml',
        '-p', 'safe-project', 'exec', '-T', 'mysql', 'argv-probe'
    )
    $failures = @()
    foreach ($case in $cases) {
        $sentinel = 'FOLLOWING-' + $case.Name
        $result = @(Invoke-ComposeCapture 'mysql' (@('argv-probe') + @($case.Value) + @($sentinel)))
        if ($result.Count -ne 1 -or $result[0].ToString() -cne '7') {
            throw "Native stdout failed for case '$($case.Name)'."
        }
        $captured = @(Get-Content -LiteralPath $capturePath | ForEach-Object {
            [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_))
        })
        $expected = @($prefix + @($case.Value) + @($sentinel))
        $equal = $captured.Count -eq $expected.Count
        for ($index = 0; $equal -and $index -lt $expected.Count; $index++) {
            $equal = $captured[$index] -ceq $expected[$index]
        }
        if (-not $equal) {
            $failures += "$($case.Name): expected $($expected.Count) arguments, received $($captured.Count)"
        }
    }
    if ($failures.Count) { throw "Native argv matrix failed $($failures.Count)/$($cases.Count) cases: $($failures -join '; ')." }

    $combined = @('argv-probe')
    foreach ($case in $cases) { $combined += @($case.Value) + @('FOLLOWING-' + $case.Name) }
    Invoke-ComposeCapture 'mysql' $combined | Out-Null
    $captured = @(Get-Content -LiteralPath $capturePath | ForEach-Object {
        [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_))
    })
    $expected = @($prefix[0..11]) + $combined
    if ($captured.Count -ne $expected.Count) { throw 'Combined matrix changed the number of native arguments.' }
    for ($index = 0; $index -lt $expected.Count; $index++) {
        if ($captured[$index] -cne $expected[$index]) { throw "Combined matrix changed native argument $index." }
    }

    $env:TASK17_ARGV_MODE = 'flood'
    $result = @(Invoke-ComposeCapture 'mysql' @('argv-probe', 'flood'))
    if (@($result | Where-Object { $_ -ceq ('OUT:' + ('o' * 2048)) }).Count -ne 128 -or
        @($result | Where-Object { $_ -ceq ('ERR:' + ('e' * 2048)) }).Count -ne 128 -or
        @($result | Where-Object { $_ -ceq '7' }).Count -ne 1) {
        throw 'Concurrent stdout/stderr draining lost output or deadlocked.'
    }
    Remove-Item Env:TASK17_ARGV_MODE
    $unicode = [string][char]0x4E2D + [char]0x6587 + [char]::ConvertFromUtf32(0x1F680)
    $env:TASK17_ARGV_STDOUT = "`r`n$unicode`r`n`r`nlast-without-newline"
    $result = @(Invoke-ComposeCapture 'mysql' @('argv-probe', 'lines'))
    if ($result.Count -ne 4 -or $result[0] -cne '' -or $result[1] -cne $unicode -or
        $result[2] -cne '' -or $result[3] -cne 'last-without-newline') {
        throw 'stdout line boundaries or Unicode were changed.'
    }
    Remove-Item Env:TASK17_ARGV_STDOUT
    $env:TASK17_ARGV_STDERR = $unicode
    $result = @(Invoke-ComposeCapture 'mysql' @('argv-probe', 'stderr-unicode'))
    if ($result.Count -ne 2 -or $result -cnotcontains '7' -or $result -cnotcontains $unicode) {
        throw 'Successful stderr or its Unicode text was lost.'
    }

    $canary = 'TASK17_SECRET_CANARY_MUST_NOT_BE_LOGGED'
    $env:TASK17_ARGV_EXIT_CODE = '9'
    $env:TASK17_ARGV_STDERR = $canary
    $global:LASTEXITCODE = 0
    $caught = $null
    try {
        Invoke-ComposeCapture 'mysql' @('sh', '-lc', $shellCommand, 'task17', $sql) | Out-Null
    } catch {
        $caught = $_.Exception.Message
    }
    if ($caught -cne "Isolated Compose query failed for 'mysql'.") {
        throw 'Native non-zero exit status did not become the bounded verifier error.'
    }
    if ($caught.Contains($canary)) { throw 'Native stderr leaked a secret canary.' }
    if ($ErrorActionPreference -cne 'Stop') { throw 'Native invocation changed the caller error preference.' }

    $env:PATH = $temporaryRoot
    [IO.File]::Move($nativePath, (Join-Path $temporaryRoot 'capture.saved'))
    [IO.File]::WriteAllText($nativePath, 'This is an invalid executable fixture.')
    $caught = $null
    try { Invoke-ComposeCapture 'mysql' @('argv-probe', $canary) | Out-Null } catch { $caught = $_.Exception.Message }
    if ($caught -cne "Isolated Compose query failed for 'mysql'.") { throw 'Native launch failure did not become a bounded error.' }

    Write-Output 'TASK17_NATIVE_ARGV_TEST_PASS'
} finally {
    $env:PATH = $priorPath
    if ($null -eq $priorCapturePath) { Remove-Item Env:TASK17_ARGV_CAPTURE_PATH -ErrorAction SilentlyContinue } else { $env:TASK17_ARGV_CAPTURE_PATH = $priorCapturePath }
    if ($null -eq $priorExitCode) { Remove-Item Env:TASK17_ARGV_EXIT_CODE -ErrorAction SilentlyContinue } else { $env:TASK17_ARGV_EXIT_CODE = $priorExitCode }
    if ($null -eq $priorStderr) { Remove-Item Env:TASK17_ARGV_STDERR -ErrorAction SilentlyContinue } else { $env:TASK17_ARGV_STDERR = $priorStderr }
    if ($null -eq $priorMode) { Remove-Item Env:TASK17_ARGV_MODE -ErrorAction SilentlyContinue } else { $env:TASK17_ARGV_MODE = $priorMode }
    if ($null -eq $priorStdout) { Remove-Item Env:TASK17_ARGV_STDOUT -ErrorAction SilentlyContinue } else { $env:TASK17_ARGV_STDOUT = $priorStdout }
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $resolvedRoot = [IO.Path]::GetFullPath($temporaryRoot)
    if (-not $resolvedRoot.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolvedRoot) -notmatch '^task17 native argv [a-f0-9]{32}$') { throw 'Unsafe test cleanup target.' }
    if (Test-Path -LiteralPath $resolvedRoot) { Remove-Item -LiteralPath $resolvedRoot -Recurse -Force }
}
