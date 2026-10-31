#!/bin/sh
# Arch Linux внутри JavaOS (как WSL в Windows): отдельный корень, программы запускаются через chroot
# и показывают окна на общем экране (:0). Вызывается из JavaWin (приложение «Arch Linux», терминал).
#
#   arch.sh status                 — installed / missing
#   arch.sh extract <tar.zst>      — распаковать официальный bootstrap-архив Arch
#   arch.sh setup                  — зеркало, ключи pacman, шрифты
#   arch.sh exec <команда> [арг]   — выполнить внутри и дождаться (вывод — сюда)
#   arch.sh run "<командная строка>" — запустить программу в фоне (для ярлыков)

DATA=/mnt/javaos
[ -d "$DATA" ] && grep -q " $DATA " /proc/mounts || DATA=/home/tc/JavaWin
ROOT=${ARCH_ROOT:-$DATA/arch}
# «Загрузки» JavaWin: туда же складывают файлы Firefox и прочие программы (~/Downloads у root)
if [ "$DATA" = /mnt/javaos ]; then JW=$DATA/home; else JW=$DATA; fi
DL="$JW/C/Загрузки"
MIRROR='https://geo.mirror.pkgbuild.com/$repo/os/$arch'

mounted() { grep -q " $1 " /proc/mounts; }

mounts() {
    # корень Arch — отдельная точка монтирования (до всех остальных): тогда внутри можно сделать pivot_root,
    # а после него программа уже не «в chroot» и ей разрешены песочницы (user namespaces) — без них нет Steam
    mounted "$ROOT" || mount --bind "$ROOT" "$ROOT"
    for d in proc sys dev dev/pts dev/shm run tmp/.X11-unix; do mkdir -p "$ROOT/$d"; done
    mounted "$ROOT/proc"    || mount -t proc proc "$ROOT/proc"
    mounted "$ROOT/sys"     || mount -o bind /sys "$ROOT/sys"
    mounted "$ROOT/dev"     || mount -o bind /dev "$ROOT/dev"
    mounted "$ROOT/dev/pts" || mount -t devpts devpts "$ROOT/dev/pts"
    mounted "$ROOT/dev/shm" || mount -t tmpfs -o mode=1777 shm "$ROOT/dev/shm"
    mounted "$ROOT/run"     || mount -t tmpfs -o mode=755 run "$ROOT/run"
    mounted "$ROOT/tmp/.X11-unix" || mount -o bind /tmp/.X11-unix "$ROOT/tmp/.X11-unix"
    mkdir -p "$ROOT/run/user/0" && chmod 700 "$ROOT/run/user/0"
    # системная шина dbus (её запускает bt.sh) — для Discord, Bluetooth и прочих
    for s in /run/dbus /var/run/dbus /usr/local/var/run/dbus; do
        if [ -S "$s/system_bus_socket" ]; then
            mkdir -p "$ROOT/run/dbus"
            mounted "$ROOT/run/dbus" || mount -o bind "$s" "$ROOT/run/dbus"
            break
        fi
    done
    # звук: служба PulseAudio системы видна программам Arch (и Bluetooth-наушники тоже)
    if [ -d /run/user/0/pulse ] && ! mounted "$ROOT/run/user/0/pulse"; then
        mkdir -p "$ROOT/run/user/0/pulse" && mount -o bind /run/user/0/pulse "$ROOT/run/user/0/pulse"
    fi
    if [ -d "$DL" ] && ! mounted "$ROOT/root/Downloads"; then
        mkdir -p "$ROOT/root/Downloads"
        # что успели скачать до подключения — переносим в «Загрузки»
        for f in "$ROOT/root/Downloads"/* "$ROOT/root/Downloads"/.[!.]*; do [ -e "$f" ] && mv -n "$f" "$DL"/; done
        mount -o bind "$DL" "$ROOT/root/Downloads"
    fi
    cp /etc/resolv.conf "$ROOT/etc/resolv.conf" 2>/dev/null
    # номер машины — без него Firefox и другие не могут создать шину dbus
    if [ ! -s "$ROOT/etc/machine-id" ]; then
        id=$(cat /var/lib/dbus/machine-id /etc/machine-id 2>/dev/null | head -n 1)
        [ -n "$id" ] || id=$(cat /proc/sys/kernel/random/uuid | tr -d - )
        echo "$id" > "$ROOT/etc/machine-id"
    fi
    mkdir -p "$ROOT/var/lib/dbus" && [ -s "$ROOT/var/lib/dbus/machine-id" ] || cp "$ROOT/etc/machine-id" "$ROOT/var/lib/dbus/machine-id" 2>/dev/null
    # несколько зеркал Arch: одно «зависло» — pacman берёт следующее (раньше было одно, и большие установки обрывались)
    ml="$ROOT/etc/pacman.d/mirrorlist"
    if [ -f "$ml" ] && ! grep -q javaos-mirrors "$ml"; then
        {
            echo "# javaos-mirrors"
            for s in geo.mirror.pkgbuild.com mirror.yandex.ru/archlinux mirror.truenetwork.ru/archlinux \
                     ftp.fau.de/archlinux mirror.netcologne.de/archlinux mirrors.kernel.org/archlinux; do
                echo "Server = https://$s/\$repo/os/\$arch"
            done
        } > "$ml"
    fi
    # медленный интернет: не обрывать скачивание через 10 секунд «тишины»
    conf="$ROOT/etc/pacman.conf"
    [ -f "$conf" ] && ! grep -q "^DisableDownloadTimeout" "$conf" && sed -i "s/^\[options\]/[options]\nDisableDownloadTimeout/" "$conf"
    # 32-битные библиотеки (multilib) — нужны Steam и Wine
    if [ -f "$conf" ] && grep -q "^#\[multilib\]" "$conf"; then
        sed -i "/^#\[multilib\]/{s/^#//;n;s/^#//}" "$conf"
        chroot "$ROOT" /usr/bin/pacman -Sy --noconfirm >/dev/null 2>&1   # список пакетов multilib (иначе Steam не найдётся)
    fi
    # «замок» pacman остаётся, если компьютер выключили посреди установки, — тогда магазин и pacman
    # пишут «unable to lock database». Если pacman сейчас не работает, замок старый — убираем
    pgrep -x pacman >/dev/null 2>&1 || rm -f "$ROOT/var/lib/pacman/db.lck"
}

# видеокарта: пока не проверено, что 3D работает, программы рисуют процессором (иначе падают на nouveau)
GLENV="LIBGL_ALWAYS_SOFTWARE=1"
ZINK="MESA_LOADER_DRIVER_OVERRIDE=zink LIBGL_KOPPER_DRI2=1"
[ -f "$ROOT/.javaos-gpu-ok3" ] && GLENV="$(cat "$ROOT/.javaos-gpu-ok3")"

nvidia() { grep -qs 0x10de /sys/class/drm/card*/device/vendor; }

# NVIDIA: свободный драйвер NVK (Vulkan) + Zink (OpenGL поверх Vulkan) из Arch — один раз ставим и проверяем
gpu_setup() {
    nvidia || return 0
    [ -f "$ROOT/.javaos-gpu-tried3" ] && return 0
    . /tmp/javaos.env 2>/dev/null
    GLENV="LIBGL_ALWAYS_SOFTWARE=1"
    inside pacman -S --noconfirm --needed mesa vulkan-nouveau vulkan-icd-loader mesa-utils vulkan-tools >/tmp/gpu-setup.log 2>&1 || return 0
    # OpenGL через Vulkan (Zink): X-сервер JavaOS без DRI3, поэтому сам Mesa Zink не выбирает — просим явно
    GLENV="$ZINK"
    r=$(inside glxinfo -B 2>&1)
    {
        echo "=== $(date)"; echo "$r" | head -n 20
        echo "--- vulkan"; inside vulkaninfo --summary 2>&1 | grep -iE "deviceName|driverName|apiVersion|error" | head -n 8
    } > /tmp/gpu-check.log 2>&1
    [ -n "$BOOTMNT" ] && { cp /tmp/gpu-check.log "$BOOTMNT/javaos-gpu-log.txt"; sync; }
    touch "$ROOT/.javaos-gpu-tried3"
    # видеокарта заработала, если OpenGL рисует не процессор (llvmpipe/softpipe)
    # и что картинку можно вывести в окно: без DRI3 у X-сервера Zink считает на видеокарте, но окно
    # остаётся пустым (could not create swapchain) — тогда лучше процессор
    if echo "$r" | grep -q "OpenGL renderer" && ! echo "$r" | grep -qiE "llvmpipe|softpipe|could not create swapchain|No DRI3"; then
        echo "$ZINK" > "$ROOT/.javaos-gpu-ok3"
    else
        rm -f "$ROOT/.javaos-gpu-ok3"
        GLENV="LIBGL_ALWAYS_SOFTWARE=1"
    fi
}

inside() {
    mounts
    chroot "$ROOT" /usr/bin/env -i HOME=/root USER=root TERM=xterm DISPLAY=:0 LANG=C.UTF-8 \
        PATH=/usr/local/sbin:/usr/local/bin:/usr/bin XDG_RUNTIME_DIR=/run/user/0 PULSE_SERVER=unix:/run/user/0/pulse/native \
        NO_AT_BRIDGE=1 GTK_A11Y=none $GLENV "$@"
}

case "$1" in
    status)
        [ -x "$ROOT/usr/bin/pacman" ] && [ -f "$ROOT/.javaos-ready" ] && echo installed || echo missing
        ;;
    root)
        echo "$ROOT"
        ;;
    extract)
        [ -f "$2" ] || { echo "нет файла $2"; exit 1; }
        rm -rf "$DATA/.arch-new" && mkdir -p "$DATA/.arch-new" || exit 1
        echo "Распаковка…"
        zstd -dc "$2" | tar -x -C "$DATA/.arch-new" --numeric-owner -f - || { echo "ошибка распаковки"; exit 1; }
        rm -rf "$ROOT" && mv "$DATA/.arch-new/root.x86_64" "$ROOT" && rm -rf "$DATA/.arch-new" || exit 1
        echo "Распаковано в $ROOT"
        ;;
    setup)
        : # зеркала пишет mounts()
        conf="$ROOT/etc/pacman.conf"
        # песочница pacman требует Landlock, его может не быть в ядре; проверку места — mount-точки
        grep -q '^DisableSandbox' "$conf" || sed -i 's/^\[options\]/[options]\nDisableSandbox/' "$conf"
        sed -i 's/^#ParallelDownloads.*/ParallelDownloads = 5/; s/^CheckSpace/#CheckSpace/' "$conf"
        echo "Ключи pacman…"
        inside pacman-key --init || exit 1
        inside pacman-key --populate archlinux || exit 1
        echo "Обновление списка пакетов и шрифты…"
        inside pacman -Sy --noconfirm --needed ttf-dejavu xdotool || exit 1
        touch "$ROOT/.javaos-ready"
        echo "Готово"
        ;;
    exec)
        shift
        # вывод — сюда же (его показывает JavaWin) и в журнал; не получилось — журнал и состояние сети на флешку
        # (E:\javaos-store-log.txt): видно, почему магазин не скачал программу
        . /tmp/javaos.env 2>/dev/null
        { inside "$@"; echo $? > /tmp/arch-exec.rc; } 2>&1 | tee /tmp/arch-exec.log
        code=$(cat /tmp/arch-exec.rc 2>/dev/null || echo 1)
        if [ "$code" != 0 ] && [ -n "$BOOTMNT" ]; then
            {
                echo "=== $(date) код $code: $*"
                tail -n 60 /tmp/arch-exec.log
                echo "--- сеть"; ifconfig 2>&1 | grep -E "^[a-z]|inet addr"; route -n 2>&1
                echo "--- dns"; cat /etc/resolv.conf; cat "$ROOT/etc/resolv.conf" 2>&1
                echo "--- ping"; ping -c 2 -W 3 1.1.1.1 2>&1 | tail -n 2
                echo "--- имя"; nslookup geo.mirror.pkgbuild.com 2>&1 | tail -n 3
                echo "--- wifi"; iw dev 2>&1 | head -n 12; tail -n 15 /tmp/wpa.log 2>/dev/null
            } > "$BOOTMNT/javaos-store-log.txt" 2>&1
            sync
        fi
        exit "$code"
        ;;
    run)
        shift
        cmd="$*"
        prog=$(basename "${cmd%% *}" | tr -cd 'A-Za-z0-9._-')   # у каждой программы свой журнал на флешке
        # программы на Electron/Chromium (Discord, VS Code, Chromium…) от root без этого ключа не запускаются
        case "${cmd%% *}" in
            */discord*|discord*|*/code|code|chromium|*/chromium|google-chrome*|*/google-chrome*|vesktop*|*/vesktop*|element-desktop*|*/element-desktop*|obsidian*|*/obsidian*|*electron*)
                # ключ — сразу после имени программы: всё после «--» программа считает адресом
                case "$cmd" in
                    *--no-sandbox*) ;;
                    *" "*) cmd="${cmd%% *} --no-sandbox ${cmd#* }" ;;
                    *) cmd="$cmd --no-sandbox" ;;
                esac ;;
        esac
        # прошлая версия JavaOS ставила Discord запрет на обновление — новому Discord он мешает установиться
        sed -i 's/"SKIP_HOST_UPDATE": true,\{0,1\} *//' "$ROOT/root/.config/discord/settings.json" 2>/dev/null
        gpu_setup
        ZINK="MESA_LOADER_DRIVER_OVERRIDE=zink LIBGL_KOPPER_DRI2=1"
[ -f "$ROOT/.javaos-gpu-ok3" ] && GLENV="$(cat "$ROOT/.javaos-gpu-ok3")"
        # Discord: модули (голос и др.) могли недокачаться, пока он запускался невидимым, — тогда он пишет
        # «Discord повреждён», а голосовой чат «Отключено». Один раз удаляем их — Discord скачает заново
        case "${cmd%% *}" in
            */discord*|discord*)
                dc="$ROOT/root/.config/discord"
                [ -d "$dc" ] && [ ! -f "$dc/.javaos-modfix" ] && { rm -rf "$dc/module_data"; touch "$dc/.javaos-modfix"; }
                # в JavaOS нет трея: «свернуть в трей» оставляло белое окно — пусть крестик закрывает Discord
                s="$dc/settings.json"
                if [ -f "$s" ] && ! grep -q MINIMIZE_TO_TRAY "$s"; then sed -i '0,/{/s//{ "MINIMIZE_TO_TRAY": false, "OPEN_ON_STARTUP": false,/' "$s"
                elif [ ! -f "$s" ]; then mkdir -p "$dc"; echo '{ "MINIMIZE_TO_TRAY": false, "OPEN_ON_STARTUP": false }' > "$s"
                else sed -i 's/"MINIMIZE_TO_TRAY": *true/"MINIMIZE_TO_TRAY": false/' "$s"; fi ;;
        esac
        # Steam не запускается от root — запускаем от обычного пользователя «player» (X и звук открыты всем)
        case "${cmd%% *}" in
            */steam|steam|steam-runtime|*/steam-runtime|*/vlc|vlc)
                inside sh -c "id player >/dev/null 2>&1 || useradd -m -G audio,video player || useradd -m player" >/dev/null 2>&1
                mkdir -p "$ROOT/run/user/1000/pulse"
                inside chown -R player:player /run/user/1000 >/dev/null 2>&1
                mounted "$ROOT/run/user/1000/pulse" || { [ -d /run/user/0/pulse ] && mount -o bind /run/user/0/pulse "$ROOT/run/user/1000/pulse"; }
                # su в chroot без PAM падает (код 71) — setpriv меняет пользователя без входа и паролей
                # Steam — сразу его настоящий скрипт с подробным журналом шагов (bash -x)
                bin=${cmd%% *}
                case "$bin" in *steam*) bin="/usr/lib/steam/steam" ;; esac
                # подробности для журнала: есть ли пользователь, его папка, и каждый шаг скрипта steam (sh -x)
                # unshare + pivot_root: своё пространство монтирований, корень Arch становится настоящим корнем —
                # из chroot ядро песочницы не разрешает («Steam now requires user namespaces»)
                cmd="unshare -m --propagation private sh -c 'mkdir -p /.oldroot; pivot_root / /.oldroot && umount -l /.oldroot; echo --- id; id player; echo --- steam; exec setpriv --reuid=player --regid=player --init-groups env HOME=/home/player USER=player LOGNAME=player DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/1000 PULSE_SERVER=unix:/run/user/1000/pulse/native $GLENV $bin'" ;;
        esac
                # звуковая библиотека нужна почти всем программам с окном, но в зависимостях пакетов её часто нет
        [ -e "$ROOT/usr/lib/libasound.so.2" ] || inside pacman -S --noconfirm --needed alsa-lib >/dev/null 2>&1
        # и библиотека PulseAudio: без неё у Discord не грузится голос («Discord повреждён», голосовой чат «Отключено»),
        # а браузеры и игры не видят Bluetooth-наушники
        [ -e "$ROOT/usr/lib/libpulse.so.0" ] || inside pacman -S --noconfirm --needed libpulse >/dev/null 2>&1
        # журнал — на флешку (E:\javaos-arch-log.txt): видно, почему программа не открылась
        LOG=$DATA/arch-${prog:-app}.log
        . /tmp/javaos.env 2>/dev/null
        (
            { echo "=== $(date) $cmd"; } >"$LOG"
            # пока программа работает — журнал на флешку каждые 15 с (её могут не закрыть до выключения)
            if [ -n "$BOOTMNT" ]; then
                ( while sleep 15; do tail -c 60000 "$LOG" > "$BOOTMNT/javaos-log-${prog:-app}.txt" 2>/dev/null; sync; done ) &
                cp_pid=$!
            fi
            inside /bin/sh -c "exec $cmd" >>"$LOG" 2>&1
            echo "=== программа закрылась, код $?" >>"$LOG"
            # Steam пишет свои ошибки не сюда, а в свой журнал — приложить его конец
            sl="$ROOT/home/player/.local/share/Steam/logs/console-linux.txt"
            [ "$prog" = steam ] && [ -f "$sl" ] && { echo "=== журнал Steam (console-linux.txt)"; tail -n 80 "$sl"; } >>"$LOG"
            [ -n "$cp_pid" ] && kill "$cp_pid" 2>/dev/null
            if [ -n "$BOOTMNT" ]; then
                tail -c 60000 "$LOG" > "$BOOTMNT/javaos-log-${prog:-app}.txt" 2>/dev/null; sync
            fi
        ) &
        ;;
    *)
        echo "использование: arch.sh status|extract|setup|exec|run"
        exit 2
        ;;
esac
