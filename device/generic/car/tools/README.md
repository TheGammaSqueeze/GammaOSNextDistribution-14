# AAOS car AVD tools
This folder contains scripts to help you to build, run and share car AVD images. We intentailly keep this on the AOSP main trunk for vairous branches from Android 10.

## Download the tools
- To clone the tool only: git_clone_projects.sh
- Link it into an Android source tree, e.g.:
```
ln -s $HOME/avd/car/tools $ANDROID_BUILD_TOP/device/generic/car/tools
```

## Build an AVD image
This builds AOSP car x86_64 userdebug AVD.
```
. device/generic/car/tools/mk_car_avd.sh
```

## Pack an AVD image
The standard way to pack an AVD image is to build emu_img_zip target. Whereas this script is just for a quick validation on other host via Android Emulator during development. Which can pack necessary image files to $HOME/Downloads/x86_64
```
device/generic/car/tools/test_pack_avd_img.sh
```
* You can use it as an example to create your own script for an AVD target by pack_avd_img.sh.
* pack_avd_img.sh contains brief descriptions of each file needed.
* AVD device profile example: x86_64/devices.xml
* SDK addon xml example: aaos-sys-img2-1.xml
  * You should edit the xml, e.g. ZIP_FILE_SIZE, SHASUM, zip url & etc. accordingly.

## Start an AVD

### New AVD config
To create a clean local AVD config and run with the latest Android Emulator engine from the SDK.
```
device/generic/car/tools/test_run_local_avd.sh
```

To run the existing AVD config.
```
device/generic/car/tools/run_local_avd.sh
```

### Multiple AVDs
run_local_avd.sh allows you to setup and run multiple AVDs. You can crease a script for each of them. e.g.
```
AVD_IMAGE_DIR=$HOME/avd/aosp_car_x86_64/x86_64 \
  WORKDIR=$HOME/avd/aosp_car_x86_64 \
  ABI="x86_64" \
  $SCRIPT_DIR/tools/run_local_avd.sh
```
* Deleting the WORKDIR will let the script create it from scratch next time as the 1st run.

### Change the config
The default AVD config.ini are created as the following default settings at the 1st run. You can also change them at the 1st run, e.g.
```
ANDROID_SDK_ROOT="/Users/$USER/Library/Android/sdk" \
  WORKDIR="$HOME/avd/aosp_car_x86_64" \
  AVD_IMAGE_DIR="$HOME/avd/aosp_car_x86_64/x86_64" \
  ABI="x86_64" \
  DISPLAY_DENSITY=213 \
  DISPLAY_WIDTH=1920 \
  DISPLAY_HEIGHT=1080 \
  RAM_MB=3584 \
  ./run_local_avd.sh
```

The AVD can also be changed by editing the AVD config.ini directily, e.g. at:
```
$WORKDIR/.android/avd/my_car_avd_x86_64.avd/config.ini
```

### Android Emulator startup options
You can append [Android Emulator Command-line startup options](https://developer.android.com/studio/run/emulator-commandline#common) as needed. E.g.
  * to wipe user data:  ./run_local_avd.sh -wipe-data
  * to cold boot: ./run_local_avd.sh -no-snapshot-load
