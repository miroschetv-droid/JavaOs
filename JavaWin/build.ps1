# Сборка JavaWin.
# Запуск:  powershell -ExecutionPolicy Bypass -File build.ps1
#
# Что получается:
#   JavaWin.jar          — сама система (нужна установленная Java 21+)
#   JavaWin.ico          — иконка
#   dist\JavaWin\        — готовая папка с JavaWin.exe и встроенной Java, ставить ничего не нужно
#   JavaWin.iso          — образ диска с этой папкой (двойной щелчок в Windows — подключить)

param(
    # Только jar и иконка, без папки с Java и без iso.
    [switch]$JarOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path "$jdk\bin\javac.exe")) { $jdk = "F:\Android\jdk-21.0.12.1+1" }
if (-not (Test-Path "$jdk\bin\javac.exe")) { throw "не найден JDK 21: задай JAVA_HOME" }
$bin = "$jdk\bin"

# ---------------------------------------------------------------- 1. компиляция

Write-Host "[1/5] компиляция..." -ForegroundColor Yellow
if (Test-Path out) { Remove-Item out -Recurse -Force }
$src = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$bin\javac.exe" -encoding UTF-8 -d out $src
if ($LASTEXITCODE -ne 0) { throw "ошибки компиляции" }

# ---------------------------------------------------------------- 2. jar

Write-Host "[2/5] JavaWin.jar..." -ForegroundColor Yellow
if (Test-Path JavaWin.jar) { Remove-Item JavaWin.jar -Force }
& "$bin\jar.exe" --create --file JavaWin.jar --main-class javawin.Main -C out .
if ($LASTEXITCODE -ne 0) { throw "не собрался jar" }

# ---------------------------------------------------------------- 3. иконка

Write-Host "[3/5] JavaWin.ico..." -ForegroundColor Yellow
& "$bin\java.exe" -cp JavaWin.jar javawin.BuildTools ico JavaWin.ico
if ($LASTEXITCODE -ne 0) { throw "не нарисовалась иконка" }

if ($JarOnly) {
    Write-Host "готово: JavaWin.jar, JavaWin.ico" -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- 4. папка с exe и своей Java

Write-Host "[4/5] dist\JavaWin (exe + встроенная Java)..." -ForegroundColor Yellow
$stage = "build\jar"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item JavaWin.jar $stage
if (Test-Path dist) { Remove-Item dist -Recurse -Force }
& "$bin\jpackage.exe" --type app-image --name JavaWin --app-version 1.0 --vendor JavaWin `
    --input $stage --main-jar JavaWin.jar --main-class javawin.Main --icon JavaWin.ico --dest dist `
    --add-modules java.desktop,jdk.management,java.net.http,jdk.crypto.ec,jdk.charsets `
    --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-6"
if ($LASTEXITCODE -ne 0) { throw "jpackage не справился" }

# Запасной запуск через подписанный javaw.exe — если Smart App Control не пустит неподписанный JavaWin.exe.
$bat = "@echo off`r`nstart `"`" `"%~dp0runtime\bin\javaw.exe`" -jar `"%~dp0app\JavaWin.jar`"`r`n"
[System.IO.File]::WriteAllText("$root\dist\JavaWin\Start JavaWin.bat", $bat, [System.Text.Encoding]::ASCII)

# ---------------------------------------------------------------- 5. iso

Write-Host "[5/5] JavaWin.iso..." -ForegroundColor Yellow
$iso = "build\iso"
if (Test-Path $iso) { Remove-Item $iso -Recurse -Force }
New-Item -ItemType Directory -Force -Path $iso | Out-Null
Copy-Item dist\JavaWin "$iso\JavaWin" -Recurse
Copy-Item JavaWin.ico $iso
[System.IO.File]::WriteAllText("$root\$iso\autorun.inf",
    "[autorun]`r`nicon=JavaWin.ico`r`nlabel=JavaWin`r`nopen=JavaWin\JavaWin.exe`r`n", [System.Text.Encoding]::ASCII)
[System.IO.File]::WriteAllText("$root\$iso\Start JavaWin.bat",
    "@echo off`r`nstart `"`" `"%~dp0JavaWin\JavaWin.exe`"`r`n", [System.Text.Encoding]::ASCII)
Copy-Item README.md "$iso\README.txt"
& "$bin\java.exe" -cp JavaWin.jar javawin.BuildTools iso $iso JavaWin.iso JAVAWIN
if ($LASTEXITCODE -ne 0) { throw "не собрался iso" }

Write-Host ""
Write-Host "готово:" -ForegroundColor Green
Write-Host ("  JavaWin.jar           {0:N0} КБ" -f ((Get-Item JavaWin.jar).Length / 1KB))
Write-Host ("  JavaWin.ico           {0:N0} КБ" -f ((Get-Item JavaWin.ico).Length / 1KB))
Write-Host  "  dist\JavaWin\JavaWin.exe"
Write-Host ("  JavaWin.iso           {0:N1} МБ" -f ((Get-Item JavaWin.iso).Length / 1MB))
