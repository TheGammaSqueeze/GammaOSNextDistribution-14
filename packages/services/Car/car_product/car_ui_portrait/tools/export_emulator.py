#!/usr/bin/env python3

import subprocess
import os
import sys
from shutil import copy2, copytree, rmtree
from argparse import ArgumentParser as AP

# Mostly adapted from https://cs.android.com/android/platform/superproject/+/master:device/generic/car/tools/run_local_avd.sh

def fromTop(path):
    return os.path.join(os.environ['ANDROID_BUILD_TOP'], path)

def fromProductOut(path):
    return os.path.join(os.environ['ANDROID_PRODUCT_OUT'], path)

def copyImages(outputDir, abi):
    outputDir = os.path.join(outputDir, abi)
    os.mkdir(outputDir)

    try:
        copy2(fromProductOut('system-qemu.img'), os.path.join(outputDir, 'system.img'))
        copy2(fromProductOut('vendor-qemu.img'), os.path.join(outputDir, 'vendor.img'))
        if os.path.isfile(fromProductOut('kernel-ranchu-64')):
            copy2(fromProductOut('kernel-ranchu-64'), outputDir)
        else:
            copy2(fromProductOut('kernel-ranchu'), outputDir)
        copy2(fromProductOut('ramdisk-qemu.img'), os.path.join(outputDir, 'ramdisk.img'))
        copy2(fromProductOut('encryptionkey.img'), outputDir)
        # take prebuilt userdata.img
        # Ref: https://cs.android.com/android/platform/superproject/+/master:development/build/sdk.atree?q=userdata.img&ss=android%2Fplatform%2Fsuperproject:development%2Fbuild%2F
        copy2(fromTop('device/generic/goldfish/data/etc/userdata.img'), outputDir)
        copytree(fromProductOut('data'), os.path.join(outputDir, 'data'), dirs_exist_ok=True)
        copy2(fromProductOut('system/build.prop'), os.path.join(outputDir, 'build.prop'))
        copy2(fromProductOut('VerifiedBootParams.textproto'), outputDir)
        copy2(fromProductOut('config.ini'), outputDir)
        copy2(fromProductOut('advancedFeatures.ini'), outputDir)
    except FileNotFoundError as f:
        print("File not found: "+f.filename+", did you build android first?")
        sys.exit(1)

def readScreenDimens(configini):
    width = 1080
    height = 1920
    density = 160
    with open(configini, 'r') as f:
        for line in f:
            parts = line.split('=')
            if len(parts) != 2:
                continue
            parts[0] = parts[0].strip()
            parts[1] = parts[1].strip()
            if parts[0] == 'hw.lcd.width':
                width = int(parts[1])
            if parts[0] == 'hw.lcd.height':
                height = int(parts[1])
            if parts[0] == 'hw.lcd.density':
                density = int(parts[1])
    return (width, height, density)

def buildAVD(outputDir, abi):
    os.makedirs(os.path.join(outputDir, 'android/avd/my_car_avd.avd/'), exist_ok=True)
    with open(os.path.join(outputDir, 'android/avd/my_car_avd.ini'), 'w') as f:
        f.write('avd.ini.encoding=UTF-8\n')
        f.write('path=required_but_we_want_to_use_path.rel_instead\n')
        f.write('path.rel=avd/my_car_avd.avd\n')

    width, height, density = readScreenDimens(fromProductOut('config.ini'))

    with open(os.path.join(outputDir, 'android/avd/my_car_avd.avd/config.ini'), 'w') as f:
        f.write(f'''
image.sysdir.1 = unused_because_passing_-sysdir_to_emulator
hw.lcd.density = {density}
hw.lcd.width = {width}
hw.lcd.height = {height}
AvdId = my_car_avd
avd.ini.displayname = my_car_avd
hw.ramSize = 3584
abi.type = {abi}

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
''')

def genStartScript(outputDir):
    filepath = os.path.join(outputDir, 'start_emu.sh')
    with open(os.open(filepath, os.O_CREAT | os.O_WRONLY, 0o750), 'w') as f:
        f.write(f'''
# This file is auto-generated from export_emulator.py
OS="$(uname -s)"
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
if ! [[ -d $ANDROID_SDK_ROOT ]]; then
    echo Could not find android SDK root. Did you install an SDK with android studio?
    exit
fi

# TODO: this ANDROID_EMULATOR_HOME may need to not be changed.
# we had to change it so we could find the avd by a relative path,
# but changing it means makes it give an "emulator is out of date"
# warning

# TODO: You shouldn't need to pass -sysdir, it should be specified
# in the avd ini file. But I couldn't figure out how to make that work
# with a relative path.

ANDROID_EMULATOR_HOME=$(dirname $0)/android \
ANDROID_AVD_HOME=android/avd \
ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT \
$ANDROID_SDK_ROOT/emulator/emulator \
-avd my_car_avd -sysdir x86_64 $@
''')


def main():
    parser = AP(description="Export the current build as a sharable emulator")
    parser.add_argument('-o', '--output', default="/tmp/exported_emulator",
                        help='Output folder. Defaults to /tmp/exported_emulator. Will wipe any existing contents!')
    args = parser.parse_args()

    if 'ANDROID_BUILD_TOP' not in os.environ or 'ANDROID_PRODUCT_OUT' not in os.environ:
        print("Please run lunch first")
        sys.exit(1)

    if os.path.isfile(args.output):
        print("Something already exists at "+args.output)
        sys.exit(1)

    if not os.path.isdir(os.path.dirname(args.output)):
        print("Parent directory of "+args.output+" must already exist")
        sys.exit(1)

    rmtree(args.output, ignore_errors=True)
    os.mkdir(args.output)

    copyImages(args.output, 'x86_64')
    buildAVD(args.output, 'x86_64')
    genStartScript(args.output)
    print("Done. Exported to "+args.output)


if __name__ == "__main__":
    main()
