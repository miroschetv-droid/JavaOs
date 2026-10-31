# Папка release\ — всё, что нужно выложить, чтобы JavaOS можно было скачать на обычном Linux.
# Запуск:  powershell -ExecutionPolicy Bypass -File release.ps1 [-Iso JavaOS.iso]
# Потом содержимое release\ загрузить в одно место (например, в релиз GitHub)
# и в linux\javaos.sh поправить JAVAOS_URL на адрес этой папки.

param([string]$Iso = "JavaOS.iso")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$jw = "F:\JavaWin"
$out = "$root\release"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory $out | Out-Null

Copy-Item $Iso "$out\JavaOS.iso"
Copy-Item "$jw\JavaWin.jar" "$out\JavaWin.jar"

# значок для меню Linux — самая большая картинка из ico (она там уже в PNG)
$b = [IO.File]::ReadAllBytes("$jw\JavaWin.ico")
$best = $null
for ($i = 0; $i -lt [BitConverter]::ToUInt16($b, 4); $i++) {
    $e = 6 + 16 * $i
    $w = if ($b[$e] -eq 0) { 256 } else { $b[$e] }
    if (-not $best -or $w -gt $best.W) {
        $best = @{ W = $w; Size = [BitConverter]::ToInt32($b, $e + 8); Off = [BitConverter]::ToInt32($b, $e + 12) }
    }
}
if ($b[$best.Off] -ne 0x89) { throw "в ico нет PNG-картинки" }
[IO.File]::WriteAllBytes("$out\JavaWin.png", $b[$best.Off..($best.Off + $best.Size - 1)])

# скрипт — строго с переводами строк LF, иначе sh на Linux не поймёт
$sh = [IO.File]::ReadAllText("$root\linux\javaos.sh") -replace "`r`n", "`n"
[IO.File]::WriteAllText("$out\javaos.sh", $sh, (New-Object Text.UTF8Encoding($false)))

$sums = foreach ($f in "JavaOS.iso", "JavaWin.jar", "JavaWin.png", "javaos.sh") {
    (Get-FileHash "$out\$f" -Algorithm SHA256).Hash.ToLower() + "  " + $f
}
[IO.File]::WriteAllText("$out\SHA256SUMS", ($sums -join "`n") + "`n", (New-Object Text.UTF8Encoding($false)))

Get-ChildItem $out | ForEach-Object { "{0,-14} {1,8:N1} МБ" -f $_.Name, ($_.Length / 1MB) }
Write-Host "готово: $out" -ForegroundColor Green
