# Управление тестовой виртуалкой через монитор QEMU (порт 4444):
#   vmctl.ps1 shot <файл.png>        — снимок экрана
#   vmctl.ps1 cmd "<команда монитора>" — например "sendkey ret", "mouse_move 10 0", "mouse_button 1"
param([string]$what, [string]$arg, [int]$port = 4444)

function Monitor([string[]]$cmds) {
    $c = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $port)
    $s = $c.GetStream()
    $w = New-Object System.IO.StreamWriter($s)
    $w.AutoFlush = $true
    Start-Sleep -Milliseconds 200
    foreach ($x in $cmds) { $w.WriteLine($x); Start-Sleep -Milliseconds 250 }
    Start-Sleep -Milliseconds 300
    $buf = New-Object byte[] 65536
    $out = ""
    while ($s.DataAvailable) { $n = $s.Read($buf, 0, $buf.Length); $out += [Text.Encoding]::ASCII.GetString($buf, 0, $n) }
    $c.Close()
    return $out
}

if ($what -eq "shot") {
    $ppm = [IO.Path]::ChangeExtension($arg, ".ppm")
    if (Test-Path $ppm) { Remove-Item $ppm }
    Monitor @("screendump $ppm") | Out-Null
    for ($i = 0; $i -lt 20 -and -not (Test-Path $ppm); $i++) { Start-Sleep -Milliseconds 200 }
    Start-Sleep -Milliseconds 500
    & "F:\Android\jdk-21.0.12.1+1\bin\java.exe" "$PSScriptRoot\Ppm2Png.java" $ppm $arg
} elseif ($what -eq "type") {
    # напечатать строку в виртуалке и нажать Enter
    $map = @{ ' '='spc'; '/'='slash'; '.'='dot'; '-'='minus'; '_'='shift-minus'; '|'='shift-backslash'; '>'='shift-dot';
              '<'='shift-comma'; ','='comma'; ':'='shift-semicolon'; ';'='semicolon'; '='='equal'; '*'='shift-8'; '"'='shift-apostrophe';
              "'"='apostrophe'; '&'='shift-7'; '$'='shift-4'; '~'='shift-grave_accent'; '('='shift-9'; ')'='shift-0'; '%'='shift-5'; '+'='shift-equal'; '!'='shift-1'; '#'='shift-3'; '?'='shift-slash'; '\'='backslash' }
    $keys = foreach ($ch in $arg.ToCharArray()) {
        $s = [string]$ch
        if ($map.ContainsKey($s)) { "sendkey " + $map[$s] }
        elseif ($s -cmatch '[A-Z]') { "sendkey shift-" + $s.ToLower() }
        else { "sendkey " + $s }
    }
    Monitor (@($keys) + "sendkey ret") | Out-Null
} else {
    Monitor @($arg)
}
