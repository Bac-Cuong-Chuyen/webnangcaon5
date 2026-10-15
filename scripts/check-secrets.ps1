$ErrorActionPreference = 'Stop'

$insideRepo = git rev-parse --is-inside-work-tree 2>$null
if ($LASTEXITCODE -ne 0 -or $insideRepo.Trim() -ne 'true') {
    Write-Error 'Hay chay script tu ben trong thu muc Git repository.'
    exit 2
}

# Heuristic patterns only. The script reports file/commit locations, never secret values.
$secretPattern = '(?i)-----BEGIN\s+(RSA\s+|EC\s+|OPENSSH\s+|DSA\s+)?PRIVATE KEY-----|\bAKIA[0-9A-Z]{16}\b|\bgh[pousr]_[A-Za-z0-9_]{20,}\b|(password|secret|token|api[_-]?key|client[_-]?secret)[\s"'']*[:=][\s"'']*[A-Za-z0-9/+_.~-]{16,}'
$textExtensions = @('.java', '.properties', '.xml', '.yml', '.yaml', '.json', '.sql', '.sh', '.ps1', '.md', '.txt', '.env', '.config', '.conf', '.gradle')
$found = $false

Write-Host '1/2 - Kiem tra file hien tai (tracked va untracked khong bi ignore)...'
$files = git ls-files -co --exclude-standard
foreach ($file in $files) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
    $leaf = [IO.Path]::GetFileName($file)
    $ext = [IO.Path]::GetExtension($file)
    if (($textExtensions -notcontains $ext.ToLowerInvariant()) -and -not $leaf.StartsWith('.env')) { continue }

    $lineNumber = 0
    foreach ($line in (Get-Content -LiteralPath $file -ErrorAction SilentlyContinue)) {
        $lineNumber++
        if ($line -match $secretPattern) {
            Write-Host "POTENTIAL SECRET in working tree: $file`:$lineNumber" -ForegroundColor Yellow
            $found = $true
        }
    }
}

Write-Host '2/2 - Kiem tra tat ca commit reachable trong Git history...'
$commits = git rev-list --all
foreach ($commit in $commits) {
    $matches = git grep -I -l -E -i -e $secretPattern $commit -- . ':(exclude)**/*.docx' ':(exclude)**/*.pdf' ':(exclude)**/*.png' ':(exclude)**/*.jpg' 2>$null
    if ($LASTEXITCODE -eq 0) {
        foreach ($match in $matches) {
            Write-Host "POTENTIAL SECRET in commit ${commit}: $match" -ForegroundColor Yellow
            $found = $true
        }
    }
}

if ($found) {
    Write-Host 'Can xem xet cac vi tri da bi co; neu bi lo thong tin that, can thu hoi/doi bi mat va lam sach lich su theo quy trinh nhom.' -ForegroundColor Yellow
    exit 1
}

Write-Host 'Khong tim thay mau bi mat theo heuristic trong tep van ban va lich su da quet.' -ForegroundColor Green
Write-Host 'Luu y: day khong phai bang chung tuyet doi rang repository khong co bi mat.'
exit 0
