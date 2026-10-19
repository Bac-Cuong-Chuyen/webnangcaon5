[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
if (-not (Test-Path -LiteralPath 'pom.xml')) { throw 'Chay tu thu muc goc du an.' }
$outDir = Join-Path $PWD ('scripts\B09-test-results\CACHE_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff'))
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$script:checks = @()
$script:snapshots = @()
$restoreNeeded = $false
$original = $null
$productPath = $null
$managerSession = $null

function Get-B09Hash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Send-B09($Session, [string]$Method, [string]$Path, $Body = $null) {
    $headers = @{ Accept = 'application/json' }
    $parameters = @{
        Uri = $BaseUrl + $Path; Method = $Method; WebSession = $Session
        UseBasicParsing = $true; MaximumRedirection = 0; TimeoutSec = 60
    }
    if ($Method -notin @('GET', 'HEAD')) {
        $csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $Session -TimeoutSec 30
        $headers[$csrf.headerName] = $csrf.token
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    $parameters.Headers = $headers
    $response = Invoke-WebRequest @parameters
    if ([int]$response.StatusCode -ne 200) { throw "HTTP $($response.StatusCode) tai $Path" }
    $text = [string]$response.Content
    return [pscustomobject]@{
        Body = $text; Json = ($text | ConvertFrom-Json)
        Hash = Get-B09Hash $text; Status = [int]$response.StatusCode
    }
}

function New-B09Session([string]$Username, [string]$ExpectedRole) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $secret = Read-Host "Nhap mat khau MOI cua $Username ($ExpectedRole)" -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential($Username, $secret)
    $login = Send-B09 $session 'POST' '/api/auth/login' @{
        username = $Username; password = $credential.GetNetworkCredential().Password
    }
    if ($login.Json.role -ne $ExpectedRole) { throw "Sai vai tro cho $Username" }
    return $session
}

function Assert-B09([string]$Code, [bool]$Condition, [string]$Detail) {
    $status = if ($Condition) { 'PASS' } else { 'FAIL' }
    $script:checks += [pscustomobject]@{ Code = $Code; Status = $status; Detail = $Detail }
    $script:checks | Export-Csv -LiteralPath (Join-Path $outDir 'checks.csv') -NoTypeInformation -Encoding UTF8
    Write-Host "$Code $status - $Detail"
    if (-not $Condition) { throw "Kiem chung $Code khong dat: $Detail" }
}

function Get-B09Stats([string]$Label) {
    $stats = (Send-B09 $adminSession 'GET' '/api/admin/report-cache').Json
    if ($stats.cache -ne 'exportReports') { throw 'Sai cache hoac cache chua bat.' }
    $script:snapshots += [pscustomobject]@{
        Label = $Label; Time = [DateTimeOffset]::Now.ToString('o')
        Generation = $stats.generation; Size = $stats.estimatedSize
        Hits = $stats.hitCount; Misses = $stats.missCount
        Loads = $stats.loadSuccessCount; FailedLoads = $stats.loadFailureCount
    }
    $script:snapshots | Export-Csv -LiteralPath (Join-Path $outDir 'cache-stats.csv') -NoTypeInformation -Encoding UTF8
    return $stats
}

function Get-B09DeniedStatus($Session, [string]$Path) {
    try { $null = Send-B09 $Session 'GET' $Path; return 200 }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

try {
    Write-Host 'Kiem chung cache LOCAL. Doi tam 1 SKU B09 va khoi phuc ngay sau test.'
    $adminSession = New-B09Session 'b08_admin' 'ADMIN'
    $managerSession = New-B09Session 'b09_manager' 'WAREHOUSE_MANAGER'
    $null = Get-B09Stats 'initial'

    $products = (Send-B09 $managerSession 'GET' '/api/products/search?keyword=B09&page=0&size=1').Json
    if ([long]$products.totalElements -ne 5000) { throw 'Chua du 5000 san pham B09.' }
    $productPath = '/api/products/' + $products.content[0].id
    $original = (Send-B09 $managerSession 'GET' $productPath).Json
    if ($original.sku -notmatch '^B09-P[0-9]{5}$') { throw 'San pham khong phai SKU seed B09 nguyen ban.' }
    $original | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $outDir 'original-product.json') -Encoding UTF8
    $fullPath = '/api/reports/exports?fromDate=2026-10-01&toDate=2026-10-15'
    $baseReport = Send-B09 $managerSession 'GET' $fullPath
    if ([long]$baseReport.Json.totalRows -ne 5000 -or [long]$baseReport.Json.totalQuantity -ne 100000) {
        throw 'Du lieu bao cao khong khop bo seed; chua sua san pham.'
    }

    # PUT cung gia tri de tao moc cache rong ma khong doi du lieu nghiep vu.
    $restoreNeeded = $true
    $null = Send-B09 $managerSession 'PUT' $productPath $original
    $s0 = Get-B09Stats 'after-initial-invalidation'
    $first = Send-B09 $managerSession 'GET' $fullPath
    $s1 = Get-B09Stats 'after-cold-read'
    Assert-B09 'CACHE01' (($s1.missCount -gt $s0.missCount) -and ($s1.loadSuccessCount -gt $s0.loadSuccessCount) -and ($first.Hash -eq $baseReport.Hash)) 'Lan dau cache miss, nap lai DB, ket qua dung.'

    $second = Send-B09 $managerSession 'GET' $fullPath
    $s2 = Get-B09Stats 'after-repeated-read'
    Assert-B09 'CACHE02' (($s2.hitCount -gt $s1.hitCount) -and ($s2.loadSuccessCount -eq $s1.loadSuccessCount) -and ($second.Hash -eq $first.Hash)) 'Goi lap cache hit, khong nap lai, noi dung giu nguyen.'

    $subset = Send-B09 $managerSession 'GET' '/api/reports/exports?fromDate=2026-10-14&toDate=2026-10-15'
    $s3 = Get-B09Stats 'after-different-filter'
    $expectedSubset = @($baseReport.Json.data | Where-Object { $_.date -ge '2026-10-14' -and $_.date -le '2026-10-15' })
    $expectedTotal = ($expectedSubset | Measure-Object -Property totalQuantity -Sum).Sum
    Assert-B09 'CACHE03' (($s3.loadSuccessCount -gt $s2.loadSuccessCount) -and ([long]$subset.Json.totalRows -eq $expectedSubset.Count) -and ([long]$subset.Json.totalQuantity -eq [long]$expectedTotal)) 'Khac khoang ngay dung cache key khac, tong hop dung.'

    $adminReportStatus = Get-B09DeniedStatus $adminSession $fullPath
    Assert-B09 'CACHE04' ($adminReportStatus -eq 403) 'ADMIN van bi chan xem bao cao da co cache (HTTP403).'
    $managerStatsStatus = Get-B09DeniedStatus $managerSession '/api/admin/report-cache'
    Assert-B09 'CACHE05' ($managerStatsStatus -eq 403) 'MANAGER khong doc duoc thong ke cache ADMIN (HTTP403).'

    $changedSku = $original.sku + '-CACHECHECK'
    $changed = [ordered]@{
        sku = $changedSku; name = $original.name; unit = $original.unit; price = $original.price
    }
    $null = Send-B09 $managerSession 'PUT' $productPath $changed
    $s4 = Get-B09Stats 'after-sku-change'
    $changedReport = Send-B09 $managerSession 'GET' $fullPath
    $s5 = Get-B09Stats 'after-read-changed-data'
    $changedRows = @($changedReport.Json.data | Where-Object { $_.sku -eq $changedSku })
    $oldRows = @($changedReport.Json.data | Where-Object { $_.sku -eq $original.sku })
    Assert-B09 'CACHE06' (($s4.generation -gt $s3.generation) -and ($s5.loadSuccessCount -gt $s4.loadSuccessCount) -and ($changedRows.Count -gt 0) -and ($oldRows.Count -eq 0) -and ([long]$changedReport.Json.totalQuantity -eq 100000)) 'Doi SKU -> invalidation -> bao cao nap du lieu moi, tong luong khong doi.'

    $null = Send-B09 $managerSession 'PUT' $productPath $original
    $restoredProduct = (Send-B09 $managerSession 'GET' $productPath).Json
    $restored = Send-B09 $managerSession 'GET' $fullPath
    $null = Get-B09Stats 'after-restoration'
    Assert-B09 'CACHE07' (($restored.Hash -eq $baseReport.Hash) -and ($restoredProduct.sku -eq $original.sku) -and ($restoredProduct.name -eq $original.name) -and ($restoredProduct.unit -eq $original.unit) -and ([decimal]$restoredProduct.price -eq [decimal]$original.price)) 'Da khoi phuc san pham va bao cao ve dung du lieu truoc test.'
    $restoreNeeded = $false
    [pscustomobject]@{
        Status = 'complete'; BaseUrl = $BaseUrl; CompletedAt = [DateTimeOffset]::Now.ToString('o')
        Checks = $script:checks.Count; ProductId = $original.id
        OriginalReportHash = $baseReport.Hash; RestoredReportHash = $restored.Hash
        DataRestored = $true; TtlExpiryTested = $false
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir 'result.json') -Encoding UTF8
    Write-Host ''
    Write-Host 'HOAN THANH CACHE: 7/7 PASS. DA KHOI PHUC DU LIEU.'
    $script:checks | Format-Table Code, Status -AutoSize
    Write-Host ('Ket qua: ' + $outDir)
} catch {
    Write-Host ('CHUA HOAN THANH: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host ('Ket qua: ' + $outDir)
    throw
} finally {
    if ($restoreNeeded -and $original -and $managerSession -and $productPath) {
        try {
            $null = Send-B09 $managerSession 'PUT' $productPath $original
            $check = (Send-B09 $managerSession 'GET' $productPath).Json
            if ($check.sku -ne $original.sku) { throw 'SKU sau khoi phuc chua khop.' }
            Write-Host 'Da khoi phuc SKU goc sau khi test dung som.'
        } catch {
            Write-Host ('CAN KHOI PHUC SAN PHAM tu original-product.json tai: ' + $outDir) -ForegroundColor Red
        }
    }
}
