# Обновить систему на загрузочной флешке (метка JAVAOS) до последней сборки.
# Загрузчик (Syslinux BIOS + UEFI) на флешке не трогается — заменяются только файлы системы из iso\.
# Запуск:  powershell -ExecutionPolicy Bypass -File flash.ps1   (сначала build.ps1)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$vol = Get-Volume | Where-Object { $_.FileSystemLabel -eq "JAVAOS" -and $_.DriveType -eq "Removable" } | Select-Object -First 1
if (-not $vol) { throw "флешка JAVAOS не найдена" }
$d = "$($vol.DriveLetter):"
if (-not (Test-Path "$d\boot\syslinux")) { throw "на $d нет загрузчика Syslinux — это не флешка JavaOS" }
Write-Host "флешка: $d" -ForegroundColor Yellow

foreach ($f in "vmlinuz64", "corepure64.gz", "javaos.gz") { Copy-Item "$root\iso\boot\$f" "$d\boot\$f" -Force }
Copy-Item "$root\iso\JavaOS.ico", "$root\iso\autorun.inf" $d -Force
# cde — пакеты, Java и список загрузки; старое убираем целиком, чтобы не осталось лишних пакетов
robocopy "$root\iso\cde" "$d\cde" /MIR /NFL /NDL /NJH /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "ошибка копирования cde" }
# память JavaOS на флешке: «диск в файле». Создаётся один раз и при обновлениях не трогается — данные сохраняются.
# Внутри он размечается в ext4 при первой загрузке JavaOS (bootlocal.sh).
# память (папку системы) flash.ps1 больше не создаёт: при первой загрузке JavaOS сама спрашивает,
# в какой папке какого диска её хранить (экран «Выберите папку системы», data.sh)
# общая папка: в Windows — E:\Мои файлы, в JavaOS — «Флешка» в Проводнике
$shared = "$d\Мои файлы"
if (-not (Test-Path $shared)) {
    New-Item -ItemType Directory $shared | Out-Null
    [IO.File]::WriteAllText("$shared\Прочти меня.txt", "Эта папка видна в JavaOS: Проводник → «Флешка».`r`nКлади сюда программы .jar, картинки и тексты — в JavaOS их можно открыть двойным щелчком.`r`n", (New-Object Text.UTF8Encoding($false)))
}
Write-Host "синхронизация на флешку..." -ForegroundColor Yellow
Write-VolumeCache $vol.DriveLetter
Write-Host "готово: на флешке новая JavaOS" -ForegroundColor Green
