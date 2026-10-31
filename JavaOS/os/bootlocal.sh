#!/bin/sh
# JavaOS: после загрузки Linux — диск, графика, оконный менеджер и JavaWin вместо рабочего стола.
# Выполняется от root из /opt/bootsync.sh, в фоне. Журнал: /tmp/javaos.log

grep -q "javaos=off" /proc/cmdline && exit 0

LOG=/tmp/javaos.log
exec >"$LOG" 2>&1
dmesg -n 1 2>/dev/null   # сообщения ядра (подключили мышь, флешку…) — в журнал, а не поверх экрана
echo "javaos: start $(date)"

# пакеты с диска (cde) монтируются в начале загрузки — дождаться последних из списка
i=0
while [ $i -lt 120 ]; do
    [ -x /usr/local/bin/Xfbdev ] && [ -e /usr/local/lib/libXtst.so.6 ] && command -v localedef >/dev/null \
        && [ -d /usr/local/lib/modules/$(uname -r)/kernel/drivers/gpu ] && command -v mkfs.ext4 >/dev/null \
        && command -v jwm >/dev/null && [ -e /usr/local/lib/dri/swrast_dri.so ] && [ -e /usr/local/lib/libGL.so.1 ] && [ -e /usr/local/lib/libgbm.so.1 ] && break
    sleep 0.5; i=$((i + 1))
done
ldconfig 2>/dev/null
echo "javaos: extensions ready after $i half-seconds"

# ---------------------------------------------------------------- постоянный диск
# Первый жёсткий диск без разделов. Пустой — размечается в ext4 с меткой JAVAOS (только если
# на нём вообще ничего нет). Туда идут файлы пользователя и Arch Linux, они переживают выключение.
DATA=/mnt/javaos
for dev in /dev/vda /dev/sda /dev/hda; do
    [ -b "$dev" ] || continue
    # форматировать пустой диск — только в виртуалке QEMU (или по javaos=format): на настоящем
    # компьютере «пустым» может оказаться чужой диск
    if ! blkid "$dev" >/dev/null 2>&1 && { grep -qi qemu /sys/class/dmi/id/sys_vendor 2>/dev/null || grep -q javaos=format /proc/cmdline; }; then
        # пустой диск: первые 1 МБ — нули?
        if [ -z "$(dd if=$dev bs=1M count=1 2>/dev/null | tr -d '\000' | head -c 1)" ]; then
            echo "javaos: formatting empty disk $dev"
            mkfs.ext4 -q -F -L JAVAOS "$dev"
        fi
    fi
    if blkid "$dev" | grep -q 'LABEL="JAVAOS"'; then
        mkdir -p $DATA && mount -t ext4 "$dev" $DATA && echo "javaos: disk $dev mounted at $DATA"
        break
    fi
done
# Нет диска JAVAOS (загрузка с флешки) — память на самой флешке: файл javaos-data.img рядом с системой.
# Это «диск в файле» (ext4 через loop): там файлы пользователя, настройки и Arch Linux, они переживают перезагрузку.
# Пустой файл (его создаёт flash.ps1) размечается при первой загрузке.
BOOTMNT=""
if ! grep -q " $DATA " /proc/mounts && [ -f /etc/sysconfig/cde ]; then
    cde=$(dirname "$(cat /etc/sysconfig/cde)")          # .../cde
    img=$(dirname "$cde")/javaos-data.img
    # точка монтирования носителя — самая длинная из /proc/mounts, с которой начинается путь
    BOOTMNT=$(awk -v p="$cde" 'index(p, $2 "/") == 1 && length($2) > length(m) { m = $2 } END { print m }' /proc/mounts)
    if [ -f "$img" ] && [ -n "$BOOTMNT" ] && mount -o remount,rw "$BOOTMNT"; then
        # пустой (неразмеченный) файл не берём: папку системы выбирают на экране «Выберите папку системы»
        blkid "$img" 2>/dev/null | grep -q 'LABEL="JAVAOS"' || img=""
    fi
    if [ -n "$img" ] && [ -f "$img" ] && [ -n "$BOOTMNT" ]; then
        # пакеты системы уже заняли сотню loop-устройств, а узел для следующего в /dev сам не появляется — создаём
        L=$(losetup -f 2>/dev/null | awk '{ print $1 }')   # бывает «/dev/loop121 (lost)» — узла ещё нет, берём только имя
        [ -n "$L" ] || L=/dev/loop$(ls -d /sys/block/loop* | wc -l)
        [ -b "$L" ] || mknod "$L" b 7 "${L#/dev/loop}"
        mkdir -p $DATA && losetup "$L" "$img" && mount -t ext4 "$L" $DATA && echo "javaos: data image $img mounted at $DATA ($L)"
    fi
    grep -q " $DATA " /proc/mounts || BOOTMNT=""
    # диск в файле на флешке: если выключить кнопкой, несохранённое пропадёт — сбрасываем на флешку каждые 5 секунд
    [ -n "$BOOTMNT" ] && ( while :; do sleep 5; sync; done ) &
fi
# Папка системы на другом диске (флешка, второй диск): JavaOS/javaos-data.img — её выбирают на экране
# «Выберите папку системы», если при загрузке ничего не нашлось
if ! grep -q " $DATA " /proc/mounts; then
    # не дольше минуты: зависший диск не должен останавливать загрузку
    img=$(timeout 60 /opt/javaos/data.sh find 2>/dev/null)
    if [ -n "$img" ]; then
        m=$(awk -v p="$img" 'index(p, $2 "/") == 1 && length($2) > length(x) { x = $2 } END { print x }' /proc/mounts)
        mount -o remount,rw "$m" 2>/dev/null
        L=$(losetup -f 2>/dev/null | awk '{ print $1 }')
        [ -n "$L" ] || L=/dev/loop$(ls -d /sys/block/loop* | wc -l)
        [ -b "$L" ] || mknod "$L" b 7 "${L#/dev/loop}"
        mkdir -p $DATA && losetup "$L" "$img" && mount -t ext4 "$L" $DATA && echo "javaos: system folder $img mounted at $DATA ($L)"
        ( while :; do sleep 5; sync; done ) &
    fi
fi
# не нашлось — JavaWin спросит, где создать папку системы
grep -q " $DATA " /proc/mounts || { touch /tmp/javaos-nodata; echo "javaos: no system folder"; }
# флешка, с которой загрузилась JavaOS, — всегда (даже если папка системы на другом диске): «Флешка» в Проводнике и журналы
if [ -z "$BOOTMNT" ] && [ -f /etc/sysconfig/cde ]; then
    cde=$(dirname "$(cat /etc/sysconfig/cde)")
    BOOTMNT=$(awk -v p="$cde" 'index(p, $2 "/") == 1 && length($2) > length(m) { m = $2 } END { print m }' /proc/mounts)
    [ -n "$BOOTMNT" ] && mount -o remount,rw "$BOOTMNT" 2>/dev/null
fi
if grep -q " $DATA " /proc/mounts; then HOMEDIR=$DATA/home; else HOMEDIR=/home/tc/JavaWin; fi
mkdir -p "$HOMEDIR"
# папка «Мои файлы» на флешке видна в JavaOS как «Флешка»: в Windows положил файл — в JavaOS он уже есть, и наоборот
if [ -n "$BOOTMNT" ]; then
    # «диск C:» в JavaWin — это папка C внутри домашней
    mkdir -p "$BOOTMNT/Мои файлы" "$HOMEDIR/C"
    rm -f "$HOMEDIR/C/Флешка"
    ln -s "$BOOTMNT/Мои файлы" "$HOMEDIR/C/Флешка" && echo "javaos: USB folder linked"
fi

# ---------------------------------------------------------------- локаль
# UTF-8, иначе Java не работает с русскими именами файлов (C.UTF-8 собирается за секунду)
mkdir -p /usr/lib/locale
localedef -i C -f UTF-8 C.UTF-8 && echo "javaos: locale C.UTF-8 ok $(date +%T)"

export HOME=/home/tc
export DISPLAY=:0
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
fc-cache -f >/dev/null 2>&1

# ---------------------------------------------------------------- видеокарта
# virtio-gpu (QEMU: -device virtio-vga). Со стандартной VGA под WHPX каждая запись в видеопамять
# перехватывается гипервизором (~75 мс на кадр); у virtio кадр рисуется в обычной памяти.
if grep -q 0x1af4 /sys/bus/pci/devices/*/vendor 2>/dev/null && grep -qE '0x1050' /sys/bus/pci/devices/*/device 2>/dev/null; then
    depmod -a 2>/dev/null
    modprobe virtio-gpu && echo "javaos: virtio-gpu loaded"
    i=0
    while ! grep -qi virtio /proc/fb && [ $i -lt 40 ]; do sleep 0.25; i=$((i + 1)); done
fi
echo "javaos: framebuffers: $(cat /proc/fb | tr '\n' ' ')"

# ---------------------------------------------------------------- сеть
# Wi-Fi (Qualcomm WCN7850 и др.): драйвер из wireless-*.tcz грузится по udev; подключение — окно «Wi-Fi» в JavaWin.
# Провод: сетевой карте (Realtek 5GbE) нужна прошивка из firmware-rtl_nic.tcz, а Tiny Core поднимает сеть
# раньше, чем она загружена, — перезапускаем адаптер и просим адрес заново (в QEMU это тоже не мешает).
modprobe ath12k 2>/dev/null
ln -sf /opt/javaos/wifi.sh /usr/local/bin/wifi
# Wi-Fi, к которому подключались раньше, — подключаемся сами (в фоне, загрузку не задерживает)
( /opt/javaos/wifi.sh auto > /tmp/wifi-auto.log 2>&1 ) &
# Bluetooth: служба и адаптер (в фоне); что нашлось — в журнал на флешке
( /opt/javaos/bt.sh start > /tmp/bt.log 2>&1 ) &
for d in /sys/class/net/eth*; do
    [ -e "$d" ] || continue
    n=$(basename "$d")
    if ! ifconfig "$n" | grep -q "inet addr"; then
        ifconfig "$n" down; ifconfig "$n" up
        # своя служба Tiny Core (udhcpc -b) продолжает просить адрес; если её нет — запускаем
        ps | grep -v grep | grep -q "udhcpc.*-i $n" || udhcpc -i "$n" -b -t 10 -T 2 >/dev/null 2>&1
    fi
done
echo "javaos: network: $(ls /sys/class/net | tr '\n' ' ')"

# ---------------------------------------------------------------- звук
# драйверы звука (alsa-modules) грузит udev; громкость в Linux по умолчанию часто 0 и «выключено» — включаем на всех картах
i=0
while [ ! -e /dev/snd/controlC0 ] && [ $i -lt 20 ]; do sleep 0.25; i=$((i + 1)); done
for c in /dev/snd/controlC*; do
    [ -e "$c" ] || continue
    n=${c#/dev/snd/controlC}
    for ctl in Master PCM Speaker Headphone Front "Line Out" "Auto-Mute Mode"; do
        amixer -q -c "$n" sset "$ctl" 80% unmute 2>/dev/null
    done
    amixer -q -c "$n" sset "Auto-Mute Mode" Disabled 2>/dev/null
done
echo "javaos: sound cards: $(cat /proc/asound/cards 2>/dev/null | grep -c ']')"
# ---------------------------------------------------------------- дальше — по команде gui
# Система загружается в консоль; графику с JavaWin запускает команда «gui» (/opt/javaos/gui.sh).
cat > /tmp/javaos.env <<ENV
DATA=$DATA
HOMEDIR=$HOMEDIR
BOOTMNT=$BOOTMNT
ENV
ln -sf /opt/javaos/gui.sh /usr/local/bin/gui
# журнал загрузки — на флешку (E:\javaos-log.txt в Windows), чтобы по нему можно было понять, что пошло не так
if [ -f /etc/sysconfig/cde ]; then
    cde=$(dirname "$(cat /etc/sysconfig/cde)")
    m=$(awk -v p="$cde" 'index(p, $2 "/") == 1 && length($2) > length(m) { m = $2 } END { print m }' /proc/mounts)
    if [ -n "$m" ] && ! grep -q " $m iso9660" /proc/mounts; then
        wasro=0; grep " $m " /proc/mounts | grep -q " ro[ ,]" && wasro=1
        [ $wasro = 1 ] && mount -o remount,rw "$m"
        { cat /tmp/javaos.log; echo "--- mounts"; grep -E "/mnt|loop" /proc/mounts | grep -v tcz; echo "--- blkid"; blkid 2>&1 | grep -v squashfs; echo "--- dmesg"; dmesg | grep -iE "fat|ext4|loop|error" | tail -n 20; echo "--- bluetooth"; ls /sys/class/bluetooth 2>&1; cat /tmp/bt.log 2>/dev/null; dmesg | grep -iE "bluetooth|btusb|qca" | tail -n 12; echo "--- gpu"; ls /dev/dri 2>&1; cat /proc/fb; dmesg | grep -iE "nouveau|drm" | tail -n 25; } > "$m/javaos-log.txt" 2>&1
        sync
        [ $wasro = 1 ] && mount -o remount,ro "$m"
    fi
fi
echo "javaos: console ready, type gui"
grep -q "javaos=gui" /proc/cmdline && /opt/javaos/gui.sh &
