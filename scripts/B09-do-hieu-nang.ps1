[CmdletBinding()]
param(
    [ValidateSet('before', 'after')][string]$Phase = 'before',
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$Username = 'b09_manager',
    [string]$BaselineDirectory
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
if (-not (Test-Path -LiteralPath 'pom.xml')) {
    throw 'Hay chay tu thu muc goc supply-chain-management co pom.xml.'
}
if ($Phase -eq 'after' -and -not $BaselineDirectory) {
    throw 'Lan do after can -BaselineDirectory tro den ket qua before.'
}

$runName = $Phase.ToUpperInvariant() + '_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
$outDir = Join-Path $PWD ('scripts\B09-test-results\' + $runName)
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$rawFile = Join-Path $outDir 'samples.csv'
$summaryFile = Join-Path $outDir 'summary.csv'
$manifestFile = Join-Path $outDir 'manifest.json'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$invariant = [System.Globalization.CultureInfo]::InvariantCulture
$manifest = $null

function Get-BodyHash([string]$Text) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString(
            $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text))
        ).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Read-B09Endpoint([string]$Path) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Uri ($BaseUrl + $Path) `
            -Method Get -WebSession $session -UseBasicParsing `
            -Headers @{ Accept = 'application/json' } `
            -MaximumRedirection 0 -TimeoutSec 60
    } finally { $watch.Stop() }
    if ([int]$response.StatusCode -ne 200) {
        throw "HTTP $($response.StatusCode) tai $Path"
    }
    if ([string]$response.Headers['Content-Type'] -notlike '*application/json*') {
        throw "Phan hoi khong phai JSON tai $Path"
    }
    $body = [string]$response.Content
    return [pscustomobject]@{
        Status = [int]$response.StatusCode
        Milliseconds = $watch.Elapsed.TotalMilliseconds
        Body = $body
        Hash = Get-BodyHash $body
        Bytes = [System.Text.Encoding]::UTF8.GetByteCount($body)
    }
}

try {
    Write-Host "Do $Phase tren $BaseUrl; tai khoan $Username"
    $secret = Read-Host "Nhap mat khau $Username" -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential($Username, $secret)
    $csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session -TimeoutSec 30
    $headers = @{}
    $headers[$csrf.headerName] = $csrf.token
    $loginBody = @{
        username = $Username
        password = $credential.GetNetworkCredential().Password
    } | ConvertTo-Json
    $login = Invoke-RestMethod "$BaseUrl/api/auth/login" -Method Post `
        -WebSession $session -Headers $headers -ContentType 'application/json' `
        -Body $loginBody -TimeoutSec 30
    $loginBody = $null
    $credential = $null
    $secret = $null
    if ($login.role -notin @('WAREHOUSE_MANAGER', 'DIRECTOR')) {
        throw 'Tai khoan can vai tro WAREHOUSE_MANAGER hoac DIRECTOR de do ba bao cao.'
    }

    # Kiem tra du lieu, khong tinh vao 50 mau.
    $products = (Read-B09Endpoint '/api/products/search?keyword=B09&page=0&size=1').Body | ConvertFrom-Json
    if ([long]$products.totalElements -ne 5000 -or @($products.content).Count -ne 1) {
        throw 'Chua xac nhan du 5000 san pham B09. Dung do de kiem tra du lieu.'
    }
    $reportChecks = @()
    foreach ($spec in @(
        @{ Name = 'imports'; Rows = 5000; Total = 500000 },
        @{ Name = 'exports'; Rows = 5000; Total = 100000 },
        @{ Name = 'transfers'; Rows = 1; Total = 50000 }
    )) {
        $path = '/api/reports/' + $spec.Name + '?fromDate=2026-10-01&toDate=2026-10-15'
        $report = (Read-B09Endpoint $path).Body | ConvertFrom-Json
        if ([long]$report.totalRows -ne $spec.Rows -or [long]$report.totalQuantity -ne $spec.Total) {
            throw "Du lieu bao cao $($spec.Name) khong khop seed: rows=$($report.totalRows), total=$($report.totalQuantity)."
        }
        $reportChecks += [pscustomobject]@{
            Name = $spec.Name; TotalRows = $report.totalRows; TotalQuantity = $report.totalQuantity
        }
    }
    Write-Host 'Da xac nhan 5000 san pham va tong cua 3 bao cao.'

    if ($Phase -eq 'after') {
        $baseline = Get-Content -LiteralPath (Join-Path $BaselineDirectory 'manifest.json') -Raw | ConvertFrom-Json
        if ($baseline.Phase -ne 'before' -or $baseline.Status -ne 'complete') {
            throw 'Baseline phai la lan before da hoan tat.'
        }
        if ($baseline.BaseUrl -ne $BaseUrl -or $baseline.Username -ne $Username) {
            throw 'BaseUrl va tai khoan phai giong lan before.'
        }
        $endpoints = @($baseline.Endpoints)
    } else {
        $id = $products.content[0].id
        $endpoints = @(
            [pscustomobject]@{ Name = 'E1_ProductSearch'; Path = '/api/products/search?keyword=B09&page=0&size=20&sortBy=id&direction=asc'; ExpectedHash = '' },
            [pscustomobject]@{ Name = 'E2_ImportReport'; Path = '/api/reports/imports?fromDate=2026-10-01&toDate=2026-10-15'; ExpectedHash = '' },
            [pscustomobject]@{ Name = 'E3_ExportReport'; Path = '/api/reports/exports?fromDate=2026-10-01&toDate=2026-10-15'; ExpectedHash = '' },
            [pscustomobject]@{ Name = 'E4_TransferReport'; Path = '/api/reports/transfers?fromDate=2026-10-01&toDate=2026-10-15'; ExpectedHash = '' },
            [pscustomobject]@{ Name = 'E5_ProductDetail'; Path = "/api/products/$id"; ExpectedHash = '' }
        )
    }

    $manifest = [ordered]@{
        Phase = $Phase; Status = 'running'; BaseUrl = $BaseUrl
        Username = $Username; Role = $login.role
        StartedAt = [DateTimeOffset]::Now.ToString('o')
        GitHead = ((& git rev-parse HEAD) -join '').Trim()
        GitStatus = @(& git status --short --branch)
        PowerShellVersion = $PSVersionTable.PSVersion.ToString()
        ComputerName = $env:COMPUTERNAME
        SamplesPerEndpoint = 50; WarmupsPerEndpoint = 5
        Measurement = 'Sequential Invoke-WebRequest, buffered response; Stopwatch; JSON/hash validation outside timer'
        CacheState = '5 warmups per endpoint; after with cache measures warmed cache, not cold miss'
        ProductCountB09 = $products.totalElements; ReportChecks = $reportChecks
        BaselineDirectory = $BaselineDirectory
        Endpoints = $endpoints
        ConfigurationHashes = @(
            foreach ($path in @('src/main/resources/application.properties', 'src/main/resources/application-b09.properties', 'database/b09-seed.sql', 'database/b09-seed-reports.sql')) {
                if (Test-Path -LiteralPath $path) {
                    [pscustomobject]@{ Path = $path; SHA256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
                }
            }
        )
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestFile -Encoding UTF8
    $summary = @()

    foreach ($endpoint in $endpoints) {
        Write-Host ("Dang do {0}: 5 lan lam nong + 50 mau..." -f $endpoint.Name)
        for ($warmup = 1; $warmup -le 5; $warmup++) {
            $probe = Read-B09Endpoint $endpoint.Path
            if ($Phase -eq 'before' -and $warmup -eq 1) { $endpoint.ExpectedHash = $probe.Hash }
            if ($probe.Hash -ne $endpoint.ExpectedHash) {
                throw "Noi dung $($endpoint.Name) thay doi so voi moc doi chieu. Khong do tren du lieu lech."
            }
        }

        $times = @()
        for ($i = 1; $i -le 50; $i++) {
            $stamp = [DateTimeOffset]::Now.ToString('o')
            try {
                $result = Read-B09Endpoint $endpoint.Path
                if ($result.Hash -ne $endpoint.ExpectedHash) {
                    throw 'Noi dung phan hoi thay doi trong khi do.'
                }
            } catch {
                [pscustomobject]@{
                    Phase = $Phase; Endpoint = $endpoint.Name; Iteration = $i
                    HttpStatus = 'ERROR'; Milliseconds = ''; ResponseBytes = ''
                    Valid = $false; Timestamp = $stamp; Url = $BaseUrl + $endpoint.Path
                    Error = $_.Exception.Message
                } | Export-Csv -LiteralPath $rawFile -NoTypeInformation -Encoding UTF8 -Append
                throw
            }
            $times += $result.Milliseconds
            [pscustomobject]@{
                Phase = $Phase; Endpoint = $endpoint.Name; Iteration = $i
                HttpStatus = $result.Status
                Milliseconds = $result.Milliseconds.ToString('F6', $invariant)
                ResponseBytes = $result.Bytes; Valid = $true; Timestamp = $stamp
                Url = $BaseUrl + $endpoint.Path; Error = ''
            } | Export-Csv -LiteralPath $rawFile -NoTypeInformation -Encoding UTF8 -Append
        }

        $ordered = @($times | Sort-Object)
        $median = ($ordered[24] + $ordered[25]) / 2.0
        $summary += [pscustomobject]@{
            Endpoint = $endpoint.Name; Samples = $times.Count
            MedianMs = $median.ToString('F3', $invariant)
            MinMs = $ordered[0].ToString('F3', $invariant)
            MaxMs = $ordered[49].ToString('F3', $invariant)
            Url = $BaseUrl + $endpoint.Path
        }
        $summary | Export-Csv -LiteralPath $summaryFile -NoTypeInformation -Encoding UTF8
        $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestFile -Encoding UTF8
    }

    $manifest.Status = 'complete'
    $manifest['CompletedAt'] = [DateTimeOffset]::Now.ToString('o')
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestFile -Encoding UTF8
    Write-Host ''
    Write-Host ('HOAN THANH: ' + $Phase.ToUpperInvariant() + ' - 250/250 mau HTTP 200, noi dung on dinh.')
    $summary | Format-Table Endpoint, Samples, MedianMs, MinMs, MaxMs -AutoSize

    if ($Phase -eq 'after') {
        $beforeRows = Import-Csv -LiteralPath (Join-Path $BaselineDirectory 'summary.csv')
        $comparison = foreach ($row in $summary) {
            $before = $beforeRows | Where-Object { $_.Endpoint -eq $row.Endpoint }
            $beforeMs = [double]::Parse([string]$before.MedianMs, $invariant)
            $afterMs = [double]::Parse([string]$row.MedianMs, $invariant)
            [pscustomobject]@{
                Endpoint = $row.Endpoint; BeforeMs = $beforeMs; AfterMs = $afterMs
                ImprovementPercent = [Math]::Round((($beforeMs - $afterMs) / $beforeMs) * 100, 2)
            }
        }
        $comparison | Export-Csv -LiteralPath (Join-Path $outDir 'comparison.csv') -NoTypeInformation -Encoding UTF8
        $comparison | Format-Table -AutoSize
    }
    Write-Host ('Ket qua: ' + $outDir)
} catch {
    if ($manifest) {
        $manifest.Status = 'failed'
        $manifest['Failure'] = $_.Exception.Message
        $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestFile -Encoding UTF8
    }
    Write-Host ('CHUA HOAN THANH. Ket qua dang do luu tai: ' + $outDir) -ForegroundColor Red
    throw
}
