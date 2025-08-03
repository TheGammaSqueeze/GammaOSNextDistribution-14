#!/bin/bash

# Copy the tests across.
adb shell rm -rf /data/local/tmp/bc-tests/
adb shell mkdir /data/local/tmp/bc-tests/
adb shell mkdir /data/local/tmp/bc-tests/scripts/
adb push tests/ /data/local/tmp/bc-tests/
adb push scripts/functions.sh /data/local/tmp/bc-tests/scripts/

if tty -s; then
  dash_t="-t"
else
  dash_t=""
fi

exec adb shell $dash_t /data/local/tmp/bc-tests/tests/all.sh -n bc 0 1 0 0 0 bc
