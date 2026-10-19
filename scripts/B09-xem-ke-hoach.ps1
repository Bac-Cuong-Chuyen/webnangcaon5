[CmdletBinding()]
param([ValidateSet('before', 'after')][string]$Phase = 'before')
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath 'pom.xml')) { throw 'Hay chay tu thu muc goc du an.' }
if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Can dung Spring Boot bang Ctrl+C truoc khi mo DB H2 file de xem plan.'
}
$source = Join-Path $PSScriptRoot 'B09XemKeHoach.java'
$jar = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar'
if (-not (Test-Path -LiteralPath $source) -or -not (Test-Path -LiteralPath $jar)) {
    throw 'Thieu file Java hoac thu vien H2 2.3.232.'
}
& java -cp $jar $source $Phase
if ($LASTEXITCODE -ne 0) { throw 'Chua lay duoc plan. Gui loi hien thi; chua tao index.' }
