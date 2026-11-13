param([int]$Port = 18092)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'D:\services\jdks\ms-21.0.7')) {
    $env:JAVA_HOME = 'D:\services\jdks\ms-21.0.7'
}
if (-not $env:JAVA_HOME) { throw '请先设置 JAVA_HOME，指向 JDK 21。' }
$env:PORT = $Port
& .\mvnw.cmd spring-boot:run
