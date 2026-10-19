$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath 'pom.xml')) { throw 'Can chay tu thu muc goc du an.' }
if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Dung Spring Boot bang Ctrl+C truoc khi ap dung index vao DB H2 file.'
}
$source = Join-Path $PSScriptRoot 'B09ApDungChiMuc.java'
$jar = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar'
if (-not (Test-Path -LiteralPath $source) -or -not (Test-Path -LiteralPath $jar)) {
    throw 'Thieu file Java hoac thu vien H2.'
}
& java -cp $jar $source
if ($LASTEXITCODE -ne 0) { throw 'Chua ap dung hoan tat. Gui loi de kiem tra, khong tu xoa index/DB.' }
