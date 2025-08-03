#!/bin/bash

# Copy the tests across.
adb sync

if tty -s; then
  green="\033[1;32m"
  red="\033[1;31m"
  plain="\033[0m"
else
  green=""
  red=""
  plain=""
fi

failures=0

check_failure() {
  if [ $? -eq 0 ]; then
    echo -e "${green}[PASS]${plain}"
  else
    failures=$(($failures+1))
    echo -e "${red}[FAIL]${plain}"
  fi
}

if [ -e "$ANDROID_PRODUCT_OUT/data/nativetest/tinyxml2-xmltest/tinyxml2-xmltest" ]; then
  adb shell "cd /data/nativetest/tinyxml2-xmltest; ./tinyxml2-xmltest"
  check_failure
fi
if [ -e "$ANDROID_PRODUCT_OUT/data/nativetest64/tinyxml2-xmltest/tinyxml2-xmltest" ]; then
  adb shell "cd /data/nativetest64/tinyxml2-xmltest; ./tinyxml2-xmltest"
  check_failure
fi

echo "_________________________________________________________________________"
echo
if [ $failures -ne 0 ]; then
  echo -e "${red}FAILED${plain}: $failures"
fi
exit $failures
