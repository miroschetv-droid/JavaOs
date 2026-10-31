#!/bin/sh
# скорость записи кадра 1024x768x32 в обычную память и в видеопамять (framebuffer)
dd if=/dev/zero of=/tmp/fbt bs=3145728 count=1 2>/dev/null
ms() { echo $(( $(date +%s%N) / 1000000 )); }
a=$(ms); for i in 1 2 3 4 5 6 7 8 9 10; do cat /tmp/fbt > /tmp/fbt2; done; b=$(ms)
for i in 1 2 3 4 5 6 7 8 9 10; do cat /tmp/fbt > /dev/fb0; done; c=$(ms)
echo "RAM:  $(( (b - a) / 10 )) ms per frame"
echo "VRAM: $(( (c - b) / 10 )) ms per frame"
rm -f /tmp/fbt /tmp/fbt2
