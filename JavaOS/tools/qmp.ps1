# Точный ввод мыши в тестовую виртуалку через QMP (порт 4456), за одно подключение:
#   qmp.ps1 "abs 47 320; btn 1; abs 300 300; btn 0; rel 10 0; wait 200"
#   abs x y — планшет, пиксели экрана гостя (размер экрана: -SW/-SH); rel dx dy — обычная мышь;
#   btn 1|0 — левая кнопка; wait мс — пауза
param([string]$steps, [int]$port = 4456, [int]$SW = 1280, [int]$SH = 800)

$c = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $port)
$c.ReceiveTimeout = 3000
$s = $c.GetStream()
$out = New-Object System.IO.StreamWriter($s); $out.AutoFlush = $true
$in = New-Object System.IO.StreamReader($s)
function Reply { try { do { $l = $in.ReadLine() } while ($l -and $l -match '"event"'); $l } catch { "(нет ответа)" } }
$null = Reply
$out.WriteLine('{"execute":"qmp_capabilities"}'); $null = Reply

foreach ($st in $steps -split ';') {
    $p = $st.Trim() -split '\s+'
    if (-not $p[0]) { continue }
    $ev = $null
    switch ($p[0]) {
        "abs"  { $ev = '[{"type":"abs","data":{"axis":"x","value":' + [int]([int]$p[1] * 32767 / $SW) + '}},{"type":"abs","data":{"axis":"y","value":' + [int]([int]$p[2] * 32767 / $SH) + '}}]' }
        "rel"  { $ev = '[{"type":"rel","data":{"axis":"x","value":' + [int]$p[1] + '}},{"type":"rel","data":{"axis":"y","value":' + [int]$p[2] + '}}]' }
        "btn"  { $ev = '[{"type":"btn","data":{"button":"left","down":' + $(if ($p[1] -eq "1") { 'true' } else { 'false' }) + '}}]' }
        "wait" { Start-Sleep -Milliseconds ([int]$p[1]) }
    }
    if ($ev) {
        $out.WriteLine('{"execute":"input-send-event","arguments":{"events":' + $ev + '}}')
        $r = Reply
        if ($r -notmatch '"return"') { "$st -> $r" }
        Start-Sleep -Milliseconds 60
    }
}
$c.Close()
