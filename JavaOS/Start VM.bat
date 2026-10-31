@echo off
rem JavaOS in a QEMU window (GTK + OpenGL: the GPU scales the picture, the mouse is mapped correctly). Fullscreen: Ctrl+Alt+F. Mouse works without capture (usb-tablet).
cd /d "%~dp0"
rem a freshly built image waits as JavaOS-new.iso while the old one is in use
if exist JavaOS-new.iso move /y JavaOS-new.iso JavaOS.iso >nul
rem shared clipboard Windows <-> JavaOS (listens on 127.0.0.1:7777 only, exits after the VM closes)
start "" "F:\JavaWin\dist\JavaWin\runtime\bin\javaw.exe" -cp "%~dp0tools\JavaWin-clip.jar" javawin.ClipHost
start "" "C:\Program Files\qemu\qemu-system-x86_64.exe" -name "JavaOS" -m 4096 -smp 8 -cpu host -drive "file=%~dp0disk.qcow2,format=qcow2,if=ide,index=0" -cdrom "%~dp0JavaOS.iso" -boot d -vga none -device virtio-vga -rtc base=localtime -audiodev sdl,id=snd -device intel-hda -device hda-output,audiodev=snd -display gtk,gl=on,zoom-to-fit=on,grab-on-hover=on -usb -device usb-tablet,id=javaos-tablet -monitor tcp:127.0.0.1:4444,server,nowait -accel whpx -accel tcg
