$ErrorActionPreference = "Stop"

if (-not (Test-Path "pom.xml")) {
    throw "Run this script from the repository root (pom.xml missing)."
}

Write-Host "=== V4 B07-B10 security + API regression tests ==="

$gitState = git status --short
if ($LASTEXITCODE -eq 0) {
    $gitState | ForEach-Object { Write-Host $_ }
}

$mergeHead = git rev-parse --git-path MERGE_HEAD 2>$null
if ($LASTEXITCODE -eq 0 -and $mergeHead -and (Test-Path $mergeHead)) {
    Write-Warning "Merge is in progress. Do not push or commit until you review the complete merge."
}

mvn -B --no-transfer-progress clean test
if ($LASTEXITCODE -ne 0) {
    throw "Maven tests failed. Review target/surefire-reports before committing."
}

Write-Host "All Maven tests passed. This does not replace manual BM1-BM12 evidence or production deployment checks."
