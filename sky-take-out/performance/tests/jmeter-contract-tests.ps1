$ErrorActionPreference = 'Stop'
$count = 0
foreach ($name in @('read-only', 'light-write', 'order-submit')) {
    [xml]$plan = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot "../jmeter/$name.jmx")
    foreach ($sampler in $plan.SelectNodes('//HTTPSamplerProxy')) {
        $count++
        $settings = @($sampler.SelectNodes('boolProp[@name="HTTPSampler.use_keepalive"]'))
        if ($settings.Count -ne 1 -or $settings[0].InnerText -cne 'true') {
            throw "$name/$($sampler.GetAttribute('testname')) must explicitly enable HTTP keepalive."
        }
    }
}
if ($count -ne 12) { throw 'Expected all 12 shipped HTTP samplers.' }
Write-Output 'JMeter keepalive static contract PASS (12 samplers)'
