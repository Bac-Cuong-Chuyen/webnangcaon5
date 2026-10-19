$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath 'pom.xml')) { throw 'Chay tu thu muc goc du an.' }
$base = 'http://localhost:8081'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$secret = Read-Host 'Nhap mat khau ADMIN MOI cua b08_admin' -AsSecureString
$credential = New-Object System.Management.Automation.PSCredential('b08_admin', $secret)
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $session -TimeoutSec 30
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$loginBody = @{
    username = 'b08_admin'; password = $credential.GetNetworkCredential().Password
} | ConvertTo-Json
$login = Invoke-RestMethod "$base/api/auth/login" -Method Post `
    -WebSession $session -Headers $headers -ContentType 'application/json' `
    -Body $loginBody -TimeoutSec 30
$loginBody = $null
$credential = $null
$secret = $null
if ($login.role -ne 'ADMIN') { throw 'Can tai khoan ADMIN.' }
$stats = Invoke-RestMethod "$base/api/admin/report-cache" -WebSession $session -TimeoutSec 30
$outDir = Join-Path $PWD ('scripts\B09-test-results\CACHE_STATS_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff'))
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
[pscustomobject]@{
    Time = [DateTimeOffset]::Now.ToString('o'); BaseUrl = $base
    Note = 'Cumulative counters since current app startup. Login and GET stats do not evict the report cache.'
    Stats = $stats
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outDir 'cache-stats.json') -Encoding UTF8
$stats | Format-List cache, generation, estimatedSize, hitCount, missCount, loadSuccessCount, loadFailureCount, evictionCount
Write-Host 'Chi doc thong ke, khong thay doi du lieu va khong xoa cache.'
Write-Host ('Ket qua: ' + $outDir)
