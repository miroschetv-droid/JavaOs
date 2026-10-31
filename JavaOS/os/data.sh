#!/bin/sh
# JavaOS: папка системы — где лежит «диск в файле» JavaOS/javaos-data.img (файлы, настройки, скачанное, Arch Linux).
#   data.sh find           — найти папку на любом диске; печатает путь к javaos-data.img (раздел остаётся подключён)
#   data.sh list           — диски: УСТРОЙСТВО|МЕТКА|ФС|СВОБОДНО_МБ|РАЗМЕР_МБ|ПАПКА_ДИСКА|СОСТОЯНИЕ
#                            (ok; hiber — Windows не выключилась до конца, писать нельзя; locked — BitLocker)
#   data.sh create УСТР ПАПКА МБ — создать javaos-data.img в папке (путь от корня диска); корень диска
#                            запоминает, где она (файл .javaos-folder) — так её находит «find»
# Диски Windows (NTFS) — через ntfs-3g: если Windows «уснула» или включён быстрый запуск, ntfs-3g сам
# не даёт писать (иначе можно испортить файлы Windows) — такой диск показываем, но выбрать нельзя.

SCAN=/mnt/javaos-scan

parts() {
    for b in /sys/class/block/*; do
        n=$(basename "$b")
        [ -f "$b/partition" ] || continue
        case "$n" in loop*|ram*|sr*|zram*) continue ;; esac
        echo "/dev/$n"
    done
}

# точка монтирования раздела (подключаем, если ещё не подключён)
mnt() {
    m=$(awk -v d="$1" '$1 == d { print $2; exit }' /proc/mounts)
    if [ -z "$m" ]; then
        m=$SCAN/$(basename "$1")
        mkdir -p "$m"
        if [ "$(fstype "$1")" = ntfs ]; then
            ntfs-3g "$1" "$m" 2>/dev/null || ntfs-3g -o ro "$1" "$m" 2>/dev/null || { rmdir "$m"; return 1; }
        else
            mount "$1" "$m" 2>/dev/null || { rmdir "$m"; return 1; }
        fi
    fi
    echo "$m"
}

fstype() { blkid -o value -s TYPE "$1" 2>/dev/null; }

case "$1" in
find)
    for p in $(parts); do
        case "$(fstype "$p")" in vfat|exfat|ext2|ext3|ext4|ntfs) ;; *) continue ;; esac
        m=$(mnt "$p") || continue
        rel=$(head -n 1 "$m/.javaos-folder" 2>/dev/null)
        for f in ${rel:+"$m/$rel/javaos-data.img"} "$m/JavaOS/javaos-data.img" "$m/javaos-data.img"; do
            [ -f "$f" ] && { echo "$f"; exit 0; }
        done
        case "$m" in $SCAN/*) umount "$m" 2>/dev/null; rmdir "$m" 2>/dev/null ;; esac
    done
    exit 1
    ;;
list)
    for p in $(parts); do
        t=$(fstype "$p")
        case "$t" in
            BitLocker) echo "$p|Диск с BitLocker|$t|0|0|-|locked"; continue ;;
            vfat|exfat|ext2|ext3|ext4|ntfs) ;;
            *) continue ;;
        esac
        m=$(mnt "$p") || continue
        # NTFS подключился только для чтения — Windows не выключилась до конца
        st=ok
        [ "$t" = ntfs ] && awk -v d="$m" '$2 == d { split($4, o, ","); if (o[1] == "ro") f = 1 } END { exit !f }' /proc/mounts && st=hiber
        set -- $(df -m "$m" | awk 'NR == 2 { print $4, $2 }')
        label=$(blkid -o value -s LABEL "$p" 2>/dev/null)
        echo "$p|${label:-без имени}|$t|$1|$2|$m|$st"
    done
    ;;
create)
    p=$2; rel=${3#/}; mb=$4
    [ -b "$p" ] || { echo "ERR: нет такого диска"; exit 1; }
    m=$(mnt "$p") || { echo "ERR: не получилось открыть диск"; exit 1; }
    mount -o remount,rw "$m" 2>/dev/null
    free=$(df -m "$m" | awk 'NR == 2 { print $4 }')
    [ "$(fstype "$p")" = vfat ] && [ "$mb" -gt 3900 ] && mb=3900   # FAT32: файл не больше 4 ГБ
    [ "$mb" -lt "$((free - 200))" ] || mb=$((free - 200))
    [ "$mb" -ge 1000 ] || { echo "ERR: на диске мало места — нужно хотя бы 1,2 ГБ"; exit 1; }
    case "/$rel/" in */../*) echo "ERR: неверная папка"; exit 1 ;; esac
    mkdir -p "$m/$rel" || { echo "ERR: в эту папку нельзя записывать"; exit 1; }
    img="$m/${rel:+$rel/}javaos-data.img"
    echo "Создаю папку системы: $mb МБ…"
    dd if=/dev/zero of="$img" bs=1M count=0 seek="$mb" 2>/dev/null || dd if=/dev/zero of="$img" bs=1M count="$mb" 2>/dev/null
    mkfs.ext4 -q -F -L JAVAOS "$img" || { rm -f "$img"; echo "ERR: не получилось разметить"; exit 1; }
    printf '%s\n' "$rel" > "$m/.javaos-folder"
    sync
    echo "OK: $img"
    ;;
*) sed -n '2,6p' "$0" ;;
esac
