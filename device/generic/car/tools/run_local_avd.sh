#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
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

# See test_run_local_avd.sh for examples

echo Run a local AVD config in WORKDIR by Android Emulator engine from the SDK

OS="$(uname -s)"
echo Running on $OS
if [[ $OS == "Linux" ]]; then
    DEFAULT_ANDROID_SDK_ROOT="$HOME/Android/Sdk"
elif [[ $OS == "Darwin" ]]; then
    DEFAULT_ANDROID_SDK_ROOT="/Users/$USER/Library/Android/sdk"
else
    echo Sorry, this does not work on $OS
    exit
fi

if [[ -z $ANDROID_SDK_ROOT ]]; then
    ANDROID_SDK_ROOT="$DEFAULT_ANDROID_SDK_ROOT"
fi

if [[ -z $WORKDIR ]]; then
    WORKDIR="$HOME/workdir"
fi

if [[ -z $ANDROID_AVD_HOME ]]; then
    ANDROID_AVD_HOME="$WORKDIR/.android/avd"
fi

if [[ -z $ABI ]]; then
    ABI="x86_64"
fi

if [[ -z $AVD_IMAGE_DIR ]]; then
    AVD_IMAGE_DIR="$WORKDIR/$ABI"
fi

if [[ -z $AVD_NAME ]]; then
    AVD_NAME="my_car_avd_$ABI"
fi

if [[ -z $DISPLAY_DENSITY ]]; then
    DISPLAY_DENSITY=213
fi

if [[ -z $DISPLAY_WIDTH ]]; then
    DISPLAY_WIDTH=1920
fi

if [[ -z $DISPLAY_HEIGHT ]]; then
    DISPLAY_HEIGHT=1080
fi

if [[ -z $RAM_MB ]]; then
    # 3.5 GB as x86 AVD is limited to 4g
    RAM_MB=3584
fi

# Get the script dir
MY_NAME=$0
MY_FILENAME=${MY_NAME##*/}  # = "name.sh"
MY_DIR=${MY_NAME%/$MY_FILENAME}  # = "/path/to"

if ! [[ -f "$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini" ]]; then
    # Create the AVD config as there is no one
    $MY_DIR/create_avd_config.sh "$AVD_NAME" "$WORKDIR" "$AVD_IMAGE_DIR" \
        "$DISPLAY_DENSITY" "$DISPLAY_WIDTH" "$DISPLAY_HEIGHT" "$RAM_MB" "$ABI"
fi

echo Running "$ANDROID_AVD_HOME/$AVD_NAME.avd" by $ANDROID_SDK_ROOT
# Tell emu where to find my car AVD & SDK
ANDROID_AVD_HOME=$ANDROID_AVD_HOME \
    ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT \
    $ANDROID_SDK_ROOT/emulator/emulator \
    -avd $AVD_NAME \
    $@ &

echo
sleep 30
echo a. Supported features
adb shell pm list features

echo
echo b. GAS versions
adb shell pm list packages --show-versioncode | grep google
adb shell pm list packages --show-versioncode | grep vending

echo
echo c. AVD Memory Info
adb shell "cat /proc/meminfo"

echo
echo d. AVD Prop and processes
adb shell getprop
adb shell ps
