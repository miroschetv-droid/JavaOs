#!/bin/sh
# JavaOS: флешки и USB-диски. JavaWin вызывает раз в 3 секунды (от root).
#   usb.sh   — подключить новые разделы USB-дисков в /mnt/usb/<раздел>, отключить вынутые;
#              печатает подключённые: РАЗДЕЛ|МЕТКА|ПАПКА
# Флешку, с которой загружена JavaOS, и раздел с папкой системы не трогаем — они уже подключены.
# vfat/exfat — с «flush»: данные пишутся сразу, флешку можно вынуть почти без риска.

U=/mnt/usb
mkdir -p $U

# вынутые: точка в /mnt/usb есть, а устройства уже нет
for m in $U/*; do
    [ -d "$m" ] || continue
    d=/dev/$(basename "$m")
    if [ ! -b "$d" ]; then umount -l "$m" 2>/dev/null; rmdir "$m" 2>/dev/null; fi
done

for b in /sys/class/block/sd*[0-9] /sys/class/block/sd[a-z]; do
    [ -e "$b" ] || continue
    n=$(basename "$b")
    # только USB
    readlink -f "$b" | grep -q /usb || continue
    # целый диск, у которого есть разделы, — пропускаем (подключаем разделы)
    if [ ! -f "$b/partition" ] && ls -d "$b/$n"[0-9]* >/dev/null 2>&1; then continue; fi
    dev=/dev/$n
    t=$(blkid -o value -s TYPE "$dev" 2>/dev/null)
    case "$t" in vfat|exfat|ntfs|ext2|ext3|ext4) ;; *) continue ;; esac
    m=$(awk -v d="$dev" '$1 == d { print $2; exit }' /proc/mounts)
    case "$m" in
        "") ;;
        $U/*) ;;
        *) continue ;;   # уже подключён системой (флешка JavaOS, папка системы)
    esac
    if [ -z "$m" ]; then
        m=$U/$n
        mkdir -p "$m"
        case "$t" in
            ntfs) ntfs-3g "$dev" "$m" 2>/dev/null || ntfs-3g -o ro "$dev" "$m" 2>/dev/null ;;
            vfat) mount -t vfat -o flush,utf8,uid=0,gid=0 "$dev" "$m" 2>/dev/null || mount "$dev" "$m" 2>/dev/null ;;
            *) mount "$dev" "$m" 2>/dev/null ;;
        esac || { rmdir "$m"; continue; }
    fi
    label=$(blkid -o value -s LABEL "$dev" 2>/dev/null)
    echo "$n|${label:-USB}|$m"
done
