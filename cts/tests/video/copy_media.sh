#!/bin/sh
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

## script to install cts video test files manually

adbOptions=" "
resLabel=CtsVideoTestCases-1.3
srcDir="/tmp/$resLabel"
tgtDir="/sdcard/test"
usage="Usage: $0 [-h] [-s serial]"

if [ $# -gt 0 ]; then
  if [ "$1" = "-h" ]; then
    echo $usage
    exit 1
  elif [ "$1" = "-s" -a "$2" != "" ] ; then
    adbOptions=""$1" "$2""
  else
    echo "bad options"
    echo $usage
    exit 1
  fi
fi

## download resources if not already done
if [ ! -f "/tmp/$resLabel.zip" ]; then
  wget "https://dl.google.com/android/xts/cts/tests/video/$resLabel.zip" -O /tmp/$resLabel.zip
fi
unzip -qo "/tmp/$resLabel" -d $srcDir

## install on target device
echo "adb $adbOptions push $srcDir $tgtDir"
adb $adbOptions shell mkdir -p $tgtDir
adb $adbOptions push $srcDir/. $tgtDir
