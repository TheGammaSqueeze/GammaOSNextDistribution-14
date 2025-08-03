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

# Creates an car AVD configuration
#
# Arguments:
#   $1: (AVD_NAME) Name of the AVD
#   $2: (WORKDIR) where to store the avd config
#   $3: (AVD_IMAGE_DIR) AVD System image dir
#   $4: (DISPLAY_DENSITY) Display density
#   $5: (DISPLAY_WIDTH) Display width px
#   $6: (DISPLAY_HEIGHT) Display height px
#   $7: (RAM_MB) AVD RAM size in MB
#   $8: (ABD) AVD ABI, e.g. x86 or x86_64

AVD_NAME=$1
WORKDIR=$2
AVD_IMAGE_DIR=$3
DISPLAY_DENSITY=$4
DISPLAY_WIDTH=$5
DISPLAY_HEIGHT=$6
RAM_MB=$7
ABI=$8

mkdir -p ${WORKDIR}/.android/avd/${AVD_NAME}.avd

# avd_name.ini
AVD_INI_FILE=${WORKDIR}/.android/avd/${AVD_NAME}.ini
echo Creating $AVD_INI_FILE
echo "avd.ini.encoding=UTF-8" >> $AVD_INI_FILE
echo "path=${WORKDIR}/.android/avd/${AVD_NAME}.avd" >> $AVD_INI_FILE
echo "path.rel=avd/${AVD_NAME}.avd" >> $AVD_INI_FILE

# avd_name.avd/config.ini
AVD_CONFIG_INI=${WORKDIR}/.android/avd/${AVD_NAME}.avd/config.ini
echo Creating $AVD_CONFIG_INI $DISPLAY_WIDTH x $DISPLAY_HEIGHT @ $DISPLAY_DENSITY with $RAM_MB
echo AVD Img: $AVD_IMAGE_DIR
cat <<EOT >> $AVD_CONFIG_INI

image.sysdir.1 = ${AVD_IMAGE_DIR}
hw.lcd.density = ${DISPLAY_DENSITY}
hw.lcd.width = ${DISPLAY_WIDTH}
hw.lcd.height = ${DISPLAY_HEIGHT}
AvdId = ${AVD_NAME}
avd.ini.displayname = ${AVD_NAME}
hw.ramSize = ${RAM_MB}
abi.type = ${ABI}

tag.display = Automotive
tag.id = android-automotive
hw.device.manufacturer = google
hw.device.name = hawk
avd.ini.encoding = UTF-8
disk.dataPartition.size = 6442450944
fastboot.chosenSnapshotFile =
fastboot.forceChosenSnapshotBoot = no
fastboot.forceColdBoot = no
fastboot.forceFastBoot = yes
hw.accelerometer = no
hw.arc = false
hw.audioInput = yes
hw.battery = no
hw.camera.back = None
hw.camera.front = None
hw.cpu.arch = x86_64
hw.cpu.ncore = 4
hw.dPad = no
hw.device.hash2 = MD5:1fdb01985c7b4d7c19ec309cc238b0f9
hw.gps = yes
hw.gpu.enabled = yes
hw.gpu.mode = auto
hw.initialOrientation = landscape
hw.keyboard = yes
hw.keyboard.charmap = qwerty2
hw.keyboard.lid = false
hw.mainKeys = no
hw.sdCard = no
hw.sensors.orientation = no
hw.sensors.proximity = no
hw.trackBall = no
runtime.network.latency = none
runtime.network.speed = full
EOT
