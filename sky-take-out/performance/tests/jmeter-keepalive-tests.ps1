param([string]$JMeterHome)
$ErrorActionPreference = 'Stop'
if (-not $JMeterHome) {
    $JMeterHome = Join-Path $PSScriptRoot '../../../.superpowers/sdd/plan/tools/apache-jmeter-5.6.3'
}
$JMeterHome = (Resolve-Path -LiteralPath $JMeterHome).Path
$classpath = (Join-Path $JMeterHome 'lib/*') + [IO.Path]::PathSeparator + (Join-Path $JMeterHome 'lib/ext/*')
# Java source-file mode leaves no compiled fixture or raw result files in the repository.
& java '-Djava.awt.headless=true' -cp $classpath (Join-Path $PSScriptRoot 'JMeterKeepAliveFixture.java') $JMeterHome (Join-Path $PSScriptRoot '../jmeter')
$javaExit = $LASTEXITCODE
if ($javaExit -ne 0) { throw "Live JMeter keepalive regression failed (exit $javaExit)." }
