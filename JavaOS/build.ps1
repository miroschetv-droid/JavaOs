# Сборка JavaOS.iso — загрузочного образа: Tiny Core Linux + Java + JavaWin.
# Запуск:  powershell -ExecutionPolicy Bypass -File build.ps1
#
# Нужно (лежит в dl\ и iso\cde\ после первой подготовки):
#   dl\CorePure64-17.1.iso   — ядро и базовая система Tiny Core
#   dl\jre-linux.tar.gz      — Java 21 для Linux (Eclipse Temurin)
#   iso\cde\optional\*.tcz   — графика, библиотеки X11, шрифты (грузятся при старте)

param(
    # куда положить образ (пока JavaOS.iso занят виртуалкой — собирать в другой файл)
    [string]$Out = "JavaOS.iso"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path "$jdk\bin\java.exe")) { $jdk = "F:\Android\jdk-21.0.12.1+1" }
$java = "$jdk\bin\java.exe"
$jw = "F:\JavaWin"

Write-Host "[1/4] JavaWin.jar..." -ForegroundColor Yellow
& powershell -ExecutionPolicy Bypass -File "$jw\build.ps1" -JarOnly
if ($LASTEXITCODE -ne 0) { throw "не собрался JavaWin" }
$jar = "$jw\JavaWin.jar"

Write-Host "[2/4] файлы загрузчика..." -ForegroundColor Yellow
if (-not (Test-Path base\boot\vmlinuz64)) {
    $img = Mount-DiskImage -ImagePath "$root\dl\CorePure64-17.1.iso" -PassThru
    $d = ($img | Get-Volume).DriveLetter
    New-Item -ItemType Directory -Force base | Out-Null
    Copy-Item "${d}:\*" base -Recurse -Force
    Dismount-DiskImage -ImagePath "$root\dl\CorePure64-17.1.iso" | Out-Null
}
New-Item -ItemType Directory -Force iso\boot\isolinux | Out-Null
Copy-Item base\boot\vmlinuz64, base\boot\corepure64.gz iso\boot -Force
Copy-Item base\boot\isolinux\isolinux.bin, base\boot\isolinux\boot.cat iso\boot\isolinux -Force
Copy-Item os\isolinux.cfg, os\boot.msg iso\boot\isolinux -Force
Copy-Item "$jw\JavaWin.ico" iso\JavaOS.ico -Force
[System.IO.File]::WriteAllText("$root\iso\autorun.inf", "[autorun]`r`nicon=JavaOS.ico`r`nlabel=JavaOS`r`n", [System.Text.Encoding]::ASCII)

Write-Host "[3/4] javaos.gz (JavaWin + автозапуск), Java и список пакетов в cde..." -ForegroundColor Yellow

# Java не в initrd, а частями в cde/jre: её распаковывает load.sh и показывает процент
# (загрузчик BIOS читает initrd молча, а с флешки это самая долгая часть старта)
$jreDir = "iso\cde\jre"
if (Test-Path $jreDir) { Remove-Item $jreDir -Recurse -Force }
New-Item -ItemType Directory -Force $jreDir | Out-Null
$bytes = [IO.File]::ReadAllBytes("$root\dl\jre-linux.tar.gz")
$parts = 20
$chunk = [math]::Ceiling($bytes.Length / $parts)
for ($i = 0; $i -lt $parts; $i++) {
    $off = $i * $chunk
    if ($off -ge $bytes.Length) { break }
    $len = [math]::Min($chunk, $bytes.Length - $off)
    $part = New-Object byte[] $len
    [Array]::Copy($bytes, $off, $part, 0, $len)
    [IO.File]::WriteAllBytes(("$root\$jreDir\jre.part.{0:D2}" -f $i), $part)
}

# все пакеты в порядке зависимостей (сначала зависимости, потом то, что от них зависит)
$opt = "$root\iso\cde\optional"
$order = New-Object System.Collections.Generic.List[string]
$seen = @{}
function Visit([string]$p) {
    if ($seen.ContainsKey($p)) { return }
    $seen[$p] = $true
    $dep = "$opt\$p.dep"
    if (Test-Path $dep) {
        foreach ($d in (Get-Content $dep)) { $d = $d.Trim(); if ($d -and (Test-Path "$opt\$d")) { Visit $d } }
    }
    if (Test-Path "$opt\$p") { $order.Add($p) }
}
foreach ($p in (Get-Content "$root\iso\cde\onboot.lst")) { $p = $p.Trim(); if ($p) { Visit $p } }
[IO.File]::WriteAllText("$root\iso\cde\load.lst", ($order -join "`n") + "`n", (New-Object Text.UTF8Encoding($false)))
Write-Host ("      пакетов: {0}, Java: {1} частей" -f $order.Count, (Get-ChildItem $jreDir).Count)

& $java -cp $jar javawin.BuildTools initrd iso\boot\javaos.gz `
    --symlink lib64 lib `
    --file $jar opt/javawin/JavaWin.jar 644 `
    --file os\load.sh opt/javaos/load.sh 755 `
    --file os\bootsync.sh opt/bootsync.sh 755 `
    --file os\bootlocal.sh opt/bootlocal.sh 755 `
    --file os\fbtest.sh opt/fbtest.sh 755 `
    --file os\arch.sh opt/javaos/arch.sh 755 `
    --file os\jwmrc opt/javaos/jwmrc 644 `
    --file os\gui.sh opt/javaos/gui.sh 755 `
    --file os\wifi.sh opt/javaos/wifi.sh 755 `
    --file os\bt.sh opt/javaos/bt.sh 755 `
    --file os\data.sh opt/javaos/data.sh 755 `
    --file os\usb.sh opt/javaos/usb.sh 755 `
    --file os\javaos.pa opt/javaos/javaos.pa 644 `
    --file os\motd etc/motd 644
if ($LASTEXITCODE -ne 0) { throw "не собрался initrd" }

Write-Host "[4/4] $Out..." -ForegroundColor Yellow
& $java -cp $jar javawin.BuildTools iso iso $Out JAVAOS --boot boot/isolinux/isolinux.bin boot/isolinux/boot.cat
if ($LASTEXITCODE -ne 0) { throw "не собрался iso" }

Write-Host ("готово: $Out  {0:N1} МБ" -f ((Get-Item $Out).Length / 1MB)) -ForegroundColor Green
