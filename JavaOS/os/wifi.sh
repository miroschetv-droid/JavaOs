#!/bin/sh
# JavaOS: Wi-Fi и проводной интернет. Вызывается JavaWin (окно «Wi-Fi») от root.
#   wifi.sh scan                — сети: сигнал(0-100)|защита(1/0)|имя
#   wifi.sh connect ИМЯ [ПАРОЛЬ] — подключиться (без пароля — открытая сеть)
#   wifi.sh status              — wifi|ИМЯ|IP, eth|IP, или none
#   wifi.sh disconnect          — отключиться и забыть сеть
#   wifi.sh auto                — при загрузке: подключиться к запомненной сети
#   wifi.sh iface               — имя Wi-Fi-адаптера (пусто — адаптера нет)
# Текст для человека — по-русски; ошибки начинаются с «ERR:».

iface() {
    for d in /sys/class/net/*/wireless /sys/class/net/*/phy80211; do
        [ -e "$d" ] && { basename "$(dirname "$d")"; return; }
    done
}

IF=$(iface)
need_if() {
    [ -n "$IF" ] || { echo "ERR: Wi-Fi-адаптер не найден (нет драйвера для этой карты)"; exit 1; }
    ifconfig "$IF" up 2>/dev/null
}

ip_of() { ifconfig "$1" 2>/dev/null | sed -n 's/.*inet addr:\([0-9.]*\).*/\1/p' | head -n1; }

case "$1" in
iface) echo "$IF" ;;

scan)
    need_if
    # сканирование иногда отвечает «busy» сразу после включения адаптера — пробуем пару раз
    for t in 1 2 3; do
        out=$(iw dev "$IF" scan 2>/dev/null) && break
        sleep 1
    done
    echo "$out" | awk '
        function flush() { if (ssid != "") { q = 2 * (sig + 100); if (q > 100) q = 100; if (q < 0) q = 0; print q "|" sec "|" ssid } }
        /^BSS / { flush(); ssid = ""; sig = -100; sec = 0 }
        /signal:/ { sig = $2 + 0 }
        /^\tSSID: / { sub(/^\tSSID: /, ""); ssid = $0 }
        /RSN:|WPA:|Privacy/ { sec = 1 }
        END { flush() }' | sort -t'|' -k1,1nr | awk -F'|' '!seen[$3]++'
    ;;

connect)
    need_if
    ssid=$2; pass=$3
    [ -n "$ssid" ] || { echo "ERR: не указано имя сети"; exit 1; }
    killall wpa_supplicant 2>/dev/null; sleep 0.5
    pkill -f "udhcpc.*$IF" 2>/dev/null
    mkdir -p /var/run/wpa_supplicant
    hex=$(printf '%s' "$ssid" | od -An -tx1 | tr -d ' \n')
    {
        echo "ctrl_interface=/var/run/wpa_supplicant"
        echo "update_config=0"
        echo "network={"
        echo "    ssid=$hex"
        echo "    scan_ssid=1"
        if [ -z "$pass" ]; then
            echo "    key_mgmt=NONE"
        else
            n=$(printf '%s' "$pass" | wc -c)
            [ "$n" -ge 8 ] && [ "$n" -le 63 ] || { echo "ERR: пароль Wi-Fi должен быть от 8 до 63 символов"; exit 1; }
            case "$pass" in
            *\"*)   # кавычка в пароле — только WPA2 через готовый ключ
                echo "    key_mgmt=WPA-PSK"
                wpa_passphrase "$ssid" "$pass" | sed -n 's/^[[:space:]]*psk=\([0-9a-f]\{64\}\)$/    psk=\1/p' ;;
            *)
                echo "    key_mgmt=WPA-PSK SAE"
                echo "    ieee80211w=1"
                echo "    psk=\"$pass\""
                echo "    sae_password=\"$pass\"" ;;
            esac
        fi
        echo "}"
    } > /tmp/wpa.conf || exit 1
    chmod 600 /tmp/wpa.conf
    wpa_supplicant -B -i "$IF" -D nl80211 -c /tmp/wpa.conf >/tmp/wpa.log 2>&1 \
        || { echo "ERR: не запустился wpa_supplicant"; tail -n 3 /tmp/wpa.log; exit 1; }
    i=0
    while [ $i -lt 25 ]; do
        iw dev "$IF" link 2>/dev/null | grep -q "Connected to" && break
        sleep 1; i=$((i + 1))
    done
    if ! iw dev "$IF" link 2>/dev/null | grep -q "Connected to"; then
        killall wpa_supplicant 2>/dev/null
        if grep -qi "WRONG_KEY\|4-Way Handshake failed\|reason=15" /tmp/wpa.log; then echo "ERR: неверный пароль"
        else echo "ERR: не удалось подключиться к «$ssid». Проверь пароль и подойди ближе к роутеру"; fi
        exit 1
    fi
    udhcpc -i "$IF" -b -t 10 -T 2 -A 3 >/dev/null 2>&1
    i=0
    while [ -z "$(ip_of "$IF")" ] && [ $i -lt 20 ]; do sleep 1; i=$((i + 1)); done
    [ -n "$(ip_of "$IF")" ] || { echo "ERR: подключились к Wi-Fi, но роутер не выдал адрес"; exit 1; }
    # запомнить сеть на диске памяти — при следующей загрузке подключимся сами (wifi.sh auto)
    if grep -qs " /mnt/javaos " /proc/mounts; then
        umask 077
        { printf '%s\n' "$ssid"; printf '%s\n' "$pass"; } > /mnt/javaos/wifi-saved
    fi
    echo "OK: подключено к «$ssid», адрес $(ip_of "$IF")"
    ;;

status)
    if [ -n "$IF" ] && iw dev "$IF" link 2>/dev/null | grep -q "Connected to"; then
        s=$(iw dev "$IF" link | sed -n 's/^[[:space:]]*SSID: //p')
        echo "wifi|$s|$(ip_of "$IF")"
        exit 0
    fi
    for d in /sys/class/net/eth* /sys/class/net/en*; do
        [ -e "$d" ] || continue
        n=$(basename "$d"); a=$(ip_of "$n")
        [ -n "$a" ] && { echo "eth|$n|$a"; exit 0; }
    done
    echo "none"
    ;;

auto)
    # при загрузке: подключиться к запомненной сети (адаптер появляется не сразу — ждём до 20 с)
    S=/mnt/javaos/wifi-saved
    [ -f "$S" ] || exit 0
    i=0; while [ -z "$IF" ] && [ $i -lt 20 ]; do sleep 1; IF=$(iface); i=$((i + 1)); done
    [ -n "$IF" ] || exit 0
    ssid=$(sed -n 1p "$S"); pass=$(sed -n 2p "$S")
    for t in 1 2 3; do
        "$0" connect "$ssid" "$pass" && exit 0
        sleep 5
    done
    ;;

disconnect)
    rm -f /mnt/javaos/wifi-saved   # отключился сам — больше не подключаемся автоматически
    killall wpa_supplicant 2>/dev/null
    [ -n "$IF" ] && { pkill -f "udhcpc.*$IF" 2>/dev/null; ifconfig "$IF" 0.0.0.0 2>/dev/null; }
    echo "OK: отключено"
    ;;

*) sed -n '2,8p' "$0" ;;
esac
