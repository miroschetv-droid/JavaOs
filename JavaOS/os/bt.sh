#!/bin/sh
# JavaOS: Bluetooth. Вызывается JavaWin (окно «Bluetooth») от root.
#   bt.sh start            — запустить службу (dbus + bluetoothd), включить адаптер
#   bt.sh status           — on|ИМЯ_АДАПТЕРА, off или none (адаптера нет)
#   bt.sh scan             — искать устройства 10 с, вывести список
#   bt.sh list             — устройства: MAC|подключено(1/0)|сопряжено(1/0)|имя
#   bt.sh connect MAC      — сопрячь, доверять и подключить
#   bt.sh disconnect MAC
#   bt.sh remove MAC       — забыть устройство
#   bt.sh mode MAC [music|headset] — режим наушников: музыка (хороший звук) или гарнитура (с микрофоном);
#                            без режима — печатает текущий (music, headset или none — не наушники)
# Ошибки начинаются с «ERR:».

BTD=$(ls /usr/local/lib/bluetooth/bluetoothd /usr/local/libexec/bluetooth/bluetoothd /usr/libexec/bluetooth/bluetoothd /usr/local/sbin/bluetoothd 2>/dev/null | head -n 1)

adapter() { ls /sys/class/bluetooth 2>/dev/null | grep -m1 '^hci'; }

start() {
    modprobe btusb 2>/dev/null
    [ -n "$(adapter)" ] || { sleep 2; }
    [ -n "$(adapter)" ] || { echo "ERR: Bluetooth-адаптер не найден"; exit 1; }
    if ! pgrep -x dbus-daemon >/dev/null; then
        mkdir -p /var/run/dbus /usr/local/var/run/dbus
        rm -f /var/run/dbus/pid /usr/local/var/run/dbus/pid
        dbus-uuidgen --ensure 2>/dev/null
        dbus-daemon --system --fork 2>/dev/null || { echo "ERR: не запустилась служба dbus"; exit 1; }
    fi
    if ! pgrep -f bluetoothd >/dev/null; then
        [ -n "$BTD" ] || { echo "ERR: нет программы bluetoothd"; exit 1; }
        "$BTD" >/tmp/bluetoothd.log 2>&1 &
        sleep 1
    fi
    rfkill unblock bluetooth 2>/dev/null
    bluetoothctl power on >/dev/null 2>&1
    bluetoothctl agent NoInputNoOutput >/dev/null 2>&1
    bluetoothctl default-agent >/dev/null 2>&1
}

list() {
    bluetoothctl devices 2>/dev/null | while read -r _ mac name; do
        info=$(bluetoothctl info "$mac" 2>/dev/null)
        c=0; p=0
        echo "$info" | grep -q "Connected: yes" && c=1
        echo "$info" | grep -q "Paired: yes" && p=1
        echo "$mac|$c|$p|${name:-$mac}"
    done
}

case "$1" in
start) start; echo "OK" ;;
status)
    a=$(adapter)
    [ -n "$a" ] || { echo none; exit 0; }
    if bluetoothctl show 2>/dev/null | grep -q "Powered: yes"; then
        echo "on|$(bluetoothctl show | sed -n 's/^[[:space:]]*Alias: //p' | head -n 1)"
    else echo off; fi
    ;;
scan)
    start
    bluetoothctl --timeout 10 scan on >/dev/null 2>&1
    list
    ;;
list) list ;;
connect)
    start
    mac=$2
    [ -n "$mac" ] || { echo "ERR: не указано устройство"; exit 1; }
    bluetoothctl info "$mac" 2>/dev/null | grep -q "Paired: yes" || \
        bluetoothctl --timeout 20 pair "$mac" >/tmp/bt-pair.log 2>&1
    bluetoothctl trust "$mac" >/dev/null 2>&1
    bluetoothctl --timeout 15 connect "$mac" >/tmp/bt-connect.log 2>&1
    if bluetoothctl info "$mac" 2>/dev/null | grep -q "Connected: yes"; then echo "OK: подключено"
    else echo "ERR: не удалось подключиться. Включи на устройстве режим сопряжения и попробуй снова"; exit 1; fi
    ;;
mode)
    export PULSE_SERVER=${PULSE_SERVER:-unix:/run/user/0/pulse/native}
    card="bluez_card.$(echo "$2" | tr : _)"
    cur=$(pactl list cards 2>/dev/null | awk -v c="$card" '$1 == "Name:" { on = ($2 == c) } on && /Active Profile:/ { print $3; exit }')
    case "$3" in
        "") case "$cur" in a2dp*) echo music ;; headset*|handsfree*) echo headset ;; *) echo none ;; esac ;;
        music) pactl set-card-profile "$card" a2dp_sink >/dev/null 2>&1 && echo "OK: режим «Музыка»" || echo "ERR: не получилось" ;;
        headset)
            pactl set-card-profile "$card" headset_head_unit >/dev/null 2>&1 || pactl set-card-profile "$card" handsfree_head_unit >/dev/null 2>&1 \
                && echo "OK: режим «Гарнитура» — микрофон наушников включён" || echo "ERR: эти наушники не умеют работать гарнитурой" ;;
    esac
    ;;
disconnect) bluetoothctl disconnect "$2" >/dev/null 2>&1; echo "OK: отключено" ;;
remove) bluetoothctl remove "$2" >/dev/null 2>&1; echo "OK: устройство забыто" ;;
*) sed -n '2,10p' "$0" ;;
esac
