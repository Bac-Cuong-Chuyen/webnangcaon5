[CmdletBinding()]
param([switch]$UseExistingResults)
$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'pom.xml'))) { throw 'Run from the repository root containing pom.xml.' }
if (-not $UseExistingResults) {
    & mvn clean test
    if ($LASTEXITCODE -ne 0) { throw 'Maven failed; acceptance has not been marked PASS.' }
}
$cases = Get-Content -LiteralPath 'verification/b09-acceptance-cases.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$cases = @($cases)
$manual = Get-Content -LiteralPath 'verification/b09-acceptance-manual.json' -Raw -Encoding UTF8 | ConvertFrom-Json
if ($cases.Count -ne 25 -or @($cases.Id | Select-Object -Unique).Count -ne 25) { throw 'Expected 25 unique case IDs.' }
$distribution = @{NGHIEP_VU=9; NGOAI_LE=6; BAO_MAT=7; BAO_CAO=3}
foreach ($group in $distribution.Keys) {
    if (@($cases | Where-Object Group -eq $group).Count -ne $distribution[$group]) { throw "Invalid distribution: $group" }
}
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss_fff'
$outDir = Join-Path $repoRoot "scripts/B09-test-results/ACCEPTANCE_$stamp"
New-Item -ItemType Directory -Path (Join-Path $outDir 'junit') -Force | Out-Null
$sourceReports = @{}
$evidenceHashes = [Collections.Generic.List[object]]::new()
$results = [Collections.Generic.List[object]]::new()
$repoPrefix = $repoRoot.TrimEnd('\') + '\'
function Resolve-EvidencePath([string]$relative) {
    $absolute = [IO.Path]::GetFullPath((Join-Path $repoRoot $relative))
    if (-not $absolute.StartsWith($repoPrefix,[StringComparison]::OrdinalIgnoreCase)) { throw "Path outside repository: $relative" }
    return $absolute
}
foreach ($case in $cases) {
    $status = 'MISSING'
    $actual = ''
    $provenance = ''
    $suiteTime = ''
    if ($case.Level -like 'Manual*') {
        $valid = $manual.CaseId -eq $case.Id -and $manual.Result -eq 'PASS' -and @($manual.Evidence).Count -ge 2
        foreach ($item in $manual.Evidence) {
            $file = Resolve-EvidencePath $item.Path
            if (-not (Test-Path -LiteralPath $file)) { $valid=$false; continue }
            $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
            if ($hash -ne $item.SHA256) { $valid=$false }
            $evidenceHashes.Add([PSCustomObject]@{Path=$item.Path;SHA256=$hash;Kind='Previously observed manual evidence'})
        }
        $status = if ($valid) { 'PASS' } else { 'MISSING' }
        $actual = $manual.Observation
        $provenance = 'verification/b09-acceptance-manual.json'
        $suiteTime = $manual.ObservedDate
    } else {
        $relative = 'target/surefire-reports/TEST-' + $case.Class + '.xml'
        $file = Resolve-EvidencePath $relative
        $provenance = $relative + ' :: ' + $case.Test
        if (Test-Path -LiteralPath $file) {
            if (-not $sourceReports.ContainsKey($case.Class)) {
                $settings = [Xml.XmlReaderSettings]::new()
                $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
                $settings.XmlResolver = $null
                $reader = [Xml.XmlReader]::Create($file,$settings)
                try { $document = [Xml.XmlDocument]::new(); $document.XmlResolver=$null; $document.Load($reader) } finally { $reader.Dispose() }
                $sourceReports[$case.Class] = $document
                Copy-Item -LiteralPath $file -Destination (Join-Path $outDir ('junit/' + [IO.Path]::GetFileName($file)))
                $evidenceHashes.Add([PSCustomObject]@{Path=$relative;SHA256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash;Kind='JUnit XML source'})
            }
            $suite = $sourceReports[$case.Class].DocumentElement
            $suiteTime = $suite.GetAttribute('timestamp')
            $matches = @($suite.SelectNodes('testcase') | Where-Object { $_.GetAttribute('name') -ceq $case.Test -and $_.GetAttribute('classname') -ceq $case.Class })
            if ($matches.Count -eq 1) {
                $node = $matches[0]
                if ($null -ne $node.SelectSingleNode('failure|error')) { $status='FAIL'; $actual='JUnit recorded a failure or error; inspect the copied XML.' }
                elseif ($null -ne $node.SelectSingleNode('skipped')) { $status='SKIPPED'; $actual='JUnit skipped this test; not accepted as PASS.' }
                else { $status='PASS'; $actual='JUnit PASS: ' + $case.Expected }
            } else { $actual='Expected exactly one matching JUnit testcase, found ' + $matches.Count }
        } else { $actual='JUnit report is missing.' }
    }
    $results.Add([PSCustomObject]@{
        Id=$case.Id; Group=$case.Group; Title=$case.Title; Preconditions=$case.Preconditions
        Steps=$case.Steps; Expected=$case.Expected; Actual=$actual; Status=$status
        Level=$case.Level; Evidence=$provenance; SourceTimestamp=$suiteTime
    })
}
$results | Export-Csv -LiteralPath (Join-Path $outDir 'results.csv') -NoTypeInformation -Encoding UTF8
$header = '<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>B09 acceptance results</title><style>body{font:14px system-ui;margin:24px}table{border-collapse:collapse}td,th{border:1px solid #aaa;padding:8px;text-align:left;vertical-align:top}th{background:#eee}</style></head><body><h1>B09 acceptance - 25 cases</h1><p>24 JUnit mappings and 1 previously observed manual Excel case. Local evidence; not an online certification.</p>'
$html = ($results | ConvertTo-Html -Fragment | Out-String)
[IO.File]::WriteAllText((Join-Path $outDir 'report.html'),$header+$html+'</body></html>',[Text.UTF8Encoding]::new($false))
$passCount = @($results | Where-Object Status -eq 'PASS').Count
try {
    $head = (& git rev-parse HEAD 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { $head='unavailable' }
} catch { $head='unavailable' }
$sourceSnapshot = @(Get-ChildItem -LiteralPath 'src' -File -Recurse | Where-Object { $_.Extension -in @('.java','.html','.css','.js','.properties') } | ForEach-Object {
    [PSCustomObject]@{Path=$_.FullName.Substring($repoPrefix.Length);SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$manifest = [PSCustomObject]@{
    CollectedAtUtc=[DateTime]::UtcNow.ToString('o'); GitHeadAtCollection=$head
    Mode=$(if($UseExistingResults){'Reuse existing JUnit XML; tests were NOT rerun by this command'}else{'mvn clean test then collect'})
    Scope='Local JVM/H2 test databases and manual browser Excel evidence; no production requests'
    Distribution=$distribution; Total=25; Passed=$passCount; AutomatedMappings=24; ManualEvidenceCases=1
    Note='JUnit source timestamps are preserved. Collection HEAD is not claimed as the historical test execution commit.'
    EvidenceHashes=$evidenceHashes; SourceSnapshotAtCollection=$sourceSnapshot
}
[IO.File]::WriteAllText((Join-Path $outDir 'manifest.json'),($manifest | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$results | Select-Object Id,Group,Status,Level | Format-Table -AutoSize
Write-Host "ACCEPTANCE: $passCount/25 PASS (24 JUnit mappings + 1 manual evidence case)."
Write-Host "Results: $outDir"
if ($passCount -ne 25) { throw 'Acceptance is incomplete. Evidence was saved; no missing/skipped/failed case is treated as PASS.' }
