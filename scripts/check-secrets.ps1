$ErrorActionPreference = 'Stop'

function Assert-B08GitSuccess {
    param([string]$Step)
    if ($LASTEXITCODE -ne 0) {
        throw "Git failed during: $Step. Scan is incomplete."
    }
}

$repoRoot = git rev-parse --show-toplevel
Assert-B08GitSuccess 'repository lookup'
Set-Location -LiteralPath $repoRoot

$currentSha = git rev-parse HEAD
Assert-B08GitSuccess 'HEAD lookup'
$commits = @(git rev-list --all)
Assert-B08GitSuccess 'history enumeration'
if ($commits.Count -eq 0) { throw 'No reachable commits found. Scan is incomplete.' }

# Separate regular expressions: .NET for current files; POSIX ERE for git grep.
# Only locations are retained. Secret values are never written to the report.
$currentPattern = '(?i)-----BEGIN\s+(?:(?:RSA|EC|OPENSSH|DSA)\s+)?PRIVATE KEY-----|\bAKIA[0-9A-Z]{16}\b|\bgh[pousr]_[A-Za-z0-9_]{20,}\b|(?:password|secret|token|api[_-]?key|client[_-]?secret)[\s"'']*[:=]\s*["''][^"''\r\n]{8,}["'']'
$historyPattern = '-----BEGIN[[:space:]]+((RSA|EC|OPENSSH|DSA)[[:space:]]+)?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{20,}|(password|secret|token|api[_-]?key|client[_-]?secret)[[:space:]"'']*[:=][[:space:]]*["''][^"'']{8,}["'']'
$textExtensions = @('.java','.properties','.xml','.yml','.yaml','.json','.sql','.sh','.ps1','.md','.txt','.env','.config','.conf','.gradle','.html','.js','.css')
$locations = [System.Collections.Generic.List[object]]::new()

$files = @(git -c core.quotePath=false ls-files -co --exclude-standard)
Assert-B08GitSuccess 'current file enumeration'
$files = @($files | Sort-Object -Unique)
$textCount = 0

foreach ($file in $files) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
    $leaf = [IO.Path]::GetFileName($file)
    $extension = [IO.Path]::GetExtension($file).ToLowerInvariant()
    if (($textExtensions -notcontains $extension) -and -not $leaf.StartsWith('.env')) { continue }
    $textCount++
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $repoRoot $file))) {
        $lineNumber++
        if ($line -match $currentPattern) {
            $locations.Add([PSCustomObject]@{
                Scope='CURRENT'; Commit='WORKTREE'; File=$file
                Line=$lineNumber; Classification='REVIEW_REQUIRED'
            })
        }
    }
}

Write-Host "Scanning $($commits.Count) reachable commits; secret values are suppressed..."
$commitNumber = 0
foreach ($historyCommit in $commits) {
    $commitNumber++
    Write-Progress -Activity 'B08 Git history scan' -Status "$commitNumber / $($commits.Count)" -PercentComplete (100 * $commitNumber / $commits.Count)
    $historyFiles = @(git -c core.quotePath=false grep -I -l -i -E -e $historyPattern $historyCommit -- .)
    $grepExit = $LASTEXITCODE
    if ($grepExit -gt 1 -or $grepExit -lt 0) {
        throw "git grep failed at commit $historyCommit. Scan is incomplete."
    }
    if ($grepExit -eq 0) {
        foreach ($location in $historyFiles) {
            $separator = $location.IndexOf(':')
            if ($separator -lt 0) { throw 'Unexpected git grep output. Scan is incomplete.' }
            $locations.Add([PSCustomObject]@{
                Scope='HISTORY'; Commit=$historyCommit
                File=$location.Substring($separator + 1)
                Line=''; Classification='REVIEW_REQUIRED'
            })
        }
    }
}
Write-Progress -Activity 'B08 Git history scan' -Completed

$keyPaths = @(git -c core.quotePath=false log --all --diff-filter=A --format= --name-only -- '.env' '.env.*' ':(glob)**/.env' ':(glob)**/.env.*' ':(glob)**/*.pem' ':(glob)**/*.key' ':(glob)**/*.p12' ':(glob)**/*.pfx' ':(glob)**/*.jks')
Assert-B08GitSuccess 'key and environment filename scan'
$keyPaths = @($keyPaths | Where-Object { $_.Trim() } | Sort-Object -Unique)
foreach ($keyPath in $keyPaths) {
    $locations.Add([PSCustomObject]@{
        Scope='KEY_OR_ENV_PATH'; Commit='ALL_REFS'; File=$keyPath
        Line=''; Classification='REVIEW_REQUIRED'
    })
}

$outputDirectory = Join-Path $repoRoot ('target/B08-security-evidence/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$locationFile = Join-Path $outputDirectory 'locations.csv'
if ($locations.Count -gt 0) {
    $locations | Export-Csv -LiteralPath $locationFile -NoTypeInformation -Encoding UTF8
} else {
    'Scope,Commit,File,Line,Classification' | Set-Content -LiteralPath $locationFile -Encoding UTF8
}
$uniqueReviewFiles = @($locations | Select-Object -ExpandProperty File -Unique)
$summary = [PSCustomObject]@{
    CheckedAt = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')
    Head = $currentSha
    ReachableCommits = $commits.Count
    CurrentTextFiles = $textCount
    FlaggedLocations = $locations.Count
    UniqueFlaggedFiles = $uniqueReviewFiles.Count
    KeyOrEnvironmentPaths = $keyPaths.Count
    GitErrors = 0
    ReviewStatus = $(if ($locations.Count -eq 0) { 'NO_PATTERN_MATCHES_WITHIN_SCOPE' } else { 'REVIEW_REQUIRED_NOT_CONFIRMED_LEAKS' })
}
$summary | Format-List | Out-String | Tee-Object -FilePath (Join-Path $outputDirectory 'summary.txt')
Write-Host '=== FILE LOCATIONS TO REVIEW (NO SECRET VALUES) ==='
if ($uniqueReviewFiles.Count -gt 0) { $uniqueReviewFiles | ForEach-Object { Write-Host $_ } }
Write-Host "Evidence folder: $outputDirectory"
Write-Host 'Scope: current non-ignored text files and text blobs in all reachable fetched refs; binary/archive contents are not scanned.'
Write-Host 'A match requires classification. No matches do not prove absence of every possible secret.'
