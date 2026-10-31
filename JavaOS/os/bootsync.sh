#!/bin/sh
# runs before login; the boot waits for it
/usr/bin/sethostname box
/opt/javaos/load.sh
/opt/bootlocal.sh &
