# Снимок одного экрана тестовой виртуалки через QMP: qmpshot.ps1 <файл.png> [head] [device]
# Для виртуалки с несколькими экранами (virtio-vga,max_outputs=2,id=gpu).
param([string]$png, [int]$head = 0, [string]$device = "gpu", [int]$port = 4456)

$ppm = [IO.Path]::ChangeExtension($png, ".ppm")
if (Test-Path $ppm) { Remove-Item $ppm }
$c = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $port)
$c.ReceiveTimeout = 5000
$s = $c.GetStream()
$out = New-Object System.IO.StreamWriter($s); $out.AutoFlush = $true
$in = New-Object System.IO.StreamReader($s)
$null = $in.ReadLine()
$out.WriteLine('{"execute":"qmp_capabilities"}'); $null = $in.ReadLine()
$f = $ppm.Replace('\', '\\')
$out.WriteLine('{"execute":"screendump","arguments":{"filename":"' + $f + '","device":"' + $device + '","head":' + $head + '}}')
do { $r = $in.ReadLine() } while ($r -match '"event"')
$c.Close()
if ($r -notmatch '"return"') { Write-Host "screendump: $r"; exit 1 }
for ($i = 0; $i -lt 20 -and -not (Test-Path $ppm); $i++) { Start-Sleep -Milliseconds 200 }
& "F:\Android\jdk-21.0.12.1+1\bin\java.exe" "$PSScriptRoot\Ppm2Png.java" $ppm $png
