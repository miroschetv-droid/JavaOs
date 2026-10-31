#!/bin/sh
# JavaOS: загрузка системы с носителя (флешка, CD, iso) с полосой процентов на экране.
# Вместо встроенного «cde» Tiny Core: находим папку cde, по очереди подключаем пакеты
# из load.lst (уже в порядке зависимостей) и распаковываем Java. Процент — по размеру файлов.
# Запускается из /opt/bootsync.sh от root, до входа в систему.

TTY=/dev/console
say() { printf "%s" "$1" > $TTY; }
fsize() { ls -l "$1" 2>/dev/null | awk '{print $5+0}'; }   # в busybox нет stat

# --------------------------------------------------------------- найти носитель с /cde
# javaos_dir=/javaos — система установлена в папку на разделе жёсткого диска (без флешки)
DIR=$(sed -n "s/.*javaos_dir=\([^ ]*\).*/\1/p" /proc/cmdline)
SRC=""
tries=0
while [ -z "$SRC" ] && [ $tries -lt 30 ]; do
    for dev in /dev/sr[0-9] /dev/sd[a-z] /dev/sd[a-z][0-9] /dev/vd[a-z] /dev/vd[a-z][0-9] /dev/hd[a-z] /dev/hd[a-z][0-9] /dev/nvme[0-9]n[0-9]p[0-9] /dev/mmcblk[0-9]p[0-9]; do
        [ -b "$dev" ] || continue
        mnt=/mnt/${dev#/dev/}
        mkdir -p "$mnt"
        # utf8 — чтобы русские имена файлов на флешке (FAT32) читались правильно; ext4 такой опции не знает — тогда без неё
        grep -q " $mnt " /proc/mounts || mount -o ro,utf8 "$dev" "$mnt" 2>/dev/null || mount -o ro "$dev" "$mnt" 2>/dev/null || continue
        if [ -f "$mnt$DIR/cde/load.lst" ]; then SRC=$mnt$DIR/cde; break; fi
        umount "$mnt" 2>/dev/null
    done
    [ -n "$SRC" ] && break
    [ $tries = 0 ] && say "Waiting for the JavaOS drive..."
    sleep 1; tries=$((tries + 1))   # USB-флешки на настоящем железе появляются не сразу
done
[ -n "$SRC" ] || { say "
JavaOS: boot drive with /cde not found.
"; exit 1; }
echo "$SRC/optional" > /etc/sysconfig/cde

# --------------------------------------------------------------- что грузить и сколько это весит
JRE_PARTS=$(ls "$SRC"/jre/jre.part.* 2>/dev/null)
TOTAL=0
for f in $(cat "$SRC/load.lst") ; do
    s=$(fsize "$SRC/optional/$f"); TOTAL=$((TOTAL + s))
done
for p in $JRE_PARTS; do s=$(fsize "$p"); TOTAL=$((TOTAL + s)); done
[ $TOTAL -gt 0 ] || TOTAL=1
DONE=0

bar() {   # $1 — что сейчас грузится
    pct=$((DONE * 100 / TOTAL))
    n=$((pct * 40 / 100))
    b=""; i=0
    while [ $i -lt 40 ]; do [ $i -lt $n ] && b="$b#" || b="$b."; i=$((i + 1)); done
    printf "\r  Loading JavaOS  [%s] %3d%%  %-28.28s" "$b" "$pct" "$1" > $TTY
}

# пингвин с кружкой кофе — пока Java «заваривается»
cat > $TTY <<'PENGUIN'
          .--.
         |o_o |        JavaOS 1.0
         |:_/ |        brewing your desktop...
        //   \ \           ( (
       (|     | )           ) )
      /'\_   _/`\       .........
      \___)=(___/       |       |]
                        \       /
                         `-----'

PENGUIN
bar "starting"

# --------------------------------------------------------------- прошивки, которых нет в пакетах (NVIDIA RTX 50xx и др.)
# лежат в cde/firmware — копируем до загрузки драйверов, чтобы драйвер нашёл их с первого раза
[ -d "$SRC/firmware" ] && { mkdir -p /usr/local/lib/firmware; cp -a "$SRC/firmware/." /usr/local/lib/firmware/; }

# --------------------------------------------------------------- пакеты (уже в порядке зависимостей)
for f in $(cat "$SRC/load.lst"); do
    bar "${f%.tcz}"
    su tc -c "tce-load -i -b \"$SRC/optional/$f\"" >/dev/null 2>&1
    s=$(fsize "$SRC/optional/$f"); DONE=$((DONE + s))
done
ldconfig 2>/dev/null
# прошивки (Wi-Fi, сетевые карты) лежат в пакетах firmware-*.tcz — показать ядру, где их искать
echo -n /usr/local/lib/firmware > /sys/module/firmware_class/parameters/path 2>/dev/null
[ -e /etc/sysconfig/newmodules ] && { depmod -a 2>/dev/null; udevadm trigger; }

# --------------------------------------------------------------- Java (архив нарезан на части — процент идёт по частям)
if [ -n "$JRE_PARTS" ]; then
    mkdir -p /opt/jre.new
    for p in $JRE_PARTS; do
        bar "Java"
        cat "$p"
        s=$(fsize "$p"); DONE=$((DONE + s))
    done | tar -xzf - -C /opt/jre.new
    rm -rf /opt/jre && mv /opt/jre.new/* /opt/jre && rmdir /opt/jre.new
fi

DONE=$TOTAL
bar "done"
say "
"
