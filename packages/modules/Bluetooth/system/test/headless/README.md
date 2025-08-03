##
## bluetooth headless
##
## A device-side executable that consists of a binary executable
## driving the Android libbluetooth libraries.
##

Requirements:
    1. Android installation,
    2. Root access to adb connected Android device.

Build: Source, lunch and build as typical Android target for selected device and architecture.
    cd $ANDROID_BUILD_TOP
    . build/envsetup.sh && lunch <target>
    make bt_headless

Install: Push the binary to an executable area on target device.
    adb push ${ANDROID_PRODUCT_OUT}/system/bin/bt_headless /data/data/.

Prepare: Ensure the system is queisced to prevent resource conflicts from the bluetooth process.
    adb shell stop

Run: Script or directly execute the target file.
    adb shell /data/data/bt_headless --loop=10 nop
    ```
    [1102/174836.145418:INFO:btif_config_cache.cc(67)] BtifConfigCache, capacity: 10000
    Nop loop:0
    Nop loop:1
    Nop loop:2
    Nop loop:3
    Nop loop:4
    Nop loop:5
    Nop loop:6
    Nop loop:7
    Nop loop:8
    Nop loop:9
    ```
