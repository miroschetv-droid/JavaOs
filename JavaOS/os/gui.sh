#!/bin/sh
# JavaOS: графический режим — Xorg, оконный менеджер и JavaWin. Запуск из консоли: gui
[ "$(id -u)" = 0 ] || exec sudo "$0" "$@"
[ -e /tmp/.X11-unix/X0 ] && { echo "Графика уже запущена."; exit 0; }
. /tmp/javaos.env
LOG=/tmp/javaos-gui.log
echo "Запуск графики… (журнал: $LOG)"
exec 3>&1 >"$LOG" 2>&1
export HOME=/home/tc DISPLAY=:0 LANG=C.UTF-8 LC_ALL=C.UTF-8

# ---------------------------------------------------------------- X-сервер
# Xorg — полный набор расширений (нужен программам Arch) и раскладка us/ru по Alt+Shift.
# Не поднялся — запасной Xfbdev, как раньше.
# modesetting требует libgbm из Mesa (её нет) — берём fbdev: он рисует в тот же framebuffer (virtio или VESA)
# modesetting (нужен libgbm из libEGL.tcz) рисует сразу в буфер virtio-gpu и отдаёт кадр по изменениям;
# fbdev ходит через эмуляцию framebuffer ядра, а она сбрасывает картинку на экран ~20 раз в секунду.
# Не поднялся modesetting — пробуем fbdev (x=fbdev в строке загрузки — сразу fbdev).
# Несколько мониторов (без настройки X их клонирует). Главный — с самым большим разрешением (по первому,
# «родному» режиму из /sys), остальные — цепочкой слева от него: как обычно стоят дома (большой справа, переносной слева).
# Имена выходов X берём из /sys: card0-Virtual-2 -> Virtual-2, card0-HDMI-A-1 -> HDMI-1.
MONOPTS=""; MONSECT=""; OUTS=""; best=""; bestA=0; n=0
for st in /sys/class/drm/card*-*/status; do
    [ "$(cat "$st" 2>/dev/null)" = connected ] || continue
    d=$(dirname "$st")
    out=$(basename "$d" | sed 's/^card[0-9]*-//; s/^HDMI-A-/HDMI-/')
    m=$(head -n 1 "$d/modes" 2>/dev/null)
    w=$(echo "${m%%x*}" | tr -cd 0-9); h=$(echo "${m##*x}" | tr -cd 0-9)   # «1920x1080i» тоже бывает
    px=0; [ -n "$w" ] && [ -n "$h" ] && px=$((w * h))
    # физический размер экрана в сантиметрах — из паспорта монитора (EDID, байты 21 и 22):
    # у большого монитора и у переносного разрешение может совпадать, а размер — нет
    set -- $(od -An -tu1 -j21 -N2 "$d/edid" 2>/dev/null)
    cm=0; [ -n "$1" ] && [ -n "$2" ] && cm=$(($1 * $2))
    a=$((cm * 100000000 + px))   # сначала сантиметры, при равенстве — пиксели
    echo "javaos: monitor $out ${m:-?} ${1:-?}x${2:-?} cm"
    OUTS="$OUTS $out"
    n=$((n + 1))
    [ "$a" -gt "$bestA" ] && { bestA=$a; best=$out; }
done
if [ $n -gt 1 ]; then
    MONOPTS="
    Option \"Monitor-$best\" \"main\""
    MONSECT="
Section \"Monitor\"
    Identifier \"main\"
    Option \"Primary\" \"true\"
EndSection"
    prev=$best; k=0
    for out in $OUTS; do
        [ "$out" = "$best" ] && continue
        k=$((k + 1))
        MONOPTS="$MONOPTS
    Option \"Monitor-$out\" \"side$k\""
        MONSECT="$MONSECT
Section \"Monitor\"
    Identifier \"side$k\"
    Option \"LeftOf\" \"$prev\"
EndSection"
        prev=$out
    done
fi
echo "javaos: monitors connected: $n, main: ${best:-?}"

xconf() {
cat > /tmp/xorg.conf <<EOF
Section "ServerFlags"
    Option "AutoAddDevices" "true"
    Option "BlankTime" "0"
    Option "StandbyTime" "0"
    Option "SuspendTime" "0"
    Option "OffTime" "0"
EndSection
Section "Device"
    Identifier "card"
    Driver "$DRIVER"
    Option "AccelMethod" "none"
    Option "ShadowFB" "true"
    # курсор рисуется в саму картинку: аппаратный курсор virtio-gpu QEMU не показывает при захвате мыши (игровой режим)
    Option "SWcursor" "true"$( [ $DRIVER = modesetting ] && echo "$MONOPTS" )
EndSection$( [ $DRIVER = modesetting ] && echo "$MONSECT" )
Section "Screen"
    Identifier "screen"
    Device "card"
    DefaultDepth 24
    SubSection "Display"
        Depth 24
    EndSubSection
EndSection
Section "InputClass"
    Identifier "keyboard"
    MatchIsKeyboard "on"
    Driver "evdev"
    Option "XkbLayout" "us,ru"
    Option "XkbOptions" "grp:alt_shift_toggle"
EndSection
Section "InputClass"
    Identifier "pointer"
    MatchIsPointer "on"
    Driver "evdev"
EndSection
EOF
}
XSERVER=none
DRIVERS="modesetting fbdev"
[ -e /dev/dri/card0 ] || DRIVERS="fbdev"
grep -q "x=fbdev" /proc/cmdline && DRIVERS="fbdev"
grep -q "x=xfbdev" /proc/cmdline && DRIVERS=""
command -v Xorg >/dev/null || DRIVERS=""
for DRIVER in $DRIVERS; do
    xconf
    Xorg :0 vt2 -nolisten tcp -noreset -config /tmp/xorg.conf -logfile /tmp/Xorg.0.log &
    XPID=$!
    i=0
    while [ ! -e /tmp/.X11-unix/X0 ] && [ $i -lt 60 ] && kill -0 $XPID 2>/dev/null; do sleep 0.25; i=$((i + 1)); done
    [ -e /tmp/.X11-unix/X0 ] && { XSERVER=Xorg; break; }
    echo "javaos: Xorg with $DRIVER failed"; tail -8 /tmp/Xorg.0.log 2>/dev/null
    kill $XPID 2>/dev/null; sleep 0.5; rm -f /tmp/.X0-lock
done
if [ $XSERVER = none ]; then
    echo "javaos: Xorg failed, falling back to Xfbdev"
    tail -20 /tmp/Xorg.0.log 2>/dev/null
    Xfbdev :0 -nolisten tcp -s 0 -mouse /dev/input/mice,3 &
    i=0
    while [ ! -e /tmp/.X11-unix/X0 ] && [ $i -lt 40 ]; do sleep 0.25; i=$((i + 1)); done
    XSERVER=Xfbdev
fi
echo "javaos: X ready ($XSERVER, $DRIVER)"
[ -x /usr/local/bin/xsetroot ] && xsetroot -solid black -cursor_name left_ptr

# оконный менеджер — рамки для окон Linux-программ (у Xfbdev хватает и без него)
if [ $XSERVER = Xorg ] && command -v jwm >/dev/null; then
    jwm -f /opt/javaos/jwmrc &
    echo "javaos: jwm started"
fi

# ---------------------------------------------------------------- звук (PulseAudio)
# через PulseAudio звук идёт и в колонки, и в Bluetooth-наушники. Программы ALSA (Java, плеер) направляем
# в него через /etc/asound.conf — но только если служба запустилась, иначе звука не было бы совсем.
export XDG_RUNTIME_DIR=/run/user/0
mkdir -p /run/user/0/pulse && chmod 700 /run/user/0
/opt/javaos/bt.sh start >/dev/null 2>&1   # dbus и bluetoothd — до PulseAudio, чтобы он увидел Bluetooth
# PulseAudio берёт звуковую карту (в том числе USB-микрофон и USB-колонки), только когда udev пометил её
# «готова» (SOUND_INITIALIZED). В Tiny Core такого правила нет — без него PulseAudio не видел ни одной карты
mkdir -p /etc/udev/rules.d
echo 'SUBSYSTEM=="sound", KERNEL=="card*", ENV{SOUND_INITIALIZED}="1"' > /etc/udev/rules.d/78-javaos-sound.rules
udevadm control --reload-rules 2>/dev/null
udevadm trigger --subsystem-match=sound --action=change 2>/dev/null
udevadm settle --timeout=5 2>/dev/null
# PulseAudio берёт звуковую карту (в том числе USB-микрофон и USB-колонки), только когда udev пометил её
# «готова» (SOUND_INITIALIZED). В Tiny Core такого правила нет — без него PulseAudio не видел ни одной карты
mkdir -p /etc/udev/rules.d
echo 'SUBSYSTEM=="sound", KERNEL=="card*", ENV{SOUND_INITIALIZED}="1"' > /etc/udev/rules.d/78-javaos-sound.rules
udevadm control --reload-rules 2>/dev/null
udevadm trigger --subsystem-match=sound --action=change 2>/dev/null
udevadm settle --timeout=5 2>/dev/null
if command -v pulseaudio >/dev/null; then
    mkdir -p /root
    HOME=/root pulseaudio -n -F /opt/javaos/javaos.pa --daemonize=yes --exit-idle-time=-1 --log-target=file:/tmp/pulse.log --log-level=info 2>/dev/null
    i=0; while [ ! -S /run/user/0/pulse/native ] && [ $i -lt 20 ]; do sleep 0.25; i=$((i + 1)); done
    if [ -S /run/user/0/pulse/native ]; then
        printf 'pcm.!default { type pulse }\nctl.!default { type pulse }\n' > /etc/asound.conf
        echo "javaos: pulseaudio started"
    else
        rm -f /etc/asound.conf
        echo "javaos: pulseaudio failed"; tail -n 5 /tmp/pulse.log
    fi
fi
export PULSE_SERVER=unix:/run/user/0/pulse/native

# ---------------------------------------------------------------- JavaWin
# Swing рисует в задний буфер; по умолчанию он лежит в памяти X-сервера, и каждое сглаженное
# или полупрозрачное рисование читает пиксели обратно из X — держим его в обычной памяти.
JOPTS="-Dsun.java2d.pmoffscreen=false"
grep -q "j2d=pixmap" /proc/cmdline && JOPTS=""
echo "javaos: JOPTS=$JOPTS fb=$(cat /sys/class/graphics/fb0/bits_per_pixel 2>/dev/null)bpp home=$HOMEDIR"

# журнал графики — на флешку (E:\javaos-gui-log.txt), когда система уже запустится
if [ -n "$BOOTMNT" ]; then
    ( sleep 30; { cat "$LOG"; echo "--- Xorg outputs"; grep -iE "Output|modeset|RightOf|Monitor" /tmp/Xorg.0.log | tail -n 30; } > "$BOOTMNT/javaos-gui-log.txt" 2>&1; sync
      # звук: что видит PulseAudio — раз в минуту (наушники подключают позже)
      while sleep 60; do
          { cat "$LOG"; echo "--- pulse $(date +%T)"; pactl info 2>&1 | head -n 12; echo "--- sinks"; pactl list short sinks 2>&1
            echo "--- sources"; pactl list short sources 2>&1; echo "--- cards"; pactl list cards 2>&1 | grep -E "Name:|Active Profile|bluez|output:|input:" | head -n 40
            echo "--- pulse.log"; tail -n 30 /tmp/pulse.log; } > "$BOOTMNT/javaos-gui-log.txt" 2>&1; sync
      done ) &
fi
/opt/jre/bin/java -Xmx768m $JOPTS -Duser.language=ru -Duser.country=RU -Djavawin.home="$HOMEDIR" \
    -Djavaos.arch=/opt/javaos/arch.sh -Djavaos.wifi=/opt/javaos/wifi.sh -Djavaos.bt=/opt/javaos/bt.sh -Djavaos.data=/opt/javaos/data.sh -Djavaos.usb=/opt/javaos/usb.sh $([ -f /tmp/javaos-nodata ] && echo -Djavaos.nodata=1) -jar /opt/javawin/JavaWin.jar --os
code=$?
echo "javaos: JavaWin exited with code $code"
case $code in
    10|11)
        sync
        # дождаться закрытия программ, которые держат файлы на диске памяти
        killall -q -TERM java 2>/dev/null; sleep 1
        sync
        if grep -q " $DATA " /proc/mounts; then
            L=$(awk -v d="$DATA" '$2 == d { print $1 }' /proc/mounts)
            umount -l $DATA/arch/* 2>/dev/null
            umount $DATA 2>/dev/null || { mount -o remount,ro $DATA 2>/dev/null; }
            sync
            [ -n "$L" ] && losetup -d "$L" 2>/dev/null
            sync
        fi
        # диски, на которых папка системы (в том числе диск Windows через ntfs-3g), и флешки — отключить аккуратно,
        # иначе Windows сочтёт диск «грязным» и начнёт проверку
        for m in /mnt/usb/* /mnt/javaos-scan/*; do
            [ -d "$m" ] && grep -q " $m " /proc/mounts && { umount "$m" 2>/dev/null || umount -l "$m"; }
        done
        i=0; while pgrep -x ntfs-3g >/dev/null && [ $i -lt 20 ]; do sleep 0.25; i=$((i + 1)); done   # ntfs-3g дописывает диск
        sync
        # память на флешке: после диска в файле — вернуть флешку в «только чтение», чтобы FAT32 записалась целиком
        [ -n "$BOOTMNT" ] && { sync; mount -o remount,ro "$BOOTMNT" 2>/dev/null; sync; sleep 1; }
        [ $code = 10 ] && poweroff -f || reboot -f ;;   # всё уже на диске — выключаем сразу
esac
# вышли из системы — гасим графику и возвращаемся в консоль
killall jwm Xorg Xfbdev 2>/dev/null
sleep 1
chvt 1 2>/dev/null
echo "Графика закрыта. Снова запустить: gui" >&3
