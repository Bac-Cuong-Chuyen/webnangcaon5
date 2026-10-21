param([switch]$SameTransfer)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$base = 'http://localhost:8080'
$prefix = if ($SameTransfer) { 'B06_SAME_' } else { 'B06_' }
$run = $prefix + [guid]::NewGuid().ToString('N').Substring(0, 10)
$draftCount = if ($SameTransfer) { 1 } else { 50 }
$transferQuantity = if ($SameTransfer) { 3 } else { 1 }
$expectedSuccess = if ($SameTransfer) { 1 } else { 10 }
$expectedRejected = 50 - $expectedSuccess
$expectedSource = if ($SameTransfer) { 7 } else { 0 }
$expectedTarget = 10 - $expectedSource
$mode = if ($SameTransfer) { '50 requests - SAME transfer' } else { '50 requests - DIFFERENT transfers' }
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.CookieContainer = [System.Net.CookieContainer]::new()
$handler.UseCookies = $true
$handler.MaxConnectionsPerServer = 50
[System.Net.ServicePointManager]::DefaultConnectionLimit = 50
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(120)
$jobs = @()

function Post-Json($path, $body) {
    $json = $body | ConvertTo-Json -Compress
    $content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
    try {
        $response = $client.PostAsync("$base$path", $content).GetAwaiter().GetResult()
        try {
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) {
                throw "Setup failed: $path HTTP $([int]$response.StatusCode) $text"
            }
            return $text
        } finally { $response.Dispose() }
    } finally { $content.Dispose() }
}

try {
    Write-Host "Preparing isolated test: $run"
    # A fresh password belongs only to this isolated test run; never log it.
    $passwordBytes = New-Object byte[] 32
    $passwordGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $passwordGenerator.GetBytes($passwordBytes)
        $account = @{ username = $run.ToLower(); password = [Convert]::ToBase64String($passwordBytes) }
    } finally {
        $passwordGenerator.Dispose()
        [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    }
    $null = Post-Json '/api/auth/register' $account
    $null = Post-Json '/api/auth/login' $account
    $source = Post-Json '/api/warehouses' @{
        code = "${run}_SRC"; name = 'B06 source test'; address = 'Local test'
    } | ConvertFrom-Json
    $target = Post-Json '/api/warehouses' @{
        code = "${run}_DST"; name = 'B06 target test'; address = 'Local test'
    } | ConvertFrom-Json
    $product = Post-Json '/api/products' @{
        sku = "${run}_PRODUCT"; name = 'B06 concurrency test'; unit = 'pcs'; price = 100
    } | ConvertFrom-Json
    if (-not $source.id -or -not $target.id -or -not $product.id) {
        throw 'Setup response did not contain valid warehouse/product IDs.'
    }
    $null = Post-Json '/api/imports' @{
        receiptCode = "${run}_IMPORT"; warehouseId = $source.id
        productId = $product.id; quantity = 10
    }
    $ids = @()
    foreach ($i in 1..$draftCount) {
        $transfer = Post-Json '/api/transfers' @{
            transferCode = "${run}_T$i"; fromWarehouseId = $source.id
            toWarehouseId = $target.id; productId = $product.id; quantity = $transferQuantity
        } | ConvertFrom-Json
        if (-not $transfer.id -or $transfer.status -ne 'DRAFT') {
            throw "Invalid draft transfer at index $i."
        }
        $ids += $transfer.id
    }
    if ($SameTransfer) {
        $singleId = $ids[0]
        $ids = @(1..50 | ForEach-Object { $singleId })
    }

    Write-Host 'Sending 50 overlapping completion requests...'
    $watch = [Diagnostics.Stopwatch]::StartNew()
    foreach ($id in $ids) {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Post, "$base/api/transfers/$id/complete")
        $jobs += [pscustomobject]@{
            Id = $id; Request = $request; Task = $client.SendAsync($request)
        }
    }
    $results = foreach ($job in $jobs) {
        try {
            $response = $job.Task.GetAwaiter().GetResult()
            try {
                [pscustomobject]@{
                    TransferId = $job.Id; Status = [int]$response.StatusCode
                    Body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                }
            } finally { $response.Dispose() }
        } catch {
            [pscustomobject]@{
                TransferId = $job.Id; Status = 0; Body = $_.Exception.Message
            }
        }
    }
    $watch.Stop()
    $folder = Join-Path $PSScriptRoot "B06-test-results\$run"
    $null = New-Item -ItemType Directory -Path $folder -Force
    $results | Export-Csv (Join-Path $folder '50-responses.csv') -NoTypeInformation -Encoding UTF8
    $sql = @"
SELECT w.id AS warehouse_id, w.code, i.quantity
FROM warehouse w
LEFT JOIN inventory i ON i.warehouse_id = w.id AND i.product_id = $($product.id)
WHERE w.id IN ($($source.id), $($target.id))
ORDER BY w.id;

SELECT status, COUNT(*) AS receipt_count
FROM stock_transfer
WHERE LEFT(transfer_code, LENGTH('$run')) = '$run'
GROUP BY status ORDER BY status;

SELECT COUNT(*) AS detail_count, COALESCE(SUM(d.quantity), 0) AS total_transferred
FROM transfer_detail d JOIN stock_transfer t ON t.id = d.transfer_id
WHERE LEFT(t.transfer_code, LENGTH('$run')) = '$run'
AND t.status = 'COMPLETED';
"@
    $sql | Set-Content (Join-Path $folder 'verify.sql') -Encoding UTF8
    $ok = @($results | Where-Object Status -eq 200).Count
    $rejected = @($results | Where-Object Status -eq 422).Count
    $other = $results.Count - $ok - $rejected
    $summary = @"
===== KET QUA B06-05 =====
Ma dot test: $run
Che do: $mode
So phieu tao: $draftCount; So luong moi phieu: $transferQuantity
ID phieu dau: $($ids[0])
Kho nguon: $($source.id); Kho dich: $($target.id)
San pham: $($product.id); Ton nguon ban dau: 10
Tong request: $($results.Count)
HTTP 200: $ok; HTTP 422: $rejected; Loi khac: $other
Thoi gian: $($watch.ElapsedMilliseconds) ms
Ky vong HTTP: $expectedSuccess x 200; $expectedRejected x 422
Ky vong CSDL: nguon $expectedSource; dich $expectedTarget; COMPLETED $expectedSuccess
"@
    $summary | Set-Content (Join-Path $folder 'summary.txt') -Encoding UTF8
    Write-Host $summary
    Write-Host "Saved results: $folder"
    if ($results.Count -eq 50 -and $ok -eq $expectedSuccess -and $rejected -eq $expectedRejected -and $other -eq 0) {
        Write-Host 'HTTP counts match. Verify database before concluding PASS.' -ForegroundColor Green
    } else {
        Write-Host 'HTTP counts do NOT match expected results. Keep evidence; do not mark PASS.' -ForegroundColor Red
    }
    Write-Host ''
    Write-Host 'COPY THE FOLLOWING SQL INTO H2 CONSOLE:'
    Write-Host $sql
} finally {
    foreach ($job in $jobs) {
        if (-not $job.Task.IsCompleted) {
            try { $r = $job.Task.GetAwaiter().GetResult(); $r.Dispose() } catch {}
        }
        $job.Request.Dispose()
    }
    $client.Dispose()
    $handler.Dispose()
}
